package com.lia.mediaplayer.ui.screen;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.input.Mods;
import com.lia.mediaplayer.ui.input.PointerTracker;
import com.lia.mediaplayer.ui.theme.Theme;
import com.lia.mediaplayer.ui.widget.Constraints;
import com.lia.mediaplayer.ui.widget.Container;
import com.lia.mediaplayer.ui.widget.InputContext;
import com.lia.mediaplayer.ui.widget.RenderContext;
import com.lia.mediaplayer.ui.widget.Size;
import com.lia.mediaplayer.ui.widget.Widget;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * A full screen drawn by the toolkit. The platform shows it inside its one vanilla host
 * screen, which forwards drawing and input here and asks the questions vanilla asks (does it
 * pause the game, does Escape close it).
 *
 * <p>The screen owns one root widget filling it, laid out again when the screen changes size
 * or the tree asks for it.
 */
public abstract class UiScreen {

    private final Text title;
    private final Supplier<Theme> theme;
    private final LongSupplier nanoTime;
    private final InputContext input = new InputContext();
    private final PointerTracker pointer = new PointerTracker();
    private final Root root;
    private Rect laidOutAt = Rect.EMPTY;

    protected UiScreen(Text title, Supplier<Theme> theme, LongSupplier nanoTime, Widget content) {
        this.title = title;
        this.theme = theme;
        this.nanoTime = nanoTime;
        this.root = new Root(content);
    }

    public final Text title() {
        return title;
    }

    /** Whether opening this screen pauses a singleplayer game. */
    public boolean isPauseScreen() {
        return false;
    }

    /** Whether the media windows are drawn over this screen, live, as over the chat. */
    public boolean showsWindows() {
        return false;
    }

    /** Whether the game's blurred, darkened backdrop goes behind this screen. */
    public boolean drawsBackground() {
        return true;
    }

    /** Called when the screen is closed, by Escape or otherwise. */
    public void removed() {}

    /** Called at the start of each frame, before layout: the moment to show or hide widgets. */
    protected void beforeRender() {}

    /** Draws the screen. {@code mods} are the modifiers held now, for a drag in progress. */
    public final void render(Canvas canvas, double mouseX, double mouseY, Mods mods) {
        beforeRender();
        var area = new Rect(0, 0, canvas.width(), canvas.height());
        if (!area.equals(laidOutAt) || root.dirty) {
            root.dirty = false;
            laidOutAt = area;
            root.layout(area, new Constraints(area.width(), area.height(), canvas.measurer()));
        }
        InputEvent moved = pointer.moveTo(mouseX, mouseY, mods);
        if (moved != null) {
            dispatch(moved);
        }
        var ctx = new RenderContext(theme.get(), nanoTime.getAsLong(), mouseX, mouseY, true);
        root.render(canvas, ctx);
        if (input.captured() == null) {
            root.tooltipAt(mouseX, mouseY).ifPresent(lines -> canvas.tooltip(lines, (int) mouseX, (int) mouseY));
        }
    }

    /**
     * Input the host screen received: presses, releases, scrolls, keys and characters.
     *
     * @return whether the screen took it
     */
    public final boolean input(InputEvent event) {
        InputEvent stamped = switch (event) {
            case InputEvent.PointerDown down ->
                pointer.press(down.x(), down.y(), down.button(), down.mods(), nanoTime.getAsLong());
            case InputEvent.PointerUp up -> pointer.release(up.x(), up.y(), up.button(), up.mods());
            default -> event;
        };
        return dispatch(stamped);
    }

    private boolean dispatch(InputEvent event) {
        var captured = input.captured();
        boolean followsCapture = event instanceof InputEvent.PointerDrag || event instanceof InputEvent.PointerUp;
        if (followsCapture && captured != null) {
            captured.onInput(event, input);
            if (event instanceof InputEvent.PointerUp) {
                input.releaseCapture();
            }
            return true;
        }
        return root.onInput(event, input);
    }

    /** The root: fills the screen with the content and records when the tree asks for a layout. */
    private static final class Root extends Container {
        private final Widget content;
        private boolean dirty = true;

        Root(Widget content) {
            this.content = addChild(content);
        }

        @Override
        public void invalidate() {
            dirty = true;
        }

        @Override
        public Size measure(Constraints constraints) {
            return new Size(constraints.maxWidth(), constraints.maxHeight());
        }

        @Override
        public void layout(Rect newBounds, Constraints constraints) {
            super.layout(newBounds, constraints);
            content.layout(newBounds, constraints);
        }
    }
}
