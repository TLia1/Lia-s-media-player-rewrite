package com.lia.mediaplayer.core.text;

import com.lia.mediaplayer.core.ModInfo;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Every translation key the code names directly. Keys built from data — a link label, an
 * error cause — come from the classes that build them; the language-file test checks both.
 */
public final class Keys {

    private static final String GUI = "gui." + ModInfo.MOD_ID + ".";
    private static final String COMMAND = "command." + ModInfo.MOD_ID + ".";
    private static final String CHAT = "chat." + ModInfo.MOD_ID + ".";

    /** {@code %s}: what follows the word, such as the queued count. */
    public static final String TIME_LIVE = GUI + "time.live";

    public static final String RETRY = GUI + "error.retry";

    public static final String PLAY = GUI + "tooltip.play";
    public static final String PAUSE = GUI + "tooltip.pause";
    public static final String NEXT = GUI + "tooltip.next";
    public static final String PREVIOUS = GUI + "tooltip.previous";
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
    public static final String HIDE = GUI + "tooltip.hide";
    public static final String THEATER = GUI + "tooltip.theater";
    public static final String FAVORITE = GUI + "tooltip.favorite";
    public static final String UNFAVORITE = GUI + "tooltip.unfavorite";
    public static final String COPY_LINK = GUI + "tooltip.copy_link";
    public static final String OPEN_IN_BROWSER = GUI + "tooltip.open_in_browser";

    /** {@code %s}: how many tracks wait. */
    public static final String QUEUE_HEADER = GUI + "queue.header";

    public static final String QUEUE_EMPTY = GUI + "queue.empty";
    public static final String QUEUE_SHOW = GUI + "queue.show";
    public static final String QUEUE_HIDE = GUI + "queue.hide";
    public static final String QUEUE_REMOVE = GUI + "queue.remove";

    public static final String IMAGE_LOADING = GUI + "image.loading";
    public static final String IMAGE_FAILED = GUI + "image.load_failed";

    /** The hover hints of chat labels: what a click and its modifiers do. */
    public static final String CHAT_HINT_VIDEO = CHAT + "hint.video";

    public static final String CHAT_HINT_AUDIO = CHAT + "hint.audio";
    public static final String CHAT_HINT_COLLECTION = CHAT + "hint.collection";
    public static final String CHAT_HINT_AUDIO_COLLECTION = CHAT + "hint.audio_collection";

    /** The small buttons over the chat. */
    public static final String CHIP_PLAYLISTS = GUI + "chip.playlists";

    /** {@code %s}: how many windows are hidden. */
    public static final String CHIP_HIDDEN = GUI + "chip.hidden";

    /** {@code %s}: the title of what started playing. */
    public static final String NOW_PLAYING = GUI + "banner.now_playing";

    public static final String CONTROLS_TITLE = GUI + "controls.title";
    public static final String CONTROLS_HINT = GUI + "controls.hint";
    public static final String CONTROLS_EMPTY = GUI + "controls.empty";

    /** The pause menu's button. */
    public static final String SETTINGS_BUTTON = GUI + "settings.button";

    public static final String SETTINGS_TITLE = GUI + "settings.title";
    public static final String SETTINGS_SEARCH = GUI + "settings.search";
    public static final String SETTINGS_NO_MATCH = GUI + "settings.no_match";
    public static final String SETTINGS_ALL = GUI + "settings.all";
    public static final String SETTINGS_RESET = GUI + "settings.reset";
    public static final String SETTINGS_RESET_ALL = GUI + "settings.reset_all";
    public static final String UPDATE_TOOLS = GUI + "settings.update_tools";
    public static final String TOOLS_LOCATING = GUI + "settings.tools.locating";
    /** {@code %s}: the tool; {@code %s}: the percentage, or "…" while unknown. */
    public static final String TOOLS_DOWNLOADING = GUI + "settings.tools.downloading";

    public static final String TOOLS_READY = GUI + "settings.tools.ready";
    /** {@code %s}: the tools that could not be found. */
    public static final String TOOLS_MISSING = GUI + "settings.tools.missing";

    public static final String DONE = GUI + "done";
    public static final String BACK = GUI + "back";

    public static final String PLAYLISTS_TITLE = GUI + "playlists.title";
    /** {@code %s}: how many playlists. */
    public static final String PLAYLISTS_COUNT = GUI + "playlists.count";

    public static final String PLAYLISTS_NEW_NAME = GUI + "playlists.new_name";
    public static final String PLAYLISTS_DEFAULT_NAME = GUI + "playlists.default_name";
    public static final String PLAYLISTS_IMPORTED_NAME = GUI + "playlists.imported_name";
    public static final String PLAYLISTS_CREATE = GUI + "playlists.create";
    public static final String PLAYLISTS_IMPORT = GUI + "playlists.import";
    public static final String PLAYLISTS_IMPORT_TOOLTIP = GUI + "playlists.import.tooltip";
    public static final String PLAYLISTS_EXPORT = GUI + "playlists.export";
    public static final String PLAYLISTS_EXPORT_TOOLTIP = GUI + "playlists.export.tooltip";
    public static final String PLAYLISTS_HISTORY = GUI + "playlists.history";
    public static final String PLAYLISTS_RENAME = GUI + "playlists.rename";
    public static final String PLAYLISTS_SEARCH = GUI + "playlists.search";
    /** {@code %s}: how many entries. */
    public static final String PLAYLISTS_ENTRIES = GUI + "playlists.entries";
    /** {@code %s}: how many match; {@code %s}: how many in all. */
    public static final String PLAYLISTS_ENTRIES_FILTERED = GUI + "playlists.entries.filtered";

    public static final String PLAYLISTS_PASTE_LINK = GUI + "playlists.paste_link";
    public static final String PLAYLISTS_ADD = GUI + "playlists.add";
    public static final String PLAYLISTS_PLAY = GUI + "playlists.play";
    public static final String PLAYLISTS_SHUFFLE = GUI + "playlists.shuffle";
    public static final String PLAYLISTS_LOOP_ON = GUI + "playlists.loop.on";
    public static final String PLAYLISTS_LOOP_OFF = GUI + "playlists.loop.off";
    public static final String PLAYLISTS_LOOP_TOOLTIP = GUI + "playlists.loop.tooltip";
    public static final String PLAYLISTS_DELETE = GUI + "playlists.delete";
    public static final String PLAYLISTS_DELETE_CONFIRM = GUI + "playlists.delete.confirm";
    public static final String PLAYLISTS_REMOVE = GUI + "playlists.remove";
    public static final String PLAYLISTS_NONE = GUI + "playlists.none";
    public static final String PLAYLISTS_NONE_HINT = GUI + "playlists.none.hint";
    public static final String PLAYLISTS_SELECT = GUI + "playlists.select";
    public static final String PLAYLISTS_SELECT_HINT = GUI + "playlists.select.hint";
    public static final String PLAYLISTS_EMPTY = GUI + "playlists.empty";
    public static final String PLAYLISTS_EMPTY_HINT = GUI + "playlists.empty.hint";
    public static final String PLAYLISTS_NO_MATCH = GUI + "playlists.no_match";
    public static final String PLAYLISTS_NO_MATCH_HINT = GUI + "playlists.no_match.hint";
    public static final String PLAYLISTS_IMPORTING = GUI + "playlists.importing";

    public static final String HISTORY_TITLE = GUI + "history.title";
    public static final String HISTORY_SEARCH = GUI + "history.search";
    /** {@code %s}: how many entries. */
    public static final String HISTORY_COUNT = GUI + "history.count";

    public static final String HISTORY_EMPTY = GUI + "history.empty";
    public static final String HISTORY_EMPTY_HINT = GUI + "history.empty.hint";
    public static final String HISTORY_FAVORITES_ON = GUI + "history.favorites.on";
    public static final String HISTORY_FAVORITES_OFF = GUI + "history.favorites.off";
    public static final String HISTORY_FAVORITES_TOOLTIP = GUI + "history.favorites.tooltip";
    public static final String HISTORY_CLEAR = GUI + "history.clear";
    public static final String HISTORY_CLEAR_TOOLTIP = GUI + "history.clear.tooltip";
    public static final String HISTORY_PLAY = GUI + "history.play";
    /** {@code %s}: the playlist's name. */
    public static final String HISTORY_ADD = GUI + "history.add";

    public static final String HISTORY_ADD_NONE = GUI + "history.add_none";
    public static final String HISTORY_REMOVE = GUI + "history.remove";
    public static final String HISTORY_KIND_IMAGE = GUI + "history.kind.image";
    public static final String HISTORY_KIND_VIDEO = GUI + "history.kind.video";
    public static final String HISTORY_KIND_AUDIO = GUI + "history.kind.audio";

    /** {@code %s}: the type the player typed. */
    public static final String COMMAND_INVALID_TYPE = COMMAND + "invalid_type";

    public static final String COMMAND_UNSUPPORTED_URL = COMMAND + "unsupported_url";
    /** {@code %s}: what the link is; {@code %s}: what it was asked to play as. */
    public static final String COMMAND_KIND_MISMATCH = COMMAND + "kind_mismatch";

    public static final String KEY_CATEGORY = "key.category." + ModInfo.MOD_ID + ".main";

    private Keys() {}

    /** The keys above. */
    public static List<String> named() {
        var keys = new ArrayList<String>();
        for (Field field : Keys.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                try {
                    keys.add((String) field.get(null));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return List.copyOf(keys);
    }
}
