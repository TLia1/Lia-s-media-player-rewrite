package com.lia.mediaplayer.api.queue;

import com.lia.mediaplayer.api.media.RepeatMode;
import java.util.List;

/**
 * The tracks a playback will play after the current one — what its queue panel shows — and the
 * edits that panel makes. Client thread only, like the rest of a handle's controls.
 *
 * <p>Indices count waiting tracks, 0 being the next to play (unless shuffled).
 *
 * @since 4.0.0
 */
public interface QueueView {

    /** The waiting tracks, in queue order. A copy: it does not change as the queue does. */
    List<QueueEntry> entries();

    int size();

    /**
     * Appends a link, as dropping it on the window would; a playlist link is expanded into its
     * entries first. If nothing plays, it starts at once.
     *
     * @throws IllegalArgumentException if no source recognises the link, or it is a picture
     */
    void add(String url);

    /** Plays the waiting track at {@code index} now. */
    void jumpTo(int index);

    /**
     * Drops the waiting track at {@code index}.
     *
     * @throws IndexOutOfBoundsException if there is no track there
     */
    void remove(int index);

    /** Moves the track at {@code from} to {@code to}. */
    void move(int from, int to);

    /** Drops every waiting track; the current one plays on. */
    void clear();

    RepeatMode repeat();

    void setRepeat(RepeatMode mode);

    boolean shuffle();

    void setShuffle(boolean on);
}
