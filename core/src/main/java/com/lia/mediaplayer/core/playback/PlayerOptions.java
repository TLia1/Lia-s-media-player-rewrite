package com.lia.mediaplayer.core.playback;

import com.lia.mediaplayer.api.source.ResolveOptions;
import java.time.Duration;

/**
 * How a player plays.
 *
 * @param resolve what to ask the resolver for; its {@code maxHeight} also bounds the decoded
 *     picture
 * @param startAt where to begin
 * @param startPaused land in {@code Paused} instead of {@code Playing} once ready
 * @param maxFps frames are decoded at the source rate, but never above this
 * @param longPause a pause longer than this resumes by starting ffmpeg again, because remote
 *     servers drop idle connections; a shorter one resumes in place
 * @param videoBuffer how far ahead decoded frames are kept, bounded by memory at high resolutions
 * @param audioBuffer how much decoded sound is kept: the cushion against network jitter
 * @param volume linear, 0 to 1
 */
public record PlayerOptions(
        ResolveOptions resolve,
        Duration startAt,
        boolean startPaused,
        int maxFps,
        Duration longPause,
        Duration videoBuffer,
        Duration audioBuffer,
        float volume) {

    public static final PlayerOptions DEFAULT = new PlayerOptions(
            ResolveOptions.DEFAULT,
            Duration.ZERO,
            false,
            60,
            Duration.ofSeconds(10),
            Duration.ofSeconds(1),
            Duration.ofSeconds(2),
            1f);

    public PlayerOptions {
        if (startAt.isNegative() || maxFps < 1 || maxFps > 240 || longPause.isNegative()) {
            throw new IllegalArgumentException("Bad player options");
        }
        if (videoBuffer.isNegative() || videoBuffer.isZero() || audioBuffer.toMillis() < 100) {
            throw new IllegalArgumentException("Buffers too small");
        }
        volume = Math.max(0, Math.min(1, volume));
    }

    public PlayerOptions withStartAt(Duration newStart) {
        return new PlayerOptions(resolve, newStart, startPaused, maxFps, longPause, videoBuffer, audioBuffer, volume);
    }

    public PlayerOptions withResolve(ResolveOptions newResolve) {
        return new PlayerOptions(newResolve, startAt, startPaused, maxFps, longPause, videoBuffer, audioBuffer, volume);
    }

    public PlayerOptions withLongPause(Duration newLongPause) {
        return new PlayerOptions(resolve, startAt, startPaused, maxFps, newLongPause, videoBuffer, audioBuffer, volume);
    }

    public PlayerOptions withVolume(float newVolume) {
        return new PlayerOptions(resolve, startAt, startPaused, maxFps, longPause, videoBuffer, audioBuffer, newVolume);
    }
}
