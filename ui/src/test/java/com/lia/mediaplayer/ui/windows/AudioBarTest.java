package com.lia.mediaplayer.ui.windows;

import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.core.diag.PlaybackError;
import com.lia.mediaplayer.core.playback.PlayerState;
import com.lia.mediaplayer.core.testing.FakePlatform;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.input.Mods;
import com.lia.mediaplayer.ui.testing.FakePlayback;
import com.lia.mediaplayer.ui.testing.RecordingCanvas;
import com.lia.mediaplayer.ui.theme.Themes;
import com.lia.mediaplayer.ui.widget.RenderContext;
import com.lia.mediaplayer.ui.window.WindowManager;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AudioBarTest {

    @TempDir
    Path gameDir;

    private final FakePlayback playback = new FakePlayback();
    private final WindowManager windows = new WindowManager();
    private final RecordingCanvas canvas = new RecordingCanvas(640, 360);

    private void render(double x, double y) {
        windows.render(canvas.clear(), new RenderContext(Themes.DARK, 0, x, y, true));
    }

    @Test
    void aCompactBarWithTheTitleAndTheTransport() {
        var bar = windows.open(new AudioBar(playback, WindowServices.of(new FakePlatform(gameDir))));
        playback.snapshot = FakePlayback.at(
                new PlayerState.Playing(Duration.ofSeconds(5)), Duration.ofSeconds(5), Duration.ofSeconds(200));
        render(-1, -1);
        assertThat(bar.frame().width()).isEqualTo(AudioBar.DEFAULT_WIDTH);
        assertThat(bar.frame().height()).isEqualTo(2 + 13 + TransportBar.HEIGHT);
        assertThat(bar.kind()).isEqualTo(AudioBar.KIND);
        assertThat(bar.control()).isSameAs(playback);
        assertThat(canvas.drewText("Clip")).isTrue();
        assertThat(canvas.drewText("0:05 / 3:20")).isTrue();
        assertThat(canvas.textures()).isEmpty();

        // Dragging the grip changes the width only.
        Rect f = bar.frame();
        windows.input(new InputEvent.PointerDown(f.right() - 2, f.bottom() - 2, 0, Mods.NONE, 1));
        windows.input(new InputEvent.PointerDrag(f.right() - 42, f.bottom() + 40, 0, 0, 0, Mods.NONE));
        windows.input(new InputEvent.PointerUp(f.right() - 42, f.bottom() + 40, 0, Mods.NONE));
        render(-1, -1);
        assertThat(bar.frame().width()).isEqualTo(AudioBar.DEFAULT_WIDTH - 40);
        assertThat(bar.frame().height()).isEqualTo(f.height());
    }

    @Test
    void aFailureShowsInTheTitleWithItsAdvice() {
        var bar = windows.open(new AudioBar(playback, WindowServices.of(new FakePlatform(gameDir))));
        var error = new PlaybackError(PlaybackError.Cause.NETWORK, "Connection refused");
        playback.snapshot = FakePlayback.at(new PlayerState.Failed(error), Duration.ZERO, null);
        render(-1, -1);
        assertThat(canvas.drewText("error.liasmediaplayer.cause")).isTrue();
        Rect f = bar.frame();
        render(f.x() + 20, f.y() + 5);
        assertThat(canvas.tooltip())
                .hasValueSatisfying(t -> assertThat(t.lines())
                        .containsExactly(
                                "error.liasmediaplayer.cause.network", "error.liasmediaplayer.cause.network.hint"));
        render(f.x() + 30, f.bottom() - 3);
        assertThat(canvas.tooltip().map(t -> t.lines().getFirst()))
                .isNotEqualTo(Optional.of("error.liasmediaplayer.cause.network"));

        playback.closed = true;
        bar.tick();
        assertThat(bar.isClosed()).isTrue();
    }

    @Test
    void closingTheBarClosesItsPlayback() {
        var bar = windows.open(new AudioBar(playback, WindowServices.of(new FakePlatform(gameDir))));
        render(-1, -1);
        bar.close();
        assertThat(playback.closed).isTrue();
    }
}
