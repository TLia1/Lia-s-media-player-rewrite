package com.lia.mediaplayer.ui.window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.icon.Glyphs;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.input.Key;
import com.lia.mediaplayer.ui.input.Mods;
import com.lia.mediaplayer.ui.testing.RecordingCanvas;
import com.lia.mediaplayer.ui.theme.Role;
import com.lia.mediaplayer.ui.theme.Themes;
import com.lia.mediaplayer.ui.widget.IconButton;
import com.lia.mediaplayer.ui.widget.RenderContext;
import com.lia.mediaplayer.ui.widget.Size;
import com.lia.mediaplayer.ui.widget.Spacer;
import org.junit.jupiter.api.Test;

class WindowManagerTest {

    private final WindowManager windows = new WindowManager();
    private final RecordingCanvas canvas = new RecordingCanvas(400, 300);

    static final class TestWindow extends Window {
        boolean closedCalled;
        int ticks;

        TestWindow(String title) {
            super(Text.literal(title));
            addTitleButton(new IconButton(Glyphs::close, this::close).tooltip(Text.literal("Close")));
            setBody(new Spacer());
        }

        @Override
        public Size preferredSize() {
            return new Size(100, 60);
        }

        @Override
        public String kind() {
            return "test";
        }

        @Override
        public void tick() {
            ticks++;
        }

        @Override
        protected void onClosed() {
            closedCalled = true;
        }
    }

    private void render(double x, double y, long nanos) {
        windows.render(canvas.clear(), new RenderContext(Themes.DARK, nanos, x, y, true));
    }

    private static InputEvent.PointerDown down(double x, double y) {
        return new InputEvent.PointerDown(x, y, 0, Mods.NONE, 1);
    }

    private static InputEvent.PointerDrag drag(double x, double y, Mods mods) {
        return new InputEvent.PointerDrag(x, y, 0, 0, 0, mods);
    }

    private static InputEvent.PointerUp up(double x, double y) {
        return new InputEvent.PointerUp(x, y, 0, Mods.NONE);
    }

    @Test
    void windowsArePlacedAtTheFirstFrameAndCascade() {
        var first = windows.open(new TestWindow("one"));
        assertThat(first.isPlaced()).isFalse();
        render(-1, -1, 0);
        assertThat(first.frame()).isEqualTo(new Rect(294, 6, 100, 60));
        var second = windows.open(new TestWindow("two"));
        assertThat(second.frame()).isEqualTo(new Rect(280, 20, 100, 60));
        assertThat(windows.windows()).containsExactly(first, second);
        assertThat(windows.focused()).contains(second);
        assertThat(windows.frontMost(TestWindow.class)).contains(second);
        assertThatThrownBy(() -> windows.open(first)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eachWindowDrawsAboveTheOneBeforeAndTheFrontOneIsLit() {
        windows.open(new TestWindow("one"));
        windows.open(new TestWindow("two"));
        render(-1, -1, 0);
        render(-1, -1, 1_000_000_000L);
        assertThat(canvas.layer()).isEqualTo(1);
        assertThat(canvas.strings()).containsExactly("one", "two");
        int lit = Themes.DARK.color(Role.BORDER_FOCUSED);
        assertThat(canvas.fills().stream().filter(f -> f.argb() == lit).allMatch(f -> f.layer() == 1))
                .isTrue();
        assertThat(canvas.fills().stream().anyMatch(f -> f.argb() == lit)).isTrue();
    }

    @Test
    void aPressRaisesTheWindowUnderItAndAMissIsNotTaken() {
        var first = windows.open(new TestWindow("one"));
        var second = windows.open(new TestWindow("two"));
        render(-1, -1, 0);
        // (300, 60) is inside the first window only.
        assertThat(windows.input(down(390, 64))).isTrue();
        windows.input(up(390, 64));
        assertThat(windows.focused()).contains(first);
        assertThat(windows.input(down(10, 200))).isFalse();
        assertThat(windows.input(up(10, 200))).isFalse();
        assertThat(windows.input(new InputEvent.PointerMove(10, 10))).isFalse();
        assertThat(second.isClosed()).isFalse();
    }

    @Test
    void draggingMovesAWindowAndSnapsItUnlessShiftIsHeld() {
        var window = windows.open(new TestWindow("one"));
        render(-1, -1, 0);
        windows.input(down(300, 10));
        windows.input(drag(9, 10, Mods.NONE));
        assertThat(window.frame()).isEqualTo(new Rect(0, 0, 100, 60));
        windows.input(drag(9, 10, Mods.shiftOnly()));
        assertThat(window.frame()).isEqualTo(new Rect(3, 6, 100, 60));
        windows.input(drag(-500, -500, Mods.shiftOnly()));
        assertThat(window.frame().x()).isZero();
        assertThat(windows.input(up(0, 0))).isTrue();
        assertThat(windows.input(drag(200, 200, Mods.NONE))).isFalse();
    }

    @Test
    void theGripResizesDownToTheMinimum() {
        var window = windows.open(new TestWindow("one"));
        render(-1, -1, 0);
        windows.input(down(392, 63));
        windows.input(drag(342, 43, Mods.NONE));
        assertThat(window.frame()).isEqualTo(new Rect(294, 6, 80, 40));
        windows.input(drag(900, 900, Mods.NONE));
        assertThat(window.frame().right()).isEqualTo(400);
        assertThat(window.frame().bottom()).isEqualTo(300);
        windows.input(up(900, 900));
        render(399, 299, 0);
        assertThat(canvas.fills()).isNotEmpty();
    }

    @Test
    void ctrlAndTheWheelZoomAndAPlainWheelIsSwallowed() {
        var window = windows.open(new TestWindow("one"));
        render(-1, -1, 0);
        assertThat(windows.input(new InputEvent.Scroll(340, 36, 1, Mods.ctrlOnly())))
                .isTrue();
        assertThat(window.frame().width()).isEqualTo(110);
        assertThat(window.frame().height()).isEqualTo(66);
        assertThat(window.frame().right()).isLessThanOrEqualTo(400);
        // Down a step at a time, until the minimum width stops it.
        for (int i = 0; i < 6; i++) {
            windows.input(new InputEvent.Scroll(340, 36, -1, Mods.ctrlOnly()));
        }
        assertThat(window.frame().width()).isEqualTo(80);
        assertThat(windows.input(new InputEvent.Scroll(340, 36, 1, Mods.NONE))).isTrue();
        assertThat(windows.input(new InputEvent.Scroll(5, 250, 1, Mods.NONE))).isFalse();
    }

    @Test
    void theCloseButtonClosesAndShowsItsTooltip() {
        var window = windows.open(new TestWindow("one"));
        render(-1, -1, 0);
        render(383, 13, 0);
        assertThat(canvas.tooltip())
                .hasValueSatisfying(t -> assertThat(t.lines()).containsExactly("Close"));
        windows.renderHud(canvas.clear(), new RenderContext(Themes.DARK, 0, 383, 13, false));
        assertThat(canvas.tooltip()).isEmpty();

        assertThat(windows.input(down(383, 13))).isTrue();
        assertThat(window.isClosed()).isTrue();
        assertThat(window.closedCalled).isTrue();
        assertThat(windows.isEmpty()).isTrue();
        window.close();
        window.raise();
    }

    @Test
    void closingTheWindowBeingDraggedLetsGoOfIt() {
        var window = windows.open(new TestWindow("one"));
        render(-1, -1, 0);
        windows.input(down(300, 10));
        window.close();
        assertThat(windows.input(drag(10, 10, Mods.NONE))).isFalse();
    }

    @Test
    void ticksReachOpenWindowsAndCloseAllEmptiesTheStack() {
        var one = windows.open(new TestWindow("one"));
        var two = windows.open(new TestWindow("two"));
        windows.tick();
        assertThat(one.ticks).isEqualTo(1);
        assertThat(two.ticks).isEqualTo(1);
        windows.closeAll();
        assertThat(windows.isEmpty()).isTrue();
        assertThat(one.closedCalled && two.closedCalled).isTrue();
        assertThat(windows.focused()).isEmpty();
    }

    @Test
    void aSmallerScreenPullsWindowsBackOnIt() {
        var window = windows.open(new TestWindow("one"));
        render(-1, -1, 0);
        var small = new RecordingCanvas(200, 100);
        windows.render(small, new RenderContext(Themes.DARK, 0, -1, -1, true));
        assertThat(window.frame()).isEqualTo(new Rect(100, 6, 100, 60));
        assertThat(windows.screen()).isEqualTo(new Rect(0, 0, 200, 100));
    }

    @Test
    void keysGoToTheFrontWindow() {
        assertThat(windows.input(new InputEvent.KeyDown(Key.SPACE, Mods.NONE))).isFalse();
        windows.open(new TestWindow("one"));
        assertThat(windows.input(new InputEvent.KeyDown(Key.SPACE, Mods.NONE))).isFalse();
        assertThat(windows.input(new InputEvent.CharTyped('a'))).isFalse();
        windows.cancelGestures();
    }
}
