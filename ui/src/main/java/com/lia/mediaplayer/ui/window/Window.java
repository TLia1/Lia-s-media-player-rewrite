package com.lia.mediaplayer.ui.window;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.ui.anim.Animation;
import com.lia.mediaplayer.ui.anim.Easing;
import com.lia.mediaplayer.ui.canvas.Argb;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.theme.Role;
import com.lia.mediaplayer.ui.widget.Column;
import com.lia.mediaplayer.ui.widget.Constraints;
import com.lia.mediaplayer.ui.widget.Container;
import com.lia.mediaplayer.ui.widget.IconButton;
import com.lia.mediaplayer.ui.widget.InputContext;
import com.lia.mediaplayer.ui.widget.Label;
import com.lia.mediaplayer.ui.widget.RenderContext;
import com.lia.mediaplayer.ui.widget.Row;
import com.lia.mediaplayer.ui.widget.Size;
import com.lia.mediaplayer.ui.widget.Widget;
import org.jspecify.annotations.Nullable;

/**
 * A floating window: a title strip with the title and corner buttons, a body the subclass
 * provides, a one-pixel edge that lights up on the front window, and a resize grip.
 *
 * <p>Pressing anywhere no control takes and dragging moves the window, snapping to the
 * screen and to other windows unless Shift is held. Dragging the grip in the bottom-right
 * corner resizes it; Ctrl (Cmd on macOS) and the wheel zoom it about the cursor.
 *
 * <p>A window is the root of its own widget tree: it lays the tree out again only when
 * something in it asked for it, or when it moved or changed size.
 */
public abstract class Window extends Container {

    /** Height of the title strip. */
    public static final int TITLE_BAR = 13;
    /** Side of the resize grip's square in the bottom-right corner. */
    public static final int GRIP = 7;
    /** How much one notch of Ctrl+wheel scales a window. */
    static final double ZOOM_STEP = 1.1;

    private static final long FOCUS_FADE_NANOS = 150_000_000L;

    private enum Gesture {
        NONE,
        MOVE,
        RESIZE
    }

    private final Column column = new Column();
    private final Row titleBar = new Row();
    private final Label titleLabel;
    private final Animation focus = new Animation(0, FOCUS_FADE_NANOS, Easing.OUT_CUBIC);
    private @Nullable WindowManager manager;
    private boolean placed;
    private boolean layoutDirty = true;
    private boolean closed;
    private boolean hidden;
    private Gesture gesture = Gesture.NONE;
    private double grabX;
    private double grabY;
    private Rect grabFrame = Rect.EMPTY;

    // Adding the column calls invalidate(), which is final here and only sets a flag.
    @SuppressWarnings("this-escape")
    protected Window(Text title) {
        titleBar.padding(3, 0).gap(1);
        titleLabel = titleBar.add(new Label(title, Role.TEXT), 1, false);
        column.add(titleBar, 0, true);
        addChild(column);
    }

    /** The size a new window opens at. */
    public abstract Size preferredSize();

    /** The smallest it may be resized to. */
    public Size minimumSize() {
        return new Size(80, TITLE_BAR + 20);
    }

    public boolean isResizable() {
        return true;
    }

    /** Whether the wheel alone zooms, for a window with nothing else to scroll. Ctrl+wheel always does. */
    protected boolean wheelZooms() {
        return false;
    }

    /** What kind of window this is, for placement: windows of a kind cascade together. */
    public abstract String kind();

    /** Whether a frame the player gave this window is worth opening the next one at: not a theatre frame. */
    protected boolean remembersFrame() {
        return true;
    }

    /** Called once a client tick while the window is open. */
    public void tick() {}

    /** Called once, after the window has left the stack: release what it owns. */
    protected void onClosed() {}

    /**
     * Called at the start of every frame, before layout: the moment to read the state the frame
     * will show and update widgets from it, so a widget that appears is laid out before it draws.
     */
    protected void beforeLayout() {}

    /** Adds a button to the title strip, right of the title, in the order added. */
    protected final IconButton addTitleButton(IconButton button) {
        return titleBar.add(button);
    }

    /** Sets the body below the title strip. Call once, from the subclass constructor. */
    protected final <W extends Widget> W setBody(W body) {
        return column.add(body, 1);
    }

    protected final void setTitle(Text title) {
        titleLabel.setText(title);
    }

    public final Text title() {
        return titleLabel.text();
    }

    /** Hides the title strip, for a window that draws its own title. */
    protected final void setTitleBarVisible(boolean visible) {
        titleBar.setVisible(visible);
    }

    // ---- Geometry -----------------------------------------------------------

    public final Rect frame() {
        return bounds();
    }

    /** The area windows live in, as of the last frame; empty before the first. */
    protected final Rect screen() {
        WindowManager owner = manager;
        return owner == null ? Rect.EMPTY : owner.screen();
    }

    public final boolean isPlaced() {
        return placed;
    }

    /** Moves and sizes the window. It is laid out again before it next draws. */
    public final void setFrame(Rect frame) {
        Size min = minimumSize();
        Rect sized = frame.withSize(Math.max(min.width(), frame.width()), Math.max(min.height(), frame.height()));
        if (!sized.equals(bounds()) || !placed) {
            placed = true;
            layoutDirty = true;
            setBounds(sized);
        }
    }

    @Override
    public final void invalidate() {
        layoutDirty = true;
    }

    @Override
    public final Size measure(Constraints constraints) {
        return preferredSize();
    }

    @Override
    public final void layout(Rect newBounds, Constraints constraints) {
        setFrame(newBounds);
    }

    /** Refreshes the window's state and lays it out again if anything asked for it. Before each draw. */
    public final void layoutIfNeeded(Constraints constraints) {
        beforeLayout();
        if (!layoutDirty) {
            return;
        }
        layoutDirty = false;
        column.layout(bounds().inset(1), constraints.withMax(bounds().width(), bounds().height()));
    }

    // ---- Drawing ------------------------------------------------------------

    @Override
    public void render(Canvas canvas, RenderContext ctx) {
        Rect b = bounds();
        canvas.fill(b, ctx.color(Role.WINDOW_BG));
        if (titleBar.isVisible()) {
            canvas.fill(titleBar.bounds(), ctx.color(Role.TITLE_BAR_BG));
        }
        super.render(canvas, ctx);
        float lit = focus.value(ctx.nanos());
        canvas.outline(b, Argb.mix(ctx.color(Role.BORDER), ctx.color(Role.BORDER_FOCUSED), lit));
        if (isResizable() && ctx.interactive() && (gesture == Gesture.RESIZE || ctx.hovered(b))) {
            drawGrip(canvas, ctx.color(lit > 0.5f ? Role.BORDER_FOCUSED : Role.BORDER));
        }
    }

    /**
     * Draws the window on the HUD, where nothing can be clicked: the whole window by default.
     * A video window overrides this to show its picture only.
     */
    public void renderHud(Canvas canvas, RenderContext ctx) {
        render(canvas, ctx);
    }

    private void drawGrip(Canvas canvas, int color) {
        Rect b = bounds();
        for (int i = 0; i < 3; i++) {
            int x = b.right() - 3 - i * 2;
            for (int j = 0; j <= i; j++) {
                int y = b.bottom() - 3 - j * 2;
                canvas.fill(x, y - 2 * (i - j), x + 1, y - 2 * (i - j) + 1, color);
            }
        }
    }

    final void setFocused(boolean focused, long nanos) {
        focus.animateTo(focused ? 1 : 0, nanos);
    }

    // ---- Input --------------------------------------------------------------

    @Override
    public boolean onInput(InputEvent event, InputContext ctx) {
        return switch (event) {
            case InputEvent.PointerDown down -> {
                if (down.button() == InputEvent.PRIMARY_BUTTON && isResizable() && onGrip(down.x(), down.y())) {
                    beginGesture(Gesture.RESIZE, down.x(), down.y(), ctx);
                } else if (!super.onInput(event, ctx) && down.button() == InputEvent.PRIMARY_BUTTON) {
                    beginGesture(Gesture.MOVE, down.x(), down.y(), ctx);
                }
                yield true;
            }
            case InputEvent.PointerDrag drag -> {
                if (ctx.isCaptured(this)) {
                    continueGesture(drag);
                }
                yield true;
            }
            case InputEvent.PointerUp up -> {
                if (ctx.isCaptured(this)) {
                    boolean changed = !bounds().equals(grabFrame);
                    gesture = Gesture.NONE;
                    ctx.releaseCapture();
                    if (changed) {
                        remember();
                    }
                }
                yield true;
            }
            case InputEvent.Scroll scroll -> {
                if ((scroll.mods().primary() || wheelZooms()) && isResizable()) {
                    zoom(scroll.x(), scroll.y(), Math.pow(ZOOM_STEP, Math.signum(scroll.delta())));
                } else {
                    super.onInput(event, ctx);
                }
                // A wheel over a window never scrolls the chat behind it.
                yield true;
            }
            default -> super.onInput(event, ctx);
        };
    }

    boolean onGrip(double x, double y) {
        Rect b = bounds();
        return x >= b.right() - GRIP && y >= b.bottom() - GRIP && b.contains(x, y);
    }

    private void beginGesture(Gesture kind, double x, double y, InputContext ctx) {
        gesture = kind;
        grabX = x;
        grabY = y;
        grabFrame = bounds();
        ctx.capture(this);
    }

    private void continueGesture(InputEvent.PointerDrag drag) {
        int dx = (int) Math.round(drag.x() - grabX);
        int dy = (int) Math.round(drag.y() - grabY);
        WindowManager owner = manager;
        Rect screen = owner == null ? Rect.EMPTY : owner.screen();
        switch (gesture) {
            case MOVE -> {
                Rect moved = grabFrame.translate(dx, dy);
                if (owner != null) {
                    moved = owner.snap(this, moved, drag.mods().shift()).clampInto(screen);
                }
                setFrame(moved);
            }
            case RESIZE -> {
                int width = grabFrame.width() + dx;
                int height = grabFrame.height() + dy;
                if (!screen.isEmpty()) {
                    width = Math.min(width, screen.right() - grabFrame.x());
                    height = Math.min(height, screen.bottom() - grabFrame.y());
                }
                setFrame(grabFrame.withSize(width, height));
            }
            case NONE -> {
                // A drag without a press on this window: nothing to do.
            }
        }
    }

    /** Scales the window by {@code factor} about {@code (x, y)}, keeping its shape. */
    void zoom(double x, double y, double factor) {
        Rect b = bounds();
        Size min = minimumSize();
        WindowManager owner = manager;
        Rect screen = owner == null ? Rect.EMPTY : owner.screen();
        double scale = factor;
        scale = Math.max(scale, Math.max((double) min.width() / b.width(), (double) min.height() / b.height()));
        if (!screen.isEmpty()) {
            scale = Math.min(
                    scale, Math.min((double) screen.width() / b.width(), (double) screen.height() / b.height()));
        }
        int width = (int) Math.round(b.width() * scale);
        int height = (int) Math.round(b.height() * scale);
        int nx = (int) Math.round(x - (x - b.x()) * width / (double) b.width());
        int ny = (int) Math.round(y - (y - b.y()) * height / (double) b.height());
        Rect zoomed = new Rect(nx, ny, width, height);
        setFrame(screen.isEmpty() ? zoomed : zoomed.clampInto(screen));
        remember();
    }

    private void remember() {
        WindowManager owner = manager;
        if (owner != null && remembersFrame()) {
            owner.remembered(this);
        }
    }

    // ---- Lifecycle ----------------------------------------------------------

    final void attach(WindowManager owner) {
        manager = owner;
    }

    public final boolean isClosed() {
        return closed;
    }

    /** Takes the window off the screen and releases what it owns. Closing twice is a no-op. */
    public final void close() {
        if (closed) {
            return;
        }
        closed = true;
        WindowManager owner = manager;
        manager = null;
        if (owner != null) {
            owner.detach(this);
        }
        onClosed();
    }

    public final boolean isHidden() {
        return hidden;
    }

    /** Hides the window, or shows it again; a hidden window keeps playing. */
    public final void setHidden(boolean newHidden) {
        hidden = newHidden;
    }

    /** Hides it: the corner button. */
    public final void hide() {
        setHidden(true);
    }

    /** Brings the window to the front. */
    public final void raise() {
        WindowManager owner = manager;
        if (owner != null) {
            owner.raise(this);
        }
    }
}
