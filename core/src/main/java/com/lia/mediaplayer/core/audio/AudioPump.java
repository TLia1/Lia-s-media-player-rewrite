package com.lia.mediaplayer.core.audio;

import com.lia.mediaplayer.core.lifecycle.CoreExecutors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongConsumer;
import org.jspecify.annotations.Nullable;

/**
 * Moves PCM from the current session's {@link PcmRing} to the {@link AudioSink}, in chunks of
 * about {@value #CHUNK_MILLIS} ms, on one platform thread per player. It also keeps the master
 * clock: the session's start position plus the frames the sink has played since the session's
 * feed became current.
 *
 * <p>The loop allocates nothing, takes no lock and logs nothing. When a seek replaces the
 * feed, the pump itself flushes the sink and notes where the sink's frame counter stood, so
 * the clock of the new session starts exactly at its start position whatever the old session
 * left queued.
 */
public final class AudioPump implements AutoCloseable {

    static final int CHUNK_MILLIS = 20;
    private static final long IDLE_PARK_NANOS = 2_000_000;

    /** One session's sound: its ring, and where in the media it starts. */
    private static final class Feed {
        final PcmRing ring;
        final long generation;
        final long startMicros;
        /** The sink's frame counter when this feed became current. Written before {@code active}. */
        long baseFrames;
        /** Frames written to the sink from this feed. Written by the pump, read by the clock. */
        volatile long writtenFrames;

        boolean drainReported;

        Feed(PcmRing ring, long generation, long startMicros) {
            this.ring = ring;
            this.generation = generation;
            this.startMicros = startMicros;
        }
    }

    private final AudioSink sink;
    private final PcmFormat format;
    private final int chunkBytes;
    private final LongConsumer onDrained;
    private final Thread thread;
    private final AtomicReference<@Nullable Feed> pending = new AtomicReference<>();
    private volatile @Nullable Feed active;
    private volatile boolean playing;
    private volatile boolean closed;
    /** Times the sink was left waiting on an empty ring mid-stream. Written by the pump only. */
    private volatile long underruns;

    /**
     * Starts the pump thread. The sink must already be open.
     *
     * @param onDrained called on the pump thread with a feed's generation once its stream has
     *     ended and all of it has been played
     */
    public AudioPump(AudioSink sink, PcmFormat format, String name, LongConsumer onDrained) {
        this.sink = sink;
        this.format = format;
        int chunk = format.bytesPerSecond() * CHUNK_MILLIS / 1000;
        this.chunkBytes = chunk - chunk % format.frameBytes();
        this.onDrained = onDrained;
        this.thread = CoreExecutors.platformThread(name, Thread.MAX_PRIORITY - 1, this::run);
    }

    /**
     * Makes {@code ring} the sound to play, starting at {@code startMicros} of the media. Any
     * thread. What the previous feed left in the sink is discarded.
     */
    public void switchTo(PcmRing ring, long generation, long startMicros) {
        pending.set(new Feed(ring, generation, startMicros));
        // Unblocks a write of the previous feed, so the switch is not held up by a full line.
        sink.flush();
        LockSupport.unpark(thread);
    }

    /** Starts the sound and the clock. */
    public void play() {
        sink.start();
        playing = true;
        LockSupport.unpark(thread);
    }

    /** Stops the sound and the clock; what is queued stays queued. */
    public void pause() {
        playing = false;
        sink.stop();
    }

    public boolean isPlaying() {
        return playing;
    }

    public void setGain(float linear) {
        sink.setGain(linear);
    }

    public void setPan(float value) {
        sink.setPan(value);
    }

    /** Times the sound ran dry while playing, before its stream ended: the network fell behind. */
    public long underruns() {
        return underruns;
    }

    /** Decoded sound waiting in the current feed, in microseconds. Any thread. */
    public long bufferedMicros() {
        Feed current = active;
        return current == null ? 0 : format.framesToMicros(current.ring.available() / format.frameBytes());
    }

    /** What the current feed's ring holds when full, in microseconds. */
    public long capacityMicros() {
        Feed current = active;
        return current == null ? 0 : format.framesToMicros(current.ring.capacity() / format.frameBytes());
    }

    /** Where playback is in the media, in microseconds. Any thread. */
    public long positionMicros() {
        Feed next = pending.get();
        if (next != null) {
            return next.startMicros;
        }
        Feed current = active;
        if (current == null) {
            return 0;
        }
        long played = Math.max(0, sink.playedFrames() - current.baseFrames);
        return current.startMicros + format.framesToMicros(Math.min(played, current.writtenFrames));
    }

    @SuppressWarnings("NonAtomicVolatileUpdate") // writtenFrames and underruns have a single writer: this thread
    private void run() {
        boolean starved = false;
        while (!closed) {
            Feed next = pending.get();
            if (next != null) {
                sink.flush();
                next.baseFrames = sink.playedFrames();
                active = next;
                pending.compareAndSet(next, null);
            }
            Feed feed = active;
            if (!playing || feed == null) {
                LockSupport.parkNanos(IDLE_PARK_NANOS);
                continue;
            }
            PcmRing ring = feed.ring;
            int readable = Math.min(chunkBytes, ring.readableContiguous());
            readable -= readable % format.frameBytes();
            if (readable == 0) {
                if (!starved && !ring.isEndOfStream()) {
                    underruns++;
                }
                starved = true;
                checkDrained(feed);
                LockSupport.parkNanos(IDLE_PARK_NANOS);
                continue;
            }
            starved = false;
            int written = sink.write(ring.array(), ring.readIndex(), readable);
            written -= written % format.frameBytes();
            if (pending.get() == null && active == feed) {
                ring.consume(written);
                feed.writtenFrames += written / format.frameBytes();
            }
        }
    }

    private void checkDrained(Feed feed) {
        if (feed.drainReported || !feed.ring.isDrained()) {
            return;
        }
        if (sink.playedFrames() - feed.baseFrames >= feed.writtenFrames) {
            feed.drainReported = true;
            onDrained.accept(feed.generation);
        }
    }

    @Override
    public void close() {
        closed = true;
        playing = false;
        LockSupport.unpark(thread);
        sink.stop();
        sink.flush();
        try {
            thread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        sink.close();
    }
}
