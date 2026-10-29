package com.lia.mediaplayer.core.ffmpeg;

import com.lia.mediaplayer.api.source.StreamUrl;
import com.lia.mediaplayer.core.tools.ProcessResult;
import com.lia.mediaplayer.core.tools.ProcessRunner;
import com.lia.mediaplayer.core.tools.Tool;
import com.lia.mediaplayer.core.tools.ToolProvider;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Probes direct files and manifests before they play, once per URL for the game session.
 * Links resolved by yt-dlp are never probed: its JSON already says what they contain.
 */
public final class ProbeCache {

    private static final int CAPACITY = 256;
    private static final int MAX_JSON_BYTES = 4 * 1024 * 1024;

    private final ToolProvider tools;
    private final ProcessRunner runner;
    private final Duration timeout;
    private final Map<String, ProbeResult> results = new LinkedHashMap<>(16, 0.75f, true);

    public ProbeCache(ToolProvider tools, ProcessRunner runner, Duration timeout) {
        this.tools = tools;
        this.runner = runner;
        this.timeout = timeout;
    }

    /** What {@code url} contains. Blocking. */
    public ProbeResult probe(StreamUrl url, Map<String, String> headers) throws IOException {
        synchronized (results) {
            ProbeResult cached = results.get(url.url());
            if (cached != null) {
                return cached;
            }
        }
        String ffprobe = tools.require(Tool.FFPROBE).command();
        ProcessResult result = runner.run(FfmpegCommand.probe(ffprobe, url, headers), timeout, MAX_JSON_BYTES);
        if (!result.succeeded() || result.stdout().isBlank()) {
            // ffprobe's own reason (an HTTP 403, a missing codec) is what the error panel needs.
            throw new IOException(
                    "ffprobe could not read " + url.url() + " (exit " + result.exitCode() + "): " + result.stderr());
        }
        ProbeResult probed = ProbeResult.parse(result.stdout());
        if (!probed.hasVideo() && !probed.hasAudio()) {
            throw new IOException(url.url() + " does not contain any stream ffmpeg can play");
        }
        synchronized (results) {
            results.put(url.url(), probed);
            var oldest = results.keySet().iterator();
            while (results.size() > CAPACITY && oldest.hasNext()) {
                oldest.next();
                oldest.remove();
            }
        }
        return probed;
    }
}
