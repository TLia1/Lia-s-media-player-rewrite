package com.lia.mediaplayer.core.library;

import com.google.gson.JsonObject;
import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.MediaMetadata;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.source.ResolveOptions;
import com.lia.mediaplayer.api.source.WebUrls;
import com.lia.mediaplayer.core.link.SourceRegistry;
import com.lia.mediaplayer.core.resolve.ResolverChain;
import com.lia.mediaplayer.core.storage.Json;
import com.lia.mediaplayer.core.storage.JsonStore;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * The titles of links and the addresses of their pictures, kept in {@code cache/titles.json}
 * between game sessions: a playlist of 200 tracks opens with its names and pictures on the
 * second visit without 200 resolutions. A link not in the file is resolved once — which also
 * leaves it in the resolver's cache, quick to play — and added. The {@value #MAX_LINKS} most
 * recently added are kept.
 *
 * <p>Safe from any thread. The file is read on the IO executor, never on the caller's thread.
 */
public final class LinkInfoStore {

    static final int SCHEMA = 1;
    static final int MAX_LINKS = 4096;

    private final JsonStore<Map<String, LinkInfo>> store;
    private final SourceRegistry sources;
    private final ResolverChain resolver;
    private final Executor io;

    public LinkInfoStore(
            JsonStore<Map<String, LinkInfo>> store, SourceRegistry sources, ResolverChain resolver, Executor io) {
        this.store = store;
        this.sources = sources;
        this.resolver = resolver;
        this.io = io;
    }

    /** What is known of {@code url}: from the file, or resolved and then remembered. */
    public CompletableFuture<LinkInfo> lookup(String url) {
        return CompletableFuture.supplyAsync(
                        () -> Optional.ofNullable(store.get().get(url)), io)
                .thenCompose(
                        known -> known.map(CompletableFuture::completedFuture).orElseGet(() -> resolve(url)));
    }

    /** Forgets every link: the "clear the cache" button. */
    public void clear() {
        store.set(Map.of());
    }

    /** How many links are remembered. Reads the file if it was not read yet. */
    public int size() {
        return store.get().size();
    }

    private CompletableFuture<LinkInfo> resolve(String url) {
        Optional<MediaRef> ref = sources.match(url);
        // A list's title would cost a whole expansion; it shows as its link.
        if (ref.isEmpty() || ref.get().kind() == MediaKind.COLLECTION) {
            return CompletableFuture.completedFuture(LinkInfo.NONE);
        }
        boolean picture = ref.get().kind() == MediaKind.IMAGE;
        return resolver.resolve(ref.get(), new ResolveOptions(ResolveOptions.DEFAULT.maxHeight(), !picture))
                .thenApply(media -> {
                    MediaMetadata metadata = media.metadata();
                    // A picture is its own thumbnail.
                    Optional<String> thumbnail = picture
                            ? Optional.of(media.primary().url())
                            : metadata.thumbnail().filter(WebUrls::isWebUrl);
                    var info = new LinkInfo(metadata.title().orElse(""), thumbnail);
                    remember(url, info);
                    return info;
                });
    }

    private void remember(String url, LinkInfo info) {
        store.update(links -> {
            if (info.equals(links.get(url))) {
                return links;
            }
            var updated = new LinkedHashMap<String, LinkInfo>(links);
            updated.remove(url);
            updated.put(url, info);
            var oldest = updated.keySet().iterator();
            while (updated.size() > MAX_LINKS && oldest.hasNext()) {
                oldest.next();
                oldest.remove();
            }
            return Collections.unmodifiableMap(updated);
        });
    }

    /** The codec of {@code titles.json}: {@code {"links": {url: {"title": …, "thumbnail": …}}}}, oldest first. */
    public static JsonStore.Codec<Map<String, LinkInfo>> codec() {
        return new JsonStore.Codec<>() {
            @Override
            public Map<String, LinkInfo> defaults() {
                return Map.of();
            }

            @Override
            public Map<String, LinkInfo> decode(JsonObject json) {
                var links = new LinkedHashMap<String, LinkInfo>();
                for (var entry : Json.object(json, "links").entrySet()) {
                    if (!entry.getValue().isJsonObject() || !WebUrls.isWebUrl(entry.getKey())) {
                        continue;
                    }
                    JsonObject link = entry.getValue().getAsJsonObject();
                    String thumbnail = Json.string(link, "thumbnail", "");
                    links.put(
                            entry.getKey(),
                            new LinkInfo(
                                    Json.string(link, "title", ""),
                                    WebUrls.isWebUrl(thumbnail) ? Optional.of(thumbnail) : Optional.empty()));
                }
                return Collections.unmodifiableMap(links);
            }

            @Override
            public JsonObject encode(Map<String, LinkInfo> value) {
                var links = new JsonObject();
                value.forEach((url, info) -> {
                    var link = new JsonObject();
                    link.addProperty("title", info.title());
                    info.thumbnail().ifPresent(thumbnail -> link.addProperty("thumbnail", thumbnail));
                    links.add(url, link);
                });
                var json = new JsonObject();
                json.add("links", links);
                return json;
            }
        };
    }
}
