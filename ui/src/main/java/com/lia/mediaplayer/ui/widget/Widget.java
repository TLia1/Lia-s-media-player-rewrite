package com.lia.mediaplayer.ui.widget;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.input.InputEvent;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * One element of the retained-mode toolkit. A widget {@linkplain #measure says how big it
 * would like to be}, is {@linkplain #layout given its bounds} by its container, draws itself
 * inside them, and is offered input over them.
 *
 * <p>Layout runs only when something {@linkplain #invalidate asked for it} — a content change,
 * a resize — never every frame; {@link #render} only draws. Client thread only.
 */
public abstract class Widget {

    private @Nullable Widget parent;
    private Rect bounds = Rect.EMPTY;
    private boolean visible = true;
    private @Nullable Text accessibleName;

    /** The size this widget would like within {@code constraints}. */
    public abstract Size measure(Constraints constraints);

    /**
     * Places the widget. Containers override this to measure and place their children too,
     * with {@code constraints} for the font.
     */
    public void layout(Rect newBounds, Constraints constraints) {
        setBounds(newBounds);
    }

    /** Moves the widget without laying anything out: for a widget that is its own layout root. */
    protected final void setBounds(Rect newBounds) {
        bounds = newBounds;
    }

    public abstract void render(Canvas canvas, RenderContext ctx);

    /**
     * Offers an event to this widget.
     *
     * @return whether it was consumed; nothing else sees a consumed event
     */
    public boolean onInput(InputEvent event, InputContext ctx) {
        return false;
    }

    /** The tooltip to show with the cursor at {@code (x, y)}, which is inside this widget. */
    public Optional<List<Text>> tooltip(double x, double y) {
        return Optional.empty();
    }

    /** The deepest visible widget at {@code (x, y)}, or {@code null} if it is outside this one. */
    public @Nullable Widget widgetAt(double x, double y) {
        return visible && bounds.contains(x, y) ? this : null;
    }

    /** The tooltip of the widget at {@code (x, y)} or of the nearest of its ancestors that has one. */
    @SuppressWarnings("ReferenceEquality") // walking up to this very widget
    public final Optional<List<Text>> tooltipAt(double x, double y) {
        for (Widget w = widgetAt(x, y); w != null; w = w == this ? null : w.parent) {
            Optional<List<Text>> tip = w.tooltip(x, y);
            if (tip.isPresent()) {
                return tip;
            }
        }
        return Optional.empty();
    }

    /** Asks for a new layout before the next frame: the size this widget wants has changed. */
    public void invalidate() {
        Widget p = parent;
        if (p != null) {
            p.invalidate();
        }
    }

    public final Rect bounds() {
        return bounds;
    }

    public final boolean isVisible() {
        return visible;
    }

    /** A hidden widget takes no room, draws nothing and gets no input. */
    public final void setVisible(boolean newVisible) {
        if (visible != newVisible) {
            visible = newVisible;
            invalidate();
        }
    }

    public final @Nullable Widget parent() {
        return parent;
    }

    final void setParent(@Nullable Widget newParent) {
        parent = newParent;
    }

    /** What a screen reader would call this widget, once narration exists. */
    public final Optional<Text> accessibleName() {
        return Optional.ofNullable(accessibleName);
    }

    public final void setAccessibleName(Text name) {
        accessibleName = name;
    }
}
