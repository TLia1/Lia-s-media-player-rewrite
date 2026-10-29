package com.lia.mediaplayer.ui.widget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.testing.FakeText;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.icon.Glyphs;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.input.Key;
import com.lia.mediaplayer.ui.input.Mods;
import com.lia.mediaplayer.ui.testing.RecordingCanvas;
import com.lia.mediaplayer.ui.theme.Role;
import com.lia.mediaplayer.ui.theme.Themes;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ControlsTest {

    private static final Constraints ROOM = new Constraints(1000, 1000, new FakeText());
    private final InputContext input = new InputContext();

    private static InputEvent.PointerDown down(double x, double y) {
        return new InputEvent.PointerDown(x, y, 0, Mods.NONE, 1);
    }

    private static RenderContext pointerAt(double x, double y) {
        return new RenderContext(Themes.DARK, 0, x, y, true);
    }

    @Test
    void anIconButtonActsOnAPrimaryClickAndColoursItsState() {
        var clicks = new ArrayList<Mods>();
        boolean[] enabled = {true};
        boolean[] active = {false};
        var button = new IconButton(Glyphs::close, clicks::add)
                .enabledWhen(() -> enabled[0])
                .activeWhen(() -> active[0])
                .tooltip(Text.literal("Close"));
        assertThat(button.measure(ROOM)).isEqualTo(new Size(13, 13));
        button.layout(new Rect(0, 0, 13, 13), ROOM);

        assertThat(button.onInput(down(5, 5), input)).isTrue();
        assertThat(button.onInput(new InputEvent.PointerDown(5, 5, 1, Mods.NONE, 1), input))
                .isFalse();
        assertThat(clicks).containsExactly(Mods.NONE);

        var canvas = new RecordingCanvas(50, 50);
        button.render(canvas, pointerAt(-1, -1));
        assertThat(canvas.fills()).allMatch(f -> f.argb() == Themes.DARK.color(Role.ICON));
        button.render(canvas.clear(), pointerAt(5, 5));
        assertThat(canvas.fills()).allMatch(f -> f.argb() == Themes.DARK.color(Role.ICON_HOVER));
        active[0] = true;
        button.render(canvas.clear(), pointerAt(-1, -1));
        assertThat(canvas.fills()).allMatch(f -> f.argb() == Themes.DARK.color(Role.ICON_ACTIVE));

        enabled[0] = false;
        assertThat(button.onInput(down(5, 5), input)).isTrue();
        assertThat(clicks).hasSize(1);
        button.render(canvas.clear(), pointerAt(5, 5));
        assertThat(canvas.fills()).allMatch(f -> f.argb() == Themes.DARK.color(Role.ICON_DISABLED));
        assertThat(button.tooltip(5, 5)).contains(List.of(Text.literal("Close")));
    }

    @Test
    void aDangerousButtonWarnsOnHover() {
        var button = new IconButton(Glyphs::close, () -> {}).dangerous();
        button.layout(new Rect(0, 0, 13, 13), ROOM);
        var canvas = new RecordingCanvas(50, 50);
        button.render(canvas, pointerAt(3, 3));
        assertThat(canvas.fills()).allMatch(f -> f.argb() == Themes.DARK.color(Role.DANGER));
        assertThat(button.tooltip(3, 3)).isEmpty();
    }

    @Test
    void aSeekBarScrubsWhileDraggedAndSeeksOnceOnRelease() {
        var seeks = new ArrayList<Double>();
        boolean[] seekable = {true};
        var bar = new SeekBar(() -> 0.25, () -> seekable[0], seeks::add, f -> Text.literal("at " + f));
        bar.layout(new Rect(10, 0, 100, 11), ROOM);
        assertThat(bar.measure(ROOM).height()).isEqualTo(SeekBar.HEIGHT);
        assertThat(bar.shownFraction()).isEqualTo(0.25);

        assertThat(bar.onInput(down(60, 5), input)).isTrue();
        assertThat(input.isCaptured(bar)).isTrue();
        assertThat(bar.isScrubbing()).isTrue();
        assertThat(bar.shownFraction()).isEqualTo(0.5);
        assertThat(bar.onInput(new InputEvent.PointerDrag(85, 30, 0, 25, 25, Mods.NONE), input))
                .isTrue();
        assertThat(bar.shownFraction()).isEqualTo(0.75);
        assertThat(bar.tooltip(0, 0)).contains(List.of(Text.literal("at 0.75")));
        assertThat(seeks).isEmpty();

        var canvas = new RecordingCanvas(200, 50);
        bar.render(canvas, pointerAt(-1, -1));
        // Track, fill, and the knob that shows while scrubbing.
        assertThat(canvas.fills()).hasSize(3);

        assertThat(bar.onInput(new InputEvent.PointerUp(500, 0, 0, Mods.NONE), input))
                .isTrue();
        assertThat(seeks).containsExactly(1.0);
        assertThat(bar.isScrubbing()).isFalse();
        assertThat(input.captured()).isNull();
        assertThat(bar.onInput(new InputEvent.PointerUp(500, 0, 0, Mods.NONE), input))
                .isFalse();

        bar.render(canvas.clear(), pointerAt(-1, -1));
        assertThat(canvas.fills()).hasSize(2);
        assertThat(bar.fractionAt(0)).isZero();
    }

    @Test
    void aLiveStreamHasNothingToSeek() {
        var seeks = new ArrayList<Double>();
        var bar = new SeekBar(() -> Double.NaN, () -> false, seeks::add, f -> Text.literal("x"));
        bar.layout(new Rect(0, 0, 100, 11), ROOM);
        assertThat(bar.onInput(down(50, 5), input)).isTrue();
        assertThat(bar.isScrubbing()).isFalse();
        assertThat(bar.tooltip(50, 5)).isEmpty();
        var canvas = new RecordingCanvas(200, 50);
        bar.render(canvas, pointerAt(50, 5));
        assertThat(canvas.fills()).hasSize(1);
        assertThat(bar.shownFraction()).isZero();
    }

    @Test
    void aSliderFollowsTheCursorAndTheWheel() {
        double[] value = {0.5};
        var slider = new Slider(40, () -> value[0], v -> value[0] = v, v -> Text.literal(Math.round(v * 100) + "%"));
        assertThat(slider.measure(ROOM)).isEqualTo(new Size(40, Slider.HEIGHT));
        slider.layout(new Rect(0, 0, 40, 11), ROOM);

        assertThat(slider.onInput(down(10, 5), input)).isTrue();
        assertThat(value[0]).isEqualTo(0.25);
        assertThat(slider.onInput(new InputEvent.PointerDrag(30, 5, 0, 20, 0, Mods.NONE), input))
                .isTrue();
        assertThat(value[0]).isEqualTo(0.75);
        var canvas = new RecordingCanvas(100, 50);
        slider.render(canvas, pointerAt(-1, -1));
        assertThat(canvas.fills()).hasSize(3);
        assertThat(slider.onInput(new InputEvent.PointerUp(30, 5, 0, Mods.NONE), input))
                .isTrue();
        assertThat(slider.onInput(new InputEvent.PointerDrag(0, 5, 0, -30, 0, Mods.NONE), input))
                .isFalse();

        assertThat(slider.onInput(new InputEvent.Scroll(5, 5, 1, Mods.NONE), input))
                .isTrue();
        assertThat(value[0]).isCloseTo(0.8, within(1e-9));
        assertThat(slider.onInput(new InputEvent.Scroll(5, 5, 1, Mods.ctrlOnly()), input))
                .isFalse();
        assertThat(slider.tooltip(5, 5)).contains(List.of(Text.literal("80%")));
    }

    @Test
    void aTextButtonIsSizedByItsLabel() {
        int[] runs = {0};
        var button = new TextButton(Text.literal("Retry"), () -> runs[0]++);
        Size size = button.measure(ROOM);
        assertThat(size).isEqualTo(new Size(30 + 2 * TextButton.PAD_X, FakeText.LINE_HEIGHT + 2 * TextButton.PAD_Y));
        button.layout(new Rect(0, 0, size.width(), size.height()), ROOM);
        assertThat(button.onInput(down(1, 1), input)).isTrue();
        assertThat(button.onInput(new InputEvent.KeyDown(Key.ENTER, Mods.NONE), input))
                .isFalse();
        assertThat(runs[0]).isEqualTo(1);
        var canvas = new RecordingCanvas(100, 50);
        button.render(canvas, pointerAt(1, 1));
        assertThat(canvas.strings()).containsExactly("Retry");
        assertThat(button.accessibleName()).contains(Text.literal("Retry"));
    }

    @Test
    void aContainerOffersPointerEventsTopmostFirstAndFindsTooltips() {
        var row = new Row();
        var clicked = new ArrayList<String>();
        var left = row.add(new IconButton(Glyphs::close, () -> clicked.add("left")).tooltip(Text.literal("L")));
        var right = row.add(new IconButton(Glyphs::copy, () -> clicked.add("right")));
        row.layout(new Rect(0, 0, 26, 13), ROOM);

        assertThat(row.onInput(down(20, 5), input)).isTrue();
        assertThat(row.onInput(down(100, 5), input)).isFalse();
        assertThat(row.onInput(new InputEvent.KeyDown(Key.SPACE, Mods.NONE), input))
                .isFalse();
        assertThat(row.onInput(new InputEvent.CharTyped('x'), input)).isFalse();
        assertThat(row.onInput(new InputEvent.PointerMove(5, 5), input)).isFalse();
        assertThat(row.onInput(new InputEvent.Scroll(5, 5, 1, Mods.NONE), input))
                .isFalse();
        assertThat(clicked).containsExactly("right");
        assertThat(row.widgetAt(3, 3)).isSameAs(left);
        assertThat(row.widgetAt(100, 3)).isNull();
        assertThat(row.tooltipAt(3, 3)).contains(List.of(Text.literal("L")));
        assertThat(row.tooltipAt(20, 3)).isEmpty();

        right.setVisible(false);
        assertThat(row.onInput(down(20, 5), input)).isFalse();
        right.setAccessibleName(Text.literal("copy"));
        assertThat(right.accessibleName()).isPresent();
    }
}
