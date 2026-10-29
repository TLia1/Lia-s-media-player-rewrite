package com.lia.mediaplayer.core.migrate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.media.RepeatMode;
import com.lia.mediaplayer.api.settings.StringListSetting;
import com.lia.mediaplayer.api.source.WebUrls;
import com.lia.mediaplayer.core.ModInfo;
import com.lia.mediaplayer.core.library.HistoryEntry;
import com.lia.mediaplayer.core.library.LibraryCodec;
import com.lia.mediaplayer.core.library.LibraryData;
import com.lia.mediaplayer.core.library.Playlist;
import com.lia.mediaplayer.core.settings.SettingsRegistry;
import com.lia.mediaplayer.core.settings.VideoQuality;
import com.lia.mediaplayer.core.storage.DataPaths;
import com.lia.mediaplayer.core.storage.Json;
import com.lia.mediaplayer.core.storage.JsonStore;
import com.lia.mediaplayer.core.storage.WindowState;
import com.lia.mediaplayer.core.storage.WindowStates;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Brings v2's files over on the first start of v3: {@code config.json} becomes {@code
 * settings.json} (each option under its new id), {@code playlists.json} and {@code
 * history.json} become {@code library.json}, and {@code windows.json} is rewritten in place.
 * The originals are renamed {@code *.v2.bak}, never deleted. The {@code bin/} folder is
 * reused as it is.
 *
 * <p>It runs only when {@code settings.json} does not exist and a v2 file does, so it runs
 * once. A v2 file that cannot be read is left where it is and the rest is imported.
 */
public final class V2Import {

    private static final Logger LOGGER = LoggerFactory.getLogger(V2Import.class);

    /** v2 and v3 name the mod's own settings under the same namespace. */
    private static final String OWN = ModInfo.MOD_ID + ":";
    /** v2's resolution setting was an index into these heights. */
    private static final int[] V2_HEIGHTS = {144, 240, 360, 480, 720};

    /** v2 option name to v3 setting id, where only the name changed. */
    private static final Map<String, String> RENAMED = Map.of(
            "max_video_windows", OWN + "windows.max_video",
            "max_audio_windows", OWN + "windows.max_audio",
            "max_pinned_images", OWN + "windows.max_images",
            "max_image_cache_mb", OWN + "performance.image_cache_mb",
            "default_window_position", OWN + "windows.default_position",
            "link_filter_mode", OWN + "chat.link_filter",
            "auto_update_tools", OWN + "tools.auto_update");

    /** v2 option name to v3 setting id, for v2's comma-separated lists, now JSON arrays. */
    private static final Map<String, String> LISTS = Map.of(
            "blocked_domains", OWN + "chat.blocked_hosts",
            "allowed_domains", OWN + "chat.allowed_hosts",
            "blocked_senders", OWN + "chat.blocked_senders");

    /** Ids of the v2 options with no v3 equivalent, dropped on purpose. */
    private static final List<String> DROPPED = List.of(
            "max_api_surfaces",
            "max_api_video_surfaces",
            "max_headless_audio",
            "max_gif_frames",
            "max_image_cache_entries",
            "frame_queue_capacity",
            "yt_dlp_timeout",
            "hardware_decoding");

    private V2Import() {}

    /** What was imported, for the log and the tests. */
    public record Result(int settings, int playlists, int historyEntries, int windowKinds) {
        static final Result NOTHING = new Result(0, 0, 0, 0);
    }

    /** Imports v2's files if this is the first start after v2. */
    public static Result runIfNeeded(DataPaths paths) {
        Path root = paths.root();
        if (Files.exists(paths.settings()) || !hasV2Files(root)) {
            return Result.NOTHING;
        }
        LOGGER.info("Importing the settings and library of Lia's Media Player 2 from {}", root);
        @Nullable JsonElement config = read(root.resolve("config.json"));
        @Nullable JsonElement playlists = read(root.resolve("playlists.json"));
        @Nullable JsonElement history = read(root.resolve("history.json"));
        @Nullable
        JsonElement windows = isV2Windows(root.resolve("windows.json")) ? read(root.resolve("windows.json")) : null;

        Map<String, JsonElement> settings =
                config != null && config.isJsonObject() ? settings(config.getAsJsonObject()) : Map.of();
        LibraryData library = new LibraryData(
                playlists != null && playlists.isJsonArray() ? playlists(playlists.getAsJsonArray()) : List.of(),
                history != null && history.isJsonArray() ? history(history.getAsJsonArray()) : List.of());
        Map<String, WindowState> states =
                windows != null && windows.isJsonObject() ? windows(windows.getAsJsonObject()) : Map.of();

        backUp(root, config, "config.json");
        backUp(root, playlists, "playlists.json");
        backUp(root, history, "history.json");
        backUp(root, windows, "windows.json");
        try {
            JsonStore.writeFile(paths.settings(), 1, SettingsRegistry.codec().encode(settings));
            if (!library.playlists().isEmpty() || !library.history().isEmpty()) {
                JsonStore.writeFile(paths.library(), LibraryCodec.SCHEMA, new LibraryCodec().encode(library));
            }
            if (!states.isEmpty()) {
                JsonStore.writeFile(
                        paths.windows(),
                        WindowStates.SCHEMA,
                        WindowStates.codec().encode(states));
            }
        } catch (IOException e) {
            LOGGER.warn("Could not write the imported files: {}", e.toString());
        }
        var result = new Result(
                settings.size(), library.playlists().size(), library.history().size(), states.size());
        LOGGER.info("Imported {}", result);
        return result;
    }

    private static boolean hasV2Files(Path root) {
        return Files.exists(root.resolve("config.json"))
                || Files.exists(root.resolve("playlists.json"))
                || Files.exists(root.resolve("history.json"))
                || isV2Windows(root.resolve("windows.json"));
    }

    /** v3 writes a schema into {@code windows.json}; v2 did not. */
    private static boolean isV2Windows(Path file) {
        @Nullable JsonElement json = read(file);
        return json != null && json.isJsonObject() && !json.getAsJsonObject().has("schema");
    }

    private static @Nullable JsonElement read(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read {} for the import: {}", file, e.toString());
            return null;
        }
    }

    /** Renames a file that was read to {@code <name>.v2.bak}; one that could not be read stays. */
    private static void backUp(Path root, @Nullable JsonElement read, String name) {
        if (read == null) {
            return;
        }
        try {
            Files.move(root.resolve(name), root.resolve(name + ".v2.bak"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("Could not rename {}: {}", name, e.toString());
        }
    }

    // ---- config.json --------------------------------------------------------

    static Map<String, JsonElement> settings(JsonObject config) {
        var out = new LinkedHashMap<String, JsonElement>();
        for (Map.Entry<String, JsonElement> entry : config.entrySet()) {
            String id = entry.getKey();
            JsonElement value = entry.getValue();
            if (!id.startsWith(OWN)) {
                // An addon's option: already namespaced by its owner, kept for when it returns.
                out.put(id, value);
                continue;
            }
            String name = id.substring(OWN.length());
            try {
                convert(name, value, out);
            } catch (RuntimeException e) {
                LOGGER.warn("Skipping v2 option {}: {}", id, e.toString());
            }
        }
        return out;
    }

    private static void convert(String name, JsonElement value, Map<String, JsonElement> out) {
        if (RENAMED.containsKey(name)) {
            out.put(RENAMED.get(name), value);
        } else if (LISTS.containsKey(name)) {
            var array = new JsonArray();
            StringListSetting.parse(value.getAsString()).forEach(array::add);
            out.put(LISTS.get(name), array);
        } else if (name.equals("video_resolution")) {
            int index = Math.max(0, Math.min(V2_HEIGHTS.length - 1, value.getAsInt()));
            out.put(
                    OWN + "video.max_height",
                    new JsonPrimitive(VideoQuality.atMost(V2_HEIGHTS[index]).name()));
        } else if (name.equals("theme")) {
            String theme = value.getAsString().strip();
            out.put(OWN + "appearance.theme", new JsonPrimitive(theme.contains(":") ? theme : OWN + theme));
        } else if (!DROPPED.contains(name)) {
            LOGGER.info("v2 option {} has no equivalent", name);
        }
    }

    // ---- playlists.json and history.json ------------------------------------

    static List<Playlist> playlists(JsonArray array) {
        var playlists = new ArrayList<Playlist>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject json = element.getAsJsonObject();
            var urls = new ArrayList<String>();
            for (JsonElement url : Json.array(json, "urls")) {
                if (url.isJsonPrimitive()) {
                    urls.add(url.getAsString());
                }
            }
            playlists.add(new Playlist(UUID.randomUUID().toString(), Json.string(json, "name", ""), urls));
        }
        return playlists;
    }

    static List<HistoryEntry> history(JsonArray array) {
        var entries = new ArrayList<HistoryEntry>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject json = element.getAsJsonObject();
            String url = Json.string(json, "url", "");
            if (WebUrls.isWebUrl(url)) {
                entries.add(new HistoryEntry(
                        url,
                        Json.enumValue(json, "kind", MediaKind.VIDEO),
                        Instant.ofEpochMilli(Json.longValue(json, "playedAt", 0)),
                        Json.bool(json, "favorite", false)));
            }
        }
        return entries;
    }

    // ---- windows.json -------------------------------------------------------

    /** v2 kept the content's width; v3 keeps the window's, which adds its one-pixel edges. */
    static Map<String, WindowState> windows(JsonObject json) {
        var states = new LinkedHashMap<String, WindowState>();
        for (String kind : List.of("image", "video", "audio")) {
            JsonElement element = json.get(kind);
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject v2 = element.getAsJsonObject();
            boolean sized = Json.bool(v2, "sized", false);
            states.put(
                    kind,
                    new WindowState(
                            Json.bool(v2, "placed", false),
                            Json.integer(v2, "x", 0),
                            Json.integer(v2, "y", 0),
                            sized ? Json.integer(v2, "width", 0) + 2 : 0,
                            0,
                            Json.enumValue(v2, "repeat", RepeatMode.OFF),
                            Json.bool(v2, "shuffle", false),
                            Json.bool(v2, "queuePanel", false),
                            WindowState.DEFAULT.volume()));
        }
        return states;
    }
}
