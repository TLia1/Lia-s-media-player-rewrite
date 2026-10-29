package com.lia.mediaplayer.ui.widget;

import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.testing.FakeText;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.input.Mods;
import com.lia.mediaplayer.ui.testing.RecordingCanvas;
import com.lia.mediaplayer.ui.theme.Role;
import com.lia.mediaplayer.ui.theme.Themes;
import org.junit.jupiter.api.Test;

class ScrollPaneTest {

    private static final Constraints ROOM = new Constraints(1000, 1000, new FakeText());
    private final InputContext input = new InputContext();

    @Test
    void scrollsTallContentAndLetsShortContentBe() {
        var column = new Column();
        boolean[] on = {false};
        var toggle = column.add(new Toggle(() -> on[0], value -> on[0] = value));
        column.add(new Spacer(10, 200));
        var pane = new ScrollPane(column);
        pane.layout(new Rect(0, 0, 100, 50), ROOM);
        assertThat(pane.measure(ROOM).height()).isEqualTo(Toggle.HEIGHT + 200);

        Rect t = toggle.bounds();
        pane.onInput(new InputEvent.PointerDown(t.centerX(), t.centerY(), 0, Mods.NONE, 1), input);
        assertThat(on[0]).isTrue();
        assertThat(pane.onInput(new InputEvent.Scroll(5, 5, -1, Mods.NONE), input))
                .isTrue();
        assertThat(pane.scroll()).isPositive();
        assertThat(toggle.bounds().y()).isNegative();

        var canvas = new RecordingCanvas(200, 100);
        pane.render(canvas, new RenderContext(Themes.DARK, 0, 5, 5, true));
        assertThat(canvas.fills()).anyMatch(f -> f.argb() == Themes.DARK.color(Role.SCROLL_THUMB));
        assertThat(canvas.clipDepth()).isZero();

        pane.onInput(new InputEvent.PointerDown(98, 0, 0, Mods.NONE, 1), input);
        pane.onInput(new InputEvent.PointerDrag(98, 49, 0, 0, 49, Mods.NONE), input);
        assertThat(pane.scroll()).isEqualTo(Toggle.HEIGHT + 200 - 50);
        pane.onInput(new InputEvent.PointerUp(98, 49, 0, Mods.NONE), input);
        pane.scrollTo(0);
        assertThat(pane.scroll()).isZero();

        var shortPane = new ScrollPane(new Spacer(10, 10));
        shortPane.layout(new Rect(0, 0, 100, 50), ROOM);
        assertThat(shortPane.onInput(new InputEvent.Scroll(5, 5, -1, Mods.NONE), input))
                .isFalse();
    }

    @Test
    void aToggleFlipsAndShowsItsState() {
        boolean[] on = {true};
        var toggle = new Toggle(() -> on[0], value -> on[0] = value);
        toggle.layout(new Rect(0, 0, Toggle.WIDTH, Toggle.HEIGHT), ROOM);
        var canvas = new RecordingCanvas(50, 50);
        toggle.render(canvas, new RenderContext(Themes.DARK, 0, -1, -1, true));
        assertThat(canvas.fills().getFirst().argb()).isEqualTo(Themes.DARK.color(Role.FILL));
        assertThat(toggle.onInput(new InputEvent.PointerDown(1, 1, 1, Mods.NONE, 1), input))
                .isFalse();
        assertThat(toggle.onInput(new InputEvent.PointerDown(1, 1, 0, Mods.NONE, 1), input))
                .isTrue();
        assertThat(on[0]).isFalse();
    }

    @Test
    void aTextButtonCanChangeItsLabelAndBeDisabled() {
        int[] count = {0};
        boolean[] enabled = {true};
        var button = new TextButton(() -> Text.literal("n" + count[0]), press -> count[0]++)
                .enabledWhen(() -> enabled[0])
                .activeWhen(() -> true)
                .minWidth(80)
                .tooltip(Text.literal("tip"));
        assertThat(button.measure(ROOM).width()).isEqualTo(80);
        button.layout(new Rect(0, 0, 80, 14), ROOM);
        button.click();
        assertThat(button.label()).isEqualTo(Text.literal("n1"));
        enabled[0] = false;
        button.click();
        assertThat(count[0]).isEqualTo(1);
        var canvas = new RecordingCanvas(100, 50);
        button.render(canvas, new RenderContext(Themes.DARK, 0, 5, 5, true));
        assertThat(canvas.strings()).containsExactly("n1");
        assertThat(button.tooltip(1, 1)).isPresent();
    }
}
