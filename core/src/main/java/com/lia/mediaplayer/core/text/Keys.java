package com.lia.mediaplayer.core.text;

import com.lia.mediaplayer.core.ModInfo;
import java.util.List;

/**
 * Every translation key the code names directly. Keys built from data — a link label, an
 * error cause — come from the classes that build them; the language-file test checks both.
 */
public final class Keys {

    private static final String GUI = "gui." + ModInfo.MOD_ID + ".";
    private static final String COMMAND = "command." + ModInfo.MOD_ID + ".";

    /** {@code %s}: what follows the word, such as the queued count. */
    public static final String TIME_LIVE = GUI + "time.live";

    public static final String RETRY = GUI + "error.retry";

    public static final String PLAY = GUI + "tooltip.play";
    public static final String PAUSE = GUI + "tooltip.pause";
    public static final String NEXT = GUI + "tooltip.next";
    public static final String MUTE = GUI + "tooltip.mute";
    public static final String UNMUTE = GUI + "tooltip.unmute";
    /** {@code %s}: the volume in percent. */
    public static final String VOLUME = GUI + "tooltip.volume";

    public static final String LOOP_OFF = GUI + "tooltip.loop.off";
    public static final String LOOP_ALL = GUI + "tooltip.loop.all";
    public static final String LOOP_ONE = GUI + "tooltip.loop.one";
    public static final String SHUFFLE_ON = GUI + "tooltip.shuffle.on";
    public static final String SHUFFLE_OFF = GUI + "tooltip.shuffle.off";
    public static final String CLOSE = GUI + "tooltip.close";
    public static final String COPY_LINK = GUI + "tooltip.copy_link";
    public static final String OPEN_IN_BROWSER = GUI + "tooltip.open_in_browser";

    public static final String CONTROLS_TITLE = GUI + "controls.title";
    public static final String CONTROLS_HINT = GUI + "controls.hint";
    public static final String CONTROLS_EMPTY = GUI + "controls.empty";

    /** {@code %s}: the type the player typed. */
    public static final String COMMAND_INVALID_TYPE = COMMAND + "invalid_type";

    public static final String COMMAND_UNSUPPORTED_URL = COMMAND + "unsupported_url";
    /** {@code %s}: what the link is; {@code %s}: what it was asked to play as. */
    public static final String COMMAND_KIND_MISMATCH = COMMAND + "kind_mismatch";

    public static final String COMMAND_IMAGES_LATER = COMMAND + "images_not_yet";

    public static final String KEY_CATEGORY = "key.category." + ModInfo.MOD_ID + ".main";
    public static final String KEY_CONTROLS = "key." + ModInfo.MOD_ID + ".controls";

    private Keys() {}

    /** The keys above. */
    public static List<String> named() {
        return List.of(
                TIME_LIVE,
                RETRY,
                PLAY,
                PAUSE,
                NEXT,
                MUTE,
                UNMUTE,
                VOLUME,
                LOOP_OFF,
                LOOP_ALL,
                LOOP_ONE,
                SHUFFLE_ON,
                SHUFFLE_OFF,
                CLOSE,
                COPY_LINK,
                OPEN_IN_BROWSER,
                CONTROLS_TITLE,
                CONTROLS_HINT,
                CONTROLS_EMPTY,
                COMMAND_INVALID_TYPE,
                COMMAND_UNSUPPORTED_URL,
                COMMAND_KIND_MISMATCH,
                COMMAND_IMAGES_LATER,
                KEY_CATEGORY,
                KEY_CONTROLS);
    }
}
