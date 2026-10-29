package com.lia.mediaplayer.ui.widget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.testing.FakeText;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.testing.RecordingCanvas;
import com.lia.mediaplayer.ui.theme.Role;
import com.lia.mediaplayer.ui.theme.Themes;
import org.junit.jupiter.api.Test;

class LayoutTest {

    private static final FakeText FONT = new FakeText();
    private static final Constraints ROOM = new Constraints(1000, 1000, FONT);
    private static final RenderContext CTX = new RenderContext(Themes.DARK, 0, Double.NaN, Double.NaN, true);

    @Test
    void aRowGivesFixedChildrenTheirSizeAndSharesTheRestByWeight() {
        var row = new Row();
        row.gap(2).padding(3, 1);
        var a = row.add(new Spacer(10, 6));
        var flexOne = row.add(new Spacer(), 1);
        var flexTwo = row.add(new Spacer(), 2);
        var b = row.add(new Spacer(20, 4));
        row.layout(new Rect(0, 0, 100, 10), ROOM);

        assertThat(a.bounds()).isEqualTo(new Rect(3, 2, 10, 6));
        // 100 - 2*3 padding - 10 - 20 - 3 gaps of 2 = 58, split 1:2.
        assertThat(flexOne.bounds().width() + flexTwo.bounds().width()).isEqualTo(58);
        assertThat(flexOne.bounds().width()).isEqualTo(19);
        assertThat(flexTwo.bounds().x()).isEqualTo(flexOne.bounds().right() + 2);
        assertThat(flexOne.bounds().height()).isEqualTo(8);
        assertThat(b.bounds()).isEqualTo(new Rect(77, 3, 20, 4));
        assertThat(row.measure(ROOM)).isEqualTo(new Size(6 + 10 + 20 + 6, 8));
    }

    @Test
    void aColumnStacksAndSkipsHiddenChildren() {
        var column = new Column();
        var top = column.add(new Spacer(5, 10), 0, true);
        var hidden = column.add(new Spacer(5, 50));
        var rest = column.add(new Spacer(), 1);
        hidden.setVisible(false);
        column.layout(new Rect(0, 0, 40, 100), ROOM);
        assertThat(top.bounds()).isEqualTo(new Rect(0, 0, 40, 10));
        assertThat(rest.bounds()).isEqualTo(new Rect(0, 10, 40, 90));
        assertThat(column.measure(ROOM)).isEqualTo(new Size(5, 10));
        assertThat(column.children()).containsExactly(top, hidden, rest);
        assertThatThrownBy(() -> column.add(new Spacer(), -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aWidgetBelongsToOneParent() {
        var spacer = new Spacer();
        new Row().add(spacer);
        assertThatThrownBy(() -> new Column().add(spacer)).isInstanceOf(IllegalArgumentException.class);
        assertThat(spacer.parent()).isInstanceOf(Row.class);
    }

    @Test
    void aLabelThatFitsIsDrawnAsItIsAndAligned() {
        var label = new Label(Text.literal("hello"), Role.TEXT).align(Label.Align.RIGHT);
        assertThat(label.measure(ROOM)).isEqualTo(new Size(30, FakeText.LINE_HEIGHT));
        assertThat(label.measure(ROOM.withMax(12, 100)).width()).isEqualTo(12);
        label.layout(new Rect(0, 0, 100, 8), ROOM);
        var canvas = new RecordingCanvas(200, 200);
        label.render(canvas, CTX);
        assertThat(canvas.texts()).singleElement().satisfies(t -> {
            assertThat(t.text()).isEqualTo("hello");
            assertThat(t.x()).isEqualTo(70);
        });

        label.align(Label.Align.CENTER);
        label.render(canvas.clear(), CTX);
        assertThat(canvas.texts().getFirst().x()).isEqualTo(35);
    }

    @Test
    void aLabelTooLongIsCutWithAnEllipsis() {
        var label = new Label(Text.literal("a long title here"), Role.TEXT)
                .shadow(true)
                .color(Role.DANGER);
        label.layout(new Rect(0, 0, 60, 8), ROOM);
        var canvas = new RecordingCanvas(200, 200);
        label.render(canvas, CTX);
        String shown = canvas.texts().getFirst().text();
        assertThat(shown).endsWith("…");
        assertThat(FONT.width(shown)).isLessThanOrEqualTo(60);
        assertThat(canvas.texts().getFirst().shadow()).isTrue();
        assertThat(canvas.texts().getFirst().argb()).isEqualTo(Themes.DARK.color(Role.DANGER));

        label.setText(Text.literal("short"));
        label.render(canvas.clear(), CTX);
        assertThat(canvas.strings()).containsExactly("short");
        assertThat(label.text()).isEqualTo(Text.literal("short"));
        assertThat(Label.ellipsize(FONT, "abc", 3)).isEmpty();
    }

    @Test
    void wrapsTextBetweenWordsAndCutsWordsThatCannotFit() {
        assertThat(TextWrap.lines(FONT, "one two three", 48)).containsExactly("one two", "three");
        assertThat(TextWrap.lines(FONT, "abcdefghij", 24)).containsExactly("abcd", "efgh", "ij");
        assertThat(TextWrap.lines(FONT, "first\nsecond", 600)).containsExactly("first", "second");
        assertThat(TextWrap.lines(FONT, "anything", 0)).isEmpty();
    }

    @Test
    void sizesNeverGoNegative() {
        assertThat(new Size(-1, -2)).isEqualTo(Size.ZERO);
        assertThat(new Constraints(-5, 3, FONT).maxWidth()).isZero();
    }
}
