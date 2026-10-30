package com.lia.mediaplayer.core.addon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.policy.Interceptor;
import com.lia.mediaplayer.api.policy.PlayOrigin;
import com.lia.mediaplayer.api.source.CollectionSource;
import com.lia.mediaplayer.api.source.LinkSource;
import com.lia.mediaplayer.api.source.MediaMetadata;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.source.ResolveOptions;
import com.lia.mediaplayer.api.source.ResolvedMedia;
import com.lia.mediaplayer.api.source.Resolver;
import com.lia.mediaplayer.api.source.StreamUrl;
import com.lia.mediaplayer.api.source.TimestampLinks;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.link.BuiltinSources;
import com.lia.mediaplayer.core.link.ChatRewriter;
import com.lia.mediaplayer.core.link.SourceRegistry;
import com.lia.mediaplayer.core.policy.Interceptors;
import com.lia.mediaplayer.core.resolve.ResolveCache;
import com.lia.mediaplayer.core.resolve.ResolverChain;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** What addons register with the engine: sources, resolvers and interceptors, behind the guard. */
class AddonRegistrationsTest {

    private final AddonGuard guard = new AddonGuard(2);
    private final SourceRegistry sources = new SourceRegistry(BuiltinSources.all());

    /** Claims every page of {@code tube.example}, and can link to a moment of it. */
    private static final class TubeSource implements LinkSource, TimestampLinks {
        final MediaKind kind;
        final String claimedId;

        TubeSource(MediaKind kind, String claimedId) {
            this.kind = kind;
            this.claimedId = claimedId;
        }

        @Override
        public String id() {
            return "myaddon:tube";
        }

        @Override
        public Optional<MediaRef> match(URI uri, String raw) {
            return "tube.example".equals(uri.getHost())
                    ? Optional.of(new MediaRef(raw, kind, claimedId, Text.literal("[tube]")))
                    : Optional.empty();
        }

        @Override
        public Optional<String> linkAt(MediaRef ref, Duration position) {
            return Optional.of(ref.url() + "?at=" + position.toSeconds());
        }
    }

    @Test
    void anAddonSourceIsAskedBeforeTheBuiltInsOfItsPriorityAndKeepsItsOwnId() {
        // A built-in claims .mp4 files; the addon's source of the same priority goes first.
        sources.addAddonSource(new TubeSource(MediaKind.AUDIO, "liasmediaplayer:youtube"), guard);
        MediaRef ref = sources.match("https://tube.example/clip.mp4").orElseThrow();
        assertThat(ref.sourceId())
                .as("an addon cannot pass its links off as a built-in's")
                .isEqualTo("myaddon:tube");
        assertThat(ref.kind()).isEqualTo(MediaKind.AUDIO);
        List<String> order = sources.sources().stream().map(LinkSource::id).toList();
        assertThat(order.indexOf("myaddon:tube")).isLessThan(order.indexOf(BuiltinSources.VIDEO_FILE));
        assertThat(order.indexOf("myaddon:tube"))
                .as("the narrower built-ins of a higher priority still go first")
                .isGreaterThan(order.indexOf(BuiltinSources.YOUTUBE_PLAYLIST));
        assertThat(sources.match("https://cdn.example/clip.mp4").orElseThrow().sourceId())
                .isEqualTo(BuiltinSources.VIDEO_FILE);
    }

    @Test
    void anAddonSourceSaysHowToLinkToAMoment() {
        sources.addAddonSource(new TubeSource(MediaKind.VIDEO, "myaddon:tube"), guard);
        MediaRef tube = sources.match("https://tube.example/v").orElseThrow();
        assertThat(sources.linkAt(tube, Duration.ofSeconds(42))).contains("https://tube.example/v?at=42");
        MediaRef youtube = sources.match("https://youtu.be/abc").orElseThrow();
        assertThat(sources.linkAt(youtube, Duration.ofSeconds(42))).contains("https://youtu.be/abc?t=42s");
    }

    @Test
    void aBrokenAddonSourceIsSkippedThenDisabled() {
        var calls = new AtomicInteger();
        sources.addAddonSource(
                new LinkSource() {
                    @Override
                    public String id() {
                        return "myaddon:broken";
                    }

                    @Override
                    public Optional<MediaRef> match(URI uri, String raw) {
                        calls.incrementAndGet();
                        throw new IllegalStateException("bug");
                    }
                },
                guard);
        for (int i = 0; i < 4; i++) {
            assertThat(sources.match("https://youtu.be/abc")).isPresent();
        }
        assertThat(calls).hasValue(2);
        assertThat(guard.isDisabled("myaddon:broken")).isTrue();
    }

    @Test
    void anAddonCollectionSaysWhatItsEntriesAre() {
        sources.addAddonSource(
                new CollectionSource() {
                    @Override
                    public MediaKind entryKind() {
                        return MediaKind.AUDIO;
                    }

                    @Override
                    public String id() {
                        return "myaddon:album";
                    }

                    @Override
                    public Optional<MediaRef> match(URI uri, String raw) {
                        return "albums.example".equals(uri.getHost())
                                ? Optional.of(new MediaRef(raw, MediaKind.COLLECTION, id(), Text.literal("[album]")))
                                : Optional.empty();
                    }
                },
                guard);
        assertThat(sources.playsAs(sources.match("https://albums.example/1").orElseThrow()))
                .isEqualTo(MediaKind.AUDIO);
    }

    @Test
    void addonResolversGoFirstInTheOrderAdded() throws Exception {
        InstantSource clock = () -> Instant.parse("2026-10-29T12:00:00Z");
        var builtIn = new Fixed("liasmediaplayer:builtin", "https://cdn.example/builtin.mp4");
        var chain = new ResolverChain(List.of(builtIn), new ResolveCache(clock, 8));
        chain.addAddonResolver(new Fixed("myaddon:first", "https://cdn.example/first.mp4"), guard);
        chain.addAddonResolver(new Fixed("myaddon:second", "https://cdn.example/second.mp4"), guard);
        var ref = new MediaRef("https://tube.example/v", MediaKind.VIDEO, "myaddon:tube", Text.literal("x"));
        ResolvedMedia media = chain.resolve(ref, ResolveOptions.DEFAULT).get();
        assertThat(media.primary().url()).isEqualTo("https://cdn.example/first.mp4");
        assertThat(media.ref()).as("cached under the link asked for").isEqualTo(ref);
    }

    @Test
    void anAddonResolverThatThrowsFailsTheResolutionAndCountsAgainstIt() {
        var chain = new ResolverChain(List.of(), new ResolveCache(Instant::now, 8));
        chain.addAddonResolver(
                new Resolver() {
                    @Override
                    public String id() {
                        return "myaddon:broken";
                    }

                    @Override
                    public boolean handles(MediaRef ref) {
                        return true;
                    }

                    @Override
                    public CompletableFuture<ResolvedMedia> resolve(MediaRef ref, ResolveOptions options) {
                        throw new IllegalStateException("bug");
                    }
                },
                guard);
        var ref = new MediaRef("https://tube.example/v", MediaKind.VIDEO, "myaddon:tube", Text.literal("x"));
        assertThatThrownBy(() -> chain.resolve(ref, ResolveOptions.DEFAULT).get())
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("myaddon:broken");
        assertThat(guard.failures("myaddon:broken")).isEqualTo(1);
    }

    @Test
    void interceptorsRefuseOnTopOfTheFilterAndABrokenOneAllows() {
        var interceptors = new Interceptors();
        var rewriter = new ChatRewriter(sources, interceptors);
        assertThat(rewriter.rewrite(List.of("https://youtu.be/abc"), "Steve")).hasSize(1);

        interceptors.add(
                new Interceptor() {
                    @Override
                    public String id() {
                        return "myaddon:broken";
                    }

                    @Override
                    public boolean allowChatLink(MediaRef ref, Optional<String> sender) {
                        throw new IllegalStateException("bug");
                    }
                },
                guard);
        assertThat(rewriter.rewrite(List.of("https://youtu.be/abc"), "Steve")).hasSize(1);

        interceptors.add(
                new Interceptor() {
                    @Override
                    public String id() {
                        return "myaddon:no_steve";
                    }

                    @Override
                    public boolean allowChatLink(MediaRef ref, Optional<String> sender) {
                        return !sender.equals(Optional.of("Steve"));
                    }

                    @Override
                    public boolean allowPlay(MediaRef ref, PlayOrigin origin) {
                        return origin != PlayOrigin.API;
                    }
                },
                guard);
        assertThat(rewriter.rewrite(List.of("https://youtu.be/abc"), "Steve")).isEmpty();
        assertThat(rewriter.rewrite(List.of("https://youtu.be/abc"), "Alex")).hasSize(1);
        assertThat(rewriter.rewrite(List.of("https://youtu.be/abc"), null)).hasSize(1);
        MediaRef ref = sources.match("https://youtu.be/abc").orElseThrow();
        assertThat(interceptors.allowPlay(ref, PlayOrigin.API)).isFalse();
        assertThat(interceptors.allowPlay(ref, PlayOrigin.CHAT_CLICK)).isTrue();
    }

    /** Resolves everything to one URL. */
    private record Fixed(String id, String url) implements Resolver {
        @Override
        public boolean handles(MediaRef ref) {
            return true;
        }

        @Override
        public CompletableFuture<ResolvedMedia> resolve(MediaRef ref, ResolveOptions options) {
            var other = new MediaRef(ref.url() + "#canonical", ref.kind(), ref.sourceId(), ref.label());
            return CompletableFuture.completedFuture(new ResolvedMedia(
                    other,
                    Optional.of(StreamUrl.of(url)),
                    Optional.empty(),
                    Map.of(),
                    MediaMetadata.UNKNOWN,
                    Instant.parse("2026-10-29T13:00:00Z")));
        }
    }
}
