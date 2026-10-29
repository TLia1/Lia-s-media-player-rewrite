package com.lia.mediaplayer.ui.widget;

import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.input.InputEvent;
import org.jspecify.annotations.Nullable;

/**
 * Shows a widget taller than the room it has, a slice at a time: the wheel and the scroll bar
 * move it. The content is laid out at its full height and drawn clipped. For a few dozen rows
 * of different kinds (the settings); a long list of alike rows is a {@link ListView}.
 *
 * <p>While the content does not fit, the wheel scrolls it rather than reaching the controls
 * under the cursor: a slider passing under the cursor must not catch a scroll meant for the
 * page.
 */
public final class ScrollPane extends Container {

    private static final int WHEEL_STEP = 20;

    private final Widget content;
    private double scroll;
    private int contentHeight;
    private boolean draggingThumb;
    private @Nullable Constraints laidOutWith;

    public ScrollPane(Widget content) {
        this.content = addChild(content);
    }

    public double scroll() {
        return scroll;
    }

    @Override
    public Size measure(Constraints constraints) {
        return new Size(
                constraints.maxWidth(),
                Math.min(constraints.maxHeight(), content.measure(constraints).height()));
    }

    @Override
    public void layout(Rect newBounds, Constraints constraints) {
        super.layout(newBounds, constraints);
        laidOutWith = constraints;
        placeContent();
    }

    private void placeContent() {
        Constraints constraints = laidOutWith;
        if (constraints == null) {
            return;
        }
        Rect view = bounds();
        int width = view.width() - Scrollbar.WIDTH - 1;
        contentHeight = content.measure(constraints.withMax(width, Integer.MAX_VALUE / 2))
                .height();
        scroll = Scrollbar.clamp(scroll, view, contentHeight);
        content.layout(
                new Rect(view.x(), view.y() - (int) Math.round(scroll), width, contentHeight),
                constraints.withMax(width, contentHeight));
    }

    /** Scrolls so the content's {@code y} offset is at the top, as far as it goes. */
    public void scrollTo(double offset) {
        scroll = Scrollbar.clamp(offset, bounds(), contentHeight);
        placeContent();
    }

    @Override
    public void render(Canvas canvas, RenderContext ctx) {
        Rect view = bounds();
        canvas.pushClip(view);
        RenderContext inner = ctx.hovered(view) ? ctx : ctx.withoutPointer();
        content.render(canvas, inner);
        canvas.popClip();
        Scrollbar.draw(canvas, ctx, view, contentHeight, scroll);
    }

    @Override
    public boolean onInput(InputEvent event, InputContext ctx) {
        Rect view = bounds();
        boolean scrollable = contentHeight > view.height();
        return switch (event) {
            case InputEvent.Scroll wheel
            when scrollable -> {
                scrollTo(scroll - Math.signum(wheel.delta()) * WHEEL_STEP);
                yield true;
            }
            case InputEvent.PointerDown down
            when scrollable && Scrollbar.track(view).contains(down.x(), down.y()) -> {
                draggingThumb = true;
                ctx.capture(this);
                scrollTo(Scrollbar.scrollFor(view, contentHeight, down.y()));
                yield true;
            }
            case InputEvent.PointerDrag drag
            when draggingThumb -> {
                scrollTo(Scrollbar.scrollFor(view, contentHeight, drag.y()));
                yield true;
            }
            case InputEvent.PointerUp up
            when draggingThumb -> {
                draggingThumb = false;
                ctx.releaseCapture();
                yield true;
            }
            default -> super.onInput(event, ctx);
        };
    }
}
