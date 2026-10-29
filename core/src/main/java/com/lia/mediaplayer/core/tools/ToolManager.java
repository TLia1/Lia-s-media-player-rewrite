package com.lia.mediaplayer.core.tools;

import com.lia.mediaplayer.core.event.EventBus;
import com.lia.mediaplayer.core.net.Http;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Makes sure ffmpeg, ffprobe and yt-dlp are available: finds installed copies, downloads
 * what is missing into the mod's {@code bin/} folder, and replaces a yt-dlp too old to keep
 * up with YouTube.
 *
 * <p>{@link #start()} runs this once in the background, at game start, so the tools are ready
 * before the first link is clicked. Every step is published as a {@link ToolState} on the
 * event bus.
 */
public final class ToolManager implements ToolProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolManager.class);

    /**
     * How old a yt-dlp build may be before it is treated as stale. It releases every week or
     * two and YouTube breaks extractors at about that pace, so a month is generous.
     */
    static final int YT_DLP_MAX_AGE_DAYS = 30;

    private static final Pattern RELEASE_DATE = Pattern.compile("(\\d{4})\\.(\\d{2})\\.(\\d{2})");

    /**
     * What the manager may do on its own.
     *
     * @param download fetch missing tools; off, a missing tool is reported and nothing is
     *     downloaded
     * @param updateStaleYtDlp replace a yt-dlp older than {@value #YT_DLP_MAX_AGE_DAYS} days at startup
     */
    public record Policy(boolean download, boolean updateStaleYtDlp) {
        public static final Policy DEFAULT = new Policy(true, true);
    }

    private final ToolLocator locator;
    private final ToolDownloader downloader;
    private final EventBus events;
    private final Executor io;
    private final Policy policy;
    private final Supplier<LocalDate> today;
    private final AtomicReference<ToolState> state = new AtomicReference<>(new ToolState.Locating());
    private final AtomicReference<@Nullable CompletableFuture<ToolState>> install = new AtomicReference<>();

    public ToolManager(
            ToolLocator locator,
            ToolDownloader downloader,
            EventBus events,
            Executor io,
            Policy policy,
            Supplier<LocalDate> today) {
        this.locator = locator;
        this.downloader = downloader;
        this.events = events;
        this.io = io;
        this.policy = policy;
        this.today = today;
    }

    /** {@link #today} for the real clock. */
    public static Supplier<LocalDate> systemToday() {
        return () -> LocalDate.now(ZoneId.systemDefault());
    }

    /** Starts the install in the background; later calls return the same future. */
    public CompletableFuture<ToolState> start() {
        var started = new CompletableFuture<ToolState>();
        if (!install.compareAndSet(null, started)) {
            return requireInstall();
        }
        io.execute(() -> complete(started, () -> install(false)));
        return started;
    }

    /**
     * Downloads yt-dlp again even though a copy works — what "update the tools" means, since
     * the copy being replaced is working, just too old for what YouTube serves today — and
     * ffmpeg if it is missing.
     */
    public CompletableFuture<ToolState> update() {
        var updating = new CompletableFuture<ToolState>();
        CompletableFuture<ToolState> previous = install.getAndSet(updating);
        CompletableFuture<ToolState> after = previous == null ? CompletableFuture.completedFuture(state()) : previous;
        var unused =
                after.handle((ignored, error) -> null).thenRunAsync(() -> complete(updating, () -> install(true)), io);
        return updating;
    }

    public ToolState state() {
        return state.get();
    }

    @Override
    public LocatedTool require(Tool tool) throws ToolMissingException {
        ToolState settled;
        try {
            settled = start().join();
        } catch (CompletionException e) {
            throw new ToolMissingException(tool);
        }
        Map<Tool, LocatedTool> tools = switch (settled) {
            case ToolState.Ready ready -> ready.tools();
            case ToolState.Missing missing -> missing.found();
            case ToolState.Locating locating -> Map.of();
            case ToolState.Downloading downloading -> Map.of();
        };
        LocatedTool located = tools.get(tool);
        if (located == null) {
            throw new ToolMissingException(tool);
        }
        return located;
    }

    private CompletableFuture<ToolState> requireInstall() {
        CompletableFuture<ToolState> current = install.get();
        if (current == null) {
            throw new IllegalStateException("Install not started");
        }
        return current;
    }

    private void complete(CompletableFuture<ToolState> future, Supplier<ToolState> work) {
        try {
            future.complete(work.get());
        } catch (RuntimeException e) {
            LOGGER.error("Tool install failed", e);
            future.completeExceptionally(e);
        }
    }

    private ToolState install(boolean forceYtDlp) {
        publish(new ToolState.Locating());
        var found = new EnumMap<Tool, LocatedTool>(Tool.class);
        for (Tool tool : Tool.values()) {
            locator.find(tool).ifPresent(located -> found.put(tool, located));
        }

        LocatedTool ytDlp = found.get(Tool.YT_DLP);
        boolean stale = false;
        if (ytDlp != null && isStale(ytDlp.version(), today.get())) {
            LOGGER.info("yt-dlp {} is more than {} days old", ytDlp.version(), YT_DLP_MAX_AGE_DAYS);
            stale = true;
        }
        if (policy.download() && (ytDlp == null || forceYtDlp || (stale && policy.updateStaleYtDlp()))) {
            fetch(Tool.YT_DLP, () -> downloader.downloadYtDlp(progressOf(Tool.YT_DLP)))
                    .flatMap(path -> locator.check(Tool.YT_DLP, path))
                    .ifPresent(located -> found.put(Tool.YT_DLP, located));
        }

        if (policy.download() && (!found.containsKey(Tool.FFMPEG) || !found.containsKey(Tool.FFPROBE))) {
            fetch(Tool.FFMPEG, () -> {
                downloader.downloadFfmpeg(progressOf(Tool.FFMPEG));
                return locator.managedPath(Tool.FFMPEG);
            });
            for (Tool tool : List.of(Tool.FFMPEG, Tool.FFPROBE)) {
                locator.check(tool, locator.managedPath(tool)).ifPresent(located -> found.put(tool, located));
            }
        }

        ToolState result;
        if (found.size() == Tool.values().length) {
            result = new ToolState.Ready(found);
            LOGGER.info("Tools ready: {}", found.values());
        } else {
            var checked = new EnumMap<Tool, List<Path>>(Tool.class);
            for (Tool tool : Tool.values()) {
                if (!found.containsKey(tool)) {
                    checked.put(tool, locator.candidates(tool));
                }
            }
            result = new ToolState.Missing(found, checked);
            LOGGER.warn("Tools missing: {}; checked {}", checked.keySet(), checked);
        }
        publish(result);
        return result;
    }

    @FunctionalInterface
    private interface Download {
        Path run() throws IOException;
    }

    private Optional<Path> fetch(Tool tool, Download download) {
        publish(new ToolState.Downloading(tool, -1));
        try {
            return Optional.of(download.run());
        } catch (IOException e) {
            LOGGER.warn("Could not download {}: {}", tool.baseName(), e.toString());
            return Optional.empty();
        }
    }

    /** Publishes progress in steps of a percent, so a fast download does not flood the bus. */
    private Http.Progress progressOf(Tool tool) {
        int[] lastPercent = {-1};
        return (bytes, total) -> {
            if (total <= 0) {
                return;
            }
            int percent = (int) (bytes * 100 / total);
            if (percent != lastPercent[0]) {
                lastPercent[0] = percent;
                publish(new ToolState.Downloading(tool, percent / 100.0));
            }
        };
    }

    private void publish(ToolState newState) {
        state.set(newState);
        events.post(newState);
    }

    /**
     * Whether a yt-dlp version string (a release date, e.g. {@code 2026.09.14}) is older than
     * {@value #YT_DLP_MAX_AGE_DAYS} days on {@code today}. A version without a readable date is
     * not stale: it is more likely a distribution's own packaging than an old copy, and nagging
     * about it every launch would be worse than missing one update.
     */
    static boolean isStale(String version, LocalDate today) {
        var matcher = RELEASE_DATE.matcher(version);
        if (!matcher.find()) {
            return false;
        }
        try {
            LocalDate released = LocalDate.of(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)));
            return ChronoUnit.DAYS.between(released, today) > YT_DLP_MAX_AGE_DAYS;
        } catch (java.time.DateTimeException e) {
            return false;
        }
    }
}
