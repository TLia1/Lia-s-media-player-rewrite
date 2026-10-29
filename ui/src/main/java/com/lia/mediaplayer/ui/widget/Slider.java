package com.lia.mediaplayer.ui.widget;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.theme.Role;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

/**
 * A value from 0 to 1 set by pressing, dragging or scrolling. Unlike {@link SeekBar} it
 * reports every change as it happens: the volume should follow the cursor.
 */
public final class Slider extends Widget {

    static final int BAR = 3;
    static final int HEIGHT = 11;
    /** How far one notch of the wheel moves the value. */
    static final double WHEEL_STEP = 0.05;

    private final int width;
    private final DoubleSupplier value;
    private final DoubleConsumer onChange;
    private final DoubleFunction<Text> label;
    private boolean dragging;

    public Slider(int width, DoubleSupplier value, DoubleConsumer onChange, DoubleFunction<Text> label) {
        this.width = width;
        this.value = value;
        this.onChange = onChange;
        this.label = label;
    }

    @Override
    public Size measure(Constraints constraints) {
        return new Size(width, HEIGHT);
    }

    @Override
    public void render(Canvas canvas, RenderContext ctx) {
        Rect b = bounds();
        int top = b.y() + (b.height() - BAR) / 2;
        canvas.fill(b.x(), top, b.right(), top + BAR, ctx.color(Role.TRACK));
        int filled = (int) Math.round(b.width() * clamp(value.getAsDouble()));
        canvas.fill(b.x(), top, b.x() + filled, top + BAR, ctx.color(Role.FILL));
        if (dragging || ctx.hovered(b)) {
            int knob = b.x() + filled;
            canvas.fill(knob - 1, top - 2, knob + 1, top + BAR + 2, ctx.color(Role.KNOB));
        }
    }

    @Override
    public boolean onInput(InputEvent event, InputContext ctx) {
        return switch (event) {
            case InputEvent.PointerDown down
            when down.button() == InputEvent.PRIMARY_BUTTON -> {
                dragging = true;
                ctx.capture(this);
                onChange.accept(valueAt(down.x()));
                yield true;
            }
            case InputEvent.PointerDrag drag
            when dragging -> {
                onChange.accept(valueAt(drag.x()));
                yield true;
            }
            case InputEvent.PointerUp up
            when dragging -> {
                dragging = false;
                ctx.releaseCapture();
                yield true;
            }
            case InputEvent.Scroll scroll
            when !scroll.mods().primary() -> {
                onChange.accept(clamp(value.getAsDouble() + Math.signum(scroll.delta()) * WHEEL_STEP));
                yield true;
            }
            default -> false;
        };
    }

    @Override
    public Optional<List<Text>> tooltip(double x, double y) {
        return Optional.of(List.of(label.apply(clamp(value.getAsDouble()))));
    }

    private double valueAt(double x) {
        Rect b = bounds();
        return b.width() <= 0 ? 0 : clamp((x - b.x()) / b.width());
    }

    private static double clamp(double v) {
        return Double.isNaN(v) ? 0 : Math.max(0, Math.min(1, v));
    }
}
