package com.lia.mediaplayer.core.playback;

import static com.lia.mediaplayer.core.playback.TestEngine.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.MediaMetadata;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.source.ResolveOptions;
import com.lia.mediaplayer.api.source.ResolvedMedia;
import com.lia.mediaplayer.api.source.Resolver;
import com.lia.mediaplayer.api.source.StreamUrl;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.diag.PlaybackError;
import com.lia.mediaplayer.core.playback.TestEngine.StateLog;
import com.lia.mediaplayer.core.testing.FakeAudioSink;
import com.lia.mediaplayer.core.testing.FakeYtDlp;
import com.lia.mediaplayer.core.testing.MediaServer;
import com.lia.mediaplayer.core.testing.TestMedia;
import com.lia.mediaplayer.core.video.VideoFrame;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The engine against the machine's real ffmpeg, on media generated for the test and served
 * over HTTP with byte ranges. The sound card is a fake that keeps real time, so these tests
 * take about as long as the media they play.
 */
@Tag("integration")
class PlayerIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @TempDir
    static Path mediaDir;

    static MediaServer server;
    static String av;
    static String videoOnly;
    static String audioOnly;
    static String shortClip;

    @BeforeAll
    static void generateMedia() throws IOException {
        TestEngine.requireFfmpeg();
        server = new MediaServer();
        av = server.serve("/av.mkv", TestMedia.audioVideo(mediaDir, "av.mkv", 320, 240, 4));
        videoOnly = server.serve("/video.mkv", TestMedia.videoOnly(mediaDir, "video.mkv", 320, 240, 3));
        audioOnly = server.serve("/audio.flac", TestMedia.audioOnly(mediaDir, "audio.flac", 3));
        shortClip = server.serve("/short.mkv", TestMedia.audioVideo(mediaDir, "short.mkv", 160, 120, 2));
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    private static MediaRef direct(TestEngine engine, String url) {
        return engine.sources.match(url).orElseThrow();
    }

    /** Takes due frames as a window would, until the player ends or fails. */
    private static List<VideoFrameRecord> watch(Player player, StateLog log) {
        var seen = new ArrayList<VideoFrameRecord>();
        await(TIMEOUT, "the end", () -> {
            VideoFrame frame = player.takeFrame();
            if (frame != null) {
                seen.add(new VideoFrameRecord(frame.ptsMicros(), frame.width(), frame.height()));
                player.releaseFrame(frame);
            }
            return log.last() instanceof PlayerState.Ended || log.last() instanceof PlayerState.Failed;
        });
        return seen;
    }

    private record VideoFrameRecord(long ptsMicros, int width, int height) {}

    @Test
    void playsPictureAndSoundToTheEndAtRealSpeed() {
        try (var engine = TestEngine.create()) {
            var log = new StateLog();
            Player player = engine.engine.play(direct(engine, av), PlayerOptions.DEFAULT, log);
            log.awaitLast(PlayerState.Playing.class, TIMEOUT);
            long playingSince = System.nanoTime();

            List<VideoFrameRecord> frames = watch(player, log);

            assertThat(log.last()).isInstanceOf(PlayerState.Ended.class);
            assertThat(log.kinds().subList(0, 3))
                    .containsExactly(
                            PlayerState.Resolving.class, PlayerState.Buffering.class, PlayerState.Playing.class);
            // Paced by the sound card, not by how fast ffmpeg decodes.
            assertThat(Duration.ofNanos(System.nanoTime() - playingSince)).isGreaterThan(Duration.ofMillis(3300));
            assertThat(frames).hasSizeGreaterThan(30);
            assertThat(frames).allSatisfy(f -> {
                assertThat(f.width()).isEqualTo(320);
                assertThat(f.height()).isEqualTo(240);
            });
            assertThat(frames)
                    .extracting(VideoFrameRecord::ptsMicros)
                    .isSorted()
                    .doesNotHaveDuplicates();
            PlayerSnapshot snapshot = player.snapshot();
            assertThat(snapshot.hasVideo()).isTrue();
            assertThat(snapshot.hasAudio()).isTrue();
            assertThat(snapshot.duration())
                    .hasValueSatisfying(d -> assertThat(d.toMillis()).isCloseTo(4000, within(100L)));
            assertThat(engine.sinks)
                    .singleElement()
                    .satisfies(sink -> assertThat(sink.starts()).isPositive());
        }
    }

    /**
     * v2 relaunched ffmpeg on every resume because resuming in place froze the picture. With
     * the rings, a short pause resumes in place and the picture keeps coming to the end.
     */
    @Test
    void aShortPauseResumesInPlaceAndKeepsPlaying() throws InterruptedException {
        try (var engine = TestEngine.create()) {
            var log = new StateLog();
            Player player = engine.engine.play(direct(engine, av), PlayerOptions.DEFAULT, log);
            log.awaitLast(PlayerState.Playing.class, TIMEOUT);
            Thread.sleep(800);
            player.pause();
            log.awaitLast(PlayerState.Paused.class, TIMEOUT);
            long pausedAt = player.positionMicros();
            int statesBeforeResume = log.states.size();
            Thread.sleep(700);
            assertThat(player.positionMicros()).isCloseTo(pausedAt, within(40_000L));

            player.play();
            log.awaitLast(PlayerState.Playing.class, TIMEOUT);
            List<VideoFrameRecord> frames = watch(player, log);

            assertThat(log.states.subList(statesBeforeResume, log.states.size()))
                    .extracting(Object::getClass)
                    .containsExactly(PlayerState.Playing.class, PlayerState.Ended.class);
            assertThat(frames)
                    .extracting(VideoFrameRecord::ptsMicros)
                    .anySatisfy(pts -> assertThat(pts).isGreaterThan(3_500_000L));
        }
    }

    @Test
    void aLongPauseStartsANewSessionAtThePausedPosition() throws InterruptedException {
        try (var engine = TestEngine.create()) {
            var log = new StateLog();
            var options = PlayerOptions.DEFAULT.withLongPause(Duration.ofMillis(200));
            Player player = engine.engine.play(direct(engine, av), options, log);
            log.awaitLast(PlayerState.Playing.class, TIMEOUT);
            Thread.sleep(1000);
            player.pause();
            log.awaitLast(PlayerState.Paused.class, TIMEOUT);
            long pausedAt = player.positionMicros();
            Thread.sleep(400);

            player.play();
            log.awaitLast(PlayerState.Playing.class, TIMEOUT);

            assertThat(log.count(PlayerState.Buffering.class)).isEqualTo(2);
            var resumed = (PlayerState.Playing) log.last();
            assertThat(resumed.position().toNanos() / 1000).isCloseTo(pausedAt, within(100_000L));
        }
    }

    @Test
    void seekingShowsFramesFromTheTarget() {
        try (var engine = TestEngine.create()) {
            var log = new StateLog();
            Player player = engine.engine.play(direct(engine, av), PlayerOptions.DEFAULT, log);
            log.awaitLast(PlayerState.Playing.class, TIMEOUT);

            player.seek(Duration.ofMillis(2500));
            await(TIMEOUT, "seeking", () -> log.saw(PlayerState.Seeking.class));
            log.awaitLast(PlayerState.Playing.class, TIMEOUT);

            assertThat(log.states)
                    .filteredOn(PlayerState.Seeking.class::isInstance)
                    .singleElement()
                    .isEqualTo(new PlayerState.Seeking(Duration.ofMillis(2500)));
            assertThat(player.positionMicros()).isGreaterThanOrEqualTo(2_500_000L);
            List<VideoFrameRecord> frames = watch(player, log);
            assertThat(frames)
                    .isNotEmpty()
                    .allSatisfy(f -> assertThat(f.ptsMicros()).isGreaterThanOrEqualTo(2_500_000L));
        }
    }

    @Test
    void seekingWhilePausedStaysPausedOnTheNewFrame() {
        try (var engine = TestEngine.create()) {
            var log = new StateLog();
            var options = new PlayerOptions(
                    ResolveOptions.DEFAULT,
                    Duration.ZERO,
                    true,
                    60,
                    Duration.ofSeconds(10),
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(2),
                    0.5f);
            Player player = engine.engine.play(direct(engine, av), options, log);
            log.awaitLast(PlayerState.Paused.class, TIMEOUT);

            player.seek(Duration.ofSeconds(2));
            await(
                    TIMEOUT,
                    "seek done",
                    () -> log.saw(PlayerState.Seeking.class) && log.last() instanceof PlayerState.Paused);

            assertThat(log.last()).isEqualTo(new PlayerState.Paused(Duration.ofSeconds(2)));
            var frame = new AtomicInteger();
            await(TIMEOUT, "the frame at the target", () -> {
                VideoFrame due = player.takeFrame();
                if (due != null) {
                    frame.set((int) (due.ptsMicros() / 1000));
                    player.releaseFrame(due);
                }
                return frame.get() > 0;
            });
            assertThat(frame.get()).isEqualTo(2000);
            assertThat(engine.sinks.getFirst().gain()).isEqualTo(0.5f);
            player.close();
        }
    }

    @Test
    void theGamesMasterVolumeScalesEveryPlayback() {
        try (var engine = TestEngine.create()) {
            engine.client.run(() -> engine.registry.setMasterVolume(0.5f));
            Playback playback = engine.client.call(
                    () -> engine.registry.open(direct(engine, av), PlayerOptions.DEFAULT.withVolume(0.8f)));
            await(TIMEOUT, "the sound line", () -> !engine.sinks.isEmpty());
            await(TIMEOUT, "half of 80%", () -> engine.sinks.getFirst().gain() == 0.4f);

            // The slider moves: the player follows, and its own volume is untouched.
            engine.client.run(() -> engine.registry.setMasterVolume(1));
            await(TIMEOUT, "the full 80%", () -> engine.sinks.getFirst().gain() == 0.8f);
            assertThat(engine.client.call(playback::volume)).isEqualTo(0.8f);

            engine.client.run(() -> engine.registry.setMasterVolume(0.25f));
            engine.client.run(() -> playback.setVolume(1));
            await(TIMEOUT, "a quarter", () -> engine.sinks.getFirst().gain() == 0.25f);
            engine.client.run(playback::close);
        }
    }

    @Test
    void aMissingFileFailsWithItsCause() {
        try (var engine = TestEngine.create()) {
            var log = new StateLog();
            engine.engine.play(direct(engine, server.fail("/missing.mkv", 404)), PlayerOptions.DEFAULT, log);
            log.awaitLast(PlayerState.Failed.class, TIMEOUT);

            var failed = (PlayerState.Failed) log.last();
            assertThat(failed.error().cause()).isEqualTo(PlaybackError.Cause.NOT_FOUND);
        }
    }

    @Test
    void aSilentVideoPlaysOnTheWallClock() {
        try (var engine = TestEngine.create()) {
            var log = new StateLog();
            Player player = engine.engine.play(direct(engine, videoOnly), PlayerOptions.DEFAULT, log);
            log.awaitLast(PlayerState.Playing.class, TIMEOUT);
            long since = System.nanoTime();
            List<VideoFrameRecord> frames = watch(player, log);

            assertThat(log.last()).isInstanceOf(PlayerState.Ended.class);
            assertThat(Duration.ofNanos(System.nanoTime() - since)).isGreaterThan(Duration.ofMillis(2500));
            assertThat(frames).isNotEmpty();
            assertThat(player.snapshot().hasAudio()).isFalse();
            assertThat(engine.sinks).isEmpty();
        }
    }

    @Test
    void anAudioLinkPlaysSoundOnly() {
        try (var engine = TestEngine.create()) {
            var log = new StateLog();
            MediaRef ref = direct(engine, audioOnly);
            assertThat(ref.kind()).isEqualTo(MediaKind.AUDIO);
            Player player = engine.engine.play(ref, PlayerOptions.DEFAULT, log);
            log.awaitLast(PlayerState.Ended.class, TIMEOUT);

            assertThat(player.snapshot().hasVideo()).isFalse();
            assertThat(player.snapshot().hasAudio()).isTrue();
            assertThat(player.positionMicros()).isGreaterThan(2_800_000L);
        }
    }

    @Test
    void withoutASoundDeviceAVideoPlaysSilentlyAndSoundFails() {
        try (var engine = TestEngine.create(FakeAudioSink::unavailable, List.of(), Path.of("unused"))) {
            var log = new StateLog();
            Player video = engine.engine.play(direct(engine, shortClip), PlayerOptions.DEFAULT, log);
            log.awaitLast(PlayerState.Ended.class, TIMEOUT);
            assertThat(video.snapshot().hasAudio()).isFalse();

            var soundLog = new StateLog();
            engine.engine.play(direct(engine, audioOnly), PlayerOptions.DEFAULT, soundLog);
            soundLog.awaitLast(PlayerState.Failed.class, TIMEOUT);
            assertThat(((PlayerState.Failed) soundLog.last()).error().cause())
                    .isEqualTo(PlaybackError.Cause.NO_AUDIO_DEVICE);
        }
    }

    /** YouTube serves picture and sound apart; one ffmpeg reads both. */
    @Test
    void separatePictureAndSoundFromYtDlpPlayTogether(@TempDir Path dir) throws IOException {
        assumeTrue(FakeYtDlp.isSupported(), "the fake yt-dlp is a shell script");
        String json = """
                {"title": "Two streams", "duration": 3.0, "width": 320, "height": 240, "fps": 30,
                 "requested_formats": [
                   {"url": "%s", "protocol": "https", "vcodec": "mpeg4", "acodec": "none", "width": 320, "height": 240},
                   {"url": "%s", "protocol": "https", "vcodec": "none", "acodec": "flac"}],
                 "http_headers": {"User-Agent": "test-agent", "Referer": "https://example.com/"}}
                """.formatted(videoOnly, audioOnly);
        var ytDlp = FakeYtDlp.create(dir, json, "", 0);
        try (var engine = TestEngine.create(FakeAudioSink::new, List.of(), ytDlp.path())) {
            var log = new StateLog();
            MediaRef ref = engine.sources
                    .match("https://www.youtube.com/watch?v=abcdefghijk")
                    .orElseThrow();
            Player player = engine.engine.play(ref, PlayerOptions.DEFAULT, log);
            log.awaitLast(PlayerState.Playing.class, TIMEOUT);

            PlayerSnapshot snapshot = player.snapshot();
            assertThat(snapshot.title()).contains("Two streams");
            assertThat(snapshot.hasVideo()).isTrue();
            assertThat(snapshot.hasAudio()).isTrue();
            watch(player, log);
            assertThat(log.last()).isInstanceOf(PlayerState.Ended.class);
            assertThat(ytDlp.lastArguments())
                    .contains("--ignore-config", "-J", "--")
                    .endsWith(ref.url());
        }
    }

    @Test
    void anExpiredStreamIsResolvedOnceMoreBeforeFailing() {
        var calls = new AtomicInteger();
        String expired = server.fail("/expired.mkv", 403);
        Resolver expiring = new Resolver() {
            @Override
            public boolean handles(MediaRef ref) {
                return ref.sourceId().equals("test:expiring");
            }

            @Override
            public CompletableFuture<ResolvedMedia> resolve(MediaRef ref, ResolveOptions options) {
                String url = calls.incrementAndGet() == 1 ? expired : av;
                return CompletableFuture.completedFuture(new ResolvedMedia(
                        ref,
                        Optional.of(StreamUrl.of(url)),
                        Optional.empty(),
                        Map.of(),
                        MediaMetadata.UNKNOWN.withTracks(MediaMetadata.Tracks.VIDEO_AND_AUDIO),
                        Instant.now().plusSeconds(3600)));
            }
        };
        try (var engine = TestEngine.create(FakeAudioSink::new, List.of(expiring), Path.of("unused"))) {
            var log = new StateLog();
            var ref = new MediaRef("https://example.com/watch", MediaKind.VIDEO, "test:expiring", Text.literal("x"));
            engine.engine.play(ref, PlayerOptions.DEFAULT, log);
            log.awaitLast(PlayerState.Playing.class, TIMEOUT);

            assertThat(calls.get()).isEqualTo(2);
            assertThat(log.saw(PlayerState.Failed.class)).isFalse();
        }
    }

    @Test
    void aCollectionPlaysItsEntriesOneAfterAnother(@TempDir Path dir) throws IOException {
        assumeTrue(FakeYtDlp.isSupported(), "the fake yt-dlp is a shell script");
        String json = """
                {"_type": "playlist", "title": "Two clips",
                 "entries": [{"url": "%s"}, {"url": "%s"}]}
                """.formatted(shortClip, shortClip.replace("/short.mkv", "/short.mkv?again"));
        var ytDlp = FakeYtDlp.create(dir, json, "", 0);
        try (var engine = TestEngine.create(FakeAudioSink::new, List.of(), ytDlp.path())) {
            var log = new StateLog();
            MediaRef playlist = engine.sources
                    .match("https://www.youtube.com/playlist?list=PL123")
                    .orElseThrow();
            assertThat(playlist.kind()).isEqualTo(MediaKind.COLLECTION);
            engine.client.run(
                    () -> engine.registry.open(playlist, PlayerOptions.DEFAULT).setListener(log));

            await(TIMEOUT.multipliedBy(2), "both clips to end", () -> log.count(PlayerState.Ended.class) == 2);
            assertThat(log.players.stream().map(Player::id).distinct()).hasSize(2);
            assertThat(log.players.getLast().ref().url()).endsWith("?again");
        }
    }

    @Test
    void aLinkQueuedWhilePlayingWaitsAndOneQueuedAfterTheEndStartsAtOnce() {
        try (var engine = TestEngine.create()) {
            var log = new StateLog();
            Playback playback = engine.client.call(() -> {
                Playback opened = engine.registry.open(direct(engine, shortClip), PlayerOptions.DEFAULT);
                opened.setListener(log);
                engine.registry.enqueue(opened, direct(engine, shortClip + "?second"));
                return opened;
            });
            assertThat(engine.client.call(() -> playback.queue().size())).isEqualTo(1);

            await(TIMEOUT.multipliedBy(2), "both clips to end", () -> log.count(PlayerState.Ended.class) == 2);
            assertThat(log.players.getLast().ref().url()).endsWith("?second");

            // The queue has run dry; a link queued now plays straight away.
            engine.client.run(() -> playback.enqueue(direct(engine, shortClip + "?third")));
            await(
                    TIMEOUT,
                    "the third clip",
                    () -> log.players.getLast().ref().url().endsWith("?third"));
            engine.client.run(playback::close);
            assertThat(engine.client.call(playback::isClosed)).isTrue();
            assertThat(engine.client.call(() -> engine.registry.playbacks().isEmpty()))
                    .isTrue();
        }
    }

    @Test
    void closingAPlayerKillsItsFfmpeg() throws Exception {
        try (var engine = TestEngine.create()) {
            var log = new StateLog();
            Player player = engine.engine.play(direct(engine, av), PlayerOptions.DEFAULT, log);
            log.awaitLast(PlayerState.Playing.class, TIMEOUT);
            assertThat(engine.processes.runningCount()).isEqualTo(1);

            player.close();
            player.closed().get();

            await(Duration.ofSeconds(5), "ffmpeg to exit", () -> engine.processes.runningCount() == 0);
            assertThat(engine.sinks.getFirst().isOpen()).isFalse();
        }
    }
}
