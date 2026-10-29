package com.lia.mediaplayer.ui.widget;

import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.testing.FakeText;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.icon.Glyphs;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.input.Mods;
import com.lia.mediaplayer.ui.testing.RecordingCanvas;
import com.lia.mediaplayer.ui.theme.Role;
import com.lia.mediaplayer.ui.theme.Themes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ListViewTest {

    private static final Constraints ROOM = new Constraints(1000, 1000, new FakeText());
    private static final int ROW = 10;

    private final InputContext input = new InputContext();
    private final List<String> events = new ArrayList<>();
    private int made;
    private ListView<String> list;

    /** A row with a label and a button. */
    private final class TestRow extends ListView.Row<String> {
        private final Row row = new Row();
        private final Label label = new Label(Text.literal(""), Role.TEXT);
        private String item = "";

        TestRow() {
            made++;
            row.add(label, 1);
            row.add(new IconButton(Glyphs::close, () -> events.add("remove " + item)));
            addChild(row);
        }

        @Override
        protected void bind(String newItem, int index) {
            item = newItem;
            label.setText(Text.literal(newItem));
        }

        @Override
        public Size measure(Constraints constraints) {
            return new Size(constraints.maxWidth(), ROW);
        }

        @Override
        public void layout(Rect newBounds, Constraints constraints) {
            super.layout(newBounds, constraints);
            row.layout(newBounds, constraints);
        }
    }

    @BeforeEach
    void build() {
        list = new ListView<String>(ROW, TestRow::new)
                .onActivate((index, press) -> events.add("activate " + index + "x" + press.clicks()))
                .reorderable((from, to) -> events.add("move " + from + " " + to), () -> true)
                .selected(index -> index == 1)
                .whenEmpty(Text.literal("Empty"), Text.literal("Add some"));
        list.setItems(IntStream.range(0, 100).mapToObj(i -> "item" + i).toList());
        list.layout(new Rect(0, 0, 100, 50), ROOM);
    }

    private RecordingCanvas render(double x, double y) {
        var canvas = new RecordingCanvas(200, 100);
        list.render(canvas, new RenderContext(Themes.DARK, 0, x, y, true));
        return canvas;
    }

    @Test
    void onlyTheRowsOnScreenExist() {
        assertThat(made).isLessThanOrEqualTo(50 / ROW + 2);
        assertThat(render(-1, -1).strings()).startsWith("item0", "item1").doesNotContain("item20");
        list.onInput(new InputEvent.Scroll(5, 5, -1, Mods.NONE), input);
        assertThat(list.scroll()).isEqualTo(2 * ROW);
        assertThat(render(-1, -1).strings()).contains("item2").doesNotContain("item0");
        list.scrollTo(1_000_000);
        assertThat(list.scroll()).isEqualTo(100 * ROW - 50);
        assertThat(render(-1, -1).strings()).contains("item99");
        list.reveal(0);
        assertThat(list.scroll()).isZero();
    }

    @Test
    void theSelectedAndHoveredRowsStandOut() {
        var fills = render(5, 25).fills();
        assertThat(fills).anyMatch(f -> f.rect().y() == ROW && f.argb() == Themes.DARK.color(Role.ROW_SELECTED));
        assertThat(fills).anyMatch(f -> f.rect().y() == 2 * ROW && f.argb() == Themes.DARK.color(Role.ROW_HOVER));
        assertThat(fills).anyMatch(f -> f.argb() == Themes.DARK.color(Role.SCROLL_THUMB));
    }

    @Test
    void aPressActivatesTheRowUnlessItsButtonTakesIt() {
        list.onInput(new InputEvent.PointerDown(5, 25, 0, Mods.NONE, 2), input);
        list.onInput(new InputEvent.PointerUp(5, 25, 0, Mods.NONE), input);
        Rect button = new Rect(100 - Scrollbar.WIDTH - 1 - 13, 0, 13, ROW);
        list.onInput(new InputEvent.PointerDown(button.centerX(), 5, 0, Mods.NONE, 1), input);
        assertThat(events).containsExactly("activate 2x2", "remove item0");
    }

    @Test
    void draggingARowMovesItToTheGapUnderTheCursor() {
        list.onInput(new InputEvent.PointerDown(5, 5, 0, Mods.NONE, 1), input);
        assertThat(input.isCaptured(list)).isTrue();
        list.onInput(new InputEvent.PointerDrag(5, 6, 0, 0, 1, Mods.NONE), input);
        assertThat(list.isDragging()).isFalse();
        list.onInput(new InputEvent.PointerDrag(5, 31, 0, 0, 25, Mods.NONE), input);
        assertThat(list.isDragging()).isTrue();
        assertThat(list.dropGap()).isEqualTo(3);
        assertThat(render(5, 31).fills()).anyMatch(f -> f.argb() == Themes.DARK.color(Role.FILL));
        list.onInput(new InputEvent.PointerUp(5, 31, 0, Mods.NONE), input);
        assertThat(events).containsExactly("activate 0x1", "move 0 3");
        assertThat(input.captured()).isNull();
    }

    @Test
    void holdingADragAtTheEdgeScrolls() {
        list.onInput(new InputEvent.PointerDown(5, 5, 0, Mods.NONE, 1), input);
        list.onInput(new InputEvent.PointerDrag(5, 48, 0, 0, 43, Mods.NONE), input);
        var canvas = new RecordingCanvas(200, 100);
        list.render(canvas, new RenderContext(Themes.DARK, 1_000_000_000L, 5, 48, true));
        assertThat(list.scroll()).isPositive();
    }

    @Test
    void theScrollBarCanBeDragged() {
        list.onInput(new InputEvent.PointerDown(98, 49, 0, Mods.NONE, 1), input);
        assertThat(list.scroll()).isGreaterThan(900);
        list.onInput(new InputEvent.PointerDrag(98, 0, 0, 0, -49, Mods.NONE), input);
        assertThat(list.scroll()).isZero();
        list.onInput(new InputEvent.PointerUp(98, 0, 0, Mods.NONE), input);
    }

    @Test
    void anEmptyListSaysSo() {
        list.setItems(List.of());
        assertThat(render(-1, -1).strings()).containsExactly("Empty", "Add some");
        assertThat(list.measure(ROOM).height()).isZero();
    }
}
