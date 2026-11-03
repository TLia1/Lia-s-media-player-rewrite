package com.lia.mediaplayer.core.video;

import java.util.Objects;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;

/**
 * The decoded frames of one session, waiting for their time.
 *
 * <p>The session's reader {@linkplain #put puts} frames in decode order and blocks while the
 * ring is full: that is what holds ffmpeg back once it is far enough ahead. The view
 * {@linkplain #takeDue takes} the newest frame that is due, and the frames it skipped go back
 * to the pool. A {@link ReentrantLock} rather than {@code synchronized}, because the reader is
 * a virtual thread and must not pin its carrier while it waits.
 */
public final class FrameRing {

    private final @Nullable VideoFrame[] frames;
    private final FramePool pool;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private int head;
    private int count;
    private long dropped;
    private boolean closed;

    public FrameRing(int capacity, FramePool pool) {
        if (capacity < 2) {
            throw new IllegalArgumentException("A frame ring needs room for at least two frames");
        }
        this.frames = new VideoFrame[capacity];
        this.pool = pool;
    }

    public int capacity() {
        return frames.length;
    }

    /**
     * Adds a frame, waiting while the ring is full.
     *
     * @return {@code false} if the ring was closed, in which case the frame went back to the pool
     */
    public boolean put(VideoFrame frame) throws InterruptedException {
        lock.lock();
        try {
            while (count == frames.length && !closed) {
                notFull.await();
            }
            if (closed) {
                pool.release(frame);
                return false;
            }
            frames[(head + count) % frames.length] = frame;
            count++;
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * The newest frame due at {@code nowMicros}, or {@code null} if none is. Older due frames
     * are released. The caller releases the frame it gets once it is done with it.
     */
    public @Nullable VideoFrame takeDue(long nowMicros) {
        lock.lock();
        try {
            VideoFrame newest = null;
            while (count > 0 && at(head).ptsMicros() <= nowMicros) {
                if (newest != null) {
                    pool.release(newest);
                    dropped++;
                }
                newest = poll();
            }
            return newest;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Releases due frames that a newer due frame has superseded, keeping the newest for the
     * view. Keeps a player without a view moving: nobody else would ever empty the ring.
     *
     * @return how many frames were dropped
     */
    public int discardSuperseded(long nowMicros) {
        lock.lock();
        try {
            int dropped = 0;
            while (count > 1 && at((head + 1) % frames.length).ptsMicros() <= nowMicros) {
                pool.release(poll());
                dropped++;
            }
            this.dropped += dropped;
            return dropped;
        } finally {
            lock.unlock();
        }
    }

    /** Whether every frame held is due at {@code nowMicros}: the reader has nothing newer to show. */
    public boolean allDue(long nowMicros) {
        lock.lock();
        try {
            return count == 0 || at((head + count - 1) % frames.length).ptsMicros() <= nowMicros;
        } finally {
            lock.unlock();
        }
    }

    /** Frames decoded and due, but never taken for display because a newer one was due too. */
    public long dropped() {
        lock.lock();
        try {
            return dropped;
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }

    /** Releases every frame and makes the reader's next {@link #put} return {@code false}. */
    public void close() {
        lock.lock();
        try {
            closed = true;
            while (count > 0) {
                pool.release(poll());
            }
            notFull.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /** The frame in slot {@code index}, which holds one. Lock held. */
    private VideoFrame at(int index) {
        return Objects.requireNonNull(frames[index]);
    }

    /** Removes the oldest frame. Lock held, ring not empty. */
    private VideoFrame poll() {
        VideoFrame frame = at(head);
        frames[head] = null;
        head = (head + 1) % frames.length;
        count--;
        notFull.signal();
        return frame;
    }
}
