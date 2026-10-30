package com.lia.mediaplayer.api.ui;

import com.lia.mediaplayer.api.Ids;
import com.lia.mediaplayer.api.media.MediaHandle;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * A fixed key over the chat for an addon: Ctrl (Cmd on macOS) with a letter or digit, and Shift
 * if {@code shift}. A bare letter or digit cannot be one — it is what the player types into the
 * chat. The mod's own shortcuts are matched first, so an addon cannot take one of their keys.
 *
 * @param key a letter ({@code 'J'}, case ignored) or a digit
 * @param action told the front-most player shown, if any; returns whether it did something.
 *     {@code false} lets the key go on to the chat field — return it when there was nothing to
 *     do, or a key typed into the chat disappears
 * @since 4.0.0
 */
public record ShortcutSpec(String id, char key, boolean shift, Predicate<Optional<MediaHandle>> action) {

    public ShortcutSpec {
        Ids.require(id, "Shortcut id");
        char upper = Character.toUpperCase(key);
        if (!(upper >= 'A' && upper <= 'Z') && !(upper >= '0' && upper <= '9')) {
            throw new IllegalArgumentException(id + ": a shortcut key is a letter or a digit, not '" + key + "'");
        }
        key = upper;
    }

    /** Ctrl and {@code key}, without Shift. */
    public static ShortcutSpec ctrl(String id, char key, Predicate<Optional<MediaHandle>> action) {
        return new ShortcutSpec(id, key, false, action);
    }
}
