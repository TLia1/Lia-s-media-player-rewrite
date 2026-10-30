package com.lia.mediaplayer.api.ui;

/**
 * The glyph of a {@link WindowAction}: one of the mod's own icons. They are drawn from
 * rectangles at the corner row's one size, in whatever colour the theme and the hover call
 * for, so a button of an addon looks like the mod's own in every theme.
 *
 * @since 4.0.0
 */
public enum ActionIcon {
    PLAY,
    NEXT,
    PREVIOUS,
    LOOP,
    SHUFFLE,
    /** A circular arrow: retry, restart. */
    RETRY,
    COPY,
    /** A box with an arrow leaving it: open elsewhere, share. */
    EXTERNAL_LINK,
    HEART,
    HIDE,
    THEATER,
    /** Lines of a list. */
    QUEUE,
    PLUS,
    UP,
    DOWN,
    /** A musical note. */
    NOTE,
    CLOSE
}
