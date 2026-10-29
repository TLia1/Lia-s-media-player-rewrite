package com.lia.mediaplayer.ui.screen;

/**
 * Which screen the game has open, as far as the UI needs to know. The UI never sees a
 * vanilla screen object.
 *
 * @param chatFieldEmpty whether the chat's text field is empty (always {@code true} off the chat),
 *     which is when a bare key can safely mean a shortcut
 */
public record ScreenContext(Kind kind, boolean chatFieldEmpty) {

    public static final ScreenContext NONE = new ScreenContext(Kind.NONE, true);

    public enum Kind {
        /** No screen: in the world. */
        NONE,
        CHAT,
        PAUSE,
        /** A {@link UiScreen} of this mod, in the platform's host screen. */
        HOSTED,
        /** Any other screen. */
        OTHER
    }
}
