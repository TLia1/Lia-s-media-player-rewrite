package com.lia.mediaplayer.core.playback;

import java.util.function.LongSupplier;

/**
 * The clock of a session without sound: elapsed wall time since it was started, frozen while
 * paused. Read from any thread, changed from the player's actor.
 */
final class WallClock {

    private static final long PAUSED = Long.MIN_VALUE;

    private final LongSupplier nanoTime;
    private volatile long baseMicros;
    private volatile long runningSinceNanos = PAUSED;

    WallClock(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    long nowMicros() {
        long since = runningSinceNanos;
        long base = baseMicros;
        return since == PAUSED ? base : base + (nanoTime.getAsLong() - since) / 1000;
    }

    /** Stops at {@code micros}. */
    void reset(long micros) {
        runningSinceNanos = PAUSED;
        baseMicros = micros;
    }

    void resume() {
        if (runningSinceNanos == PAUSED) {
            runningSinceNanos = nanoTime.getAsLong();
        }
    }

    void pause() {
        if (runningSinceNanos != PAUSED) {
            baseMicros = nowMicros();
            runningSinceNanos = PAUSED;
        }
    }
}
