package com.lia.mediaplayer.core.resolve;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.source.ResolveOptions;
import com.lia.mediaplayer.api.source.ResolvedMedia;
import com.lia.mediaplayer.api.source.Resolver;
import com.lia.mediaplayer.core.link.BuiltinSources;
import com.lia.mediaplayer.core.tools.ProcessResult;
import com.lia.mediaplayer.core.tools.ProcessRunner;
import com.lia.mediaplayer.core.tools.Tool;
import com.lia.mediaplayer.core.tools.ToolProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves web pages (YouTube, SoundCloud, and the thousand other sites yt-dlp knows) with
 * one {@code yt-dlp -J} call. Its JSON carries the stream URLs, the HTTP headers they need,
 * and the title, duration, thumbnail and geometry — so no separate title request and no
 * probe are needed for these links.
 */
public final class YtDlpResolver implements Resolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(YtDlpResolver.class);

    /** A video's JSON is a few hundred kilobytes; a pathological one is not worth reading. */
    private static final int MAX_JSON_BYTES = 32 * 1024 * 1024;

    /**
     * The YouTube player clients yt-dlp extracts from, most preferred first, as v2 used them.
     *
     * <p>yt-dlp's own default hands out {@code googlevideo} URLs that serve a few megabytes and
     * then answer 403: they are meant to be fetched in small ranged chunks, which yt-dlp does
     * and ffmpeg does not. The {@code android} client returns plainly streamable URLs. YouTube
     * changes this often, so {@code -Dliasmediaplayer.ytdlp.clients=…} overrides it, and an
     * empty value lets yt-dlp decide.
     */
    public static final String DEFAULT_YOUTUBE_CLIENTS = "android,default";

    private final ToolProvider tools;
    private final ProcessRunner runner;
    private final Executor io;
    private final InstantSource clock;
    private final Duration timeout;
    private final String youtubeClients;

    public YtDlpResolver(
            ToolProvider tools,
            ProcessRunner runner,
            Executor io,
            InstantSource clock,
            Duration timeout,
            String youtubeClients) {
        this.tools = tools;
        this.runner = runner;
        this.io = io;
        this.clock = clock;
        this.timeout = timeout;
        this.youtubeClients = youtubeClients;
    }

    /** Everything that plays and is not a direct file: yt-dlp is the fallback extractor. */
    @Override
    public boolean handles(MediaRef ref) {
        return (ref.kind() == MediaKind.VIDEO || ref.kind() == MediaKind.AUDIO)
                && !BuiltinSources.isDirect(ref.sourceId());
    }

    @Override
    public CompletableFuture<ResolvedMedia> resolve(MediaRef ref, ResolveOptions options) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return resolveNow(ref, options);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                },
                io);
    }

    private ResolvedMedia resolveNow(MediaRef ref, ResolveOptions options) throws IOException {
        String ytDlp = tools.require(Tool.YT_DLP).command();
        long started = System.nanoTime();
        ProcessResult result = runner.run(command(ytDlp, ref, options), timeout, MAX_JSON_BYTES);
        if (!result.succeeded() || result.stdout().isBlank()) {
            // yt-dlp's own words: they are what PlaybackError reads to tell a private video
            // from an outdated extractor, and what a bug report needs.
            String detail = result.stderr().isBlank() ? "found no playable media for " + ref.url() : result.stderr();
            throw new IOException("yt-dlp: " + detail);
        }
        ResolvedMedia media = YtDlpJson.parse(result.stdout(), ref, clock.instant());
        LOGGER.info(
                "Resolved {} in {} ms ({}, {})",
                ref.url(),
                (System.nanoTime() - started) / 1_000_000,
                media.metadata().tracks(),
                media.audio().isPresent() && media.video().isPresent() ? "separate audio" : "one stream");
        return media;
    }

    /** The command line, with the link after {@code --} so it can never be read as an option. */
    List<String> command(String ytDlp, MediaRef ref, ResolveOptions options) {
        var command = new ArrayList<>(List.of(
                ytDlp,
                // A user's own config file could change the output format parsed here, or add
                // --exec.
                "--ignore-config",
                "--no-playlist",
                "--no-warnings",
                "--no-progress",
                "-J"));
        if (ref.sourceId().equals(BuiltinSources.YOUTUBE) && !youtubeClients.isBlank()) {
            command.add("--extractor-args");
            command.add("youtube:player_client=" + youtubeClients);
        }
        command.add("-f");
        command.add(formatSelector(ref, options));
        command.add("--");
        command.add(ref.url());
        return command;
    }

    /**
     * Separate picture and sound at the wanted height first: YouTube serves only 360p as a
     * single muxed stream, so asking for one capped v2 there. Sound only when the picture
     * will not be shown.
     */
    static String formatSelector(MediaRef ref, ResolveOptions options) {
        if (options.audioOnly() || ref.kind() == MediaKind.AUDIO) {
            return "ba/b";
        }
        int h = options.maxHeight();
        return "bv*[height<=" + h + "]+ba/b[height<=" + h + "]/b";
    }
}
