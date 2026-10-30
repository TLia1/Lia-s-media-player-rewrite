package com.lia.mediaplayer.api.policy;

/**
 * Who asked for something to play.
 *
 * @since 4.0.0
 */
public enum PlayOrigin {
    /** A label clicked in chat. */
    CHAT_CLICK,
    /** The {@code /show} command. */
    COMMAND,
    /** A key: the clipboard key, or an addon's key binding. */
    KEYBIND,
    /** A playlist's Play or Shuffle. */
    PLAYLIST,
    /** "Play again" in the history. */
    HISTORY,
    /** An addon, through {@link com.lia.mediaplayer.api.MediaPlayer#play}. */
    API
}
