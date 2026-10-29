package com.lia.mediaplayer.core.settings;

import com.lia.mediaplayer.api.settings.BoolSetting;
import com.lia.mediaplayer.api.settings.EnumSetting;
import com.lia.mediaplayer.api.settings.IntSetting;
import com.lia.mediaplayer.api.settings.SettingSpec;
import com.lia.mediaplayer.api.settings.StringListSetting;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.policy.LinkFilter;
import java.util.List;
import java.util.Locale;

/** The engine's settings. The UI registers its own beside them. */
public final class CoreSettings {

    /** The tallest picture fetched and decoded. YouTube included, unlike v2. */
    public static final EnumSetting<VideoQuality> MAX_HEIGHT = new EnumSetting<>(
            Settings.info("video.max_height", Settings.VIDEO), VideoQuality.P720, VideoQuality::label);

    public static final EnumSetting<LinkFilter.Mode> LINK_FILTER = new EnumSetting<>(
            Settings.info("chat.link_filter", Settings.CHAT),
            LinkFilter.Mode.OFF,
            mode -> Text.translatable(
                    Settings.labelKey("chat.link_filter") + "." + mode.name().toLowerCase(Locale.ROOT)));

    public static final StringListSetting BLOCKED_HOSTS =
            new StringListSetting(Settings.info("chat.blocked_hosts", Settings.CHAT), List.of());

    public static final StringListSetting ALLOWED_HOSTS =
            new StringListSetting(Settings.info("chat.allowed_hosts", Settings.CHAT), List.of());

    public static final StringListSetting BLOCKED_SENDERS =
            new StringListSetting(Settings.info("chat.blocked_senders", Settings.CHAT), List.of());

    /** Texture memory the pictures of chat links and image windows may hold. */
    public static final IntSetting IMAGE_CACHE_MB = IntSetting.of(
                    Settings.infoWithWarning("performance.image_cache_mb", Settings.PERFORMANCE), 256, 32, 1024)
            .withFormat(Settings.labelKey("performance.image_cache_mb") + ".value");

    /** Replace a yt-dlp more than 30 days old at startup, rather than only saying so. */
    public static final BoolSetting AUTO_UPDATE_TOOLS =
            new BoolSetting(Settings.info("tools.auto_update", Settings.TOOLS), true);

    private CoreSettings() {}

    public static List<SettingSpec<?>> all() {
        return List.of(
                MAX_HEIGHT,
                LINK_FILTER,
                BLOCKED_HOSTS,
                ALLOWED_HOSTS,
                BLOCKED_SENDERS,
                IMAGE_CACHE_MB,
                AUTO_UPDATE_TOOLS);
    }

    /** The chat filter the four chat settings describe. */
    public static LinkFilter linkFilter(SettingsRegistry settings) {
        return new LinkFilter(
                settings.get(LINK_FILTER),
                settings.get(BLOCKED_HOSTS),
                settings.get(ALLOWED_HOSTS),
                settings.get(BLOCKED_SENDERS));
    }
}
