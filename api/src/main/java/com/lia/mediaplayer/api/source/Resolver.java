package com.lia.mediaplayer.api.source;

import java.util.concurrent.CompletableFuture;

/**
 * Turns a recognised link into streams ffmpeg can open. Resolvers are asked in order, addon
 * resolvers first; the first that {@linkplain #handles handles} a link resolves it.
 */
public interface Resolver {

    /** Whether this resolver takes {@code ref}. Cheap, no IO, thread-safe. */
    boolean handles(MediaRef ref);

    /**
     * Resolves {@code ref}. Never called on the client thread; may block, but should return
     * a future that completes on a thread of its own when the work is long.
     */
    CompletableFuture<ResolvedMedia> resolve(MediaRef ref, ResolveOptions options);
}
