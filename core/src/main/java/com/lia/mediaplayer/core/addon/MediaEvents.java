package com.lia.mediaplayer.core.addon;

import com.lia.mediaplayer.api.diag.FailureRecord;
import com.lia.mediaplayer.api.event.EventSubscriptions;
import com.lia.mediaplayer.api.event.MediaEvent;
import com.lia.mediaplayer.api.event.Subscription;
import com.lia.mediaplayer.api.media.MediaHandle;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.diag.PlaybackError;
import com.lia.mediaplayer.core.event.EventBus;
import com.lia.mediaplayer.core.playback.Playback;
import com.lia.mediaplayer.core.playback.PlaybackRegistry;
import com.lia.mediaplayer.core.playback.PlayerState;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Turns what the engine reports — a track starting, a player changing state, a playback
 * closing — into the API's {@link MediaEvent}s, and posts them on the {@link EventBus}. Client
 * thread only.
 *
 * <p>The engine reports states, the API reports what changed between them: {@code Playing}
 * after {@code Paused} is a resume, the first {@code Playing} of a track is its start. A seek
 * goes from where the track was at the last {@link #tick()}, a client tick at most before.
 */
public final class MediaEvents {

    private final EventBus bus;
    private final Map<MediaHandle, Track> tracks = new HashMap<>();

    public MediaEvents(EventBus bus) {
        this.bus = bus;
    }

    /** Follows every playback of {@code registry}. */
    public static MediaEvents following(PlaybackRegistry registry, PlaybackHandles handles, EventBus bus) {
        var events = new MediaEvents(bus);
        Function<Playback, MediaHandle> handle = handles::of;
        registry.addListener(new PlaybackRegistry.Listener() {
            @Override
            public void trackStarted(Playback playback, MediaRef ref) {
                events.trackStarted(handle.apply(playback), ref.url());
            }

            @Override
            public void stateChanged(Playback playback, PlayerState state) {
                events.stateChanged(handle.apply(playback), state);
            }

            @Override
            public void closed(Playback playback) {
                events.closed(handle.apply(playback));
            }
        });
        return events;
    }

    /** Subscriptions for an addon, its listeners called through {@code guard} as {@code owner}. */
    public EventSubscriptions subscriptions(AddonGuard guard, String owner) {
        return new EventSubscriptions() {
            @Override
            public <E extends MediaEvent> Subscription on(Class<E> type, Consumer<? super E> listener) {
                EventBus.Subscription subscription =
                        bus.subscribe(type, event -> guard.run(owner, () -> listener.accept(event)));
                return subscription::close;
            }
        };
    }

    /** Notes where every playing track is, for the {@code from} of the next seek. */
    public void tick() {
        for (var entry : tracks.entrySet()) {
            Track track = entry.getValue();
            if (!(track.last instanceof PlayerState.Seeking)) {
                track.position = entry.getKey().snapshot().position();
            }
        }
    }

    void trackStarted(MediaHandle handle, String url) {
        tracks.put(handle, new Track());
        bus.post(new MediaEvent.TrackChanged(handle, url));
    }

    void stateChanged(MediaHandle handle, PlayerState state) {
        Track track = tracks.computeIfAbsent(handle, unused -> new Track());
        PlayerState before = track.last;
        track.last = state;
        switch (state) {
            case PlayerState.Playing(var position) -> {
                track.position = position;
                if (!track.started) {
                    track.started = true;
                    bus.post(new MediaEvent.Started(handle));
                } else if (before instanceof PlayerState.Paused) {
                    bus.post(new MediaEvent.Resumed(handle, position));
                }
            }
            case PlayerState.Paused(var position) -> {
                track.position = position;
                if (!(before instanceof PlayerState.Paused)) {
                    bus.post(new MediaEvent.Paused(handle, position));
                }
            }
            case PlayerState.Seeking(var target) -> bus.post(new MediaEvent.Seeked(handle, track.position, target));
            case PlayerState.Ended ended -> bus.post(new MediaEvent.Ended(handle));
            case PlayerState.Failed(var error) -> bus.post(new MediaEvent.Failed(handle, failure(handle, error)));
            case PlayerState.Idle idle -> {}
            case PlayerState.Resolving resolving -> {}
            case PlayerState.Buffering buffering -> {}
        }
    }

    void closed(MediaHandle handle) {
        tracks.remove(handle);
        bus.post(new MediaEvent.Closed(handle));
    }

    static FailureRecord failure(MediaHandle handle, PlaybackError error) {
        return new FailureRecord(
                handle.url().orElse(""),
                error.cause().id(),
                Text.translatable(error.cause().messageKey()),
                error.detail());
    }

    /** What is known of a playback's current track. */
    private static final class Track {
        PlayerState last = new PlayerState.Idle();
        Duration position = Duration.ZERO;
        boolean started;
    }
}
