package com.lia.mediaplayer.core.resolve;

import com.lia.mediaplayer.api.source.ResolveOptions;
import com.lia.mediaplayer.api.source.ResolvedMedia;
import java.time.InstantSource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Resolutions kept until their stream URLs expire, so a retry, a seek after a long pause and
 * the queue's title lookups do not run yt-dlp again. Least recently used entries go first
 * once it holds {@code capacity}.
 */
public final class ResolveCache {

    /** Resolutions differ by link and by what was asked for. */
    record Key(String url, ResolveOptions options) {}

    private final InstantSource clock;
    private final int capacity;
    private final Map<Key, ResolvedMedia> entries;

    public ResolveCache(InstantSource clock, int capacity) {
        this.clock = clock;
        this.capacity = capacity;
        this.entries = new LinkedHashMap<>(16, 0.75f, true);
    }

    synchronized Optional<ResolvedMedia> get(Key key) {
        ResolvedMedia media = entries.get(key);
        if (media == null) {
            return Optional.empty();
        }
        if (!media.expiresAt().isAfter(clock.instant())) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(media);
    }

    synchronized void put(Key key, ResolvedMedia media) {
        entries.put(key, media);
        var oldest = entries.keySet().iterator();
        while (entries.size() > capacity && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
    }

    /** Forgets every resolution of {@code url}, whatever was asked for. */
    synchronized void invalidate(String url) {
        entries.keySet().removeIf(key -> key.url().equals(url));
    }

    synchronized int size() {
        return entries.size();
    }
}
