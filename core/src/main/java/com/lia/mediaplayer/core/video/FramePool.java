package com.lia.mediaplayer.core.video;

import java.util.ArrayDeque;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Recycles frames of the current size, so a playing video allocates nothing in steady state.
 * When the size changes (a resize, an adaptive stream switching resolution) frames of the old
 * size are dropped as they come back.
 */
public final class FramePool {

    private final int maxFree;
    private final ReentrantLock lock = new ReentrantLock();
    private final ArrayDeque<VideoFrame> free;
    private int width;
    private int height;

    public FramePool(int maxFree) {
        this.maxFree = maxFree;
        this.free = new ArrayDeque<>(maxFree);
    }

    /** A frame of {@code width × height}, recycled when one is free. */
    public VideoFrame acquire(int width, int height) {
        lock.lock();
        try {
            if (this.width != width || this.height != height) {
                this.width = width;
                this.height = height;
                free.clear();
            }
            VideoFrame frame = free.pollFirst();
            if (frame != null) {
                return frame;
            }
        } finally {
            lock.unlock();
        }
        return new VideoFrame(width, height);
    }

    /** Gives a frame back. It must not be used afterwards. */
    public void release(VideoFrame frame) {
        lock.lock();
        try {
            if (frame.hasSize(width, height) && free.size() < maxFree) {
                free.addFirst(frame);
            }
        } finally {
            lock.unlock();
        }
    }

    int freeCount() {
        lock.lock();
        try {
            return free.size();
        } finally {
            lock.unlock();
        }
    }
}
