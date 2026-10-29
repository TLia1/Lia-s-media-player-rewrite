package com.lia.mediaplayer.core.ffmpeg;

import com.lia.mediaplayer.api.source.StreamUrl;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * What one ffmpeg session reads and writes.
 *
 * @param primary the first input: the picture (with or without sound), or the sound alone
 * @param separateAudio a second input carrying the sound, when the site serves it apart
 * @param headers sent with every request to either input
 * @param video write the picture, as PAM frames, to stdout
 * @param audio write the sound, as PCM, to the loopback socket
 * @param start where in the media to begin
 * @param maxWidth the picture is scaled to fit this box, keeping its aspect ratio
 * @param maxHeight see {@code maxWidth}
 * @param fps the constant rate frames are written at
 */
public record SessionPlan(
        StreamUrl primary,
        Optional<StreamUrl> separateAudio,
        Map<String, String> headers,
        boolean video,
        boolean audio,
        Duration start,
        int maxWidth,
        int maxHeight,
        double fps) {

    public SessionPlan {
        headers = Map.copyOf(headers);
        if (!video && !audio) {
            throw new IllegalArgumentException("A session must write the picture, the sound, or both");
        }
        if (start.isNegative()) {
            throw new IllegalArgumentException("Negative start: " + start);
        }
        if (video && (maxWidth < 2 || maxHeight < 2 || !(fps > 0) || fps > 240)) {
            throw new IllegalArgumentException("Bad video output: " + maxWidth + "x" + maxHeight + " @ " + fps);
        }
    }

    /** Microseconds between two frames at {@link #fps()}. */
    public double frameIntervalMicros() {
        return 1_000_000.0 / fps;
    }
}
