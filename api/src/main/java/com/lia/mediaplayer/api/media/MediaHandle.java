package com.lia.mediaplayer.api.media;

import com.lia.mediaplayer.api.queue.QueueView;
import java.time.Duration;
import java.util.Optional;

/**
 * One playback: a queue of tracks, the one playing now, and its volume — whatever shows it (a
 * video window, the audio bar). The same playback always has the same handle, so handles can
 * be compared and kept as map keys.
 *
 * <p><b>Threads.</b> The read-only views — {@link #id()}, {@link #kind()}, {@link #url()},
 * {@link #snapshot()} and {@link #isAlive()} — are safe from any thread. Everything else runs
 * on the client thread and throws {@link WrongThreadException} anywhere else. On a closed
 * handle the controls do nothing.
 *
 * @since 4.0.0
 */
public interface MediaHandle {

    /** Unique for the game session. */
    long id();

    /** {@link MediaKind#VIDEO} for a playback with a picture, {@link MediaKind#AUDIO} for sound only. */
    MediaKind kind();

    /** The link of the current track; empty before the first and once closed. */
    Optional<String> url();

    PlaybackSnapshot snapshot();

    /** Until the playback is closed — by the player, by {@link #close()}, or by leaving the world. */
    boolean isAlive();

    /** Plays, or resumes a pause; after a failure, tries the link again. */
    void play();

    void pause();

    void togglePause();

    /** Jumps to {@code position}, clamped to the track. */
    void seek(Duration position);

    /** Skips to the next track of the queue. {@code false} when there is none. */
    boolean next();

    /** Goes back to the track before. {@code false} when there is none. */
    boolean previous();

    /** Linear, 0 to 1, before the game's master volume. */
    float volume();

    /** Sets the volume, clamped to 0..1; it carries over to the tracks that follow. */
    void setVolume(float volume);

    /** The tracks waiting after the current one. */
    QueueView queue();

    /** Stops for good; the window that showed it closes. */
    void close();
}
