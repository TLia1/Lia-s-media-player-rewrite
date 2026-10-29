package com.lia.mediaplayer.ui.anim;

/**
 * A value moving from one number to another over a fixed time, read against the wall clock.
 * Retargeting mid-way starts from wherever the value is, so nothing jumps.
 */
public final class Animation {

    private final long durationNanos;
    private final Easing easing;
    private float from;
    private float to;
    private long startNanos;

    public Animation(float initial, long durationNanos, Easing easing) {
        if (durationNanos <= 0) {
            throw new IllegalArgumentException("Duration must be positive");
        }
        this.durationNanos = durationNanos;
        this.easing = easing;
        this.from = initial;
        this.to = initial;
    }

    /** Starts moving towards {@code target} from the value at {@code nowNanos}. */
    public void animateTo(float target, long nowNanos) {
        if (target == to) {
            return;
        }
        from = value(nowNanos);
        to = target;
        startNanos = nowNanos;
    }

    /** Jumps to {@code target} with no animation. */
    public void set(float target) {
        from = target;
        to = target;
    }

    public float value(long nowNanos) {
        if (from == to) {
            return to;
        }
        float t = (float) (nowNanos - startNanos) / durationNanos;
        if (t >= 1) {
            from = to;
            return to;
        }
        return from + (to - from) * easing.apply(Math.max(0, t));
    }

    public float target() {
        return to;
    }

    public boolean isDone(long nowNanos) {
        return value(nowNanos) == to;
    }
}
