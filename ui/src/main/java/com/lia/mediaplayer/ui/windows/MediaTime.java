package com.lia.mediaplayer.ui.windows;

import com.lia.mediaplayer.core.playback.PlayerSnapshot;
import com.lia.mediaplayer.core.playback.PlayerState;
import java.time.Duration;
import java.util.Optional;

/** Times as players show them, and the questions the controls ask of a snapshot. */
final class MediaTime {

    private MediaTime() {}

    /** {@code 4:05}, or {@code 1:02:03} past an hour. Negative times read as zero. */
    static String format(Duration time) {
        long total = Math.max(0, time.toSeconds());
        long hours = total / 3600;
        long minutes = (total / 60) % 60;
        long seconds = total % 60;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, seconds)
                : String.format("%d:%02d", minutes, seconds);
    }

    /** Where playback is, as a fraction of the duration; 0 when the duration is unknown. */
    static double fraction(PlayerSnapshot snapshot) {
        Optional<Duration> duration = snapshot.duration();
        if (duration.isEmpty()) {
            return 0;
        }
        return Math.max(
                0,
                Math.min(1, (double) shown(snapshot).toMillis() / duration.get().toMillis()));
    }

    /** The position to show: the target while seeking, so the bar stays where it was put. */
    static Duration shown(PlayerSnapshot snapshot) {
        return switch (snapshot.state()) {
            case PlayerState.Seeking seeking -> seeking.target();
            case PlayerState.Ended ended -> snapshot.duration().orElse(snapshot.position());
            default -> snapshot.position();
        };
    }

    /** Whether the seek bar means anything: a known duration, and a stream to seek in. */
    static boolean seekable(PlayerSnapshot snapshot) {
        if (snapshot.duration().isEmpty()) {
            return false;
        }
        return switch (snapshot.state()) {
            case PlayerState.Buffering b -> true;
            case PlayerState.Playing p -> true;
            case PlayerState.Paused p -> true;
            case PlayerState.Seeking s -> true;
            case PlayerState.Ended e -> true;
            case PlayerState.Idle i -> false;
            case PlayerState.Resolving r -> false;
            case PlayerState.Failed f -> false;
        };
    }

    /** Whether the play button should show pause bars: playback is under way or on its way. */
    static boolean running(PlayerSnapshot snapshot) {
        return switch (snapshot.state()) {
            case PlayerState.Playing p -> true;
            case PlayerState.Buffering b -> true;
            case PlayerState.Seeking s -> true;
            case PlayerState.Resolving r -> true;
            case PlayerState.Idle i -> false;
            case PlayerState.Paused p -> false;
            case PlayerState.Ended e -> false;
            case PlayerState.Failed f -> false;
        };
    }

    /** Whether to show the loading spinner. */
    static boolean busy(PlayerSnapshot snapshot) {
        return switch (snapshot.state()) {
            case PlayerState.Idle i -> true;
            case PlayerState.Resolving r -> true;
            case PlayerState.Buffering b -> true;
            case PlayerState.Seeking s -> true;
            case PlayerState.Playing p -> false;
            case PlayerState.Paused p -> false;
            case PlayerState.Ended e -> false;
            case PlayerState.Failed f -> false;
        };
    }
}
