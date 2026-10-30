package com.lia.mediaplayer.api.media;

/**
 * Where the current track of a playback is.
 *
 * @since 4.0.0
 */
public enum PlaybackState {
    /** Nothing started yet: an empty queue, or a collection still being read. */
    IDLE,
    /** Asking the resolver what to open. */
    RESOLVING,
    /** Starting, or catching up; nothing to show or hear yet. */
    BUFFERING,
    PLAYING,
    PAUSED,
    /** Jumping to another position; it plays again once the new position shows. */
    SEEKING,
    /** The last track played to its end. */
    ENDED,
    /** The track could not be played; {@link com.lia.mediaplayer.api.event.MediaEvent.Failed} says why. */
    FAILED;

    /** Whether the track is on its way to playing or playing: neither ended, failed nor idle. */
    public boolean isActive() {
        return switch (this) {
            case RESOLVING, BUFFERING, PLAYING, PAUSED, SEEKING -> true;
            case IDLE, ENDED, FAILED -> false;
        };
    }
}
