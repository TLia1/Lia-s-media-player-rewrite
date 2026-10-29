package com.lia.mediaplayer.ui.widget;

import com.lia.mediaplayer.ui.canvas.Rect;
import java.util.IdentityHashMap;

/**
 * Lays children out one after another along an axis: {@link Row} across, {@link Column} down.
 *
 * <p>A child added with a flex weight shares the room left once the fixed children have
 * their preferred size, in proportion to its weight. Across the axis every child is centred,
 * or stretched when added with {@code stretch}.
 */
public abstract sealed class Linear extends Container permits Row, Column {

    private record Slot(int flex, boolean stretch) {}

    private final boolean horizontal;
    // Keyed by the widget instance: widgets are entities, never equal to another one.
    private final IdentityHashMap<Widget, Slot> slots = new IdentityHashMap<>();
    private int gap;
    private int padMain;
    private int padCross;

    Linear(boolean horizontal) {
        this.horizontal = horizontal;
    }

    /** Adds a child at its preferred size. */
    public <W extends Widget> W add(W child) {
        return add(child, 0, false);
    }

    /** Adds a child that takes a share of the spare room, by {@code flex} weight. */
    public <W extends Widget> W add(W child, int flex) {
        return add(child, flex, true);
    }

    public <W extends Widget> W add(W child, int flex, boolean stretch) {
        if (flex < 0) {
            throw new IllegalArgumentException("flex must not be negative");
        }
        slots.put(child, new Slot(flex, stretch));
        return addChild(child);
    }

    public Linear gap(int pixels) {
        gap = pixels;
        invalidate();
        return this;
    }

    /** Padding at both ends of the axis and on both sides across it. */
    public Linear padding(int main, int cross) {
        padMain = main;
        padCross = cross;
        invalidate();
        return this;
    }

    private Slot slot(Widget child) {
        Slot slot = slots.get(child);
        return slot == null ? new Slot(0, false) : slot;
    }

    @Override
    public Size measure(Constraints constraints) {
        int main = 2 * padMain;
        int cross = 0;
        int shown = 0;
        for (Widget child : childList()) {
            if (!child.isVisible()) {
                continue;
            }
            Size size = child.measure(constraints);
            main += along(size);
            cross = Math.max(cross, across(size));
            shown++;
        }
        main += Math.max(0, shown - 1) * gap;
        cross += 2 * padCross;
        return horizontal ? new Size(main, cross) : new Size(cross, main);
    }

    @Override
    public void layout(Rect newBounds, Constraints constraints) {
        super.layout(newBounds, constraints);
        Rect inner = horizontal
                ? newBounds.inset(padMain, padCross, padMain, padCross)
                : newBounds.inset(padCross, padMain, padCross, padMain);
        int available = horizontal ? inner.width() : inner.height();
        int crossRoom = horizontal ? inner.height() : inner.width();

        int fixed = 0;
        int flexTotal = 0;
        int shown = 0;
        int count = childList().size();
        int[] sizes = new int[count];
        for (int i = 0; i < count; i++) {
            Widget child = childList().get(i);
            if (!child.isVisible()) {
                continue;
            }
            shown++;
            Slot slot = slot(child);
            if (slot.flex() > 0) {
                flexTotal += slot.flex();
            } else {
                sizes[i] = along(child.measure(constraints.withMax(inner.width(), inner.height())));
                fixed += sizes[i];
            }
        }
        int spare = Math.max(0, available - fixed - Math.max(0, shown - 1) * gap);
        int given = 0;
        int flexSeen = 0;
        for (int i = 0; i < count; i++) {
            Widget child = childList().get(i);
            Slot slot = slot(child);
            if (child.isVisible() && slot.flex() > 0) {
                flexSeen += slot.flex();
                // Cumulative rounding, so the shares add up to exactly the spare room.
                int upTo = (int) ((long) spare * flexSeen / flexTotal);
                sizes[i] = upTo - given;
                given = upTo;
            }
        }

        int at = horizontal ? inner.x() : inner.y();
        for (int i = 0; i < count; i++) {
            Widget child = childList().get(i);
            if (!child.isVisible()) {
                continue;
            }
            Slot slot = slot(child);
            int crossSize = slot.stretch()
                    ? crossRoom
                    : Math.min(crossRoom, across(child.measure(constraints.withMax(inner.width(), inner.height()))));
            int crossAt = (horizontal ? inner.y() : inner.x()) + (crossRoom - crossSize) / 2;
            Rect rect = horizontal
                    ? new Rect(at, crossAt, sizes[i], crossSize)
                    : new Rect(crossAt, at, crossSize, sizes[i]);
            child.layout(rect, constraints);
            at += sizes[i] + gap;
        }
    }

    private int along(Size size) {
        return horizontal ? size.width() : size.height();
    }

    private int across(Size size) {
        return horizontal ? size.height() : size.width();
    }
}
