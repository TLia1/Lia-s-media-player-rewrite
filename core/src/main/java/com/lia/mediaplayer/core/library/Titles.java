package com.lia.mediaplayer.core.library;

import com.lia.mediaplayer.core.cache.AsyncCache;
import com.lia.mediaplayer.core.lifecycle.ClientThread;
import java.util.Optional;

/**
 * The titles of links, for the rows of a list. A title is looked up only when its row is on
 * screen, through the {@link LinkInfoStore} — the cache on disk, or the resolver, whose cache
 * then also makes the link quick to play — and kept for the game session. A link with no title
 * (a bare file) shows as itself. Client thread only.
 */
public final class Titles {

    static final int MAX_ENTRIES = 2048;

    private final AsyncCache<String, String> cache;

    public Titles(LinkInfoStore links, ClientThread clientThread) {
        this.cache = new AsyncCache<>(
                "titles",
                clientThread,
                url -> links.lookup(url).thenApply(LinkInfo::title),
                title -> 1,
                title -> {},
                MAX_ENTRIES,
                MAX_ENTRIES);
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
}
