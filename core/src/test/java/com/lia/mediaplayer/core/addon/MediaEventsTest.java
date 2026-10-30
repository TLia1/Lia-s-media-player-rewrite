package com.lia.mediaplayer.core.addon;

import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.api.event.MediaEvent;
import com.lia.mediaplayer.api.media.MediaHandle;
import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.media.PlaybackSnapshot;
import com.lia.mediaplayer.api.media.PlaybackState;
import com.lia.mediaplayer.api.queue.QueueView;
import com.lia.mediaplayer.core.diag.PlaybackError;
import com.lia.mediaplayer.core.event.EventBus;
import com.lia.mediaplayer.core.playback.PlayerSnapshot;
import com.lia.mediaplayer.core.playback.PlayerState;
import com.lia.mediaplayer.core.testing.FakePlatform;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The engine's states, as the events an addon receives. */
class MediaEventsTest {

    @TempDir
    Path gameDir;

    private final List<MediaEvent> received = new ArrayList<>();

    private FakePlatform platform() {
        return new FakePlatform(gameDir);
    }

    @Test
    void statesBecomeWhatChangedBetweenThem() {
        var platform = platform();
        var bus = new EventBus(platform.clientThread());
        var events = new MediaEvents(bus);
        var unused = events.subscriptions(new AddonGuard(), "myaddon:events").onAny(received::add);
        var handle = new StillHandle(Duration.ofSeconds(7));

        events.trackStarted(handle, "https://example.com/a");
        events.stateChanged(handle, new PlayerState.Resolving());
        events.stateChanged(handle, new PlayerState.Buffering(Duration.ZERO));
        events.stateChanged(handle, new PlayerState.Playing(Duration.ZERO));
        events.stateChanged(handle, new PlayerState.Paused(Duration.ofSeconds(3)));
        events.stateChanged(handle, new PlayerState.Paused(Duration.ofSeconds(3)));
        events.stateChanged(handle, new PlayerState.Playing(Duration.ofSeconds(3)));
        events.tick();
        events.stateChanged(handle, new PlayerState.Seeking(Duration.ofSeconds(60)));
        events.tick();
        events.stateChanged(handle, new PlayerState.Playing(Duration.ofSeconds(60)));
        events.stateChanged(handle, new PlayerState.Ended());
        events.trackStarted(handle, "https://example.com/b");
        events.stateChanged(handle, new PlayerState.Failed(PlaybackError.classify("ERROR: Private video")));
        events.closed(handle);
        platform.runClientTasks();

        assertThat(received)
                .containsExactly(
                        new MediaEvent.TrackChanged(handle, "https://example.com/a"),
                        new MediaEvent.Started(handle),
                        new MediaEvent.Paused(handle, Duration.ofSeconds(3)),
                        new MediaEvent.Resumed(handle, Duration.ofSeconds(3)),
                        new MediaEvent.Seeked(handle, Duration.ofSeconds(7), Duration.ofSeconds(60)),
                        new MediaEvent.Ended(handle),
                        new MediaEvent.TrackChanged(handle, "https://example.com/b"),
                        received.get(7),
                        new MediaEvent.Closed(handle));
        var failed = (MediaEvent.Failed) received.get(7);
        assertThat(failed.failure().causeId()).isEqualTo("private");
        assertThat(failed.failure().url()).isEqualTo(handle.url().orElseThrow());
    }

    @Test
    void anAddonListenerThatThrowsKeepsItsSubscriptionUntilTheGuardGivesUp() {
        var platform = platform();
        var bus = new EventBus(platform.clientThread());
        var events = new MediaEvents(bus);
        var guard = new AddonGuard(2);
        var calls = new ArrayList<MediaEvent>();
        var subscription = events.subscriptions(guard, "myaddon:events").on(MediaEvent.Closed.class, event -> {
            calls.add(event);
            throw new IllegalStateException("bug");
        });
        var handle = new StillHandle(Duration.ZERO);
        for (int i = 0; i < 3; i++) {
            events.closed(handle);
        }
        events.trackStarted(handle, "https://example.com/a");
        platform.runClientTasks();
        assertThat(calls).hasSize(2).allMatch(MediaEvent.Closed.class::isInstance);

        subscription.close();
        assertThat(guard.isDisabled("myaddon:events")).isTrue();
    }

    @Test
    void engineStatesMapOneToOne() {
        assertThat(PlaybackHandle.stateOf(new PlayerState.Idle())).isEqualTo(PlaybackState.IDLE);
        assertThat(PlaybackHandle.stateOf(new PlayerState.Seeking(Duration.ZERO)))
                .isEqualTo(PlaybackState.SEEKING);
        PlaybackSnapshot snapshot = PlaybackHandle.snapshotOf(new PlayerSnapshot(
                new PlayerState.Playing(Duration.ofSeconds(2)),
                Duration.ofSeconds(3),
                Optional.of(Duration.ofSeconds(10)),
                Optional.of("Title"),
                true,
                false,
                320,
                240));
        assertThat(snapshot)
                .isEqualTo(new PlaybackSnapshot(
                        PlaybackState.PLAYING,
                        Duration.ofSeconds(3),
                        Optional.of(Duration.ofSeconds(10)),
                        Optional.of("Title"),
                        true,
                        false));
    }

    /** A handle whose position never moves. */
    private record StillHandle(Duration position) implements MediaHandle {
        @Override
        public long id() {
            return 1;
        }

        @Override
        public MediaKind kind() {
            return MediaKind.VIDEO;
        }

        @Override
        public Optional<String> url() {
            return Optional.of("https://example.com/a");
        }

        @Override
        public PlaybackSnapshot snapshot() {
            return new PlaybackSnapshot(
                    PlaybackState.PLAYING, position, Optional.empty(), Optional.empty(), true, true);
        }

        @Override
        public boolean isAlive() {
            return true;
        }

        @Override
        public void play() {}

        @Override
        public void pause() {}

        @Override
        public void togglePause() {}

        @Override
        public void seek(Duration target) {}

        @Override
        public boolean next() {
            return false;
        }

        @Override
        public boolean previous() {
            return false;
        }

        @Override
        public float volume() {
            return 1;
        }

        @Override
        public void setVolume(float volume) {}

        @Override
        public QueueView queue() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {}
    }
}
