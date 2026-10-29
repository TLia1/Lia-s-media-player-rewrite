package com.lia.mediaplayer.core.image;

import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.source.ResolveOptions;
import com.lia.mediaplayer.core.net.Http;
import com.lia.mediaplayer.core.resolve.ResolverChain;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Fetches and decodes a picture: resolves the link (a Tenor page to its GIF), downloads the
 * bytes on the IO executor with a size cap, and decodes them on the decode executor. Never
 * touches a texture.
 */
public final class ImageLoader {

    /** The largest picture fetched. */
    public static final long MAX_BYTES = 8L * 1024 * 1024;

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final ResolverChain resolver;
    private final Http http;
    private final Executor io;
    private final Executor decode;

    public ImageLoader(ResolverChain resolver, Http http, Executor io, Executor decode) {
        this.resolver = resolver;
        this.http = http;
        this.io = io;
        this.decode = decode;
    }

    public CompletableFuture<DecodedImage> load(MediaRef ref) {
        return resolver.resolve(ref, ResolveOptions.DEFAULT)
                .thenApplyAsync(media -> fetch(media.primary().url()), io)
                .thenApplyAsync(ImageLoader::decode, decode);
    }

    private byte[] fetch(String url) {
        try {
            return http.get(URI.create(url), MAX_BYTES, TIMEOUT);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static DecodedImage decode(byte[] bytes) {
        try {
            return ImageDecoders.decode(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
