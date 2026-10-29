package com.lia.mediaplayer.ui.widget;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.theme.Role;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

/**
 * How far through the media playback is, and where to go. Pressing and dragging scrubs: the
 * bar follows the cursor and the seek happens once, on release, so ffmpeg is not restarted
 * for every pixel. The bar grows and shows its knob while pointed at or scrubbed, which is
 * what says it can be dragged.
 *
 * <p>A live stream has nothing to seek within: the bar is then an empty track and ignores
 * presses.
 */
public final class SeekBar extends Widget {

    static final int BAR = 4;
    static final int BAR_ACTIVE = 6;
    static final int MIN_WIDTH = 20;
    /** Room above and below the bar that still counts as on it: hitting four pixels is a test of aim. */
    static final int HEIGHT = 11;

    private final DoubleSupplier fraction;
    private final BooleanSupplier seekable;
    private final DoubleConsumer onSeek;
    private final DoubleFunction<Text> labelAt;
    private double scrub = Double.NaN;

    /**
     * @param fraction where playback is, 0 to 1
     * @param onSeek called with the fraction chosen, once per scrub
     * @param labelAt the tooltip for a point on the bar: the time it stands for
     */
    public SeekBar(
            DoubleSupplier fraction, BooleanSupplier seekable, DoubleConsumer onSeek, DoubleFunction<Text> labelAt) {
        this.fraction = fraction;
        this.seekable = seekable;
        this.onSeek = onSeek;
        this.labelAt = labelAt;
    }

    public boolean isScrubbing() {
        return !Double.isNaN(scrub);
    }

    /** What the bar shows: the scrub position while scrubbing, otherwise playback's. */
    public double shownFraction() {
        return isScrubbing() ? scrub : clamp(fraction.getAsDouble());
    }

    @Override
    public Size measure(Constraints constraints) {
        return new Size(MIN_WIDTH, HEIGHT);
    }

    @Override
    public void render(Canvas canvas, RenderContext ctx) {
        Rect b = bounds();
        boolean live = seekable.getAsBoolean();
        boolean active = live && (isScrubbing() || ctx.hovered(b));
        int height = active ? BAR_ACTIVE : BAR;
        int top = b.y() + (b.height() - height) / 2;
        canvas.fill(b.x(), top, b.right(), top + height, ctx.color(Role.TRACK));
        if (!live) {
            return;
        }
        int filled = (int) Math.round(b.width() * shownFraction());
        canvas.fill(b.x(), top, b.x() + filled, top + height, ctx.color(Role.FILL));
        if (active) {
            int knob = b.x() + filled;
            canvas.fill(knob - 1, top - 2, knob + 2, top + height + 2, ctx.color(Role.KNOB));
        }
    }

    @Override
    public boolean onInput(InputEvent event, InputContext ctx) {
        return switch (event) {
            case InputEvent.PointerDown down
            when down.button() == InputEvent.PRIMARY_BUTTON -> {
                if (seekable.getAsBoolean()) {
                    scrub = fractionAt(down.x());
                    ctx.capture(this);
                }
                yield true;
            }
            case InputEvent.PointerDrag drag
            when isScrubbing() -> {
                scrub = fractionAt(drag.x());
                yield true;
            }
            case InputEvent.PointerUp up
            when isScrubbing() -> {
                double chosen = fractionAt(up.x());
                scrub = Double.NaN;
                ctx.releaseCapture();
                onSeek.accept(chosen);
                yield true;
            }
            default -> false;
        };
    }

    @Override
    public Optional<List<Text>> tooltip(double x, double y) {
        if (!seekable.getAsBoolean()) {
            return Optional.empty();
        }
        return Optional.of(List.of(labelAt.apply(isScrubbing() ? scrub : fractionAt(x))));
    }

    double fractionAt(double x) {
        Rect b = bounds();
        return b.width() <= 0 ? 0 : clamp((x - b.x()) / b.width());
    }

    private static double clamp(double value) {
        return Double.isNaN(value) ? 0 : Math.max(0, Math.min(1, value));
    }
}
