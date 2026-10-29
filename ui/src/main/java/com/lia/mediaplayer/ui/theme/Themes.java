package com.lia.mediaplayer.ui.theme;

import static java.util.Map.entry;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.ModInfo;
import java.util.Map;

/**
 * The themes the mod ships with. The dark one is the baseline the others are written against;
 * the high-contrast, Minecraft and light themes of v2 join with the settings screen.
 */
public final class Themes {

    public static final Theme DARK = Theme.of(
            ModInfo.MOD_ID + ":dark",
            Text.translatable("theme." + ModInfo.MOD_ID + ".dark"),
            Map.ofEntries(
                    entry(Role.WINDOW_BG, 0xD0101010),
                    entry(Role.TITLE_BAR_BG, 0xF01C1C1C),
                    entry(Role.CONTROL_BAR_BG, 0xF0181818),
                    entry(Role.PLACEHOLDER, 0xFF000000),
                    entry(Role.PANEL_BG, 0xE0141414),
                    entry(Role.BORDER, 0x60FFFFFF),
                    entry(Role.BORDER_FOCUSED, 0xFF4CA6FF),
                    entry(Role.TRACK, 0xFF4A4A4A),
                    entry(Role.FILL, 0xFF4CA6FF),
                    entry(Role.KNOB, 0xFFFFFFFF),
                    entry(Role.TEXT, 0xFFFFFFFF),
                    entry(Role.TEXT_SUBTLE, 0xFFAAAAAA),
                    entry(Role.ICON, 0xFFE0E0E0),
                    entry(Role.ICON_HOVER, 0xFFFFD23F),
                    entry(Role.ICON_ACTIVE, 0xFF4CA6FF),
                    entry(Role.ICON_DISABLED, 0xFF555555),
                    entry(Role.DANGER, 0xFFFF6B6B),
                    entry(Role.MUTED, 0xFFFF5555),
                    entry(Role.SPINNER, 0xFFE0E0E0)));

    private Themes() {}
}
