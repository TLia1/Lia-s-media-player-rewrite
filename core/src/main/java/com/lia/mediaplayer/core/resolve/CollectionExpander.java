package com.lia.mediaplayer.core.resolve;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.source.WebUrls;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.ModInfo;
import com.lia.mediaplayer.core.link.SourceRegistry;
import com.lia.mediaplayer.core.tools.ProcessResult;
import com.lia.mediaplayer.core.tools.ProcessRunner;
import com.lia.mediaplayer.core.tools.Tool;
import com.lia.mediaplayer.core.tools.ToolProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Expands a {@link MediaKind#COLLECTION} link — a YouTube playlist, a SoundCloud set, a
 * Bandcamp album, anything yt-dlp lists — into the links it holds, with one
 * {@code yt-dlp --flat-playlist -J} call that reads the list without resolving each entry.
 */
public final class CollectionExpander {

    /** Entries beyond this are dropped. A queue of 500 is already more than anyone scrolls. */
    public static final int MAX_ENTRIES = 500;

    /** The source id of entries no built-in source recognises. */
    public static final String ENTRY_SOURCE = ModInfo.MOD_ID + ":collection_entry";

    private static final int MAX_JSON_BYTES = 32 * 1024 * 1024;

    private final ToolProvider tools;
    private final ProcessRunner runner;
    private final Executor io;
    private final SourceRegistry sources;
    private final Duration timeout;

    public CollectionExpander(
            ToolProvider tools, ProcessRunner runner, Executor io, SourceRegistry sources, Duration timeout) {
        this.tools = tools;
        this.runner = runner;
        this.io = io;
        this.sources = sources;
        this.timeout = timeout;
    }

    public CompletableFuture<ExpandedCollection> expand(MediaRef ref) {
        if (ref.kind() != MediaKind.COLLECTION) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(ref.url() + " is not a collection"));
        }
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return expandNow(ref);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                },
                io);
    }

    private ExpandedCollection expandNow(MediaRef ref) throws IOException {
        String ytDlp = tools.require(Tool.YT_DLP).command();
        var command = List.of(
                ytDlp,
                "--ignore-config",
                "--flat-playlist",
                "--no-warnings",
                "--playlist-end",
                Integer.toString(MAX_ENTRIES),
                "-J",
                "--",
                ref.url());
        // Listing a long playlist pages through it, so it gets twice the time of one video.
        ProcessResult result = runner.run(command, timeout.multipliedBy(2), MAX_JSON_BYTES);
        if (!result.succeeded() || result.stdout().isBlank()) {
            String detail =
                    result.stderr().isBlank() ? "returned no playlist entries for " + ref.url() : result.stderr();
            throw new IOException("yt-dlp: " + detail);
        }
        ExpandedCollection collection = parse(result.stdout(), ref, sources);
        if (collection.entries().isEmpty()) {
            throw new IOException("yt-dlp returned no playlist entries for " + ref.url());
        }
        return collection;
    }

    static ExpandedCollection parse(String json, MediaRef collection, SourceRegistry sources) throws IOException {
        JsonObject root = Json.object(json);
        var entries = new ArrayList<MediaRef>();
        for (JsonElement element : Json.array(root, "entries")) {
            if (entries.size() == MAX_ENTRIES) {
                break;
            }
            if (element.isJsonObject()) {
                entryUrl(element.getAsJsonObject()).ifPresent(url -> entries.add(entryRef(url, collection, sources)));
            }
        }
        return new ExpandedCollection(Json.string(root, "title"), entries);
    }

    /** The entry's link. A flat YouTube entry may carry only its id. */
    private static Optional<String> entryUrl(JsonObject entry) {
        Optional<String> url = Json.string(entry, "url")
                .filter(WebUrls::isWebUrl)
                .or(() -> Json.string(entry, "webpage_url").filter(WebUrls::isWebUrl));
        if (url.isPresent()) {
            return url;
        }
        if (Json.string(entry, "ie_key").filter(key -> key.equals("Youtube")).isPresent()) {
            return Json.string(entry, "id")
                    .filter(id -> id.matches("[A-Za-z0-9_-]{6,20}"))
                    .map(id -> "https://www.youtube.com/watch?v=" + id);
        }
        return Optional.empty();
    }

    private static MediaRef entryRef(String url, MediaRef collection, SourceRegistry sources) {
        return sources.match(url)
                .filter(ref -> ref.kind() == MediaKind.VIDEO || ref.kind() == MediaKind.AUDIO)
                .orElseGet(() -> new MediaRef(
                        url,
                        sources.playsAs(collection),
                        ENTRY_SOURCE,
                        Text.translatable("chat." + ModInfo.MOD_ID + ".label.collection_entry")));
    }
}
