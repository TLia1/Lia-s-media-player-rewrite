package com.lia.mediaplayer.ui.windows;

import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.media.RepeatMode;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.diag.PlaybackError;
import com.lia.mediaplayer.core.platform.TextureRef;
import com.lia.mediaplayer.core.playback.PlayerSnapshot;
import com.lia.mediaplayer.core.playback.PlayerState;
import com.lia.mediaplayer.core.testing.FakePlatform;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.input.Mods;
import com.lia.mediaplayer.ui.testing.FakePlayback;
import com.lia.mediaplayer.ui.testing.RecordingCanvas;
import com.lia.mediaplayer.ui.theme.Themes;
import com.lia.mediaplayer.ui.widget.RenderContext;
import com.lia.mediaplayer.ui.widget.Widget;
import com.lia.mediaplayer.ui.window.WindowManager;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VideoWindowTest {

    @TempDir
    Path gameDir;

    private FakePlatform platform;
    private final FakePlayback playback = new FakePlayback();
    private final WindowManager windows = new WindowManager();
    private final RecordingCanvas canvas = new RecordingCanvas(640, 360);
    private VideoWindow window;

    @BeforeEach
    void open() {
        platform = new FakePlatform(gameDir);
        window = windows.open(new VideoWindow(playback, WindowServices.of(platform)));
        render(-1, -1, 0);
    }

    private void render(double x, double y, long nanos) {
        windows.render(canvas.clear(), new RenderContext(Themes.DARK, nanos, x, y, true));
    }

    private void click(Widget target) {
        Rect b = target.bounds();
        windows.input(new InputEvent.PointerDown(b.centerX(), b.centerY(), 0, Mods.NONE, 1));
        windows.input(new InputEvent.PointerUp(b.centerX(), b.centerY(), 0, Mods.NONE));
    }

    /** The transport's controls in M2's order: play, next, time, seek, mute, volume, loop, shuffle. */
    private Widget transportChild(int index) {
        // Previous sits right after play since M4, and the queue switch comes last.
        return transport().children().get(index == 0 ? 0 : index + 1);
    }

    private TransportBar transport() {
        return window.transport();
    }

    private VideoView view() {
        return window.view();
    }

    @Test
    void opensAtSixteenByNineInTheTopRightCorner() {
        assertThat(window.frame().width()).isEqualTo(VideoWindow.DEFAULT_PICTURE_WIDTH + 2);
        assertThat(window.frame().right()).isEqualTo(640 - 6);
        assertThat(window.kind()).isEqualTo(VideoWindow.KIND);
        assertThat(window.control()).isSameAs(playback);
        assertThat(window.minimumSize().width())
                .isLessThan(window.preferredSize().width());
    }

    @Test
    void uploadsTheNewestFrameAndDrawsItLetterboxed() {
        playback.snapshot =
                FakePlayback.at(new PlayerState.Playing(Duration.ZERO), Duration.ZERO, Duration.ofMinutes(1));
        playback.pushFrame(320, 180, (byte) 200);
        render(-1, -1, 0);

        var textures = platform.textures();
        assertThat(textures.created())
                .singleElement()
                .satisfies(t -> assertThat(t.hasSize(320, 180)).isTrue());
        assertThat(textures.uploads()).isEqualTo(1);
        assertThat(textures.pixels(textures.created().getFirst())[0]).isEqualTo((byte) 200);
        assertThat(playback.released).isEqualTo(1);
        assertThat(window.uploads()).isEqualTo(1);
        assertThat(canvas.textures()).singleElement().satisfies(call -> {
            Rect picture = call.rect();
            Rect area = view().bounds();
            assertThat(picture.width()).isEqualTo(area.width());
            assertThat(area.contains(picture.x(), picture.y())).isTrue();
        });
        assertThat(canvas.drewText("Clip")).isTrue();
        assertThat(canvas.drewText("0:00 / 1:00")).isTrue();

        // No new frame: the same texture is drawn again, nothing is uploaded.
        render(-1, -1, 0);
        assertThat(textures.uploads()).isEqualTo(1);
        assertThat(canvas.textures()).hasSize(1);
    }

    @Test
    void takesThePicturesShapeOnceThenLetterboxes() {
        playback.pushFrame(200, 200, (byte) 1);
        render(-1, -1, 0);
        render(-1, -1, 0);
        Rect picture = view().bounds();
        assertThat(picture.width()).isEqualTo(picture.height());

        playback.pushFrame(400, 100, (byte) 2);
        render(-1, -1, 0);
        assertThat(view().bounds()).isEqualTo(picture);
    }

    @Test
    void aNewFrameSizeReplacesTheTextureAndReleasesTheOld() {
        playback.pushFrame(160, 90, (byte) 1);
        render(-1, -1, 0);
        TextureRef first = platform.textures().created().getFirst();
        playback.pushFrame(320, 180, (byte) 2);
        render(-1, -1, 0);
        assertThat(platform.textures().created()).hasSize(2);
        // Released, but freed only between frames.
        assertThat(platform.textures().isLive(first)).isTrue();
        platform.textures().flushReleases();
        assertThat(platform.textures().isLive(first)).isFalse();
        assertThat(platform.textures().liveCount()).isEqualTo(1);
    }

    @Test
    void theHudShowsThePictureOnly() {
        playback.pushFrame(160, 90, (byte) 1);
        windows.renderHud(canvas.clear(), new RenderContext(Themes.DARK, 0, Double.NaN, Double.NaN, false));
        assertThat(canvas.textures()).hasSize(1);
        assertThat(canvas.texts()).isEmpty();
    }

    @Test
    void showsASpinnerOnlyAfterALoadHasTakenAWhile() {
        playback.snapshot = FakePlayback.at(new PlayerState.Playing(Duration.ZERO), Duration.ZERO, null);
        render(-1, -1, 500_000_000L);
        playback.snapshot = FakePlayback.at(new PlayerState.Buffering(Duration.ZERO), Duration.ZERO, null);
        render(-1, -1, 1_000_000_000L);
        int quick = canvas.fills().size();
        render(-1, -1, 1_500_000_000L);
        assertThat(canvas.fills().size()).isEqualTo(quick + 8);
        assertThat(canvas.drewText("LIVE")).isFalse();
        assertThat(canvas.drewText("time.live")).isTrue();
    }

    @Test
    void aFailureShowsTheCauseItsAdviceAndRetry() {
        var error = new PlaybackError(PlaybackError.Cause.GEO_BLOCKED, "ERROR: not available in your country");
        playback.snapshot = FakePlayback.at(new PlayerState.Failed(error), Duration.ZERO, null);
        render(-1, -1, 0);
        render(-1, -1, 0);
        assertThat(canvas.drewText("error.liasmediaplayer.cause.geo_blocked")).isTrue();
        assertThat(canvas.drewText("gui.liasmediaplayer.error.retry")).isTrue();

        ErrorPanel panel = (ErrorPanel) view().children().getFirst();
        assertThat(panel.tooltip(0, 0))
                .hasValueSatisfying(lines -> assertThat(lines).hasSize(1));
        click(panel.children().getFirst());
        assertThat(playback.calls).containsExactly("retry");

        // The play button turns into Retry as well.
        click(transportChild(0));
        assertThat(playback.calls).containsExactly("retry", "retry");
    }

    @Test
    void theTransportBarDrivesThePlayback() {
        playback.snapshot = FakePlayback.at(
                new PlayerState.Playing(Duration.ofSeconds(30)), Duration.ofSeconds(30), Duration.ofMinutes(2));
        render(-1, -1, 0);

        click(transportChild(0));
        assertThat(playback.calls).containsExactly("togglePause");

        click(transportChild(1));
        assertThat(playback.calls).containsExactly("togglePause");
        playback.hasNext = true;
        click(transportChild(1));
        assertThat(playback.calls).containsExactly("togglePause", "next");

        Rect seek = transportChild(3).bounds();
        windows.input(new InputEvent.PointerDown(seek.x() + seek.width() / 2.0, seek.centerY(), 0, Mods.NONE, 1));
        windows.input(new InputEvent.PointerUp(seek.x() + seek.width() / 2.0, seek.centerY(), 0, Mods.NONE));
        assertThat(playback.calls.getLast()).isEqualTo("seek 60000");

        click(transportChild(4));
        assertThat(playback.volume).isZero();
        click(transportChild(4));
        assertThat(playback.volume).isEqualTo(1f);

        Rect volume = transportChild(5).bounds();
        windows.input(new InputEvent.PointerDown(volume.x(), volume.centerY(), 0, Mods.NONE, 1));
        windows.input(new InputEvent.PointerUp(volume.x(), volume.centerY(), 0, Mods.NONE));
        assertThat(playback.volume).isZero();
        click(transportChild(4));
        assertThat(playback.volume).isEqualTo(1f);

        click(transportChild(6));
        assertThat(playback.repeat).isEqualTo(RepeatMode.ALL);
        click(transportChild(7));
        assertThat(playback.shuffle).isTrue();

        playback.queued = 3;
        render(-1, -1, 0);
        assertThat(canvas.drewText("0:30 / 2:00  +3")).isTrue();
    }

    @Test
    void controlsExplainThemselves() {
        playback.snapshot = FakePlayback.at(new PlayerState.Paused(Duration.ZERO), Duration.ZERO, Duration.ofHours(2));
        render(-1, -1, 0);
        assertThat(tooltipOver(transportChild(0))).contains("gui.liasmediaplayer.tooltip.play");
        assertThat(tooltipOver(transportChild(1))).contains("gui.liasmediaplayer.tooltip.next");
        assertThat(tooltipOver(transportChild(4))).contains("gui.liasmediaplayer.tooltip.mute");
        assertThat(tooltipOver(transportChild(5))).contains("gui.liasmediaplayer.tooltip.volume[100]");
        assertThat(tooltipOver(transportChild(6))).contains("gui.liasmediaplayer.tooltip.loop.off");
        assertThat(tooltipOver(transportChild(7))).contains("gui.liasmediaplayer.tooltip.shuffle.off");
        Rect seek = transportChild(3).bounds();
        render(seek.x(), seek.centerY(), 0);
        assertThat(canvas.tooltip())
                .hasValueSatisfying(t -> assertThat(t.lines()).containsExactly("0:00"));
        assertThat(canvas.drewText("0:00 / 2:00:00")).isTrue();
    }

    private Optional<String> tooltipOver(Widget widget) {
        Rect b = widget.bounds();
        render(b.centerX(), b.centerY(), 0);
        return canvas.tooltip().map(t -> t.lines().getFirst());
    }

    @Test
    void theTitleButtonsCopyOpenAndClose() {
        // The heart, copy, browser, theatre, hide, close.
        var buttons = window.titleButtons();
        click(buttons.get(1));
        assertThat(platform.clipboard().get()).isEqualTo(playback.url);
        click(buttons.get(2));
        assertThat(platform.openedInBrowser()).containsExactly(playback.url);
        click(buttons.get(4));
        assertThat(window.isHidden()).isTrue();
        window.setHidden(false);

        playback.pushFrame(160, 90, (byte) 1);
        render(-1, -1, 0);
        click(buttons.get(5));
        assertThat(window.isClosed()).isTrue();
        assertThat(playback.closed).isTrue();
        platform.textures().flushReleases();
        assertThat(platform.textures().liveCount()).isZero();
    }

    @Test
    void closesWhenThePlaybackIsClosedUnderIt() {
        window.tick();
        assertThat(window.isClosed()).isFalse();
        playback.closed = true;
        window.tick();
        assertThat(window.isClosed()).isTrue();
    }

    @Test
    void timesAndStatesReadAsThePlayerShowsThem() {
        assertThat(MediaTime.format(Duration.ofSeconds(65))).isEqualTo("1:05");
        assertThat(MediaTime.format(Duration.ofSeconds(3723))).isEqualTo("1:02:03");
        assertThat(MediaTime.format(Duration.ofSeconds(-4))).isEqualTo("0:00");
        PlayerSnapshot seeking = FakePlayback.at(
                new PlayerState.Seeking(Duration.ofSeconds(30)), Duration.ofSeconds(2), Duration.ofSeconds(60));
        assertThat(MediaTime.fraction(seeking)).isEqualTo(0.5);
        assertThat(MediaTime.seekable(seeking)).isTrue();
        assertThat(MediaTime.running(seeking)).isTrue();
        assertThat(MediaTime.busy(seeking)).isTrue();
        PlayerSnapshot ended = FakePlayback.at(new PlayerState.Ended(), Duration.ofSeconds(59), Duration.ofSeconds(60));
        assertThat(MediaTime.fraction(ended)).isEqualTo(1.0);
        assertThat(MediaTime.running(ended)).isFalse();
        assertThat(MediaTime.busy(ended)).isFalse();
        PlayerSnapshot idle = PlayerSnapshot.initial();
        assertThat(MediaTime.fraction(idle)).isZero();
        assertThat(MediaTime.seekable(idle)).isFalse();
        assertThat(MediaTime.busy(idle)).isTrue();
        assertThat(MediaTime.seekable(
                        FakePlayback.at(new PlayerState.Resolving(), Duration.ZERO, Duration.ofSeconds(9))))
                .isFalse();
    }

    @Test
    void theQueueOpensUnderTheControlsAndEditsTheQueue() {
        playback.upcoming.add(
                new MediaRef("https://example.com/a.mp4", MediaKind.VIDEO, "test:video", Text.literal("v")));
        playback.upcoming.add(
                new MediaRef("https://example.com/b.mp4", MediaKind.VIDEO, "test:video", Text.literal("v")));
        playback.queueVersion++;
        int height = window.frame().height();
        click(transport().children().getLast());
        assertThat(window.isQueueOpen()).isTrue();
        assertThat(window.frame().height()).isEqualTo(height + QueuePanel.HEIGHT);
        render(-1, -1, 0);
        assertThat(canvas.drewText("example.com/a.mp4")).isTrue();
        assertThat(canvas.drewText("gui.liasmediaplayer.queue.header[2]")).isTrue();

        var list = window.queue().list();
        Rect first =
                new Rect(list.bounds().x(), list.bounds().y(), list.bounds().width() - 20, QueuePanel.ROW_HEIGHT);
        windows.input(new InputEvent.PointerDown(first.x() + 5, first.y() + 3, 0, Mods.NONE, 2));
        windows.input(new InputEvent.PointerUp(first.x() + 5, first.y() + 3, 0, Mods.NONE));
        assertThat(playback.calls).contains("jumpTo 0");

        window.setQueueOpen(false);
        assertThat(window.frame().height()).isEqualTo(height);
    }

    @Test
    void theatreFillsTheScreenAndGivesTheFrameBack() {
        playback.pushFrame(160, 90, (byte) 1);
        render(-1, -1, 0);
        Rect before = window.frame();
        assertThat(window.toggleTheater()).isTrue();
        assertThat(window.inTheater()).isTrue();
        assertThat(window.frame().width()).isGreaterThan(before.width());
        assertThat(window.frame().height()).isLessThanOrEqualTo(360);
        assertThat(window.toggleTheater()).isTrue();
        assertThat(window.frame()).isEqualTo(before);
    }

    @Test
    void shiftCopiesTheLinkAtTheCurrentTime() {
        playback.url = "https://www.youtube.com/watch?v=abc";
        playback.sourceId = "liasmediaplayer:youtube";
        playback.snapshot = FakePlayback.at(
                new PlayerState.Playing(Duration.ofSeconds(75)), Duration.ofSeconds(75), Duration.ofMinutes(5));
        render(-1, -1, 0);
        Rect copy = window.titleButtons().get(1).bounds();
        windows.input(new InputEvent.PointerDown(copy.centerX(), copy.centerY(), 0, Mods.shiftOnly(), 1));
        windows.input(new InputEvent.PointerUp(copy.centerX(), copy.centerY(), 0, Mods.shiftOnly()));
        assertThat(platform.clipboard().get()).isEqualTo("https://www.youtube.com/watch?v=abc&t=75s");
    }

    @Test
    void theHeartKeepsTheLinkInTheFavourites() {
        var hearts = new HashSet<String>();
        var favorites = new WindowServices.Favorites() {
            @Override
            public boolean isFavorite(String url) {
                return hearts.contains(url);
            }

            @Override
            public void toggle(String url, MediaKind kind) {
                if (!hearts.remove(url)) {
                    hearts.add(url);
                }
            }
        };
        var services = new WindowServices(platform, favorites, url -> Optional.of("Title of " + url));
        var hearted = windows.open(new VideoWindow(playback, services));
        render(-1, -1, 0);
        click(hearted.titleButtons().getFirst());
        assertThat(hearts).containsExactly(playback.url);
        assertThat(tooltipOver(hearted.titleButtons().getFirst())).contains("gui.liasmediaplayer.tooltip.unfavorite");
    }

    @Test
    void previousAndTheQueueSwitchExplainThemselves() {
        assertThat(tooltipOver(transport().children().get(1))).contains("gui.liasmediaplayer.tooltip.previous");
        assertThat(tooltipOver(transport().children().getLast())).contains("gui.liasmediaplayer.queue.show");
    }
}
