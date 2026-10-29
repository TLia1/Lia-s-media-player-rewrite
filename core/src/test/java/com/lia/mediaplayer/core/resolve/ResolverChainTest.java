package com.lia.mediaplayer.core.resolve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.MediaMetadata;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.source.ResolveOptions;
import com.lia.mediaplayer.api.source.ResolvedMedia;
import com.lia.mediaplayer.api.source.Resolver;
import com.lia.mediaplayer.api.source.StreamUrl;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.link.BuiltinSources;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

class ResolverChainTest {

    private Instant now = Instant.parse("2026-10-29T12:00:00Z");
    private final InstantSource clock = () -> now;
    private final MediaRef ref =
            new MediaRef("https://site.example/v/1", MediaKind.VIDEO, "test:site", Text.literal("x"));

    /** A resolver whose futures the test completes by hand. */
    private static final class Manual implements Resolver {
        final List<CompletableFuture<ResolvedMedia>> calls = new ArrayList<>();

        @Override
        public boolean handles(MediaRef ref) {
            return ref.sourceId().equals("test:site");
        }

        @Override
        public CompletableFuture<ResolvedMedia> resolve(MediaRef ref, ResolveOptions options) {
            var future = new CompletableFuture<ResolvedMedia>();
            calls.add(future);
            return future;
        }
    }

    /** Starts a resolution whose result the test reads from the resolver's calls. */
    private void request(ResolverChain chain, ResolveOptions options) {
        var unused = chain.resolve(ref, options);
    }

    private ResolvedMedia media(Instant expiresAt) {
        return new ResolvedMedia(
                ref,
                Optional.of(StreamUrl.of("https://cdn.example/v")),
                Optional.empty(),
                Map.of(),
                MediaMetadata.UNKNOWN,
                expiresAt);
    }

    @Test
    void sharesOneResolutionBetweenConcurrentRequestsThenCachesIt() throws Exception {
        var manual = new Manual();
        var chain = new ResolverChain(List.of(manual), new ResolveCache(clock, 8));

        var first = chain.resolve(ref, ResolveOptions.DEFAULT);
        var second = chain.resolve(ref, ResolveOptions.DEFAULT);
        assertThat(manual.calls).hasSize(1);
        manual.calls.getFirst().complete(media(now.plusSeconds(60)));

        assertThat(first.get()).isSameAs(second.get());
        assertThat(chain.resolve(ref, ResolveOptions.DEFAULT).get()).isSameAs(first.get());
        assertThat(manual.calls).hasSize(1);
    }

    @Test
    void resolvesAgainOnceExpiredOrInvalidated() {
        var manual = new Manual();
        var chain = new ResolverChain(List.of(manual), new ResolveCache(clock, 8));
        request(chain, ResolveOptions.DEFAULT);
        manual.calls.getFirst().complete(media(now.plusSeconds(60)));

        now = now.plusSeconds(61);
        request(chain, ResolveOptions.DEFAULT);
        assertThat(manual.calls).hasSize(2);
        manual.calls.get(1).complete(media(now.plusSeconds(60)));

        chain.invalidate(ref);
        request(chain, ResolveOptions.DEFAULT);
        assertThat(manual.calls).hasSize(3);
    }

    @Test
    void differentOptionsAreDifferentResolutions() {
        var manual = new Manual();
        var chain = new ResolverChain(List.of(manual), new ResolveCache(clock, 8));
        request(chain, ResolveOptions.DEFAULT);
        request(chain, new ResolveOptions(1080, false));
        assertThat(manual.calls).hasSize(2);
    }

    @Test
    void doesNotCacheFailures() {
        var manual = new Manual();
        var chain = new ResolverChain(List.of(manual), new ResolveCache(clock, 8));
        var failed = chain.resolve(ref, ResolveOptions.DEFAULT);
        manual.calls.getFirst().completeExceptionally(new java.io.IOException("yt-dlp: Private video"));
        assertThatThrownBy(failed::get).isInstanceOf(ExecutionException.class).hasMessageContaining("Private video");
        request(chain, ResolveOptions.DEFAULT);
        assertThat(manual.calls).hasSize(2);
    }

    @Test
    void failsWhenNoResolverHandlesTheLinkOrOneThrows() {
        var chain = new ResolverChain(List.of(), new ResolveCache(clock, 8));
        assertThatThrownBy(() -> chain.resolve(ref, ResolveOptions.DEFAULT).get())
                .hasMessageContaining("No resolver");

        Resolver broken = new Resolver() {
            @Override
            public boolean handles(MediaRef ref) {
                throw new IllegalStateException("bug in an addon");
            }

            @Override
            public CompletableFuture<ResolvedMedia> resolve(MediaRef ref, ResolveOptions options) {
                throw new AssertionError();
            }
        };
        var withBroken = new ResolverChain(List.of(broken), new ResolveCache(clock, 8));
        assertThatThrownBy(() -> withBroken.resolve(ref, ResolveOptions.DEFAULT).get())
                .hasMessageContaining("bug in an addon");
    }

    @Test
    void theCacheEvictsTheLeastRecentlyUsed() {
        var cache = new ResolveCache(clock, 2);
        var a = new ResolveCache.Key("https://a.example/", ResolveOptions.DEFAULT);
        var b = new ResolveCache.Key("https://b.example/", ResolveOptions.DEFAULT);
        var c = new ResolveCache.Key("https://c.example/", ResolveOptions.DEFAULT);
        cache.put(a, media(now.plusSeconds(60)));
        cache.put(b, media(now.plusSeconds(60)));
        assertThat(cache.get(a)).isPresent();
        cache.put(c, media(now.plusSeconds(60)));
        assertThat(cache.get(b)).isEmpty();
        assertThat(cache.get(a)).isPresent();
        assertThat(cache.size()).isEqualTo(2);
    }

    @Test
    void directLinksResolveWithoutIo() throws Exception {
        var direct = new DirectResolver(clock);
        var video =
                new MediaRef("https://cdn.example/a.m3u8", MediaKind.VIDEO, BuiltinSources.STREAM, Text.literal("x"));
        var audio = new MediaRef(
                "https://cdn.example/a.mp3", MediaKind.AUDIO, BuiltinSources.AUDIO_FILE, Text.literal("x"));
        assertThat(direct.handles(video)).isTrue();
        assertThat(direct.handles(ref)).isFalse();

        ResolvedMedia stream = direct.resolve(video, ResolveOptions.DEFAULT).get();
        assertThat(stream.video())
                .hasValueSatisfying(v -> assertThat(v.protocol()).isEqualTo("m3u8"));
        assertThat(stream.metadata().tracks()).isEqualTo(MediaMetadata.Tracks.UNKNOWN);
        assertThat(stream.expiresAt()).isEqualTo(now.plus(Duration.ofDays(1)));
        assertThat(direct.resolve(audio, ResolveOptions.DEFAULT).get().audio()).isPresent();
    }
}
