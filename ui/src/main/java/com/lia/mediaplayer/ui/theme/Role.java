package com.lia.mediaplayer.ui.theme;

/**
 * What a colour is for. A widget asks for a role ({@link #ICON_HOVER}, not "yellow"), so a
 * theme changes every place a role is used at once.
 */
public enum Role {
    /** The body of a window, behind the picture. */
    WINDOW_BG,
    /** The strip above a window's content, with its title and corner buttons. */
    TITLE_BAR_BG,
    /** The transport strip below a player's content. */
    CONTROL_BAR_BG,
    /** Fills the picture area while there is no frame to show. */
    PLACEHOLDER,
    /** A small panel over the picture: the error card, the volume read-out. */
    PANEL_BG,
    /** The edge of a window that is not the front one. */
    BORDER,
    /** The edge of the front window, distinct at a glance from {@link #BORDER}. */
    BORDER_FOCUSED,
    /** The unfilled part of a seek or volume bar. */
    TRACK,
    /** The elapsed part of a seek or volume bar. */
    FILL,
    /** The handle on a seek or volume bar. */
    KNOB,
    TEXT,
    /** Secondary text: times, counts, hints. */
    TEXT_SUBTLE,
    /** A control glyph at rest. */
    ICON,
    /** A control glyph under the cursor. */
    ICON_HOVER,
    /** A toggle that is on (loop, shuffle); never the same as hovered. */
    ICON_ACTIVE,
    /** A control that cannot be used right now. */
    ICON_DISABLED,
    /** An error message, or a destructive control under the cursor. */
    DANGER,
    /** The cross over a muted speaker. */
    MUTED,
    /** The loading spinner. */
    SPINNER
}
