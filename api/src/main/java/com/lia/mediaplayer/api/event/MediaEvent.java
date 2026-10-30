package com.lia.mediaplayer.api.event;

import com.lia.mediaplayer.api.diag.FailureRecord;
import com.lia.mediaplayer.api.media.MediaHandle;
import java.time.Duration;

/**
 * Something that happened to a playback. A closed set: a {@code switch} over it with record
 * patterns needs no default, and a case added in a later minor version is one an addon can
 * ignore by subscribing to the types it knows.
 *
 * <p>For one playback, events arrive in the order they happened: {@link TrackChanged}, then
 * {@link Started} once it plays, then any {@link Paused}, {@link Resumed} and {@link Seeked},
 * then {@link Ended} or {@link Failed}, and the next {@link TrackChanged}; {@link Closed} is
 * last.
 *
 * @since 4.0.0
 */
public sealed interface MediaEvent {

    MediaHandle handle();

    /** The current track is playing for the first time. */
    record Started(MediaHandle handle) implements MediaEvent {}

    record Paused(MediaHandle handle, Duration at) implements MediaEvent {}

    record Resumed(MediaHandle handle, Duration at) implements MediaEvent {}

    /** A jump from {@code from} to {@code to}; {@code from} is where the track was a frame before. */
    record Seeked(MediaHandle handle, Duration from, Duration to) implements MediaEvent {}

    /** A new track is current: the first one, the next one, or one picked in the queue. */
    record TrackChanged(MediaHandle handle, String url) implements MediaEvent {}

    /** The current track played to its end; the next one, if any, follows. */
    record Ended(MediaHandle handle) implements MediaEvent {}

    record Failed(MediaHandle handle, FailureRecord failure) implements MediaEvent {}

    /** The playback is gone; its handle is no longer {@linkplain MediaHandle#isAlive() alive}. */
    record Closed(MediaHandle handle) implements MediaEvent {}
}
