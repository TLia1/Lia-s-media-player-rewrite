package com.lia.mediaplayer.core.playback;

import static com.lia.mediaplayer.core.playback.TestEngine.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lia.mediaplayer.api.event.MediaEvent;
import com.lia.mediaplayer.api.media.MediaHandle;
import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.media.PlaybackState;
import com.lia.mediaplayer.api.media.RepeatMode;
import com.lia.mediaplayer.api.queue.QueueEntry;
import com.lia.mediaplayer.core.addon.AddonGuard;
import com.lia.mediaplayer.core.addon.MediaEvents;
import com.lia.mediaplayer.core.addon.PlaybackHandles;
import com.lia.mediaplayer.core.event.EventBus;
import com.lia.mediaplayer.core.testing.MediaServer;
import com.lia.mediaplayer.core.testing.TestMedia;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The API's handles and events over the real engine, on generated media. */
@Tag("integration")
class HandleIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @TempDir
    static Path mediaDir;

    static MediaServer server;
    static String clip;
    static String other;

    @BeforeAll
    static void generateMedia() throws IOException {
        TestEngine.requireFfmpeg();
        server = new MediaServer();
        clip = server.serve("/clip.mkv", TestMedia.audioVideo(mediaDir, "clip.mkv", 160, 120, 4));
        other = server.serve("/other.mkv", TestMedia.audioVideo(mediaDir, "other.mkv", 160, 120, 2));
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void aHandleReadsFromAnyThreadControlsFromTheClientThreadAndReportsWhatHappened() {
        try (var engine = TestEngine.create()) {
            var handles = new PlaybackHandles(engine.registry, engine.sources, engine.client);
            var bus = new EventBus(engine.client);
            var events = MediaEvents.following(engine.registry, handles, bus);
            List<MediaEvent> received = new CopyOnWriteArrayList<>();
            var unused = events.subscriptions(new AddonGuard(), "test:events").onAny(received::add);

            MediaHandle handle = engine.client.call(() -> {
                Playback playback =
                        engine.registry.open(engine.sources.match(clip).orElseThrow(), PlayerOptions.DEFAULT);
                return handles.of(playback);
            });
            assertThat(engine.client.call(() -> handles.all())).containsExactly(handle);
            await(TIMEOUT, "the start", () -> received.contains(new MediaEvent.Started(handle)));
            assertThat(received.getFirst()).isEqualTo(new MediaEvent.TrackChanged(handle, clip));

            // The read side is safe here, off the client thread; the controls are not.
            assertThat(handle.kind()).isEqualTo(MediaKind.VIDEO);
            assertThat(handle.url()).contains(clip);
            assertThat(handle.isAlive()).isTrue();
            assertThat(handle.snapshot().state()).isEqualTo(PlaybackState.PLAYING);
            assertThatThrownBy(handle::pause).isInstanceOf(WrongThreadException.class);
            assertThatThrownBy(() -> handle.queue().size()).isInstanceOf(WrongThreadException.class);

            engine.client.run(handle::pause);
            await(TIMEOUT, "the pause", () -> received.stream().anyMatch(MediaEvent.Paused.class::isInstance));
            engine.client.run(handle::play);
            await(TIMEOUT, "the resume", () -> received.stream().anyMatch(MediaEvent.Resumed.class::isInstance));
            engine.client.run(() -> handle.seek(Duration.ofSeconds(2)));
            await(TIMEOUT, "the seek", () -> received.stream().anyMatch(MediaEvent.Seeked.class::isInstance));

            engine.client.run(() -> {
                handle.setVolume(0.5f);
                handle.queue().add(other);
                handle.queue().setRepeat(RepeatMode.ALL);
            });
            assertThat(engine.client.call(() -> handle.queue().entries()))
                    .containsExactly(new QueueEntry(other, MediaKind.VIDEO, "liasmediaplayer:video_file"));
            assertThat(engine.client.call(handle::volume)).isEqualTo(0.5f);
            assertThat(engine.client.call(() -> handle.queue().repeat())).isEqualTo(RepeatMode.ALL);
            assertThatThrownBy(() -> engine.client.run(() -> handle.queue().add("https://example.com/page")))
                    .hasRootCauseInstanceOf(IllegalArgumentException.class);

            assertThat(engine.client.call(handle::next)).isTrue();
            await(TIMEOUT, "the next track", () -> received.contains(new MediaEvent.TrackChanged(handle, other)));

            engine.client.run(handle::close);
            await(TIMEOUT, "the close", () -> received.contains(new MediaEvent.Closed(handle)));
            assertThat(handle.isAlive()).isFalse();
            assertThat(handle.url()).isEmpty();
            // Closed: the controls do nothing, and the same playback keeps the same handle.
            engine.client.run(() -> {
                handle.play();
                assertThat(handle.next()).isFalse();
            });
            assertThat(engine.client.call(() -> handles.all())).isEmpty();
        }
    }
}
