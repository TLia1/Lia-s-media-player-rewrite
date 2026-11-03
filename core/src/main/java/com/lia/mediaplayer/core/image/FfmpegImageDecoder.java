package com.lia.mediaplayer.core.image;

import com.lia.mediaplayer.core.ffmpeg.FfmpegCommand;
import com.lia.mediaplayer.core.ffmpeg.PamReader;
import com.lia.mediaplayer.core.tools.ManagedProcess;
import com.lia.mediaplayer.core.tools.ProcessRunner;
import com.lia.mediaplayer.core.tools.Tool;
import com.lia.mediaplayer.core.tools.ToolProvider;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Pictures ImageIO cannot read — WebP and animated WebP (what Discord serves), AVIF, APNG —
 * decoded by ffmpeg: the bytes go to a temporary file, and the frames come back as PAM on
 * stdout, as for a video.
 *
 * <p>A still is asked for as one frame. An animation comes out at a constant {@value #FPS} frames a second, the finest step GIFs
 * use too, so a frame shown for 100 ms arrives five times; identical frames in a row are
 * folded back into one, which gives each its own time again. The same limits as for GIFs
 * apply: {@value GifDecoder#MAX_FRAMES} frames and {@value GifDecoder#MAX_TOTAL_PIXELS} pixels
 * in all, past which the animation is cut short.
 *
 * <p>Runs only when ffmpeg is there: without it, decoding fails like any unreadable picture.
 * Blocking; call it on the decode executor.
 */
public final class FfmpegImageDecoder implements ImageDecoders.Fallback {

    static final int FPS = 50;
    static final int FRAME_MILLIS = 1000 / FPS;
    /** Twenty seconds of animation, at most, before folding. */
    static final int MAX_DECODED_FRAMES = 20 * FPS;

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final ToolProvider tools;
    private final ProcessRunner processes;

    public FfmpegImageDecoder(ToolProvider tools, ProcessRunner processes) {
        this.tools = tools;
        this.processes = processes;
    }

    @Override
    public DecodedImage decode(byte[] data) throws IOException {
        String ffmpeg = tools.require(Tool.FFMPEG).command();
        // A still read at a constant rate would be repeated for as long as its stream claims to last.
        int maxFrames = ImageDecoders.Format.animated(data) ? MAX_DECODED_FRAMES : 1;
        Path file = Files.createTempFile("lmp-picture-", ".bin");
        try {
            Files.write(file, data);
            try (ManagedProcess process = processes.start(
                    "picture", FfmpegCommand.image(ffmpeg, file, ImageDecoders.MAX_SIDE, FPS, maxFrames))) {
                // A picture that hangs ffmpeg ends the read by ending the process. Closing it
                // also stops one still writing frames past the limits.
                CompletableFuture.delayedExecutor(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                        .execute(process::close);
                return read(process.stdout());
            } catch (NoFramesException e) {
                throw new ImageDecoders.UnsupportedImageException("ffmpeg could not read this picture");
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /** Reads PAM frames until the stream ends or the limits are reached, folding repeats. */
    static DecodedImage read(InputStream in) throws IOException {
        var kept = new ArrayList<byte[]>();
        var delays = new ArrayList<Integer>();
        int width = 0;
        int height = 0;
        long maxKept = GifDecoder.MAX_FRAMES;
        while (true) {
            PamReader.Header header = PamReader.readHeader(in);
            if (header == null) {
                break;
            }
            if (kept.isEmpty()) {
                width = header.width();
                height = header.height();
                maxKept = Math.max(
                        1, Math.min(GifDecoder.MAX_FRAMES, GifDecoder.MAX_TOTAL_PIXELS / ((long) width * height)));
            } else if (header.width() != width || header.height() != height) {
                break;
            }
            byte[] pixels = in.readNBytes(header.frameBytes());
            if (pixels.length < header.frameBytes()) {
                break;
            }
            if (!kept.isEmpty() && Arrays.equals(kept.getLast(), pixels)) {
                delays.set(delays.size() - 1, delays.getLast() + FRAME_MILLIS);
                continue;
            }
            if (kept.size() == maxKept) {
                break;
            }
            kept.add(pixels);
            delays.add(FRAME_MILLIS);
        }
        if (kept.isEmpty()) {
            throw new NoFramesException();
        }
        List<DecodedImage.Frame> frames = new ArrayList<>(kept.size());
        for (int i = 0; i < kept.size(); i++) {
            // One frame is a still, which never changes.
            int delay = kept.size() == 1 ? 0 : GifDecoder.normalise(delays.get(i));
            frames.add(new DecodedImage.Frame(ByteBuffer.wrap(kept.get(i)), delay));
        }
        return new DecodedImage(width, height, frames);
    }

    /** ffmpeg wrote nothing: it could not read the picture. */
    private static final class NoFramesException extends IOException {
        private static final long serialVersionUID = 1L;

        NoFramesException() {
            super("No frames");
        }
    }
}
