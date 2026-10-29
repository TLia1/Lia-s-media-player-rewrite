package com.lia.mediaplayer.core.playback;

import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.lia.mediaplayer.api.source.Resolver;
import com.lia.mediaplayer.core.audio.AudioSink;
import com.lia.mediaplayer.core.ffmpeg.ProbeCache;
import com.lia.mediaplayer.core.lifecycle.CoreExecutors;
import com.lia.mediaplayer.core.link.BuiltinSources;
import com.lia.mediaplayer.core.link.SourceRegistry;
import com.lia.mediaplayer.core.resolve.CollectionExpander;
import com.lia.mediaplayer.core.resolve.DirectResolver;
import com.lia.mediaplayer.core.resolve.ResolveCache;
import com.lia.mediaplayer.core.resolve.ResolverChain;
import com.lia.mediaplayer.core.resolve.YtDlpResolver;
import com.lia.mediaplayer.core.testing.FakeAudioSink;
import com.lia.mediaplayer.core.testing.TestClientThread;
import com.lia.mediaplayer.core.testing.TestMedia;
import com.lia.mediaplayer.core.tools.LocatedTool;
import com.lia.mediaplayer.core.tools.ProcessRunner;
import com.lia.mediaplayer.core.tools.Tool;
import com.lia.mediaplayer.core.tools.ToolProvider;
import java.nio.file.Path;
import java.time.Duration;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/** A complete engine around the machine's ffmpeg, with a fake sound card and a test client thread. */
final class TestEngine implements AutoCloseable {

    final TestClientThread client = new TestClientThread();
    final ProcessRunner processes = new ProcessRunner(client);
    final CoreExecutors executors = new CoreExecutors();
    final SourceRegistry sources = new SourceRegistry(BuiltinSources.all());
    final List<FakeAudioSink> sinks = new CopyOnWriteArrayList<>();
    final ResolverChain resolver;
    final PlaybackEngine engine;
    final PlaybackRegistry registry;

    private TestEngine(Supplier<FakeAudioSink> sinkFactory, List<Resolver> extraResolvers, Path ytDlp) {
        var tools = new EnumMap<Tool, LocatedTool>(Tool.class);
        tools.put(Tool.FFMPEG, new LocatedTool(Tool.FFMPEG, TestMedia.ffmpeg().orElseThrow(), "test"));
        tools.put(
                Tool.FFPROBE, new LocatedTool(Tool.FFPROBE, TestMedia.ffprobe().orElseThrow(), "test"));
        tools.put(Tool.YT_DLP, new LocatedTool(Tool.YT_DLP, ytDlp, "test"));
        ToolProvider provider = ToolProvider.fixed(tools);
        InstantSource clock = InstantSource.system();
        var resolvers = new ArrayList<Resolver>(extraResolvers);
        resolvers.add(new DirectResolver(clock));
        resolvers.add(new YtDlpResolver(provider, processes, executors.io(), clock, Duration.ofSeconds(20), ""));
        this.resolver = new ResolverChain(resolvers, new ResolveCache(clock, 16));
        Supplier<AudioSink> audioSinks = () -> {
            FakeAudioSink sink = sinkFactory.get();
            sinks.add(sink);
            return sink;
        };
        this.engine = new PlaybackEngine(new PlayerServices(
                resolver,
                new ProbeCache(provider, processes, Duration.ofSeconds(20)),
                provider,
                processes,
                audioSinks,
                executors.io(),
                client,
                System::nanoTime));
        var collections = new CollectionExpander(provider, processes, executors.io(), sources, Duration.ofSeconds(20));
        this.registry = new PlaybackRegistry(engine, collections, client, RandomGenerator.of("L32X64MixRandom"));
    }

    static TestEngine create() {
        return create(FakeAudioSink::new, List.of(), Path.of("yt-dlp-not-used"));
    }

    static TestEngine create(Supplier<FakeAudioSink> sinks, List<Resolver> extraResolvers, Path ytDlp) {
        requireFfmpeg();
        return new TestEngine(sinks, extraResolvers, ytDlp);
    }

    static void requireFfmpeg() {
        assumeTrue(
                TestMedia.ffmpeg().isPresent() && TestMedia.ffprobe().isPresent(),
                "ffmpeg and ffprobe are not installed");
    }

    /** Waits up to {@code timeout} for {@code condition}. */
    static void await(Duration timeout, String what, BooleanSupplier condition) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                fail("Timed out after " + timeout.toSeconds() + " s waiting for " + what);
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrupted");
            }
        }
    }

    @Override
    public void close() {
        client.run(registry::closeAll);
        processes.close();
        executors.close();
        client.close();
    }

    /** Records the states a player reports, in order. */
    static final class StateLog implements PlayerListener {
        final List<PlayerState> states = new CopyOnWriteArrayList<>();
        final List<Player> players = new CopyOnWriteArrayList<>();

        @Override
        public void stateChanged(Player player, PlayerState state) {
            players.add(player);
            states.add(state);
        }

        boolean saw(Class<? extends PlayerState> type) {
            return states.stream().anyMatch(type::isInstance);
        }

        long count(Class<? extends PlayerState> type) {
            return states.stream().filter(type::isInstance).count();
        }

        PlayerState last() {
            return states.isEmpty() ? new PlayerState.Idle() : states.getLast();
        }

        List<Class<?>> kinds() {
            return states.stream().<Class<?>>map(Object::getClass).toList();
        }

        void awaitLast(Class<? extends PlayerState> type, Duration timeout) {
            await(timeout, type.getSimpleName() + " (states: " + states + ")", () -> type.isInstance(last()));
        }
    }
}
