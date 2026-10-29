package com.lia.mediaplayer.core.settings;

import com.lia.mediaplayer.api.settings.SettingInfo;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.ModInfo;

/**
 * How the mod names its own settings: one name gives the id, the label key and the
 * description key, so a new setting is one line of code and two lines per language file.
 *
 * <pre>
 * name "video.max_height"
 *   id           liasmediaplayer:video.max_height
 *   label        settings.liasmediaplayer.video.max_height
 *   description  settings.liasmediaplayer.video.max_height.description
 *   warning      settings.liasmediaplayer.video.max_height.warning   (only where there is one)
 * group "video"
 *   id           liasmediaplayer:video
 *   label        settings.liasmediaplayer.group.video
 * </pre>
 */
public final class Settings {

    public static final String VIDEO = group("video");
    public static final String WINDOWS = group("windows");
    public static final String CHAT = group("chat");
    public static final String APPEARANCE = group("appearance");
    public static final String PERFORMANCE = group("performance");
    public static final String TOOLS = group("tools");

    private Settings() {}

    /** A built-in setting's description, with its label and description keys. */
    public static SettingInfo info(String name, String group) {
        return SettingInfo.of(ModInfo.MOD_ID + ":" + name, group, Text.translatable(labelKey(name)))
                .withDescription(Text.translatable(labelKey(name) + ".description"));
    }

    /** {@link #info} with a warning, under {@code <label key>.warning}. */
    public static SettingInfo infoWithWarning(String name, String group) {
        return info(name, group).withWarning(Text.translatable(labelKey(name) + ".warning"));
    }

    public static String labelKey(String name) {
        return "settings." + ModInfo.MOD_ID + "." + name;
    }

    /** The translation key of a group's name: {@code owner:name} becomes {@code settings.owner.group.name}. */
    public static String groupKey(String group) {
        int colon = group.indexOf(':');
        return "settings." + group.substring(0, colon) + ".group." + group.substring(colon + 1);
    }

    private static String group(String name) {
        return ModInfo.MOD_ID + ":" + name;
    }
}
