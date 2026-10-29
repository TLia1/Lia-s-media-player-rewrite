package com.lia.mediaplayer.core;

import com.lia.mediaplayer.api.ApiVersion;
import com.lia.mediaplayer.core.event.EventBus;
import com.lia.mediaplayer.core.ffmpeg.ProbeCache;
import com.lia.mediaplayer.core.lifecycle.ClientThread;
import com.lia.mediaplayer.core.lifecycle.CoreExecutors;
import com.lia.mediaplayer.core.lifecycle.Disposer;
import com.lia.mediaplayer.core.link.BuiltinSources;
import com.lia.mediaplayer.core.link.SourceRegistry;
import com.lia.mediaplayer.core.net.Http;
import com.lia.mediaplayer.core.platform.Platform;
import com.lia.mediaplayer.core.playback.PlaybackEngine;
import com.lia.mediaplayer.core.playback.PlaybackRegistry;
import com.lia.mediaplayer.core.playback.PlayerServices;
import com.lia.mediaplayer.core.resolve.CollectionExpander;
import com.lia.mediaplayer.core.resolve.DirectResolver;
import com.lia.mediaplayer.core.resolve.ResolveCache;
import com.lia.mediaplayer.core.resolve.ResolverChain;
import com.lia.mediaplayer.core.resolve.YtDlpResolver;
import com.lia.mediaplayer.core.storage.DataPaths;
import com.lia.mediaplayer.core.tools.ProcessRunner;
import com.lia.mediaplayer.core.tools.ToolDownloader;
import com.lia.mediaplayer.core.tools.ToolLocator;
import com.lia.mediaplayer.core.tools.ToolManager;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The composition root of the engine: builds every core service once, in dependency order,
 * and owns their lifetimes. It holds no logic of its own.
 *
 * <p>Two lifecycles meet here. The <em>game session</em> lasts from construction to
 * {@link #close()}: tools, settings, the library. The <em>world session</em> ends at each
 * {@link #onWorldLeft()}: players, windows, in-memory caches.
 */
public final class MediaPlayerCore implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(MediaPlayerCore.class);

    /** yt-dlp can be slow on a first call or a long playlist page. */
    private static final Duration YT_DLP_TIMEOUT = Duration.ofSeconds(60);

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(20);
    private static final int RESOLVE_CACHE_ENTRIES = 256;

    private final Platform platform;
    private final DataPaths paths;
    private final Disposer disposer = new Disposer();
    private final EventBus events;
    private final ToolManager tools;
    private final SourceRegistry sources;
    private final ResolverChain resolver;
    private final CollectionExpander collections;
    private final PlaybackEngine engine;
    private final PlaybackRegistry playbacks;

    public MediaPlayerCore(Platform platform) {
        this(platform, CoreOptions.defaults());
    }

    public MediaPlayerCore(Platform platform, CoreOptions options) {
        this.platform = platform;
        this.paths = DataPaths.under(platform.gameDirectory());
        LOGGER.info(
                "{} starting on {} (API {}), data in {}",
                ModInfo.NAME,
                platform.describe(),
                ApiVersion.CURRENT,
                paths.root());

        ClientThread clientThread = platform.clientThread();
        InstantSource clock = InstantSource.system();
        var executors = disposer.add(new CoreExecutors());
        this.events = new EventBus(clientThread);
        var processes = disposer.add(new ProcessRunner(clientThread));
        var http = disposer.add(new Http(clientThread));

        this.tools = new ToolManager(
                new ToolLocator(options.host(), paths.bin(), processes),
                new ToolDownloader(http, options.host(), paths.bin(), options.downloads(), processes),
                events,
                executors.io(),
                options.toolPolicy(),
                ToolManager.systemToday());

        this.sources = new SourceRegistry(BuiltinSources.all());
        String youtubeClients = Objects.requireNonNullElse(
                options.host().properties().apply("liasmediaplayer.ytdlp.clients"),
                YtDlpResolver.DEFAULT_YOUTUBE_CLIENTS);
        this.resolver = new ResolverChain(
                List.of(
                        new DirectResolver(clock),
                        new YtDlpResolver(tools, processes, executors.io(), clock, YT_DLP_TIMEOUT, youtubeClients)),
                new ResolveCache(clock, RESOLVE_CACHE_ENTRIES));
        this.collections = new CollectionExpander(tools, processes, executors.io(), sources, YT_DLP_TIMEOUT);

        this.engine = new PlaybackEngine(new PlayerServices(
                resolver,
                new ProbeCache(tools, processes, PROBE_TIMEOUT),
                tools,
                processes,
                options.audioSinks(),
                executors.io(),
                clientThread,
                System::nanoTime));
        this.playbacks =
                disposer.add(new PlaybackRegistry(engine, collections, clientThread, RandomGenerator.getDefault()));

        if (options.installTools()) {
            var unused = tools.start();
        }
    }

    public Platform platform() {
        return platform;
    }

    public DataPaths paths() {
        return paths;
    }

    public EventBus events() {
        return events;
    }

    public ToolManager tools() {
        return tools;
    }

    public SourceRegistry sources() {
        return sources;
    }

    public ResolverChain resolver() {
        return resolver;
    }

    public CollectionExpander collections() {
        return collections;
    }

    /** Creates single players; most callers want {@link #playbacks()}. */
    public PlaybackEngine engine() {
        return engine;
    }

    public PlaybackRegistry playbacks() {
        return playbacks;
    }

    /** Stops everything that belongs to the world the player just left. */
    public void onWorldLeft() {
        LOGGER.debug("World left");
        playbacks.closeAll();
    }

    /** Releases everything; called once when the game shuts down. */
    @Override
    public void close() {
        LOGGER.info("{} shutting down", ModInfo.NAME);
        disposer.close();
    }
}
