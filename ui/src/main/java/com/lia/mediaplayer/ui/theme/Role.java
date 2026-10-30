package com.lia.mediaplayer.ui.theme;

import com.lia.mediaplayer.api.ui.ThemeRole;

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
    /** The edge of the front window, distinct at a glance from {@link #BORDER}. */
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
    /** A toggle that is on (loop, shuffle); never the same as hovered. */
    ICON_ACTIVE,
    /** A control that cannot be used right now. */
    ICON_DISABLED,
    /** An error message, or a destructive control under the cursor. */
    DANGER,
    /** The cross over a muted speaker. */
    MUTED,
    /** The loading spinner. */
    SPINNER;

    /**
     * The role an addon's theme names. A switch rather than a lookup by name, so a role added
     * to the API without one here is a compile error.
     */
    public static Role of(ThemeRole role) {
        return switch (role) {
            case WINDOW_BG -> WINDOW_BG;
            case TITLE_BAR_BG -> TITLE_BAR_BG;
            case CONTROL_BAR_BG -> CONTROL_BAR_BG;
            case PLACEHOLDER -> PLACEHOLDER;
            case PANEL_BG -> PANEL_BG;
            case LIST_BG -> LIST_BG;
            case ROW_BG -> ROW_BG;
            case ROW_HOVER -> ROW_HOVER;
            case ROW_SELECTED -> ROW_SELECTED;
            case FIELD_BG -> FIELD_BG;
            case CHIP_BG -> CHIP_BG;
            case CHIP_HOVER -> CHIP_HOVER;
            case BANNER_BG -> BANNER_BG;
            case BORDER -> BORDER;
            case BORDER_FOCUSED -> BORDER_FOCUSED;
            case TRACK -> TRACK;
            case FILL -> FILL;
            case KNOB -> KNOB;
            case SCROLL_TRACK -> SCROLL_TRACK;
            case SCROLL_THUMB -> SCROLL_THUMB;
            case TEXT -> TEXT;
            case TEXT_SUBTLE -> TEXT_SUBTLE;
            case TEXT_DIM -> TEXT_DIM;
            case ICON -> ICON;
            case ICON_HOVER -> ICON_HOVER;
            case ICON_ACTIVE -> ICON_ACTIVE;
            case ICON_DISABLED -> ICON_DISABLED;
            case DANGER -> DANGER;
            case MUTED -> MUTED;
            case SPINNER -> SPINNER;
        };
    }
}
