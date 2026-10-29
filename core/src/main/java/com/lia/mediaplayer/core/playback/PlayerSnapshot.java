package com.lia.mediaplayer.core.playback;

import java.time.Duration;
import java.util.Optional;

/**
 * A consistent view of a player, safe to read from any thread.
 *
 * @param position live, from the master clock
 * @param videoWidth the size frames are decoded at, 0 until the first one
 * @param videoHeight see {@code videoWidth}
 */
public record PlayerSnapshot(
        PlayerState state,
        Duration position,
        Optional<Duration> duration,
        Optional<String> title,
        boolean hasVideo,
        boolean hasAudio,
        int videoWidth,
        int videoHeight) {

    /** Before anything is known: idle, at the start, no tracks. */
    public static PlayerSnapshot initial() {
        return new PlayerSnapshot(
                new PlayerState.Idle(), Duration.ZERO, Optional.empty(), Optional.empty(), false, false, 0, 0);
    }

    PlayerSnapshot withPosition(Duration newPosition) {
        return new PlayerSnapshot(state, newPosition, duration, title, hasVideo, hasAudio, videoWidth, videoHeight);
    }
}
