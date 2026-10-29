package com.lia.mediaplayer.ui.windows;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.text.Keys;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.theme.Role;
import com.lia.mediaplayer.ui.widget.Constraints;
import com.lia.mediaplayer.ui.widget.InputContext;
import com.lia.mediaplayer.ui.widget.RenderContext;
import com.lia.mediaplayer.ui.widget.Size;
import com.lia.mediaplayer.ui.widget.Widget;
import java.util.List;
import java.util.Optional;

/**
 * The volume as v2 showed it: a vertical bar that rises out of the speaker button while the
 * cursor is on either, over the picture or past the top of the audio bar. Pressing or dragging
 * sets the volume, the wheel steps it. The transport bar places it and shows it; the window
 * draws it over everything else ({@link com.lia.mediaplayer.ui.window.Window#addPopup}).
 */
final class VolumePopup extends Widget {

    static final int BAR_WIDTH = 6;
    static final int BAR_HEIGHT = 40;
    static final int PADDING = 3;
    static final int WIDTH = BAR_WIDTH + 2 * PADDING;
    static final int HEIGHT = BAR_HEIGHT + 2 * PADDING;
    /** How far one notch of the wheel moves the volume. */
    static final double WHEEL_STEP = 0.05;

    private final PlaybackControl control;
    private boolean dragging;

    VolumePopup(PlaybackControl control) {
        this.control = control;
        setVisible(false);
    }

    /** Whether a drag that started on the bar is still going: the bar stays open meanwhile. */
    boolean isDragging() {
        return dragging;
    }

    @Override
    public Size measure(Constraints constraints) {
        return new Size(WIDTH, HEIGHT);
    }

    @Override
    public void render(Canvas canvas, RenderContext ctx) {
        Rect b = bounds();
        canvas.fill(b, ctx.color(Role.PANEL_BG));
        canvas.outline(b, ctx.color(Role.BORDER));
        Rect bar = bar();
        canvas.fill(bar, ctx.color(Role.TRACK));
        int filled = (int) Math.round(BAR_HEIGHT * clamp(control.volume()));
        canvas.fill(bar.x(), bar.bottom() - filled, bar.right(), bar.bottom(), ctx.color(Role.FILL));
        int knob = bar.bottom() - filled;
        canvas.fill(bar.x() - 2, knob - 1, bar.right() + 2, knob + 1, ctx.color(Role.KNOB));
    }

    @Override
    public boolean onInput(InputEvent event, InputContext ctx) {
        return switch (event) {
            case InputEvent.PointerDown down
            when down.button() == InputEvent.PRIMARY_BUTTON -> {
                dragging = true;
                ctx.capture(this);
                control.setVolume((float) valueAt(down.y()));
                yield true;
            }
            case InputEvent.PointerDrag drag
            when dragging -> {
                control.setVolume((float) valueAt(drag.y()));
                yield true;
            }
            case InputEvent.PointerUp up
            when dragging -> {
                dragging = false;
                ctx.releaseCapture();
                yield true;
            }
            case InputEvent.Scroll scroll -> {
                control.setVolume((float) clamp(control.volume() + Math.signum(scroll.delta()) * WHEEL_STEP));
                yield true;
            }
            default -> false;
        };
    }

    @Override
    public Optional<List<Text>> tooltip(double x, double y) {
        return Optional.of(List.of(Text.translatable(Keys.VOLUME, Math.round(clamp(control.volume()) * 100))));
    }

    private Rect bar() {
        Rect b = bounds();
        return new Rect(b.x() + PADDING, b.y() + PADDING, BAR_WIDTH, BAR_HEIGHT);
    }

    /** The volume at height {@code y}: full at the top of the bar, silent at its foot. */
    private double valueAt(double y) {
        Rect bar = bar();
        return clamp((bar.bottom() - y) / BAR_HEIGHT);
    }

    private static double clamp(double v) {
        return Double.isNaN(v) ? 0 : Math.max(0, Math.min(1, v));
    }
}
