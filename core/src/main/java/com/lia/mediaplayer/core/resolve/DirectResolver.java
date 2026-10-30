package com.lia.mediaplayer.core.resolve;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.MediaMetadata;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.source.ResolveOptions;
import com.lia.mediaplayer.api.source.ResolvedMedia;
import com.lia.mediaplayer.api.source.Resolver;
import com.lia.mediaplayer.api.source.StreamUrl;
import com.lia.mediaplayer.core.ModInfo;
import com.lia.mediaplayer.core.link.BuiltinSources;
import java.time.Duration;
import java.time.InstantSource;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Direct files and HLS/DASH manifests: ffmpeg opens them as they are. No IO; what the file
 * contains is learnt by probing it just before it plays.
 */
public final class DirectResolver implements Resolver {

    /** A direct link does not expire, but a day keeps the cache from holding it forever. */
    private static final Duration VALIDITY = Duration.ofDays(1);

    private final InstantSource clock;

    public DirectResolver(InstantSource clock) {
        this.clock = clock;
    }

    @Override
    public String id() {
        return ModInfo.MOD_ID + ":direct";
    }

    @Override
    public boolean handles(MediaRef ref) {
        return BuiltinSources.isDirect(ref.sourceId());
    }

    @Override
    public CompletableFuture<ResolvedMedia> resolve(MediaRef ref, ResolveOptions options) {
        String protocol = ref.url().contains(".m3u8") ? "m3u8" : ref.url().contains(".mpd") ? "dash" : "https";
        var stream = Optional.of(new StreamUrl(ref.url(), protocol));
        boolean audio = ref.kind() == MediaKind.AUDIO;
        return CompletableFuture.completedFuture(new ResolvedMedia(
                ref,
                audio ? Optional.empty() : stream,
                audio ? stream : Optional.empty(),
                Map.of(),
                MediaMetadata.UNKNOWN,
                clock.instant().plus(VALIDITY)));
    }
}
