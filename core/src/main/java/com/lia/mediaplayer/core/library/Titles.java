package com.lia.mediaplayer.core.library;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.source.ResolveOptions;
import com.lia.mediaplayer.core.cache.AsyncCache;
import com.lia.mediaplayer.core.lifecycle.ClientThread;
import com.lia.mediaplayer.core.link.SourceRegistry;
import com.lia.mediaplayer.core.resolve.ResolverChain;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The titles of links, for the rows of a list. A title is looked up only when its row is on
 * screen, through the resolver — whose cache then also makes the link quick to play — and kept
 * for the game session. A link with no title (a bare file) shows as itself. Client thread only.
 */
public final class Titles {

    static final int MAX_ENTRIES = 2048;

    private final AsyncCache<String, String> cache;

    public Titles(SourceRegistry sources, ResolverChain resolver, ClientThread clientThread) {
        this.cache = new AsyncCache<>(
                clientThread, url -> load(sources, resolver, url), title -> 1, title -> {}, MAX_ENTRIES, MAX_ENTRIES);
    }

    /** The title of {@code url} if it is known; starts looking it up if it is not. */
    public Optional<String> get(String url) {
        return cache.get(url) instanceof AsyncCache.Entry.Ready<String> ready
                        && !ready.value().isEmpty()
                ? Optional.of(ready.value())
                : Optional.empty();
    }

    /** The title if known, without starting a lookup. */
    public Optional<String> peek(String url) {
        return cache.peek(url)
                .filter(AsyncCache.Entry.Ready.class::isInstance)
                .map(entry -> ((AsyncCache.Entry.Ready<String>) entry).value())
                .filter(title -> !title.isEmpty());
    }

    /** What a link shows as while its title is unknown: its host and path, without the scheme. */
    public static String shortForm(String url) {
        String rest = url.replaceFirst("^(?i)https?://(www\\.)?", "");
        return rest.endsWith("/") ? rest.substring(0, rest.length() - 1) : rest;
    }

    private static CompletableFuture<String> load(SourceRegistry sources, ResolverChain resolver, String url) {
        Optional<MediaRef> ref = sources.match(url);
        // A list's title would cost a whole expansion; it shows as its link.
        if (ref.isEmpty() || ref.get().kind() == MediaKind.COLLECTION) {
            return CompletableFuture.completedFuture("");
        }
        return resolver.resolve(ref.get(), new ResolveOptions(ResolveOptions.DEFAULT.maxHeight(), true))
                .thenApply(media -> media.metadata().title().orElse(""));
    }
}
