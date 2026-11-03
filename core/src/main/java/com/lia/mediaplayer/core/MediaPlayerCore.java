package com.lia.mediaplayer.core;

import com.google.gson.JsonElement;
import com.lia.mediaplayer.api.ApiVersion;
import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.ResolveOptions;
import com.lia.mediaplayer.core.addon.AddonGuard;
import com.lia.mediaplayer.core.addon.LibraryReader;
import com.lia.mediaplayer.core.addon.MediaEvents;
import com.lia.mediaplayer.core.addon.PlaybackHandles;
import com.lia.mediaplayer.core.addon.SettingValuesView;
import com.lia.mediaplayer.core.cache.DiskCache;
import com.lia.mediaplayer.core.event.EventBus;
import com.lia.mediaplayer.core.ffmpeg.ProbeCache;
import com.lia.mediaplayer.core.image.FfmpegImageDecoder;
import com.lia.mediaplayer.core.image.ImageCache;
import com.lia.mediaplayer.core.image.ImageLoader;
import com.lia.mediaplayer.core.library.Library;
import com.lia.mediaplayer.core.library.LibraryCodec;
import com.lia.mediaplayer.core.library.LibraryData;
import com.lia.mediaplayer.core.library.LinkInfo;
import com.lia.mediaplayer.core.library.LinkInfoStore;
import com.lia.mediaplayer.core.library.Thumbnails;
import com.lia.mediaplayer.core.library.Titles;
import com.lia.mediaplayer.core.lifecycle.ClientThread;
import com.lia.mediaplayer.core.lifecycle.CoreExecutors;
import com.lia.mediaplayer.core.lifecycle.Disposer;
import com.lia.mediaplayer.core.link.BuiltinSources;
import com.lia.mediaplayer.core.link.ChatRewriter;
import com.lia.mediaplayer.core.link.SourceRegistry;
import com.lia.mediaplayer.core.migrate.V2Import;
import com.lia.mediaplayer.core.net.Http;
import com.lia.mediaplayer.core.platform.Platform;
import com.lia.mediaplayer.core.playback.PlaybackEngine;
import com.lia.mediaplayer.core.playback.PlaybackRegistry;
import com.lia.mediaplayer.core.playback.PlayerOptions;
import com.lia.mediaplayer.core.playback.PlayerServices;
import com.lia.mediaplayer.core.policy.Interceptors;
import com.lia.mediaplayer.core.resolve.CollectionExpander;
import com.lia.mediaplayer.core.resolve.DirectResolver;
import com.lia.mediaplayer.core.resolve.GiphyResolver;
import com.lia.mediaplayer.core.resolve.ResolveCache;
import com.lia.mediaplayer.core.resolve.ResolverChain;
import com.lia.mediaplayer.core.resolve.TenorResolver;
import com.lia.mediaplayer.core.resolve.YtDlpResolver;
import com.lia.mediaplayer.core.settings.CoreSettings;
import com.lia.mediaplayer.core.settings.SettingsRegistry;
import com.lia.mediaplayer.core.storage.DataPaths;
import com.lia.mediaplayer.core.storage.JsonStore;
import com.lia.mediaplayer.core.storage.WindowState;
import com.lia.mediaplayer.core.storage.WindowStates;
import com.lia.mediaplayer.core.tools.ProcessRunner;
import com.lia.mediaplayer.core.tools.ToolDownloader;
import com.lia.mediaplayer.core.tools.ToolLocator;
import com.lia.mediaplayer.core.tools.ToolManager;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The composition root of the engine: builds every core service once, in dependency order,
 * and owns their lifetimes. It holds no logic of its own.
 *
 * <p>Two lifecycles meet here. The <em>game session</em> lasts from construction to
 * {@link #close()}: tools, settings, the library, which is written to disk one last time
 * then. The <em>world session</em> ends at each
 * {@link #onWorldLeft()}: players, windows, in-memory caches.
 */
public final class MediaPlayerCore implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(MediaPlayerCore.class);

    /** yt-dlp can be slow on a first call or a long playlist page. */
    private static final Duration YT_DLP_TIMEOUT = Duration.ofSeconds(60);

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(20);
    private static final int RESOLVE_CACHE_ENTRIES = 256;
    /** How long a store waits for changes to stop before it writes. */
    private static final Duration WRITE_DEBOUNCE = Duration.ofMillis(500);

    private static final long MEGABYTE = 1024L * 1024;

    private final Platform platform;
    private final DataPaths paths;
    private final Disposer disposer = new Disposer();
    private final EventBus events;
    private final SettingsRegistry settings;
    private final Library library;
    private final WindowStates windowStates;
    private final ToolManager tools;
    private final SourceRegistry sources;
    private final ChatRewriter chat;
    private final ResolverChain resolver;
    private final CollectionExpander collections;
    private final PlaybackEngine engine;
    private final PlaybackRegistry playbacks;
    private final ImageCache images;
    private final DiskCache diskCache;
    private final LinkInfoStore links;
    private final Titles titles;
    private final Thumbnails thumbnails;
    private final Executor io;
    private final AddonGuard addonGuard = new AddonGuard();
    private final Interceptors interceptors = new Interceptors();
    private final PlaybackHandles handles;
    private final MediaEvents mediaEvents;
    private final SettingValuesView settingValues;
    private final LibraryReader libraryReader;

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
        this.io = executors.io();
        this.events = new EventBus(clientThread);
        var processes = disposer.add(new ProcessRunner(clientThread));
        var http = disposer.add(new Http(clientThread));

        // Before any store reads its file: v2's files become v3's on the first start.
        var unusedImport = V2Import.runIfNeeded(paths);
        this.settings = new SettingsRegistry(disposer.add(new JsonStore<Map<String, JsonElement>>(
                paths.settings(),
                SettingsRegistry.codec(),
                1,
                List.of(),
                executors.io(),
                executors.timer(),
                WRITE_DEBOUNCE)));
        CoreSettings.all().forEach(settings::register);
        this.library = new Library(
                disposer.add(new JsonStore<LibraryData>(
                        paths.library(),
                        new LibraryCodec(),
                        LibraryCodec.SCHEMA,
                        List.of(),
                        executors.io(),
                        executors.timer(),
                        WRITE_DEBOUNCE)),
                clock);
        this.windowStates = new WindowStates(disposer.add(new JsonStore<Map<String, WindowState>>(
                paths.windows(),
                WindowStates.codec(),
                WindowStates.SCHEMA,
                List.of(),
                executors.io(),
                executors.timer(),
                WRITE_DEBOUNCE)));

        var policy = options.toolPolicy();
        this.tools = new ToolManager(
                new ToolLocator(options.host(), paths.bin(), processes),
                new ToolDownloader(http, options.host(), paths.bin(), options.downloads(), processes),
                events,
                executors.io(),
                new ToolManager.Policy(
                        policy.download(), policy.updateStaleYtDlp() && settings.get(CoreSettings.AUTO_UPDATE_TOOLS)),
                ToolManager.systemToday());

        this.sources = new SourceRegistry(BuiltinSources.all());
        this.chat = new ChatRewriter(sources, interceptors);
        settings.applyAll(
                List.of(
                        CoreSettings.LINK_FILTER,
                        CoreSettings.BLOCKED_HOSTS,
                        CoreSettings.ALLOWED_HOSTS,
                        CoreSettings.BLOCKED_SENDERS),
                () -> chat.setFilter(CoreSettings.linkFilter(settings)));
        String youtubeClients = Objects.requireNonNullElse(
                options.host().properties().apply("liasmediaplayer.ytdlp.clients"),
                YtDlpResolver.DEFAULT_YOUTUBE_CLIENTS);
        this.resolver = new ResolverChain(
                List.of(
                        new DirectResolver(clock),
                        new GiphyResolver(clock),
                        new TenorResolver(http, executors.io(), clock),
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
        // Not RandomGenerator.getDefault(): it looks the algorithm up through a ServiceLoader,
        // which under NeoForge's module layers (1.21.11) sees the JDK's provider twice and throws.
        this.playbacks = disposer.add(new PlaybackRegistry(engine, collections, clientThread, new SplittableRandom()));
        playbacks.onTrackStarted(
                (playback, ref) -> library.recordPlay(ref.url(), playback.audioOnly() ? MediaKind.AUDIO : ref.kind()));

        var pictures = new FfmpegImageDecoder(tools, processes);
        this.images = disposer.add(new ImageCache(
                new ImageLoader(resolver, http, executors.io(), executors.decode(), pictures),
                platform.textures(),
                clientThread,
                ImageCache.DEFAULT_MAX_BYTES));
        var unusedCacheSize =
                settings.apply(CoreSettings.IMAGE_CACHE_MB, megabytes -> images.setMaxBytes(megabytes * MEGABYTE));

        // The disk cache is pruned at start, off the client thread, and again when its cap shrinks.
        this.diskCache = new DiskCache(paths.thumbnails(), ".jpg", 0);
        var unusedDiskSize = settings.apply(CoreSettings.DISK_CACHE_MB, megabytes -> {
            diskCache.setMaxBytes(megabytes * MEGABYTE);
            io.execute(diskCache::prune);
        });
        var linkFile = disposer.add(new JsonStore<Map<String, LinkInfo>>(
                paths.titles(),
                LinkInfoStore.codec(),
                1,
                List.of(),
                executors.io(),
                executors.timer(),
                WRITE_DEBOUNCE));
        io.execute(linkFile::get);
        this.links = new LinkInfoStore(linkFile, sources, resolver, executors.io());
        this.titles = new Titles(links, clientThread);
        this.thumbnails = disposer.add(new Thumbnails(
                links,
                diskCache,
                http,
                pictures,
                executors.io(),
                executors.decode(),
                platform.textures(),
                clientThread));

        this.handles = new PlaybackHandles(playbacks, sources, clientThread);
        this.mediaEvents = MediaEvents.following(playbacks, handles, events);
        this.settingValues = new SettingValuesView(settings, clientThread);
        this.libraryReader = new LibraryReader(library);

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

    /** Every setting, the mod's and the addons'. */
    public SettingsRegistry settings() {
        return settings;
    }

    /** Playlists and history. */
    public Library library() {
        return library;
    }

    /** Where the windows of each kind were left. */
    public WindowStates windowStates() {
        return windowStates;
    }

    /** The titles of links, for lists. */
    public Titles titles() {
        return titles;
    }

    /** The thumbnails of links, for lists. */
    public Thumbnails thumbnails() {
        return thumbnails;
    }

    /** Thumbnails on disk, as of the last write or prune. */
    public long diskCacheBytes() {
        return diskCache.bytes();
    }

    /**
     * Empties {@code cache/}: the thumbnails, the titles and where their pictures are. What is
     * on screen is fetched again as it is next drawn. Completes on the client thread.
     */
    public CompletableFuture<Void> clearCache() {
        platform.clientThread().checkCurrent();
        thumbnails.clear();
        return CompletableFuture.runAsync(
                () -> {
                    diskCache.clear();
                    links.clear();
                },
                io);
    }

    /** How a new player plays, from the settings: sound only, or with a picture no taller than chosen. */
    public PlayerOptions playerOptions(boolean audioOnly) {
        return PlayerOptions.DEFAULT.withResolve(
                new ResolveOptions(settings.get(CoreSettings.MAX_HEIGHT).height(), audioOnly));
    }

    public ToolManager tools() {
        return tools;
    }

    public SourceRegistry sources() {
        return sources;
    }

    /** Which chat links become labels. */
    public ChatRewriter chat() {
        return chat;
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

    /** The pictures of chat links and image windows. */
    public ImageCache images() {
        return images;
    }

    // ---- The API's side -------------------------------------------------------

    /** What every call into addon code goes through. */
    public AddonGuard addonGuard() {
        return addonGuard;
    }

    /** The addons' say over chat links and what plays. */
    public Interceptors interceptors() {
        return interceptors;
    }

    /** The API's handle of each playback. */
    public PlaybackHandles handles() {
        return handles;
    }

    /** The playback events addons subscribe to. */
    public MediaEvents mediaEvents() {
        return mediaEvents;
    }

    public SettingValuesView settingValues() {
        return settingValues;
    }

    public LibraryReader libraryReader() {
        return libraryReader;
    }

    /** Stops everything that belongs to the world the player just left. */
    public void onWorldLeft() {
        LOGGER.debug("World left");
        playbacks.closeAll();
        images.clear();
        thumbnails.clear();
    }

    /** Releases everything; called once when the game shuts down. */
    @Override
    public void close() {
        LOGGER.info("{} shutting down", ModInfo.NAME);
        disposer.close();
    }
}
