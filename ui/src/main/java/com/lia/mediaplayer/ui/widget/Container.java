package com.lia.mediaplayer.ui.widget;

import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.input.InputEvent;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A widget made of others. Children draw in the order they were added, so later ones are on
 * top, and are offered pointer events the other way round: topmost first. Keys and characters
 * go to each child in turn until one takes them.
 *
 * <p>Drags and releases are not routed here: they go to whichever widget captured the press
 * ({@link InputContext}).
 */
public abstract class Container extends Widget {

    private final List<Widget> children = new ArrayList<>();

    protected <W extends Widget> W addChild(W child) {
        if (child.parent() != null) {
            throw new IllegalArgumentException("Widget already has a parent");
        }
        child.setParent(this);
        children.add(child);
        invalidate();
        return child;
    }

    protected void removeChild(Widget child) {
        if (children.remove(child)) {
            child.setParent(null);
            invalidate();
        }
    }

    public final List<Widget> children() {
        return List.copyOf(children);
    }

    /** The children in drawing order, without a copy. For subclasses that lay them out. */
    protected final List<Widget> childList() {
        return children;
    }

    @Override
    public void render(Canvas canvas, RenderContext ctx) {
        for (Widget child : children) {
            if (child.isVisible()) {
                child.render(canvas, ctx);
            }
        }
    }

    @Override
    public boolean onInput(InputEvent event, InputContext ctx) {
        return switch (event) {
            case InputEvent.PointerDown down -> offerAt(event, down.x(), down.y(), ctx);
            case InputEvent.Scroll scroll -> offerAt(event, scroll.x(), scroll.y(), ctx);
            case InputEvent.KeyDown key -> offerAll(event, ctx);
            case InputEvent.CharTyped typed -> offerAll(event, ctx);
            case InputEvent.PointerMove move -> false;
            case InputEvent.PointerDrag drag -> false;
            case InputEvent.PointerUp up -> false;
        };
    }

    private boolean offerAt(InputEvent event, double x, double y, InputContext ctx) {
        for (int i = children.size() - 1; i >= 0; i--) {
            Widget child = children.get(i);
            if (child.isVisible() && child.bounds().contains(x, y) && child.onInput(event, ctx)) {
                return true;
            }
        }
        return false;
    }

    private boolean offerAll(InputEvent event, InputContext ctx) {
        for (Widget child : children) {
            if (child.isVisible() && child.onInput(event, ctx)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public @Nullable Widget widgetAt(double x, double y) {
        if (!isVisible() || !bounds().contains(x, y)) {
            return null;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            Widget hit = children.get(i).widgetAt(x, y);
            if (hit != null) {
                return hit;
            }
        }
        return this;
    }
}
