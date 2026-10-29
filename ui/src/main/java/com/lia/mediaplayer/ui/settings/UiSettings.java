package com.lia.mediaplayer.ui.settings;

import com.lia.mediaplayer.api.settings.BoolSetting;
import com.lia.mediaplayer.api.settings.ChoiceSetting;
import com.lia.mediaplayer.api.settings.EnumSetting;
import com.lia.mediaplayer.api.settings.IntSetting;
import com.lia.mediaplayer.api.settings.SettingSpec;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.settings.Settings;
import com.lia.mediaplayer.ui.theme.ThemeRegistry;
import com.lia.mediaplayer.ui.theme.Themes;
import com.lia.mediaplayer.ui.window.Corner;
import java.util.List;
import java.util.Locale;

/** The settings of the windows and screens, registered beside the engine's. */
public final class UiSettings {

    public static final EnumSetting<Corner> DEFAULT_POSITION = new EnumSetting<>(
            Settings.info("windows.default_position", Settings.WINDOWS),
            Corner.TOP_RIGHT,
            corner -> Text.translatable(Settings.labelKey("windows.default_position") + "."
                    + corner.name().toLowerCase(Locale.ROOT)));

    /** Past this many video windows, a new link queues in the front one instead. */
    public static final IntSetting MAX_VIDEO_WINDOWS =
            IntSetting.of(Settings.info("windows.max_video", Settings.WINDOWS), 4, 1, 10);

    public static final IntSetting MAX_AUDIO_WINDOWS =
            IntSetting.of(Settings.info("windows.max_audio", Settings.WINDOWS), 4, 1, 10);

    /** Past this many pinned pictures, the one looked at least recently gives way. */
    public static final IntSetting MAX_IMAGE_WINDOWS =
            IntSetting.of(Settings.info("windows.max_images", Settings.WINDOWS), 8, 1, 20);

    /** Whether hovering a picture label in chat shows the picture. */
    public static final BoolSetting HOVER_PREVIEW =
            new BoolSetting(Settings.info("chat.hover_preview", Settings.CHAT), true);

    private UiSettings() {}

    /** The theme setting, whose choices are the themes registered at the moment it is shown. */
    public static ChoiceSetting theme(ThemeRegistry themes) {
        return new ChoiceSetting(
                Settings.info("appearance.theme", Settings.APPEARANCE), Themes.DARK.id(), themes::choices);
    }

    /** Every UI setting, the theme's included. */
    public static List<SettingSpec<?>> all(ChoiceSetting theme) {
        return List.of(theme, DEFAULT_POSITION, MAX_VIDEO_WINDOWS, MAX_AUDIO_WINDOWS, MAX_IMAGE_WINDOWS, HOVER_PREVIEW);
    }
}
