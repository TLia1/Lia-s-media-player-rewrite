package com.lia.mediaplayer.api.media;

import java.time.Duration;
import java.util.Optional;

/**
 * A consistent view of a playback's current track at one moment. Immutable; read a new one to
 * see a change.
 *
 * @param position live, from the sound clock; while {@link PlaybackState#SEEKING}, where the
 *     seek is going
 * @param duration empty for live streams and until the length is known
 * @param title from the site, when the resolver knows it
 * @param hasVideo whether the track has a picture (an audio bar never shows one)
 * @since 4.0.0
 */
public record PlaybackSnapshot(
        PlaybackState state,
        Duration position,
        Optional<Duration> duration,
        Optional<String> title,
        boolean hasVideo,
        boolean hasAudio) {

    /** Before anything is known. */
    public static final PlaybackSnapshot IDLE =
            new PlaybackSnapshot(PlaybackState.IDLE, Duration.ZERO, Optional.empty(), Optional.empty(), false, false);

    public PlaybackSnapshot {
        if (position.isNegative()) {
            position = Duration.ZERO;
        }
    }
}
