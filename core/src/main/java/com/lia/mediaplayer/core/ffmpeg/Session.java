package com.lia.mediaplayer.core.ffmpeg;

import com.lia.mediaplayer.core.audio.PcmRing;
import com.lia.mediaplayer.core.lifecycle.CoreExecutors;
import com.lia.mediaplayer.core.tools.ManagedProcess;
import com.lia.mediaplayer.core.tools.ProcessRunner;
import com.lia.mediaplayer.core.video.FramePool;
import com.lia.mediaplayer.core.video.FrameRing;
import com.lia.mediaplayer.core.video.VideoFrame;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One ffmpeg process playing one stretch of media: from a start position until the end, a
 * seek or a track change. Frames from an older session never reach the player, because each
 * session has rings of its own.
 *
 * <p>The picture arrives on stdout as PAM frames and goes into the {@link FrameRing}. The sound
 * arrives on a TCP connection ffmpeg opens back to a listener on the loopback interface, and
 * goes into the {@link PcmRing}: one process cannot write both to stdout, Java cannot hand a
 * child a second pipe, and a named pipe would not work on Windows. The listener accepts one
 * connection and closes. Another local process could in principle win the race for it; doing
 * so needs code already running on the machine and yields audible noise. Binding to anything
 * but loopback would put the port on the network, so it is spelled out.
 *
 * <p>The two outputs pace each other: one process has one demuxer, so a full ring on either
 * side holds both back. That is the intended back-pressure, and why a player without a view
 * still drains its frame ring.
 */
public final class Session implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(Session.class);

    /** ffmpeg connects within milliseconds once it has opened its inputs; a slow server can take a while. */
    private static final int ACCEPT_TIMEOUT_MILLIS = 30_000;

    /** What a session reports, on its own threads. */
    public interface Listener {
        void firstVideoFrame(long generation, int width, int height);

        void firstAudio(long generation);

        /** The process has exited and everything it wrote is in the rings. */
        void finished(long generation, Outcome outcome);
    }

    /**
     * How the process ended.
     *
     * @param closed the session was closed, so the exit says nothing about the media
     * @param stderr the last lines ffmpeg wrote
     */
    public record Outcome(int exitCode, boolean closed, String stderr) {
        /** Ran to the end of the media. */
        public boolean completed() {
            return !closed && exitCode == 0;
        }
    }

    private final String name;
    private final long generation;
    private final SessionPlan plan;
    private final ManagedProcess process;
    private final @Nullable FrameRing frames;
    private final @Nullable FramePool pool;
    private final @Nullable PcmRing pcm;
    private final @Nullable ServerSocket audioListener;
    private final Listener listener;
    private volatile @Nullable Socket audioSocket;
    private volatile boolean closed;

    private Session(
            String name,
            long generation,
            SessionPlan plan,
            ManagedProcess process,
            @Nullable FrameRing frames,
            @Nullable FramePool pool,
            @Nullable PcmRing pcm,
            @Nullable ServerSocket audioListener,
            Listener listener) {
        this.name = name;
        this.generation = generation;
        this.plan = plan;
        this.process = process;
        this.frames = frames;
        this.pool = pool;
        this.pcm = pcm;
        this.audioListener = audioListener;
        this.listener = listener;
    }

    /**
     * Starts ffmpeg and its readers.
     *
     * @param frames where the picture goes; required when the plan writes it
     * @param pcm where the sound goes; required when the plan writes it
     */
    public static Session start(
            String name,
            long generation,
            String ffmpeg,
            SessionPlan plan,
            ProcessRunner runner,
            @Nullable FrameRing frames,
            @Nullable FramePool pool,
            @Nullable PcmRing pcm,
            Listener listener)
            throws IOException {
        if (plan.video() && (frames == null || pool == null)) {
            throw new IllegalArgumentException("A session writing the picture needs a frame ring and pool");
        }
        if (plan.audio() && pcm == null) {
            throw new IllegalArgumentException("A session writing the sound needs a PCM ring");
        }
        ServerSocket server = null;
        String audioTarget = null;
        if (plan.audio()) {
            server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            server.setSoTimeout(ACCEPT_TIMEOUT_MILLIS);
            audioTarget = "tcp://" + hostLiteral(server.getInetAddress()) + ":" + server.getLocalPort();
        }
        ManagedProcess process;
        try {
            process = runner.start(name, FfmpegCommand.session(ffmpeg, plan, audioTarget));
        } catch (IOException | RuntimeException e) {
            closeQuietly(server);
            throw e;
        }
        var session = new Session(name, generation, plan, process, frames, pool, pcm, server, listener);
        session.startThreads();
        return session;
    }

    private void startThreads() {
        Thread video = plan.video() ? CoreExecutors.virtualThread("lmp-" + name + "-video", this::readVideo) : null;
        Thread audio = plan.audio() ? CoreExecutors.virtualThread("lmp-" + name + "-audio", this::readAudio) : null;
        CoreExecutors.virtualThread("lmp-" + name + "-watch", () -> watch(video, audio));
    }

    public long generation() {
        return generation;
    }

    private void readVideo() {
        FrameRing ring = frames;
        FramePool frames = pool;
        if (ring == null || frames == null) {
            return;
        }
        long startMicros = plan.start().toNanos() / 1000;
        double interval = plan.frameIntervalMicros();
        InputStream in = process.stdout();
        long index = 0;
        try {
            while (!closed) {
                PamReader.Header header = PamReader.readHeader(in);
                if (header == null) {
                    break;
                }
                VideoFrame frame = frames.acquire(header.width(), header.height());
                if (in.readNBytes(frame.pixels(), 0, header.frameBytes()) < header.frameBytes()) {
                    frames.release(frame);
                    break;
                }
                // Constant output rate, so the n-th frame is due n intervals after the start.
                frame.stamp(generation, startMicros + Math.round(index * interval));
                if (index++ == 0) {
                    listener.firstVideoFrame(generation, header.width(), header.height());
                }
                if (!ring.put(frame)) {
                    break;
                }
            }
        } catch (IOException e) {
            if (!closed) {
                LOGGER.debug("{}: picture stream ended: {}", name, e.toString());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void readAudio() {
        PcmRing ring = pcm;
        ServerSocket server = audioListener;
        if (ring == null || server == null) {
            return;
        }
        try (Socket socket = server.accept()) {
            audioSocket = socket;
            closeQuietly(server);
            InputStream in = socket.getInputStream();
            boolean first = true;
            while (!closed && ring.fillFrom(in) > 0) {
                if (first) {
                    first = false;
                    listener.firstAudio(generation);
                }
            }
        } catch (SocketTimeoutException e) {
            if (!closed) {
                LOGGER.warn("{}: ffmpeg never connected its sound output", name);
            }
        } catch (IOException e) {
            // Closed: the session ended, or ffmpeg exited before connecting.
        } finally {
            ring.markEndOfStream();
        }
    }

    private void watch(@Nullable Thread video, @Nullable Thread audio) {
        int exitCode;
        try {
            exitCode = process.waitFor();
            // An accept that will never be answered: ffmpeg is gone.
            closeQuietly(audioListener);
            if (video != null) {
                video.join();
            }
            if (audio != null) {
                audio.join();
            }
            process.awaitStderr();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        listener.finished(generation, new Outcome(exitCode, closed || process.wasKilled(), process.stderrTail()));
    }

    /** Kills ffmpeg and releases the rings. Returns at once. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        process.close();
        closeQuietly(audioListener);
        closeQuietly(audioSocket);
        if (frames != null) {
            frames.close();
        }
        if (pcm != null) {
            pcm.close();
        }
    }

    /** The loopback address as a URL host: bracketed for IPv6, which a JVM preferring it picks. */
    static String hostLiteral(InetAddress address) {
        String text = address.getHostAddress();
        int scope = text.indexOf('%');
        if (scope >= 0) {
            text = text.substring(0, scope);
        }
        return address instanceof Inet6Address ? "[" + text + "]" : text;
    }

    private static void closeQuietly(@Nullable Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException e) {
            // Nothing left to release.
        }
    }
}
