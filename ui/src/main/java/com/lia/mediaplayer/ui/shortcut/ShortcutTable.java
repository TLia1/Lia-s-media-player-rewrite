package com.lia.mediaplayer.ui.shortcut;

import com.lia.mediaplayer.ui.input.Key;
import com.lia.mediaplayer.ui.input.Mods;
import java.time.Duration;
import java.util.Optional;

/**
 * What a key means while the windows are live over the chat or the media controls. A pure
 * function, v2's table and rules:
 *
 * <ul>
 *   <li><b>Bare Space and the left and right arrows</b>, only while nothing is being typed:
 *       they do nothing to an empty chat field, so transport and seeking stay one key away.
 *       Shift seeks further.
 *   <li><b>Everything else behind the primary modifier</b> (Ctrl, Cmd on macOS), always: the
 *       chat field binds Ctrl+A, C, V and X and nothing else, so these are free even while a
 *       message is half-written. Volume is here rather than on the bare up and down arrows,
 *       which an empty chat field uses to recall earlier messages.
 *   <li><b>Escape is never taken</b>: it closes the chat, which is what it is pressed for.
 * </ul>
 */
public final class ShortcutTable {

    /** How far a bare arrow seeks. */
    public static final Duration SEEK = Duration.ofSeconds(5);
    /** How far Shift and an arrow seek. */
    public static final Duration SEEK_FAR = Duration.ofSeconds(30);
    /** How much one press of a volume key moves the volume. */
    public static final float VOLUME_STEP = 0.05f;

    /** What a shortcut does. */
    public enum Action {
        PLAY_PAUSE,
        SEEK_BACK,
        SEEK_FORWARD,
        SEEK_BACK_FAR,
        SEEK_FORWARD_FAR,
        VOLUME_UP,
        VOLUME_DOWN,
        MUTE,
        LOOP,
        SHUFFLE,
        NEXT,
        PREVIOUS,
        THEATER
    }

    private ShortcutTable() {}

    /**
     * The action of {@code key}, or empty when the key is not the mod's and the screen must see it.
     *
     * @param typing whether text is being typed (a chat message is half-written), which
     *     withdraws every unmodified shortcut
     */
    public static Optional<Action> actionFor(Key key, Mods mods, boolean typing) {
        if (mods.primary()) {
            return Optional.ofNullable(
                    switch (key) {
                        case M -> Action.MUTE;
                        case L -> Action.LOOP;
                        case S -> Action.SHUFFLE;
                        case N -> Action.NEXT;
                        case P -> Action.PREVIOUS;
                        case F -> Action.THEATER;
                        case UP -> Action.VOLUME_UP;
                        case DOWN -> Action.VOLUME_DOWN;
                        default -> null;
                    });
        }
        if (typing || mods.alt()) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                switch (key) {
                    case SPACE -> mods.shift() ? null : Action.PLAY_PAUSE;
                    case LEFT -> mods.shift() ? Action.SEEK_BACK_FAR : Action.SEEK_BACK;
                    case RIGHT -> mods.shift() ? Action.SEEK_FORWARD_FAR : Action.SEEK_FORWARD;
                    default -> null;
                });
    }
}
