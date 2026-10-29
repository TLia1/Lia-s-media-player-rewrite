package com.lia.mediaplayer.ui.theme;

import static java.util.Map.entry;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.ModInfo;
import java.util.List;
import java.util.Map;

/**
 * The themes the mod ships with, v2's four. The dark one sets every role; the others are
 * written as what they change about it, so a role added later still has a colour in every
 * theme.
 */
public final class Themes {

    public static final Theme DARK = Theme.of(
            id("dark"),
            name("dark"),
            Map.ofEntries(
                    entry(Role.WINDOW_BG, 0xD0101010),
                    entry(Role.TITLE_BAR_BG, 0xF01C1C1C),
                    entry(Role.CONTROL_BAR_BG, 0xF0181818),
                    entry(Role.PLACEHOLDER, 0xFF000000),
                    entry(Role.PANEL_BG, 0xE0141414),
                    entry(Role.LIST_BG, 0xC0101010),
                    entry(Role.ROW_BG, 0xFF202020),
                    entry(Role.ROW_HOVER, 0xFF2E2E38),
                    entry(Role.ROW_SELECTED, 0xFF394A6B),
                    entry(Role.FIELD_BG, 0xFF000000),
                    entry(Role.CHIP_BG, 0xD0181818),
                    entry(Role.CHIP_HOVER, 0xF0303030),
                    entry(Role.BANNER_BG, 0xE0141414),
                    entry(Role.BORDER, 0x60FFFFFF),
                    entry(Role.BORDER_FOCUSED, 0xFF4CA6FF),
                    entry(Role.TRACK, 0xFF4A4A4A),
                    entry(Role.FILL, 0xFF4CA6FF),
                    entry(Role.KNOB, 0xFFFFFFFF),
                    entry(Role.SCROLL_TRACK, 0xFF333333),
                    entry(Role.SCROLL_THUMB, 0xFF6A6A6A),
                    entry(Role.TEXT, 0xFFFFFFFF),
                    entry(Role.TEXT_SUBTLE, 0xFFAAAAAA),
                    entry(Role.TEXT_DIM, 0xFF888888),
                    entry(Role.ICON, 0xFFE0E0E0),
                    entry(Role.ICON_HOVER, 0xFFFFD23F),
                    entry(Role.ICON_ACTIVE, 0xFF4CA6FF),
                    entry(Role.ICON_DISABLED, 0xFF555555),
                    entry(Role.DANGER, 0xFFFF6B6B),
                    entry(Role.MUTED, 0xFFFF5555),
                    entry(Role.SPINNER, 0xFFE0E0E0)));

    /** Pure black, white edges and saturated accents, for reading at a glance. */
    public static final Theme CONTRAST = DARK.with(
            id("contrast"),
            name("contrast"),
            Map.ofEntries(
                    entry(Role.WINDOW_BG, 0xFF000000),
                    entry(Role.TITLE_BAR_BG, 0xFF000000),
                    entry(Role.CONTROL_BAR_BG, 0xFF000000),
                    entry(Role.PANEL_BG, 0xFF000000),
                    entry(Role.LIST_BG, 0xFF000000),
                    entry(Role.ROW_BG, 0xFF000000),
                    entry(Role.ROW_HOVER, 0xFF303030),
                    entry(Role.ROW_SELECTED, 0xFF0057B8),
                    entry(Role.CHIP_BG, 0xFF000000),
                    entry(Role.CHIP_HOVER, 0xFF444444),
                    entry(Role.BANNER_BG, 0xFF000000),
                    entry(Role.BORDER, 0xFFFFFFFF),
                    entry(Role.BORDER_FOCUSED, 0xFF00E5FF),
                    entry(Role.TRACK, 0xFF666666),
                    entry(Role.FILL, 0xFF00E5FF),
                    entry(Role.SCROLL_THUMB, 0xFFAAAAAA),
                    entry(Role.TEXT_SUBTLE, 0xFFDDDDDD),
                    entry(Role.TEXT_DIM, 0xFFBBBBBB),
                    entry(Role.ICON, 0xFFFFFFFF),
                    entry(Role.ICON_HOVER, 0xFFFFE000),
                    entry(Role.ICON_ACTIVE, 0xFF00E5FF),
                    entry(Role.ICON_DISABLED, 0xFF666666),
                    entry(Role.DANGER, 0xFFFF5555),
                    entry(Role.MUTED, 0xFFFF3333)));

    /** Vanilla's own greys and its green, so the windows sit with the game's menus. */
    public static final Theme MINECRAFT = DARK.with(
            id("minecraft"),
            name("minecraft"),
            Map.ofEntries(
                    entry(Role.WINDOW_BG, 0xC0101010),
                    entry(Role.TITLE_BAR_BG, 0xE0202020),
                    entry(Role.CONTROL_BAR_BG, 0xE0202020),
                    entry(Role.PANEL_BG, 0xD0101010),
                    entry(Role.LIST_BG, 0xC0101010),
                    entry(Role.ROW_BG, 0xFF2B2B2B),
                    entry(Role.ROW_HOVER, 0xFF3C3C3C),
                    entry(Role.ROW_SELECTED, 0xFF505050),
                    entry(Role.CHIP_BG, 0xC0101010),
                    entry(Role.CHIP_HOVER, 0xE0404040),
                    entry(Role.BANNER_BG, 0xD0101010),
                    entry(Role.BORDER, 0xFF6C6C6C),
                    entry(Role.BORDER_FOCUSED, 0xFFFFFFFF),
                    entry(Role.TRACK, 0xFF6C6C6C),
                    entry(Role.FILL, 0xFF55FF55),
                    entry(Role.SCROLL_TRACK, 0xFF000000),
                    entry(Role.SCROLL_THUMB, 0xFF8B8B8B),
                    entry(Role.TEXT_SUBTLE, 0xFFA0A0A0),
                    entry(Role.TEXT_DIM, 0xFF808080),
                    entry(Role.ICON_HOVER, 0xFFFFFF55),
                    entry(Role.ICON_ACTIVE, 0xFF55FF55),
                    entry(Role.ICON_DISABLED, 0xFF6C6C6C),
                    entry(Role.DANGER, 0xFFFF5555)));

    /** Dark text on light panels. */
    public static final Theme LIGHT = DARK.with(
            id("light"),
            name("light"),
            Map.ofEntries(
                    entry(Role.WINDOW_BG, 0xF0F2F2F2),
                    entry(Role.TITLE_BAR_BG, 0xFFE2E2E2),
                    entry(Role.CONTROL_BAR_BG, 0xFFE8E8E8),
                    entry(Role.PLACEHOLDER, 0xFFD8D8D8),
                    entry(Role.PANEL_BG, 0xF0EDEDED),
                    entry(Role.LIST_BG, 0xE0F2F2F2),
                    entry(Role.ROW_BG, 0xFFE4E4E4),
                    entry(Role.ROW_HOVER, 0xFFD2DAEA),
                    entry(Role.ROW_SELECTED, 0xFFAFC6EE),
                    entry(Role.FIELD_BG, 0xFFFFFFFF),
                    entry(Role.CHIP_BG, 0xE0E8E8E8),
                    entry(Role.CHIP_HOVER, 0xFFFFFFFF),
                    entry(Role.BANNER_BG, 0xF0EDEDED),
                    entry(Role.BORDER, 0x60000000),
                    entry(Role.BORDER_FOCUSED, 0xFF1B72C4),
                    entry(Role.TRACK, 0xFFBEBEBE),
                    entry(Role.FILL, 0xFF1B72C4),
                    entry(Role.KNOB, 0xFF202020),
                    entry(Role.SCROLL_TRACK, 0xFFD0D0D0),
                    entry(Role.SCROLL_THUMB, 0xFF909090),
                    entry(Role.TEXT, 0xFF161616),
                    entry(Role.TEXT_SUBTLE, 0xFF4A4A4A),
                    entry(Role.TEXT_DIM, 0xFF7A7A7A),
                    entry(Role.ICON, 0xFF303030),
                    entry(Role.ICON_HOVER, 0xFFB07000),
                    entry(Role.ICON_ACTIVE, 0xFF1B72C4),
                    entry(Role.ICON_DISABLED, 0xFFB4B4B4),
                    entry(Role.DANGER, 0xFFC62828),
                    entry(Role.MUTED, 0xFFD32F2F),
                    entry(Role.SPINNER, 0xFF303030)));

    private Themes() {}

    /** The built-in themes, in the order the setting cycles them. */
    public static List<Theme> all() {
        return List.of(DARK, CONTRAST, MINECRAFT, LIGHT);
    }

    private static String id(String name) {
        return ModInfo.MOD_ID + ":" + name;
    }

    private static Text name(String name) {
        return Text.translatable("theme." + ModInfo.MOD_ID + "." + name);
    }
}
