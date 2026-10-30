package com.lia.mediaplayer.api.ui;

import com.lia.mediaplayer.api.text.Text;

/**
 * A button of an addon in the corner row of the mod's windows, left of the heart. At most three
 * addon buttons are drawn per window, in the order registered.
 *
 * <p>Every method is called on the client thread, {@link #appliesTo} once a frame for each
 * window: keep it to a field read. An exception from any of them is logged with the addon's
 * id, and a button that keeps failing is removed.
 *
 * @since 4.0.0
 */
public interface WindowAction {

    /** Namespaced, {@code "myaddon:share"}. */
    String id();

    ActionIcon icon();

    /** Shown when the button is hovered. */
    Text tooltip();

    /** Whether the button shows on {@code window}. By default it shows on every window. */
    default boolean appliesTo(MediaWindow window) {
        return true;
    }

    /**
     * The button was clicked.
     *
     * @param shift whether Shift was held, the mod's "a little more" modifier
     */
    void onClick(MediaWindow window, boolean shift);
}
