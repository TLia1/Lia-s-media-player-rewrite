package com.lia.mediaplayer.core.storage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lia.mediaplayer.api.media.RepeatMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code windows.json}: one {@link WindowState} per window kind. Kept beside the other stores,
 * not with the windows, so the import of v2's files can write it too.
 *
 * <pre>
 * { "schema": 1,
 *   "kinds": { "video": { "placed": true, "x": 10, "y": 20, "width": 300, "height": 0,
 *                         "repeat": "ALL", "shuffle": false, "queueOpen": true,
 *                         "volume": 0.6 } } }
 * </pre>
 */
public final class WindowStates {

    public static final int SCHEMA = 1;

    private final JsonStore<Map<String, WindowState>> store;

    public WindowStates(JsonStore<Map<String, WindowState>> store) {
        this.store = store;
    }

    public WindowState get(String kind) {
        return store.get().getOrDefault(kind, WindowState.DEFAULT);
    }

    /** Records a kind's state; the file is written only when it really changed. */
    public void put(String kind, WindowState state) {
        store.update(states -> {
            if (state.equals(states.get(kind))) {
                return states;
            }
            var copy = new LinkedHashMap<>(states);
            copy.put(kind, state);
            return Map.copyOf(copy);
        });
    }

    public static JsonStore.Codec<Map<String, WindowState>> codec() {
        return new JsonStore.Codec<>() {
            @Override
            public Map<String, WindowState> defaults() {
                return Map.of();
            }

            @Override
            public Map<String, WindowState> decode(JsonObject json) {
                var states = new LinkedHashMap<String, WindowState>();
                for (Map.Entry<String, JsonElement> entry :
                        Json.object(json, "kinds").entrySet()) {
                    if (entry.getValue().isJsonObject()) {
                        states.put(entry.getKey(), state(entry.getValue().getAsJsonObject()));
                    }
                }
                return Map.copyOf(states);
            }

            @Override
            public JsonObject encode(Map<String, WindowState> value) {
                var kinds = new JsonObject();
                value.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> kinds.add(entry.getKey(), json(entry.getValue())));
                var json = new JsonObject();
                json.add("kinds", kinds);
                return json;
            }
        };
    }

    static WindowState state(JsonObject json) {
        WindowState d = WindowState.DEFAULT;
        return new WindowState(
                Json.bool(json, "placed", d.placed()),
                Json.integer(json, "x", d.x()),
                Json.integer(json, "y", d.y()),
                Json.integer(json, "width", d.width()),
                Json.integer(json, "height", d.height()),
                Json.enumValue(json, "repeat", RepeatMode.OFF),
                Json.bool(json, "shuffle", d.shuffle()),
                Json.bool(json, "queueOpen", d.queueOpen()),
                Json.floatValue(json, "volume", d.volume()));
    }

    static JsonObject json(WindowState state) {
        var json = new JsonObject();
        json.addProperty("placed", state.placed());
        json.addProperty("x", state.x());
        json.addProperty("y", state.y());
        json.addProperty("width", state.width());
        json.addProperty("height", state.height());
        json.addProperty("repeat", state.repeat().name());
        json.addProperty("shuffle", state.shuffle());
        json.addProperty("queueOpen", state.queueOpen());
        json.addProperty("volume", state.volume());
        return json;
    }
}
