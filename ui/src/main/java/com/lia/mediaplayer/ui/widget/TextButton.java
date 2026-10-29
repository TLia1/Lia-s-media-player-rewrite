package com.lia.mediaplayer.ui.widget;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.input.Mods;
import com.lia.mediaplayer.ui.theme.Role;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * A word in a box that does something when clicked: "Retry", "Play". Its label may change
 * ({@link #TextButton(Supplier, Consumer)}): a cycling choice shows the current one. It can
 * be disabled, and drawn as on ({@link #activeWhen}).
 */
public final class TextButton extends Widget {

    static final int PAD_X = 6;
    static final int PAD_Y = 3;

    private final Supplier<Text> label;
    private final Consumer<InputEvent.PointerDown> action;
    private BooleanSupplier enabled = () -> true;
    private BooleanSupplier active = () -> false;
    private @Nullable Supplier<List<Text>> tooltip;
    private int minWidth;

    public TextButton(Text label, Runnable action) {
        this(() -> label, press -> {
            if (press.button() == InputEvent.PRIMARY_BUTTON) {
                action.run();
            }
        });
        setAccessibleName(label);
    }

    /** A button whose label is read each frame, and whose action sees the press: its button and modifiers. */
    public TextButton(Supplier<Text> label, Consumer<InputEvent.PointerDown> action) {
        this.label = label;
        this.action = action;
    }

    public TextButton enabledWhen(BooleanSupplier condition) {
        enabled = condition;
        return this;
    }

    public TextButton activeWhen(BooleanSupplier condition) {
        active = condition;
        return this;
    }

    public TextButton minWidth(int pixels) {
        minWidth = pixels;
        return this;
    }

    public TextButton tooltip(Text text) {
        tooltip = () -> List.of(text);
        return this;
    }

    public TextButton tooltipLines(Supplier<List<Text>> lines) {
        tooltip = lines;
        return this;
    }

    public boolean isEnabled() {
        return enabled.getAsBoolean();
    }

    public Text label() {
        return label.get();
    }

    @Override
    public Size measure(Constraints constraints) {
        return new Size(
                Math.max(minWidth, constraints.text().width(label.get()) + 2 * PAD_X),
                constraints.text().lineHeight() + 2 * PAD_Y);
    }

    @Override
    public void render(Canvas canvas, RenderContext ctx) {
        Rect b = bounds();
        boolean on = isEnabled();
        boolean hovered = on && ctx.hovered(b);
        canvas.fill(b, ctx.color(Role.CONTROL_BAR_BG));
        Role edge = hovered ? Role.ICON_HOVER : active.getAsBoolean() ? Role.ICON_ACTIVE : Role.BORDER;
        canvas.outline(b, ctx.color(edge));
        Text text = label.get();
        int width = canvas.measurer().width(text);
        int y = b.y() + (b.height() - canvas.measurer().lineHeight() + 1) / 2;
        Role color = !on ? Role.ICON_DISABLED : hovered ? Role.ICON_HOVER : Role.TEXT;
        canvas.pushClip(b.inset(1));
        canvas.text(text, b.x() + Math.max(2, (b.width() - width) / 2), y, ctx.color(color), false);
        canvas.popClip();
    }

    @Override
    public boolean onInput(InputEvent event, InputContext ctx) {
        if (event instanceof InputEvent.PointerDown down) {
            if (isEnabled()) {
                action.accept(down);
            }
            return true;
        }
        return false;
    }

    /** Clicks it, as a primary press with no modifiers would. For tests. */
    public void click() {
        onInput(
                new InputEvent.PointerDown(
                        bounds().centerX(), bounds().centerY(), InputEvent.PRIMARY_BUTTON, Mods.NONE, 1),
                new InputContext());
    }

    @Override
    public Optional<List<Text>> tooltip(double x, double y) {
        Supplier<List<Text>> lines = tooltip;
        return lines == null ? Optional.empty() : Optional.of(lines.get());
    }
}
