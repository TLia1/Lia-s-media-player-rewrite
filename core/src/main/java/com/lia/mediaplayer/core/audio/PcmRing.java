package com.lia.mediaplayer.core.audio;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * A single-producer, single-consumer ring of PCM bytes: the jitter buffer between ffmpeg's
 * audio socket and the sound line.
 *
 * <p>The producer reads from the socket straight into the ring's array, and the consumer
 * writes straight out of it, so no byte is copied twice and nothing is allocated after
 * construction. Positions are monotonic byte counts; the producer publishes with a release
 * store and the consumer with another, so neither side takes a lock. A side with nothing to
 * do parks for a millisecond or two.
 */
public final class PcmRing {

    private static final long IDLE_PARK_NANOS = 2_000_000;

    private final byte[] data;
    private final int mask;
    /** Bytes ever written. Advanced by the producer only. */
    private final AtomicLong head = new AtomicLong();
    /** Bytes ever read. Advanced by the consumer only. */
    private final AtomicLong tail = new AtomicLong();

    private volatile boolean endOfStream;
    private volatile boolean closed;

    /** A ring of at least {@code minBytes}, rounded up to a power of two. */
    public PcmRing(int minBytes) {
        if (minBytes <= 0 || minBytes > 1 << 28) {
            throw new IllegalArgumentException("Ring size out of range: " + minBytes);
        }
        int capacity = Integer.highestOneBit(minBytes);
        if (capacity < minBytes) {
            capacity <<= 1;
        }
        this.data = new byte[capacity];
        this.mask = capacity - 1;
    }

    /** A ring holding {@code seconds} of sound in {@code format}. */
    public static PcmRing forDuration(PcmFormat format, double seconds) {
        return new PcmRing((int) Math.ceil(format.bytesPerSecond() * seconds));
    }

    public int capacity() {
        return data.length;
    }

    // ---- Producer ----------------------------------------------------------

    /**
     * Reads once from {@code in} into free space, waiting for space if the ring is full.
     *
     * @return bytes read, or -1 at the end of the stream (which marks the ring) or once closed
     */
    public int fillFrom(InputStream in) throws IOException {
        while (!closed) {
            long h = head.get();
            int free = data.length - (int) (h - tail.getAcquire());
            if (free == 0) {
                LockSupport.parkNanos(IDLE_PARK_NANOS);
                continue;
            }
            int index = (int) (h & mask);
            int read = in.read(data, index, Math.min(free, data.length - index));
            if (read < 0) {
                endOfStream = true;
                return -1;
            }
            head.setRelease(h + read);
            return read;
        }
        return -1;
    }

    /** Marks the end of the stream without reading, e.g. when the socket never connected. */
    public void markEndOfStream() {
        endOfStream = true;
    }

    // ---- Consumer ----------------------------------------------------------

    /** Bytes ready to read in one piece from {@link #readIndex()}: up to the end of the array. */
    public int readableContiguous() {
        long t = tail.get();
        int available = (int) (head.getAcquire() - t);
        int index = (int) (t & mask);
        return Math.min(available, data.length - index);
    }

    /** Bytes written and not yet read. */
    public int available() {
        return (int) (head.getAcquire() - tail.get());
    }

    /** Where the next readable byte is in {@link #array()}. */
    public int readIndex() {
        return (int) (tail.get() & mask);
    }

    /** The backing array; read only between {@link #readIndex()} and {@link #readableContiguous()}. */
    public byte[] array() {
        return data;
    }

    /** Releases {@code bytes} the consumer has finished with. */
    public void consume(int bytes) {
        long t = tail.get();
        if (bytes < 0 || bytes > (int) (head.getAcquire() - t)) {
            throw new IllegalArgumentException("Cannot consume " + bytes + " bytes");
        }
        tail.setRelease(t + bytes);
    }

    /** The stream has ended and every byte has been read. */
    public boolean isDrained() {
        return endOfStream && available() == 0;
    }

    public boolean isEndOfStream() {
        return endOfStream;
    }

    /** Wakes a waiting producer and makes it return -1. */
    public void close() {
        closed = true;
    }
}
