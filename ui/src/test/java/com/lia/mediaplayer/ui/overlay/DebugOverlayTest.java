package com.lia.mediaplayer.ui.overlay;

import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.playback.PlayerStats;
import com.lia.mediaplayer.core.testing.FakePlatform;
import com.lia.mediaplayer.ui.testing.FakePlayback;
import com.lia.mediaplayer.ui.testing.RecordingCanvas;
import com.lia.mediaplayer.ui.theme.Themes;
import com.lia.mediaplayer.ui.widget.RenderContext;
import com.lia.mediaplayer.ui.window.WindowManager;
import com.lia.mediaplayer.ui.windows.AudioBar;
import com.lia.mediaplayer.ui.windows.VideoWindow;
import com.lia.mediaplayer.ui.windows.WindowServices;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DebugOverlayTest {

    @TempDir
    Path gameDir;

    @Test
    void describesEachPlayerFromItsCounters() {
        var services = WindowServices.of(new FakePlatform(gameDir));
        var windows = new WindowManager();
        var video = new FakePlayback();
        video.stats = new PlayerStats(360, 30, 12, 30, 1800, 2000, 4, 15_000, 1, 2, 3);
        var hidden = new FakePlayback();
        hidden.stats = new PlayerStats(0, 0, 0, 0, 1900, 2000, 0, 0, 0, 0, 2);
        var sound = new FakePlayback();
        sound.stats = new PlayerStats(0, 0, 0, 0, 500, 2000, 0, 0, 5, 0, 1);
        windows.open(new VideoWindow(video, services));
        windows.open(new VideoWindow(hidden, services)).setHidden(true);
        windows.open(new AudioBar(sound, services));

        var overlay = new DebugOverlay(() -> 3 * 1024 * 1024, () -> 512 * 1024);
        overlay.recordWindows(250_000);
        List<Text> lines = overlay.describe(windows.windows());

        assertThat(lines)
                .containsExactly(
                        Text.translatable("gui.liasmediaplayer.debug.summary", "0.25", "3.0", "0.5"),
                        Text.translatable("gui.liasmediaplayer.debug.player", 1, "video", 3L, Text.literal("")),
                        Text.translatable(
                                "gui.liasmediaplayer.debug.picture", 360, "30", "12/30", 4L, "+15", "0.00", 2L),
                        Text.translatable("gui.liasmediaplayer.debug.sound", "1800/2000", 1L),
                        Text.translatable(
                                "gui.liasmediaplayer.debug.player",
                                2,
                                "video",
                                2L,
                                Text.translatable("gui.liasmediaplayer.debug.hidden")),
                        Text.translatable("gui.liasmediaplayer.debug.no_picture"),
                        Text.translatable("gui.liasmediaplayer.debug.sound", "1900/2000", 0L),
                        Text.translatable("gui.liasmediaplayer.debug.player", 3, "audio", 1L, Text.literal("")),
                        Text.translatable("gui.liasmediaplayer.debug.sound", "500/2000", 5L));
    }

    @Test
    void drawsItsLinesInTheCornerAndRebuildsThemFourTimesASecond() {
        var windows = new WindowManager();
        var overlay = new DebugOverlay(() -> 0, () -> 0);
        var canvas = new RecordingCanvas(640, 360);
        overlay.render(canvas, new RenderContext(Themes.DARK, 0, -1, -1, false), windows.windows());
        assertThat(canvas.strings()).singleElement().asString().startsWith("gui.liasmediaplayer.debug.summary[0.00");

        overlay.recordWindows(2_000_000);
        overlay.render(canvas.clear(), new RenderContext(Themes.DARK, 1_000, -1, -1, false), windows.windows());
        assertThat(canvas.strings()).singleElement().asString().contains("[0.00");
        overlay.render(
                canvas.clear(),
                new RenderContext(Themes.DARK, DebugOverlay.REFRESH_NANOS, -1, -1, false),
                windows.windows());
        assertThat(canvas.strings()).singleElement().asString().contains("[2.00");
        assertThat(overlay.windowsNanos()).isEqualTo(2_000_000);
    }
}
