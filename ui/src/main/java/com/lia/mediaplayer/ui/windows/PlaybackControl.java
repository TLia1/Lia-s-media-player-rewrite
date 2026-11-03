package com.lia.mediaplayer.ui.windows;

import com.lia.mediaplayer.api.media.MediaHandle;
import com.lia.mediaplayer.api.media.RepeatMode;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.core.playback.PlayerSnapshot;
import com.lia.mediaplayer.core.playback.PlayerStats;
import com.lia.mediaplayer.core.video.VideoFrame;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * What a window needs from the playback it shows: its state, its frames and its controls.
 * {@link CorePlaybackControl} implements it over the engine; tests implement it by hand.
 * Client thread only.
 */
public interface PlaybackControl {

    /** The current track's state, or {@link PlayerSnapshot#initial()} before the first. */
    PlayerSnapshot snapshot();

    /** The current track. */
    Optional<MediaRef> currentRef();

    /** The link of the current track. */
    default Optional<String> currentUrl() {
        return currentRef().map(MediaRef::url);
    }

    /** The newest frame due now, or {@code null}; give it back with {@link #releaseFrame}. */
    @Nullable
    VideoFrame takeFrame();

    void releaseFrame(VideoFrame frame);

    /**
     * The height, in real pixels, at which the view shows the picture: frames are decoded no
     * larger. Cheap to call every frame.
     */
    void setViewHeight(int pixels);

    /** Whether to decode the picture; off, the sound plays alone. */
    void setVideoEnabled(boolean on);

    /** The current track's counters, for the debug overlay. */
    PlayerStats stats();

    void togglePause();

    void seek(Duration target);

    void seekBy(Duration delta);

    /** Linear, 0 to 1; kept for the tracks that follow. */
    float volume();

    void setVolume(float volume);

    boolean hasNext();

    void next();

    boolean hasPrevious();

    void previous();

    /** How many tracks wait after the current one. */
    int queued();

    /** The tracks waiting, in play order. */
    List<MediaRef> upcoming();

    /** Moves with every change to the waiting tracks, so a list can tell when to read them again. */
    int queueVersion();

    /** Plays the waiting track at {@code index} now. */
    void jumpTo(int index);

    /** Drops the waiting track at {@code index}. */
    void removeQueued(int index);

    /** Moves a waiting track into the gap before what is now at {@code insertBefore}. */
    void moveQueued(int from, int insertBefore);

    void setRepeat(RepeatMode mode);

    void setShuffle(boolean on);

    RepeatMode repeat();

    void cycleRepeat();

    boolean shuffle();

    void toggleShuffle();

    /** After a failure: resolves the link again and plays from where it failed. */
    void retry();

    /** Appends a link; a collection is expanded first. Starts at once if nothing plays. */
    void enqueue(MediaRef ref);

    boolean isClosed();

    /** The API's handle on this playback, for the addons' buttons; empty where there is none. */
    default Optional<MediaHandle> handle() {
        return Optional.empty();
    }

    /** Stops playback for good and releases the process, the sound line and the frames. */
    void close();
}
