package com.lia.mediaplayer.core.library;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.WebUrls;
import com.lia.mediaplayer.core.storage.Json;
import com.lia.mediaplayer.core.storage.JsonStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

/**
 * {@code library.json}, schema 1:
 *
 * <pre>
 * { "schema": 1,
 *   "playlists": [ { "id": "…", "name": "Road trip", "urls": [ "https://…" ] } ],
 *   "history":   [ { "url": "https://…", "kind": "VIDEO", "lastPlayed": 1730000000000, "favorite": true } ] }
 * </pre>
 *
 * Entries that cannot be read, and links that are not {@code http(s)}, are skipped one by one.
 */
public final class LibraryCodec implements JsonStore.Codec<LibraryData> {

    public static final int SCHEMA = 1;

    @Override
    public LibraryData defaults() {
        return LibraryData.EMPTY;
    }

    @Override
    public LibraryData decode(JsonObject json) {
        var playlists = new ArrayList<Playlist>();
        for (JsonElement element : Json.array(json, "playlists")) {
            if (element.isJsonObject()) {
                playlists.add(playlist(element.getAsJsonObject()));
            }
        }
        var history = new ArrayList<HistoryEntry>();
        for (JsonElement element : Json.array(json, "history")) {
            if (element.isJsonObject()) {
                JsonObject entry = element.getAsJsonObject();
                String url = Json.string(entry, "url", "");
                if (WebUrls.isWebUrl(url)) {
                    history.add(new HistoryEntry(
                            url,
                            Json.enumValue(entry, "kind", MediaKind.VIDEO),
                            Instant.ofEpochMilli(Json.longValue(entry, "lastPlayed", 0)),
                            Json.bool(entry, "favorite", false)));
                }
            }
        }
        return new LibraryData(playlists, history);
    }

    /** A playlist object; one without an id gets a new one. */
    static Playlist playlist(JsonObject json) {
        var urls = new ArrayList<String>();
        for (JsonElement url : Json.array(json, "urls")) {
            if (url.isJsonPrimitive()) {
                urls.add(url.getAsString());
            }
        }
        String id = Json.string(json, "id", "");
        return new Playlist(id.isBlank() ? newId() : id, Json.string(json, "name", ""), urls);
    }

    static String newId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public JsonObject encode(LibraryData value) {
        var playlists = new JsonArray();
        for (Playlist playlist : value.playlists()) {
            var json = new JsonObject();
            json.addProperty("id", playlist.id());
            json.addProperty("name", playlist.name());
            var urls = new JsonArray();
            playlist.urls().forEach(urls::add);
            json.add("urls", urls);
            playlists.add(json);
        }
        var history = new JsonArray();
        for (HistoryEntry entry : value.history()) {
            var json = new JsonObject();
            json.addProperty("url", entry.url());
            json.addProperty("kind", entry.kind().name());
            json.addProperty("lastPlayed", entry.lastPlayed().toEpochMilli());
            json.addProperty("favorite", entry.favorite());
            history.add(json);
        }
        var json = new JsonObject();
        json.add("playlists", playlists);
        json.add("history", history);
        return json;
    }
}
