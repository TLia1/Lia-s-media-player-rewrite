package com.lia.mediaplayer.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.text.Keys;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.input.Key;
import com.lia.mediaplayer.ui.input.Mods;
import com.lia.mediaplayer.ui.testing.RecordingCanvas;
import com.lia.mediaplayer.ui.theme.Themes;
import com.lia.mediaplayer.ui.widget.Slider;
import org.junit.jupiter.api.Test;

class MediaControlsScreenTest {

    private boolean noWindows = true;
    private final MediaControlsScreen screen = new MediaControlsScreen(() -> Themes.DARK, () -> 0L, () -> noWindows);

    @Test
    void sayHowToLeaveAndWhenNothingPlays() {
        var canvas = new RecordingCanvas(320, 200);
        screen.render(canvas, 10, 10, Mods.NONE);
        assertThat(canvas.strings()).containsExactly(Keys.CONTROLS_EMPTY, Keys.CONTROLS_HINT);
        var hint = canvas.texts().getLast();
        assertThat(hint.y()).isGreaterThan(canvas.texts().getFirst().y());
        assertThat(hint.y()).isLessThan(200);

        noWindows = false;
        screen.render(canvas.clear(), 12, 10, Mods.NONE);
        assertThat(canvas.strings()).containsExactly(Keys.CONTROLS_HINT);
    }

    @Test
    void showsTheWindowsOverTheWorldWithoutPausingIt() {
        assertThat(screen.showsWindows()).isTrue();
        assertThat(screen.drawsBackground()).isFalse();
        assertThat(screen.isPauseScreen()).isFalse();
        assertThat(screen.title().toString()).contains(Keys.CONTROLS_TITLE);
        screen.removed();
    }

    /** A screen that is one slider, to see a drag follow the widget that took the press. */
    static final class SliderScreen extends UiScreen {
        SliderScreen(Slider slider) {
            super(Text.literal("slider"), () -> Themes.DARK, () -> 0L, slider);
        }
    }

    @Test
    void aDragFollowsTheWidgetThatTookThePressWhereverTheCursorGoes() {
        double[] value = {0};
        var slider = new Slider(100, () -> value[0], v -> value[0] = v, v -> Text.literal("v" + v));
        var sliding = new SliderScreen(slider);
        var canvas = new RecordingCanvas(100, 20);
        sliding.render(canvas, 50, 5, Mods.NONE);
        assertThat(canvas.tooltip())
                .hasValueSatisfying(t -> assertThat(t.lines()).containsExactly("v0.0"));
        assertThat(sliding.input(new InputEvent.PointerDown(50, 5, 0, Mods.NONE, 1)))
                .isTrue();
        assertThat(value[0]).isEqualTo(0.5);
        // The cursor leaves the slider, and the screen, and the slider still follows it.
        sliding.render(canvas.clear(), 90, 400, Mods.NONE);
        assertThat(value[0]).isEqualTo(0.9);
        assertThat(canvas.tooltip()).isEmpty();
        assertThat(sliding.input(new InputEvent.PointerUp(90, 400, 0, Mods.NONE)))
                .isTrue();
        assertThat(sliding.input(new InputEvent.PointerUp(90, 400, 0, Mods.NONE)))
                .isFalse();
        assertThat(sliding.showsWindows()).isFalse();
        assertThat(sliding.drawsBackground()).isTrue();
    }

    @Test
    void inputOverEmptyRoomIsNotTaken() {
        var canvas = new RecordingCanvas(320, 200);
        screen.render(canvas, 10, 10, Mods.NONE);
        assertThat(screen.input(new InputEvent.PointerDown(10, 10, 0, Mods.NONE, 1)))
                .isFalse();
        screen.render(canvas.clear(), 30, 30, Mods.NONE);
        assertThat(screen.input(new InputEvent.PointerUp(30, 30, 0, Mods.NONE))).isFalse();
        assertThat(screen.input(new InputEvent.KeyDown(Key.A, Mods.NONE))).isFalse();
        assertThat(screen.input(new InputEvent.Scroll(5, 5, 1, Mods.NONE))).isFalse();
    }
}
