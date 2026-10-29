package com.lia.mediaplayer.api.source;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * What a resolver produced: everything needed to start ffmpeg.
 *
 * <p>Sites that serve picture and sound separately (YouTube's adaptive formats) fill both
 * {@code video} and {@code audio}; ffmpeg reads them as two inputs of one process. A single
 * muxed stream goes in {@code video} with {@code audio} empty, and an audio-only stream goes
 * in {@code audio} alone.
 *
 * @param httpHeaders sent with every request for these URLs; some sites refuse a request
 *     without their {@code Referer} or {@code User-Agent}
 * @param expiresAt when the URLs stop working; the engine resolves again after it
 */
public record ResolvedMedia(
        MediaRef ref,
        Optional<StreamUrl> video,
        Optional<StreamUrl> audio,
        Map<String, String> httpHeaders,
        MediaMetadata metadata,
        Instant expiresAt) {

    public ResolvedMedia {
        if (video.isEmpty() && audio.isEmpty()) {
            throw new IllegalArgumentException("Resolved media for " + ref.url() + " has no stream");
        }
        httpHeaders = Map.copyOf(httpHeaders);
        for (var header : httpHeaders.entrySet()) {
            if (containsLineBreak(header.getKey()) || containsLineBreak(header.getValue())) {
                // ffmpeg receives headers as one CRLF-separated string.
                throw new IllegalArgumentException("HTTP header contains a line break: " + header.getKey());
            }
        }
    }

    /** The URL ffmpeg opens first: the picture, or the sound when there is no picture. */
    public StreamUrl primary() {
        return video.or(() -> audio).orElseThrow();
    }

    private static boolean containsLineBreak(String text) {
        return text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0;
    }
}
