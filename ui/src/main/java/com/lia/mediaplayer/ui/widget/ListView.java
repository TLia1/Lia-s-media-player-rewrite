package com.lia.mediaplayer.ui.widget;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.theme.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * A long list of alike rows, of which only those on screen exist: a handful of {@link Row}
 * widgets are made once and bound in turn to whichever items scroll into view. A playlist of
 * five hundred tracks costs the same as one of ten, and a row looks its title up only once it
 * shows.
 *
 * <p>The wheel and the scroll bar scroll it. A press on a row that none of its controls takes
 * {@linkplain #onActivate activates} the row (a double click carries a click count of 2).
 * When {@linkplain #reorderable reordering} is on, dragging a row moves it: a line shows the
 * gap it would drop into, and holding it near the top or bottom edge scrolls.
 *
 * @param <T> what an item is
 */
public final class ListView<T> extends Container {

    /** One row: its widgets, shown for whichever item it is bound to. */
    public abstract static class Row<T> extends Container {

        /** Shows {@code item}, which sits at {@code index} in the list. */
        protected abstract void bind(T item, int index);
    }

    /** What a press on a row means. */
    @FunctionalInterface
    public interface ActivateListener {
        void activated(int index, InputEvent.PointerDown press);
    }

    /** A row dragged from {@code from} and dropped in the gap before {@code insertBefore}. */
    @FunctionalInterface
    public interface MoveListener {
        void moved(int from, int insertBefore);
    }

    static final int DRAG_SLOP = 3;
    private static final int EDGE = 10;
    private static final long EDGE_SCROLL_NANOS = 100_000_000L;

    private final int rowHeight;
    private final Supplier<? extends Row<T>> rows;
    private final List<Row<T>> pool = new ArrayList<>();
    private final List<@Nullable Object> boundItems = new ArrayList<>();
    private final List<Integer> boundIndexes = new ArrayList<>();
    private List<T> items = List.of();
    private double scroll;
    private @Nullable Constraints laidOutWith;
    private IntPredicate selected = index -> false;
    private ActivateListener onActivate = (index, press) -> {};
    private @Nullable MoveListener onMove;
    private BooleanSupplier reorderEnabled = () -> true;
    private Text emptyTitle = Text.literal("");
    private Text emptyHint = Text.literal("");
    private boolean draggingThumb;
    private int pressed = -1;
    private double pressY;
    private boolean dragging;
    private double dragY;
    private long lastEdgeScroll;

    public ListView(int rowHeight, Supplier<? extends Row<T>> rows) {
        if (rowHeight < 1) {
            throw new IllegalArgumentException("rowHeight must be positive");
        }
        this.rowHeight = rowHeight;
        this.rows = rows;
    }

    public ListView<T> selected(IntPredicate isSelected) {
        selected = isSelected;
        return this;
    }

    public ListView<T> onActivate(ActivateListener listener) {
        onActivate = listener;
        return this;
    }

    /** Lets rows be dragged to a new place, while {@code enabled} says so (not while a search filters them). */
    public ListView<T> reorderable(MoveListener listener, BooleanSupplier enabled) {
        onMove = listener;
        reorderEnabled = enabled;
        return this;
    }

    /** What an empty list says, centred: a line and a hint under it. */
    public ListView<T> whenEmpty(Text title, Text hint) {
        emptyTitle = title;
        emptyHint = hint;
        return this;
    }

    public List<T> items() {
        return items;
    }

    /** Replaces the items. Rows bound to an item that did not change keep it. */
    public void setItems(List<T> newItems) {
        if (newItems.equals(items)) {
            return;
        }
        items = List.copyOf(newItems);
        placeRows();
    }

    public double scroll() {
        return scroll;
    }

    public void scrollTo(double offset) {
        scroll = Scrollbar.clamp(offset, bounds(), contentHeight());
        placeRows();
    }

    /** Scrolls the least that shows the item at {@code index}. */
    public void reveal(int index) {
        int top = index * rowHeight;
        if (top < scroll) {
            scrollTo(top);
        } else if (top + rowHeight > scroll + bounds().height()) {
            scrollTo(top + rowHeight - bounds().height());
        }
    }

    private int contentHeight() {
        return items.size() * rowHeight;
    }

    // ---- Layout -------------------------------------------------------------

    @Override
    public Size measure(Constraints constraints) {
        return new Size(constraints.maxWidth(), Math.min(constraints.maxHeight(), contentHeight()));
    }

    @Override
    public void layout(Rect newBounds, Constraints constraints) {
        super.layout(newBounds, constraints);
        laidOutWith = constraints;
        int needed = newBounds.height() / rowHeight + 2;
        while (pool.size() < needed) {
            pool.add(addChild(rows.get()));
            boundItems.add(null);
            boundIndexes.add(-1);
        }
        placeRows();
    }

    private void placeRows() {
        Constraints constraints = laidOutWith;
        if (constraints == null) {
            return;
        }
        Rect view = bounds();
        scroll = Scrollbar.clamp(scroll, view, contentHeight());
        int width = view.width() - (contentHeight() > view.height() ? Scrollbar.WIDTH + 1 : 0);
        int first = (int) (scroll / rowHeight);
        for (int i = 0; i < pool.size(); i++) {
            Row<T> row = pool.get(i);
            int index = first + i;
            if (index >= items.size()) {
                row.layout(Rect.EMPTY, constraints);
                boundIndexes.set(i, -1);
                continue;
            }
            T item = items.get(index);
            if (boundIndexes.get(i) != index || !item.equals(boundItems.get(i))) {
                row.bind(item, index);
                boundItems.set(i, item);
                boundIndexes.set(i, index);
            }
            int y = view.y() + index * rowHeight - (int) Math.round(scroll);
            row.layout(new Rect(view.x(), y, width, rowHeight - 1), constraints.withMax(width, rowHeight - 1));
        }
    }

    // ---- Drawing ------------------------------------------------------------

    @Override
    public void render(Canvas canvas, RenderContext ctx) {
        Rect view = bounds();
        if (items.isEmpty()) {
            drawEmpty(canvas, ctx, view);
            return;
        }
        if (dragging) {
            scrollAtEdge(ctx.nanos());
        }
        canvas.pushClip(view);
        boolean inside = ctx.hovered(view);
        RenderContext inner = inside ? ctx : ctx.withoutPointer();
        for (int i = 0; i < pool.size(); i++) {
            Row<T> row = pool.get(i);
            int index = boundIndexes.get(i);
            if (index < 0 || row.bounds().isEmpty()) {
                continue;
            }
            Role background = selected.test(index) || (dragging && index == pressed)
                    ? Role.ROW_SELECTED
                    : !dragging && inner.hovered(row.bounds()) ? Role.ROW_HOVER : Role.ROW_BG;
            canvas.fill(row.bounds(), ctx.color(background));
            row.render(canvas, inner);
        }
        if (dragging) {
            int gap = dropGap();
            int y = view.y() + gap * rowHeight - (int) Math.round(scroll) - 1;
            canvas.fill(view.x(), y - 1, view.right() - Scrollbar.WIDTH, y + 1, ctx.color(Role.FILL));
        }
        canvas.popClip();
        Scrollbar.draw(canvas, ctx, view, contentHeight(), scroll);
    }

    private void drawEmpty(Canvas canvas, RenderContext ctx, Rect view) {
        var font = canvas.measurer();
        int y = view.centerY() - font.lineHeight();
        canvas.text(emptyTitle, view.centerX() - font.width(emptyTitle) / 2, y, ctx.color(Role.TEXT_SUBTLE), false);
        canvas.text(
                emptyHint,
                view.centerX() - font.width(emptyHint) / 2,
                y + font.lineHeight() + 3,
                ctx.color(Role.TEXT_DIM),
                false);
    }

    private void scrollAtEdge(long nanos) {
        if (nanos - lastEdgeScroll < EDGE_SCROLL_NANOS) {
            return;
        }
        Rect view = bounds();
        if (dragY < view.y() + EDGE) {
            lastEdgeScroll = nanos;
            scrollTo(scroll - rowHeight / 2.0);
        } else if (dragY > view.bottom() - EDGE) {
            lastEdgeScroll = nanos;
            scrollTo(scroll + rowHeight / 2.0);
        }
    }

    /** The gap the dragged row would drop into, {@code 0..size}. */
    int dropGap() {
        double local = dragY - bounds().y() + scroll;
        return Math.max(0, Math.min(items.size(), (int) Math.round(local / rowHeight)));
    }

    // ---- Input --------------------------------------------------------------

    @Override
    public boolean onInput(InputEvent event, InputContext ctx) {
        Rect view = bounds();
        return switch (event) {
            case InputEvent.Scroll wheel -> {
                if (contentHeight() > view.height()) {
                    scrollTo(scroll - Math.signum(wheel.delta()) * rowHeight * 2);
                }
                yield true;
            }
            case InputEvent.PointerDown down -> press(down, ctx);
            case InputEvent.PointerDrag drag -> {
                if (draggingThumb) {
                    scrollTo(Scrollbar.scrollFor(view, contentHeight(), drag.y()));
                } else if (pressed >= 0) {
                    dragY = drag.y();
                    dragging = dragging || Math.abs(drag.y() - pressY) > DRAG_SLOP;
                }
                yield true;
            }
            case InputEvent.PointerUp up -> {
                if (dragging) {
                    MoveListener listener = onMove;
                    if (listener != null) {
                        listener.moved(pressed, dropGap());
                    }
                }
                draggingThumb = false;
                dragging = false;
                pressed = -1;
                ctx.releaseCapture();
                yield true;
            }
            default -> super.onInput(event, ctx);
        };
    }

    private boolean press(InputEvent.PointerDown down, InputContext ctx) {
        Rect view = bounds();
        if (contentHeight() > view.height() && Scrollbar.track(view).contains(down.x(), down.y())) {
            draggingThumb = true;
            ctx.capture(this);
            scrollTo(Scrollbar.scrollFor(view, contentHeight(), down.y()));
            return true;
        }
        if (super.onInput(down, ctx)) {
            return true;
        }
        int index = (int) ((down.y() - view.y() + scroll) / rowHeight);
        if (index < 0 || index >= items.size()) {
            return true;
        }
        onActivate.activated(index, down);
        if (onMove != null && reorderEnabled.getAsBoolean() && down.button() == InputEvent.PRIMARY_BUTTON) {
            pressed = index;
            pressY = down.y();
            dragY = down.y();
            ctx.capture(this);
        }
        return true;
    }

    /** Whether a row is being dragged. */
    public boolean isDragging() {
        return dragging;
    }
}
