package com.lia.mediaplayer.api.source;

import java.time.Duration;
import java.util.Optional;

/**
 * What is known about a piece of media before it plays. Every field may be unknown.
 *
 * @param width source width in pixels, 0 if unknown
 * @param height source height in pixels, 0 if unknown
 * @param fps source frame rate, 0 if unknown
 * @param tracks which tracks the media has, when the resolver knows
 */
public record MediaMetadata(
        Optional<String> title,
        Optional<Duration> duration,
        Optional<String> thumbnail,
        boolean live,
        int width,
        int height,
        double fps,
        Tracks tracks) {

    /** Nothing known. */
    public static final MediaMetadata UNKNOWN =
            new MediaMetadata(Optional.empty(), Optional.empty(), Optional.empty(), false, 0, 0, 0, Tracks.UNKNOWN);

    public MediaMetadata {
        if (width < 0 || height < 0 || !(fps >= 0)) {
            throw new IllegalArgumentException("Negative geometry: " + width + "x" + height + " @ " + fps);
        }
        duration = duration.filter(d -> !d.isNegative() && !d.isZero());
    }

    public MediaMetadata withTracks(Tracks newTracks) {
        return new MediaMetadata(title, duration, thumbnail, live, width, height, fps, newTracks);
    }

    /** Which tracks a piece of media has. */
    public enum Tracks {
        /** Not known yet: the engine probes the stream before playing it. */
        UNKNOWN,
        VIDEO_ONLY,
        AUDIO_ONLY,
        VIDEO_AND_AUDIO;

        public static Tracks of(boolean video, boolean audio) {
            if (video) {
                return audio ? VIDEO_AND_AUDIO : VIDEO_ONLY;
            }
            if (audio) {
                return AUDIO_ONLY;
            }
            throw new IllegalArgumentException("Media without video or audio has no tracks");
        }

        public boolean hasVideo() {
            return this == VIDEO_ONLY || this == VIDEO_AND_AUDIO;
        }

        public boolean hasAudio() {
            return this == AUDIO_ONLY || this == VIDEO_AND_AUDIO;
        }
    }
}
