package com.lia.mediaplayer.ui.widget;

import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.theme.Role;
import com.lia.mediaplayer.ui.theme.Theme;

/**
 * What a widget knows while it draws.
 *
 * @param nanos the wall clock ({@code System.nanoTime()}), for animations
 * @param pointerX where the cursor is, {@code NaN} when no cursor is over this widget's window
 * @param interactive whether the controls are live: over the chat or the media controls,
 *     not on the HUD, where windows show their picture only
 */
public record RenderContext(Theme theme, long nanos, double pointerX, double pointerY, boolean interactive) {

    public int color(Role role) {
        return theme.color(role);
    }

    /** Whether the cursor is over {@code rect} and the controls are live. */
    public boolean hovered(Rect rect) {
        return interactive && rect.contains(pointerX, pointerY);
    }

    /** The same frame with no cursor: for a window covered where the cursor is. */
    public RenderContext withoutPointer() {
        return new RenderContext(theme, nanos, Double.NaN, Double.NaN, interactive);
    }
}
