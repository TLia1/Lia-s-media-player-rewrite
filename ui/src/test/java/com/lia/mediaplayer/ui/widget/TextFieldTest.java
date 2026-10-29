package com.lia.mediaplayer.ui.widget;

import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.platform.Platform;
import com.lia.mediaplayer.core.testing.FakeText;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.input.Key;
import com.lia.mediaplayer.ui.input.Mods;
import com.lia.mediaplayer.ui.testing.RecordingCanvas;
import com.lia.mediaplayer.ui.theme.Themes;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TextFieldTest {

    private static final Constraints ROOM = new Constraints(1000, 1000, new FakeText());
    private static final Mods SHIFT = Mods.shiftOnly();
    private static final Mods CTRL = Mods.ctrlOnly();
    private static final Mods CTRL_SHIFT = new Mods(true, true, false, false, false);

    private final InputContext input = new InputContext();
    private final StringBuilder clipboardText = new StringBuilder();
    private final Platform.Clipboard clipboard = new Platform.Clipboard() {
        @Override
        public String get() {
            return clipboardText.toString();
        }

        @Override
        public void set(String text) {
            clipboardText.setLength(0);
            clipboardText.append(text);
        }
    };
    private final ArrayList<String> changes = new ArrayList<>();
    private final ArrayList<String> submitted = new ArrayList<>();
    private TextField field;

    @BeforeEach
    void layOut() {
        field = new TextField(clipboard, 20)
                .placeholder(Text.literal("Search"))
                .onChange(changes::add)
                .onSubmit(submitted::add);
        Size size = field.measure(ROOM);
        assertThat(size.height()).isEqualTo(FakeText.LINE_HEIGHT + 2 * TextField.PAD);
        field.layout(new Rect(0, 0, 200, size.height()), ROOM);
    }

    private void click(double x, int clicks, Mods mods) {
        field.onInput(new InputEvent.PointerDown(x, 5, 0, mods, clicks), input);
        field.onInput(new InputEvent.PointerUp(x, 5, 0, mods), input);
    }

    private void type(String text) {
        text.codePoints().forEach(c -> field.onInput(new InputEvent.CharTyped(c), input));
    }

    private boolean key(Key key, Mods mods) {
        return field.onInput(new InputEvent.KeyDown(key, mods), input);
    }

    @Test
    void takesNothingUntilClicked() {
        assertThat(field.onInput(new InputEvent.CharTyped('a'), input)).isFalse();
        assertThat(key(Key.LEFT, Mods.NONE)).isFalse();
        click(5, 1, Mods.NONE);
        assertThat(field.isFocused()).isTrue();
        assertThat(input.isFocused(field)).isTrue();
        type("hi");
        assertThat(field.text()).isEqualTo("hi");
        assertThat(changes).containsExactly("h", "hi");
    }

    @Test
    void editsAtTheCursorAndJumpsByWord() {
        click(5, 1, Mods.NONE);
        type("hello big world");
        key(Key.LEFT, CTRL);
        assertThat(field.cursor()).isEqualTo(10);
        key(Key.BACKSPACE, CTRL);
        assertThat(field.text()).isEqualTo("hello world");
        key(Key.HOME, Mods.NONE);
        key(Key.DELETE, Mods.NONE);
        assertThat(field.text()).isEqualTo("ello world");
        key(Key.RIGHT, CTRL);
        assertThat(field.cursor()).isEqualTo(4);
        key(Key.END, Mods.NONE);
        key(Key.BACKSPACE, Mods.NONE);
        assertThat(field.text()).isEqualTo("ello worl");
        key(Key.DELETE, CTRL);
        key(Key.LEFT, Mods.NONE);
        key(Key.RIGHT, Mods.NONE);
        assertThat(field.cursor()).isEqualTo(9);
    }

    @Test
    void selectsCopiesCutsAndPastes() {
        click(5, 1, Mods.NONE);
        type("one two");
        key(Key.LEFT, CTRL_SHIFT);
        assertThat(field.selection()).isEqualTo("two");
        key(Key.C, CTRL);
        assertThat(clipboard.get()).isEqualTo("two");
        key(Key.LEFT, Mods.NONE);
        assertThat(field.selection()).isEmpty();
        assertThat(field.cursor()).isEqualTo(4);
        key(Key.A, CTRL);
        key(Key.X, CTRL);
        assertThat(field.text()).isEmpty();
        assertThat(clipboard.get()).isEqualTo("one two");
        clipboard.set("a\nb");
        key(Key.V, CTRL);
        assertThat(field.text()).isEqualTo("a b");
        key(Key.LEFT, SHIFT);
        key(Key.RIGHT, Mods.NONE);
        assertThat(field.cursor()).isEqualTo(3);
        assertThat(key(Key.A, Mods.NONE)).isFalse();
        assertThat(key(Key.ESCAPE, Mods.NONE)).isFalse();
    }

    @Test
    void neverGrowsPastItsMaximum() {
        click(5, 1, Mods.NONE);
        type("x".repeat(30));
        assertThat(field.text()).hasSize(20);
        field.setText("y".repeat(40));
        assertThat(field.text()).hasSize(20);
    }

    @Test
    void enterSubmits() {
        click(5, 1, Mods.NONE);
        type("go");
        assertThat(key(Key.ENTER, Mods.NONE)).isTrue();
        assertThat(submitted).containsExactly("go");
    }

    @Test
    void clicksPlaceTheCursorAndSelectWordsOrAll() {
        field.setText("alpha beta");
        click(TextField.PAD + 2 * FakeText.CHAR_WIDTH + 1, 1, Mods.NONE);
        assertThat(field.cursor()).isEqualTo(2);
        click(TextField.PAD + 7 * FakeText.CHAR_WIDTH, 2, Mods.NONE);
        assertThat(field.selection()).isEqualTo("beta");
        click(TextField.PAD + 7 * FakeText.CHAR_WIDTH, 3, Mods.NONE);
        assertThat(field.selection()).isEqualTo("alpha beta");

        field.onInput(new InputEvent.PointerDown(TextField.PAD, 5, 0, Mods.NONE, 1), input);
        field.onInput(
                new InputEvent.PointerDrag(TextField.PAD + 5 * FakeText.CHAR_WIDTH, 5, 0, 0, 0, Mods.NONE), input);
        field.onInput(new InputEvent.PointerUp(TextField.PAD + 5 * FakeText.CHAR_WIDTH, 5, 0, Mods.NONE), input);
        assertThat(field.selection()).isEqualTo("alpha");
    }

    @Test
    void drawsThePlaceholderTheTextAndTheCursor() {
        var canvas = new RecordingCanvas(300, 50);
        var ctx = new RenderContext(Themes.DARK, 0, -1, -1, true);
        field.render(canvas, ctx);
        assertThat(canvas.strings()).containsExactly("Search", "");
        click(5, 1, Mods.NONE);
        type("abc");
        key(Key.LEFT, SHIFT);
        field.render(canvas.clear(), ctx);
        assertThat(canvas.strings()).containsExactly("abc");
        assertThat(canvas.clipDepth()).isZero();
        field.blur();
        assertThat(field.isFocused()).isFalse();
    }

    @Test
    void scrollsToKeepTheCursorInView() {
        field.layout(new Rect(0, 0, 40, 14), ROOM);
        click(5, 1, Mods.NONE);
        type("abcdefghijklmnop");
        var canvas = new RecordingCanvas(300, 50);
        field.render(canvas, new RenderContext(Themes.DARK, 0, -1, -1, true));
        assertThat(canvas.texts().getFirst().x()).isNegative();
    }
}
