package com.lia.mediaplayer.core.resolve;

import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.source.ResolveOptions;
import com.lia.mediaplayer.api.source.ResolvedMedia;
import com.lia.mediaplayer.api.source.Resolver;
import com.lia.mediaplayer.core.addon.AddonGuard;
import com.lia.mediaplayer.core.diag.jfr.ResolveEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves links through the first resolver that handles them: addon resolvers in the order
 * added, then the built-in ones in the order given. Results are cached until their URLs
 * expire, and concurrent requests for the same link share one resolution.
 *
 * <p>Addon resolvers are called through an {@link AddonGuard}; one that throws, or whose
 * future fails with a bug rather than a refusal, counts against it.
 */
public final class ResolverChain {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResolverChain.class);

    private final List<Resolver> resolvers;
    private volatile List<Guarded> addons = List.of();
    private final ResolveCache cache;
    private final Map<ResolveCache.Key, CompletableFuture<ResolvedMedia>> inFlight = new ConcurrentHashMap<>();

    public ResolverChain(List<Resolver> resolvers, ResolveCache cache) {
        this.resolvers = List.copyOf(resolvers);
        this.cache = cache;
    }

    /** Adds an addon's resolver, asked before the built-in ones and after the addons' added earlier. */
    public synchronized void addAddonResolver(Resolver resolver, AddonGuard guard) {
        var more = new ArrayList<>(addons);
        more.add(new Guarded(resolver, guard));
        addons = List.copyOf(more);
    }

    /** Resolves {@code ref}, from the cache when its last resolution is still valid. */
    public CompletableFuture<ResolvedMedia> resolve(MediaRef ref, ResolveOptions options) {
        var key = new ResolveCache.Key(ref.url(), options);
        Optional<ResolvedMedia> cached = cache.get(key);
        if (cached.isPresent()) {
            ResolveEvent.start(ref.url()).finish(true, true);
            return CompletableFuture.completedFuture(cached.get());
        }
        var created = new CompletableFuture<ResolvedMedia>();
        CompletableFuture<ResolvedMedia> shared = inFlight.putIfAbsent(key, created);
        if (shared != null) {
            return shared.copy();
        }
        var event = ResolveEvent.start(ref.url());
        var unused = start(ref, options).whenComplete((media, error) -> {
            event.finish(false, error == null);
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
        for (Guarded addon : addons) {
            if (addon.handles(ref)) {
                return addon.resolve(ref, options);
            }
        }
        for (Resolver resolver : resolvers) {
            try {
                if (resolver.handles(ref)) {
                    return resolver.resolve(ref, options);
                }
            } catch (RuntimeException e) {
                LOGGER.error("Resolver {} failed on {}", resolver.id(), ref.url(), e);
                return CompletableFuture.failedFuture(e);
            }
        }
        return CompletableFuture.failedFuture(new IOException("No resolver handles " + ref.url()));
    }

    /** An addon's resolver, called through the guard. */
    private record Guarded(Resolver resolver, AddonGuard guard) {

        boolean handles(MediaRef ref) {
            return guard.call(resolver.id(), () -> resolver.handles(ref), false);
        }

        CompletableFuture<ResolvedMedia> resolve(MediaRef ref, ResolveOptions options) {
            CompletableFuture<ResolvedMedia> future = guard.call(
                    resolver.id(),
                    () -> resolver.resolve(ref, options),
                    CompletableFuture.failedFuture(new IOException(resolver.id() + " could not resolve " + ref.url())));
            return future.thenApply(media -> {
                if (!media.ref().url().equals(ref.url())) {
                    // The result is cached under the link asked for; keep the two the same.
                    return new ResolvedMedia(
                            ref,
                            media.video(),
                            media.audio(),
                            media.httpHeaders(),
                            media.metadata(),
                            media.expiresAt());
                }
                return media;
            });
        }
    }
}
