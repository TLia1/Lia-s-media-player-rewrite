package com.lia.mediaplayer.core.video;

import java.nio.ByteBuffer;

/**
 * One decoded picture: {@code width × height} RGBA pixels, row by row, and when it is due.
 *
 * <p>Frames are pooled: the reader fills one from the {@link FramePool}, the ring holds it, and
 * whoever takes it from the ring gives it back with {@link FramePool#release} once uploaded.
 * A frame must not be used after it has been released.
 */
public final class VideoFrame {

    private final int width;
    private final int height;
    private final byte[] pixels;
    private final ByteBuffer buffer;
    private long ptsMicros;
    private long generation;

    VideoFrame(int width, int height) {
        this.width = width;
        this.height = height;
        this.pixels = new byte[Math.multiplyExact(Math.multiplyExact(width, height), 4)];
        this.buffer = ByteBuffer.wrap(pixels);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** The RGBA bytes. */
    public byte[] pixels() {
        return pixels;
    }

    /** The RGBA bytes as a buffer over {@link #pixels()}; rewind it before reading. */
    public ByteBuffer buffer() {
        return buffer;
    }

    /** When it is due, in microseconds from the start of the media. */
    public long ptsMicros() {
        return ptsMicros;
    }

    /** The session that decoded it; a seek starts a new one. */
    public long generation() {
        return generation;
    }

    boolean hasSize(int w, int h) {
        return width == w && height == h;
    }

    /** Marks a freshly filled frame with its session and due time. For the session's reader. */
    public void stamp(long newGeneration, long newPtsMicros) {
        this.generation = newGeneration;
        this.ptsMicros = newPtsMicros;
    }
}
