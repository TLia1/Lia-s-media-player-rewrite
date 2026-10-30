package com.lia.mediaplayer.core.resolve;

import com.lia.mediaplayer.api.source.MediaMetadata;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.source.ResolveOptions;
import com.lia.mediaplayer.api.source.ResolvedMedia;
import com.lia.mediaplayer.api.source.Resolver;
import com.lia.mediaplayer.api.source.StreamUrl;
import com.lia.mediaplayer.core.ModInfo;
import com.lia.mediaplayer.core.link.BuiltinSources;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.InstantSource;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A Giphy page to the GIF behind it. String work only: the page's media id names the GIF, so
 * no request is made until the picture itself is fetched.
 */
public final class GiphyResolver implements Resolver {

    private static final Duration VALIDITY = Duration.ofDays(1);

    private final InstantSource clock;

    public GiphyResolver(InstantSource clock) {
        this.clock = clock;
    }

    @Override
    public String id() {
        return ModInfo.MOD_ID + ":giphy";
    }

    @Override
    public boolean handles(MediaRef ref) {
        return ref.sourceId().equals(BuiltinSources.GIPHY);
    }

    @Override
    public CompletableFuture<ResolvedMedia> resolve(MediaRef ref, ResolveOptions options) {
        Optional<String> gif = BuiltinSources.giphyGif(URI.create(ref.url()));
        if (gif.isEmpty()) {
            return CompletableFuture.failedFuture(new IOException("Not a Giphy page: " + ref.url()));
        }
        return CompletableFuture.completedFuture(new ResolvedMedia(
                ref,
                Optional.of(StreamUrl.of(gif.get())),
                Optional.empty(),
                Map.of(),
                MediaMetadata.UNKNOWN,
                clock.instant().plus(VALIDITY)));
    }
}
