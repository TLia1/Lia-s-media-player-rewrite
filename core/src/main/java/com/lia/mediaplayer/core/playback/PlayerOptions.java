package com.lia.mediaplayer.core.playback;

import com.lia.mediaplayer.api.source.ResolveOptions;
import java.time.Duration;
import java.util.List;

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
 * @param viewHeight the on-screen height, in real pixels, of the picture's view; 0 while there
 *     is none. The picture is decoded at the smallest {@linkplain #DECODE_STEPS step} that covers
 *     it, and at no more than 30 frames a second below 720 lines
 * @param video whether to decode the picture at all; off, a video plays its sound alone (a
 *     hidden window on a weak machine)
 */
public record PlayerOptions(
        ResolveOptions resolve,
        Duration startAt,
        boolean startPaused,
        int maxFps,
        Duration longPause,
        Duration videoBuffer,
        Duration audioBuffer,
        float volume,
        int viewHeight,
        boolean video) {

    /** The heights pictures are decoded at; a view between two takes the larger. */
    public static final List<Integer> DECODE_STEPS = List.of(360, 480, 720, 1080);

    /** Below a view this tall, frames are decoded at no more than {@value #SMALL_VIEW_FPS} a second. */
    public static final int SMALL_VIEW_HEIGHT = 720;

    public static final int SMALL_VIEW_FPS = 30;

    public static final PlayerOptions DEFAULT = new PlayerOptions(
            ResolveOptions.DEFAULT,
            Duration.ZERO,
            false,
            60,
            Duration.ofSeconds(10),
            Duration.ofSeconds(1),
            Duration.ofSeconds(2),
            1f,
            0,
            true);

    public PlayerOptions {
        if (startAt.isNegative() || maxFps < 1 || maxFps > 240 || longPause.isNegative()) {
            throw new IllegalArgumentException("Bad player options");
        }
        if (videoBuffer.isNegative() || videoBuffer.isZero() || audioBuffer.toMillis() < 100) {
            throw new IllegalArgumentException("Buffers too small");
        }
        volume = Math.max(0, Math.min(1, volume));
        viewHeight = Math.max(0, viewHeight);
    }

    /** These options with every field as given; the {@code with} methods below change one. */
    private PlayerOptions copy(
            ResolveOptions newResolve, Duration newStart, float newVolume, int newViewHeight, boolean newVideo) {
        return new PlayerOptions(
                newResolve,
                newStart,
                startPaused,
                maxFps,
                longPause,
                videoBuffer,
                audioBuffer,
                newVolume,
                newViewHeight,
                newVideo);
    }

    /**
     * The height pictures are decoded at for a view {@code viewPixels} tall: the smallest step
     * that covers it, never above {@code maxHeight}; {@code maxHeight} itself while the view's
     * size is unknown.
     */
    public static int decodeHeight(int viewPixels, int maxHeight) {
        if (viewPixels <= 0) {
            return maxHeight;
        }
        for (int step : DECODE_STEPS) {
            if (step >= viewPixels) {
                return Math.min(step, maxHeight);
            }
        }
        return maxHeight;
    }

    /** The frame rate cap for a picture decoded {@code decodeHeight} lines tall. */
    public int fpsFor(int decodeHeight) {
        return decodeHeight < SMALL_VIEW_HEIGHT ? Math.min(maxFps, SMALL_VIEW_FPS) : maxFps;
    }

    public PlayerOptions withStartAt(Duration newStart) {
        return copy(resolve, newStart, volume, viewHeight, video);
    }

    public PlayerOptions withResolve(ResolveOptions newResolve) {
        return copy(newResolve, startAt, volume, viewHeight, video);
    }

    public PlayerOptions withLongPause(Duration newLongPause) {
        return new PlayerOptions(
                resolve,
                startAt,
                startPaused,
                maxFps,
                newLongPause,
                videoBuffer,
                audioBuffer,
                volume,
                viewHeight,
                video);
    }

    public PlayerOptions withVolume(float newVolume) {
        return copy(resolve, startAt, newVolume, viewHeight, video);
    }

    public PlayerOptions withViewHeight(int pixels) {
        return copy(resolve, startAt, volume, pixels, video);
    }

    public PlayerOptions withVideo(boolean on) {
        return copy(resolve, startAt, volume, viewHeight, on);
    }
}
