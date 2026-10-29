package com.lia.mediaplayer.core.resolve;

import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.source.ResolveOptions;
import com.lia.mediaplayer.api.source.ResolvedMedia;
import com.lia.mediaplayer.api.source.Resolver;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves links through the first resolver that handles them: addon resolvers, then the
 * built-in ones in the order given. Results are cached until their URLs expire, and
 * concurrent requests for the same link share one resolution.
 */
public final class ResolverChain {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResolverChain.class);

    private final List<Resolver> resolvers;
    private final ResolveCache cache;
    private final Map<ResolveCache.Key, CompletableFuture<ResolvedMedia>> inFlight = new ConcurrentHashMap<>();

    public ResolverChain(List<Resolver> resolvers, ResolveCache cache) {
        this.resolvers = List.copyOf(resolvers);
        this.cache = cache;
    }

    /** Resolves {@code ref}, from the cache when its last resolution is still valid. */
    public CompletableFuture<ResolvedMedia> resolve(MediaRef ref, ResolveOptions options) {
        var key = new ResolveCache.Key(ref.url(), options);
        Optional<ResolvedMedia> cached = cache.get(key);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached.get());
        }
        var created = new CompletableFuture<ResolvedMedia>();
        CompletableFuture<ResolvedMedia> shared = inFlight.putIfAbsent(key, created);
        if (shared != null) {
            return shared.copy();
        }
        var unused = start(ref, options).whenComplete((media, error) -> {
            inFlight.remove(key, created);
            if (error != null) {
                created.completeExceptionally(error);
            } else {
                cache.put(key, media);
                created.complete(media);
            }
        });
        return created.copy();
    }

    /**
     * Forgets the cached resolution of {@code ref}, so the next request resolves it again —
     * what a stream URL refused with HTTP 403 calls for.
     */
    public void invalidate(MediaRef ref) {
        cache.invalidate(ref.url());
    }

    private CompletableFuture<ResolvedMedia> start(MediaRef ref, ResolveOptions options) {
        for (Resolver resolver : resolvers) {
            try {
                if (resolver.handles(ref)) {
                    return resolver.resolve(ref, options);
                }
            } catch (RuntimeException e) {
                LOGGER.error("Resolver {} failed on {}", resolver.getClass().getName(), ref.url(), e);
                return CompletableFuture.failedFuture(e);
            }
        }
        return CompletableFuture.failedFuture(new IOException("No resolver handles " + ref.url()));
    }
}
