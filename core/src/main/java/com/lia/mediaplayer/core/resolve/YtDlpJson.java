package com.lia.mediaplayer.core.resolve;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lia.mediaplayer.api.source.MediaMetadata;
import com.lia.mediaplayer.api.source.MediaMetadata.Tracks;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.source.ResolvedMedia;
import com.lia.mediaplayer.api.source.StreamUrl;
import com.lia.mediaplayer.api.source.WebUrls;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads what {@code yt-dlp -J} prints for one video.
 *
 * <p>When the format selector picked separate picture and sound ({@code bv*+ba}), they are
 * in {@code requested_formats}; otherwise the chosen format's fields sit at the top level. A
 * format whose {@code vcodec} is {@code "none"} has no picture, and likewise for
 * {@code acodec}. When either codec is missing the tracks are left unknown and the player
 * probes the stream.
 */
public final class YtDlpJson {

    private static final String NONE = "none";

    private YtDlpJson() {}

    public static ResolvedMedia parse(String json, MediaRef ref, Instant now) throws IOException {
        JsonObject root = Json.object(json);
        if (Json.string(root, "_type").filter(type -> type.equals("playlist")).isPresent()) {
            throw new IOException("yt-dlp: " + ref.url() + " is a playlist, not a single video");
        }

        Optional<StreamUrl> video = Optional.empty();
        Optional<StreamUrl> audio = Optional.empty();
        var headers = new LinkedHashMap<String, String>();
        headersOf(root, headers);
        Tracks tracks;
        JsonObject videoFormat = root;

        List<JsonObject> requested = new ArrayList<>();
        for (JsonElement element : Json.array(root, "requested_formats")) {
            if (element.isJsonObject()) {
                requested.add(element.getAsJsonObject());
            }
        }
        if (!requested.isEmpty()) {
            for (JsonObject format : requested) {
                headersOf(format, headers);
                if (video.isEmpty() && has(format, "vcodec")) {
                    video = Optional.of(stream(format, ref));
                    videoFormat = format;
                } else if (audio.isEmpty() && has(format, "acodec")) {
                    audio = Optional.of(stream(format, ref));
                }
            }
            if (video.isEmpty() && audio.isEmpty()) {
                throw new IOException("yt-dlp: no playable format for " + ref.url());
            }
            tracks = Tracks.of(
                    video.isPresent(), audio.isPresent() || (video.isPresent() && has(videoFormat, "acodec")));
        } else {
            StreamUrl single = stream(root, ref);
            Optional<String> vcodec = Json.string(root, "vcodec");
            Optional<String> acodec = Json.string(root, "acodec");
            boolean picture = vcodec.filter(NONE::equals).isEmpty();
            if (picture) {
                video = Optional.of(single);
            } else {
                audio = Optional.of(single);
            }
            boolean sound = acodec.filter(NONE::equals).isEmpty();
            tracks = vcodec.isPresent() && acodec.isPresent() && (picture || sound)
                    ? Tracks.of(picture, sound)
                    : Tracks.UNKNOWN;
        }

        var metadata = new MediaMetadata(
                Json.string(root, "title"),
                Json.number(root, "duration").map(seconds -> Duration.ofMillis(Math.round(seconds * 1000))),
                Json.string(root, "thumbnail").filter(WebUrls::isWebUrl),
                Json.bool(root, "is_live"),
                Json.integer(videoFormat, "width") > 0
                        ? Json.integer(videoFormat, "width")
                        : Json.integer(root, "width"),
                Json.integer(videoFormat, "height") > 0
                        ? Json.integer(videoFormat, "height")
                        : Json.integer(root, "height"),
                Json.number(videoFormat, "fps")
                        .or(() -> Json.number(root, "fps"))
                        .orElse(0.0),
                tracks);

        Optional<Instant> deadline =
                earliest(video.flatMap(v -> StreamExpiry.of(v.url())), audio.flatMap(a -> StreamExpiry.of(a.url())));
        return new ResolvedMedia(ref, video, audio, headers, metadata, StreamExpiry.cacheUntil(deadline, now));
    }

    private static boolean has(JsonObject format, String codecKey) {
        return Json.string(format, codecKey)
                .filter(codec -> !codec.equals(NONE))
                .isPresent();
    }

    private static StreamUrl stream(JsonObject format, MediaRef ref) throws IOException {
        String url =
                Json.string(format, "url").orElseThrow(() -> new IOException("yt-dlp: no stream URL for " + ref.url()));
        if (!WebUrls.isWebUrl(url)) {
            throw new IOException("yt-dlp returned a stream that is not http(s) for " + ref.url());
        }
        return new StreamUrl(url, Json.string(format, "protocol").orElse("https"));
    }

    /** Adds the format's headers. Ones that could not be sent safely are dropped. */
    private static void headersOf(JsonObject format, Map<String, String> into) {
        Json.child(format, "http_headers").ifPresent(object -> {
            for (var entry : object.entrySet()) {
                if (!entry.getValue().isJsonPrimitive()) {
                    continue;
                }
                String name = entry.getKey();
                String value = entry.getValue().getAsString();
                if (isSafeHeader(name) && isSafeHeader(value)) {
                    into.put(name, value);
                }
            }
        });
    }

    private static boolean isSafeHeader(String text) {
        return !text.isBlank() && text.indexOf('\r') < 0 && text.indexOf('\n') < 0;
    }

    @SafeVarargs
    private static Optional<Instant> earliest(Optional<Instant>... instants) {
        Optional<Instant> min = Optional.empty();
        for (Optional<Instant> instant : instants) {
            if (instant.isPresent() && (min.isEmpty() || instant.get().isBefore(min.get()))) {
                min = instant;
            }
        }
        return min;
    }
}
