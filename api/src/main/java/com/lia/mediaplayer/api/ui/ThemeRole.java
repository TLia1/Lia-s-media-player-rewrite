package com.lia.mediaplayer.api.ui;

/**
 * What a colour of a theme is for. A {@link ThemeSpec} names the roles it changes; the others
 * keep the dark theme's colour.
 *
 * <p>Roles are only ever added, in minor versions; a theme written today keeps working.
 *
 * @since 4.0.0
 */
public enum ThemeRole {
    /** The body of a window, behind the picture. Translucent in the dark theme. */
    WINDOW_BG,
    /** The strip above a window's content, with its title and corner buttons. */
    TITLE_BAR_BG,
    /** The transport strip below a player's content. */
    CONTROL_BAR_BG,
    /** Fills the picture area while there is no frame to show. */
    PLACEHOLDER,
    /** A small panel over the picture: the error card, the volume read-out. */
    PANEL_BG,
    /** Behind a screen's lists and panels. */
    LIST_BG,
    /** A row of a list at rest. */
    ROW_BG,
    /** A row under the cursor. */
    ROW_HOVER,
    /** The chosen row: the selected playlist, the track playing. */
    ROW_SELECTED,
    /** Inside a text field. */
    FIELD_BG,
    /** A small button over the chat ("Playlists"). */
    CHIP_BG,
    /** The same button under the cursor. */
    CHIP_HOVER,
    /** The "now playing" banner. */
    BANNER_BG,
    /** The edge of a window that is not the front one. */
    BORDER,
    /** The edge of the front window. */
    BORDER_FOCUSED,
    /** The unfilled part of a seek or volume bar. */
    TRACK,
    /** The elapsed part of a seek or volume bar. */
    FILL,
    /** The handle on a seek or volume bar. */
    KNOB,
    /** The groove of a scroll bar. */
    SCROLL_TRACK,
    /** The part of a scroll bar that moves. */
    SCROLL_THUMB,
    TEXT,
    /** Secondary text: times, counts, hints. */
    TEXT_SUBTLE,
    /** Text that is barely there: a placeholder in an empty field. */
    TEXT_DIM,
    /** A control glyph at rest. */
    ICON,
    /** A control glyph under the cursor. */
    ICON_HOVER,
    /** A toggle that is on (loop, shuffle). */
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
