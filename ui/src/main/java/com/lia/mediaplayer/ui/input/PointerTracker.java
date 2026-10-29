package com.lia.mediaplayer.ui.input;

import org.jspecify.annotations.Nullable;

/**
 * Makes moves, drags and click counts out of what crosses the platform seam: presses,
 * releases, and the cursor position read once a frame.
 *
 * <p>The game reports a drag at most once a frame anyway, because it polls the cursor once a
 * frame, so reading the position in the render hook loses nothing — and it makes dragging
 * work the same on every loader and version, including those where a loader has no drag
 * event at all.
 */
public final class PointerTracker {

    static final long DOUBLE_CLICK_NANOS = 300_000_000L;
    static final double DOUBLE_CLICK_SLOP = 4;
    private static final int NONE = -1;

    private double x = Double.NaN;
    private double y = Double.NaN;
    private int held = NONE;
    private int lastButton = NONE;
    private long lastDownNanos;
    private double lastDownX;
    private double lastDownY;
    private int clicks;

    /** A button went down at {@code (px, py)}: the event, with its click count. */
    public InputEvent.PointerDown press(double px, double py, int button, Mods mods, long nanos) {
        boolean repeat = button == lastButton
                && nanos - lastDownNanos <= DOUBLE_CLICK_NANOS
                && Math.abs(px - lastDownX) <= DOUBLE_CLICK_SLOP
                && Math.abs(py - lastDownY) <= DOUBLE_CLICK_SLOP;
        clicks = repeat ? clicks + 1 : 1;
        lastButton = button;
        lastDownNanos = nanos;
        lastDownX = px;
        lastDownY = py;
        held = button;
        x = px;
        y = py;
        return new InputEvent.PointerDown(px, py, button, mods, clicks);
    }

    public InputEvent.PointerUp release(double px, double py, int button, Mods mods) {
        if (button == held) {
            held = NONE;
        }
        x = px;
        y = py;
        return new InputEvent.PointerUp(px, py, button, mods);
    }

    /**
     * Where the cursor is this frame, with the modifiers held now: a move or, with a button
     * held, a drag — or {@code null} when it has not moved.
     */
    public @Nullable InputEvent moveTo(double px, double py, Mods mods) {
        if (Double.isNaN(x)) {
            x = px;
            y = py;
            return null;
        }
        double dx = px - x;
        double dy = py - y;
        if (dx == 0 && dy == 0) {
            return null;
        }
        x = px;
        y = py;
        return held == NONE
                ? new InputEvent.PointerMove(px, py)
                : new InputEvent.PointerDrag(px, py, held, dx, dy, mods);
    }

    public boolean isHeld() {
        return held != NONE;
    }

    /** A new screen opened: no button can be held on it, and no click continues across it. */
    public void reset() {
        held = NONE;
        lastButton = NONE;
        clicks = 0;
    }
}
