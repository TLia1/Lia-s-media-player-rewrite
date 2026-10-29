package com.lia.mediaplayer.core.ffmpeg;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lia.mediaplayer.core.resolve.Json;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

/**
 * What ffprobe found in a stream.
 *
 * @param width coded width of the first picture track, 0 if none
 * @param height coded height, 0 if none
 * @param fps its frame rate, 0 if unknown
 */
public record ProbeResult(
        boolean hasVideo, boolean hasAudio, int width, int height, double fps, Optional<Duration> duration) {

    /** Reads {@code ffprobe -show_format -show_streams -print_format json}. */
    public static ProbeResult parse(String json) throws IOException {
        JsonObject root = Json.object(json);
        boolean video = false;
        boolean audio = false;
        int width = 0;
        int height = 0;
        double fps = 0;
        for (JsonElement element : Json.array(root, "streams")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject stream = element.getAsJsonObject();
            String type = Json.string(stream, "codec_type").orElse("");
            if (type.equals("video") && !video && !isCoverArt(stream)) {
                video = true;
                width = Json.integer(stream, "width");
                height = Json.integer(stream, "height");
                fps = rate(Json.string(stream, "avg_frame_rate").orElse(""));
                if (fps <= 0 || fps > 240) {
                    fps = rate(Json.string(stream, "r_frame_rate").orElse(""));
                }
            } else if (type.equals("audio")) {
                audio = true;
            }
        }
        Optional<Duration> duration = Json.child(root, "format")
                .flatMap(format -> Json.number(format, "duration"))
                .filter(seconds -> seconds > 0)
                .map(seconds -> Duration.ofMillis(Math.round(seconds * 1000)));
        return new ProbeResult(video, audio, width, height, fps > 0 && fps <= 240 ? fps : 0, duration);
    }

    /** An MP3's or M4A's album art is a one-frame "video" stream; it is not a picture track. */
    private static boolean isCoverArt(JsonObject stream) {
        return Json.child(stream, "disposition")
                .map(disposition -> Json.integer(disposition, "attached_pic") == 1)
                .orElse(false);
    }

    /** {@code "30000/1001"} → 29.97. */
    static double rate(String text) {
        int slash = text.indexOf('/');
        try {
            if (slash < 0) {
                return text.isBlank() ? 0 : Double.parseDouble(text.strip());
            }
            double numerator = Double.parseDouble(text.substring(0, slash).strip());
            double denominator = Double.parseDouble(text.substring(slash + 1).strip());
            return denominator == 0 ? 0 : numerator / denominator;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
