package com.lia.mediaplayer.ui.widget;

import org.jspecify.annotations.Nullable;

/**
 * Input state that outlives one event, owned by whatever routes input (the window stack, a
 * screen).
 *
 * <p>A widget that takes a press can {@linkplain #capture capture} the pointer: the drags and
 * the release that follow go to it directly, wherever the cursor has gone — which is what
 * makes a seek bar scrub or a window move when the cursor leaves it mid-drag.
 *
 * <p>A widget can also take the {@linkplain #focus keyboard focus}: keys and characters then go
 * to it first. A text field does, when clicked.
 */
public final class InputContext {

    private @Nullable Widget captured;
    private @Nullable Widget focused;

    public void capture(Widget widget) {
        captured = widget;
    }

    public @Nullable Widget captured() {
        return captured;
    }

    // Widgets are entities: the captured one is that very instance.
    @SuppressWarnings("ReferenceEquality")
    public boolean isCaptured(Widget widget) {
        return captured == widget;
    }

    public void releaseCapture() {
        captured = null;
    }

    public void focus(Widget widget) {
        focused = widget;
    }

    public @Nullable Widget focused() {
        return focused;
    }

    @SuppressWarnings("ReferenceEquality")
    public boolean isFocused(Widget widget) {
        return focused == widget;
    }

    public void clearFocus() {
        focused = null;
    }
}
