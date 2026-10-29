package com.lia.mediaplayer.core.playback;

import com.lia.mediaplayer.core.diag.PlaybackError;
import java.time.Duration;

/**
 * Where a player is. Changes only on the player's actor; consumers switch over it with
 * patterns, so a new state is a compile error everywhere it must be handled.
 *
 * <p>Positions are where the state was entered; {@link Player#position()} is the live one.
 */
public sealed interface PlayerState {

    /** Created, not started. */
    record Idle() implements PlayerState {}

    /** Asking the resolver (and, for direct files, ffprobe) what to open. */
    record Resolving() implements PlayerState {}

    /** ffmpeg is starting, or catching up after a long pause; nothing to show yet. */
    record Buffering(Duration position) implements PlayerState {}

    record Playing(Duration position) implements PlayerState {}

    record Paused(Duration position) implements PlayerState {}

    /** A new session is starting at {@code target}; the seek bar stays there until it shows a frame. */
    record Seeking(Duration target) implements PlayerState {}

    /** The media played to its end. */
    record Ended() implements PlayerState {}

    record Failed(PlaybackError error) implements PlayerState {}
}
