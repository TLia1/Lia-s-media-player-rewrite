package com.lia.mediaplayer.ui.window;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.widget.Constraints;
import com.lia.mediaplayer.ui.widget.InputContext;
import com.lia.mediaplayer.ui.widget.RenderContext;
import com.lia.mediaplayer.ui.widget.Size;
import com.lia.mediaplayer.ui.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * The stack of open windows, bottom to top. It draws them in that order, each above the one
 * before, and offers pointer input top-down: the window under the cursor takes the press and
 * comes to the front. Client thread only.
 *
 * <p>A window opened before anything has been drawn waits for the first frame to be placed,
 * because only then is the size of the screen known. It opens where the last window of its kind
 * was left, if the {@link WindowMemory} knows, or else in the default corner.
 *
 * <p>A hidden window is not drawn and takes no input, but stays open: its sound goes on.
 */
// Windows and widgets are entities: "this window" always means that very instance.
@SuppressWarnings("ReferenceEquality")
public final class WindowManager {

    private final List<Window> windows = new ArrayList<>();
    private final InputContext input = new InputContext();
    private Rect screen = Rect.EMPTY;
    private WindowMemory memory = WindowMemory.NONE;
    private Supplier<Corner> corner = () -> Corner.TOP_RIGHT;

    public WindowManager() {}

    public WindowManager(WindowMemory memory, Supplier<Corner> corner) {
        this.memory = memory;
        this.corner = corner;
    }

    /** Opens {@code window} on top of the others. */
    public <W extends Window> W open(W window) {
        if (window.isClosed() || windows.contains(window)) {
            throw new IllegalArgumentException("Window already opened or closed");
        }
        window.attach(this);
        windows.add(window);
        if (!screen.isEmpty()) {
            place(window);
        }
        return window;
    }

    /** The open windows, bottom to top. */
    public List<Window> windows() {
        return List.copyOf(windows);
    }

    public boolean isEmpty() {
        return windows.isEmpty();
    }

    /** The front window that shows, which keys go to. */
    public Optional<Window> focused() {
        for (int i = windows.size() - 1; i >= 0; i--) {
            if (!windows.get(i).isHidden()) {
                return Optional.of(windows.get(i));
            }
        }
        return Optional.empty();
    }

    /** The front-most open window of {@code type}, hidden or not. */
    public <W extends Window> Optional<W> frontMost(Class<W> type) {
        for (int i = windows.size() - 1; i >= 0; i--) {
            if (type.isInstance(windows.get(i))) {
                return Optional.of(type.cast(windows.get(i)));
            }
        }
        return Optional.empty();
    }

    /** How many open windows are hidden. */
    public int hiddenCount() {
        return (int) windows.stream().filter(Window::isHidden).count();
    }

    /** Whether every window is hidden (and there is at least one). */
    public boolean allHidden() {
        return !windows.isEmpty() && windows.stream().allMatch(Window::isHidden);
    }

    /** Hides every window if any shows, else shows them all: the "hide all" key. */
    public void toggleHidden() {
        boolean anyShown = windows.stream().anyMatch(w -> !w.isHidden());
        for (Window window : windows) {
            window.setHidden(anyShown);
        }
        if (anyShown) {
            input.releaseCapture();
        }
    }

    /** Called by a window that the player moved or resized: the next of its kind opens there. */
    void remembered(Window window) {
        memory.remember(window.kind(), window.frame());
    }

    void raise(Window window) {
        if (windows.remove(window)) {
            windows.add(window);
        }
    }

    /** Called by {@link Window#close()}. */
    void detach(Window window) {
        windows.remove(window);
        Widget captured = input.captured();
        if (captured != null && isInside(captured, window)) {
            input.releaseCapture();
        }
    }

    /** Closes every window: leaving the world. */
    public void closeAll() {
        for (Window window : List.copyOf(windows)) {
            window.close();
        }
    }

    public void tick() {
        for (Window window : List.copyOf(windows)) {
            if (!window.isClosed()) {
                window.tick();
            }
        }
    }

    /** The area windows live in: the whole screen, as of the last frame. */
    public Rect screen() {
        return screen;
    }

    // ---- Drawing ------------------------------------------------------------

    /** Draws the windows with their controls live, and the tooltip under the cursor. */
    public void render(Canvas canvas, RenderContext ctx) {
        prepare(canvas, ctx);
        int count = windows.size();
        boolean first = true;
        for (int i = 0; i < count; i++) {
            Window window = windows.get(i);
            if (window.isHidden()) {
                continue;
            }
            if (!first) {
                canvas.nextLayer();
            }
            first = false;
            window.render(canvas, coveredAbove(i, ctx.pointerX(), ctx.pointerY()) ? ctx.withoutPointer() : ctx);
        }
        if (ctx.interactive() && input.captured() == null) {
            Window under = windowAt(ctx.pointerX(), ctx.pointerY());
            if (under != null) {
                Optional<List<Text>> tip = under.tooltipAt(ctx.pointerX(), ctx.pointerY());
                tip.ifPresent(lines -> canvas.tooltip(lines, (int) ctx.pointerX(), (int) ctx.pointerY()));
            }
        }
    }

    /** Draws the windows on the HUD: nothing is live, nothing is hovered. */
    public void renderHud(Canvas canvas, RenderContext ctx) {
        prepare(canvas, ctx);
        RenderContext still = ctx.withoutPointer();
        boolean first = true;
        for (Window window : windows) {
            if (window.isHidden()) {
                continue;
            }
            if (!first) {
                canvas.nextLayer();
            }
            first = false;
            window.renderHud(canvas, still);
        }
    }

    private void prepare(Canvas canvas, RenderContext ctx) {
        var now = new Rect(0, 0, canvas.width(), canvas.height());
        boolean resized = !now.equals(screen);
        screen = now;
        var constraints = new Constraints(now.width(), now.height(), canvas.measurer());
        Window front = windows.isEmpty() ? null : windows.getLast();
        for (Window window : windows) {
            if (!window.isPlaced()) {
                place(window);
            } else if (resized) {
                window.setFrame(window.frame().clampInto(now));
            }
            window.setFocused(window == front, ctx.nanos());
            window.layoutIfNeeded(constraints);
        }
    }

    private void place(Window window) {
        var others = new ArrayList<Rect>();
        for (Window other : windows) {
            if (other != window && other.isPlaced() && other.kind().equals(window.kind())) {
                others.add(other.frame());
            }
        }
        Size preferred = window.preferredSize();
        Optional<Rect> remembered = memory.frame(window.kind());
        if (remembered.isPresent()) {
            Rect last = remembered.get();
            int width = last.width() > 0 ? last.width() : preferred.width();
            int height = last.height() > 0 && window.isResizable() ? last.height() : preferred.height();
            window.setFrame(Placement.cascade(screen, new Rect(last.x(), last.y(), width, height), others));
        } else {
            window.setFrame(Placement.initial(screen, preferred, others, corner.get()));
        }
    }

    /** Whether a window is under {@code (x, y)}. */
    public boolean covers(double x, double y) {
        return windowAt(x, y) != null;
    }

    private boolean coveredAbove(int index, double x, double y) {
        for (int i = index + 1; i < windows.size(); i++) {
            if (!windows.get(i).isHidden() && windows.get(i).reaches(x, y)) {
                return true;
            }
        }
        return false;
    }

    private @Nullable Window windowAt(double x, double y) {
        for (int i = windows.size() - 1; i >= 0; i--) {
            Window window = windows.get(i);
            if (!window.isHidden() && window.reaches(x, y)) {
                return window;
            }
        }
        return null;
    }

    // ---- Input --------------------------------------------------------------

    /**
     * Offers an event to the windows.
     *
     * @return whether a window took it, in which case the screen behind must not see it
     */
    public boolean input(InputEvent event) {
        return switch (event) {
            case InputEvent.PointerDown down -> {
                Window target = windowAt(down.x(), down.y());
                if (target == null) {
                    yield false;
                }
                raise(target);
                target.onInput(event, input);
                yield true;
            }
            case InputEvent.PointerDrag drag -> forwardToCaptured(event);
            case InputEvent.PointerUp up -> {
                boolean taken = forwardToCaptured(event);
                input.releaseCapture();
                yield taken;
            }
            case InputEvent.Scroll scroll -> {
                Window target = windowAt(scroll.x(), scroll.y());
                if (target == null) {
                    yield false;
                }
                target.onInput(event, input);
                yield true;
            }
            case InputEvent.PointerMove move -> false;
            case InputEvent.KeyDown key ->
                focused().map(w -> w.onInput(event, input)).orElse(false);
            case InputEvent.CharTyped typed ->
                focused().map(w -> w.onInput(event, input)).orElse(false);
        };
    }

    private boolean forwardToCaptured(InputEvent event) {
        Widget captured = input.captured();
        if (captured == null) {
            return false;
        }
        captured.onInput(event, input);
        return true;
    }

    /** A new screen opened: whatever was being dragged on the old one is let go. */
    public void cancelGestures() {
        input.releaseCapture();
    }

    // ---- Snapping -----------------------------------------------------------

    /**
     * Where {@code moving} lands if dragged to {@code proposed}: pulled onto the screen edges
     * and centre and onto the other windows' edges and centres, unless {@code free}.
     */
    Rect snap(Window moving, Rect proposed, boolean free) {
        if (free) {
            return proposed;
        }
        var xs = new ArrayList<Integer>();
        var ys = new ArrayList<Integer>();
        xs.add(screen.x());
        xs.add(screen.right());
        xs.add(screen.centerX());
        ys.add(screen.y());
        ys.add(screen.bottom());
        ys.add(screen.centerY());
        for (Window other : windows) {
            if (other == moving || other.isHidden()) {
                continue;
            }
            Rect r = other.frame();
            xs.add(r.x());
            xs.add(r.right());
            xs.add(r.centerX());
            ys.add(r.y());
            ys.add(r.bottom());
            ys.add(r.centerY());
        }
        int x = Snap.axis(proposed.x(), proposed.width(), toArray(xs), Snap.THRESHOLD);
        int y = Snap.axis(proposed.y(), proposed.height(), toArray(ys), Snap.THRESHOLD);
        return new Rect(x, y, proposed.width(), proposed.height());
    }

    private static int[] toArray(List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).toArray();
    }

    private static boolean isInside(Widget widget, Window window) {
        for (Widget w = widget; w != null; w = w.parent()) {
            if (w == window) {
                return true;
            }
        }
        return false;
    }
}
