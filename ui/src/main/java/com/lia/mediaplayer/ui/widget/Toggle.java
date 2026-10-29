package com.lia.mediaplayer.ui.widget;

import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.theme.Role;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** A switch: a groove with a knob at one end, filled when on. A click flips it. */
public final class Toggle extends Widget {

    static final int WIDTH = 20;
    static final int HEIGHT = 10;

    private final BooleanSupplier value;
    private final Consumer<Boolean> onChange;

    public Toggle(BooleanSupplier value, Consumer<Boolean> onChange) {
        this.value = value;
        this.onChange = onChange;
    }

    @Override
    public Size measure(Constraints constraints) {
        return new Size(WIDTH, HEIGHT);
    }

    @Override
    public void render(Canvas canvas, RenderContext ctx) {
        Rect b = bounds();
        Rect groove = new Rect(b.x(), b.y() + (b.height() - HEIGHT) / 2, Math.min(WIDTH, b.width()), HEIGHT);
        boolean on = value.getAsBoolean();
        canvas.fill(groove, ctx.color(on ? Role.FILL : Role.TRACK));
        canvas.outline(groove, ctx.color(ctx.hovered(b) ? Role.ICON_HOVER : Role.BORDER));
        int knob = HEIGHT - 2;
        int x = on ? groove.right() - 1 - knob : groove.x() + 1;
        canvas.fill(new Rect(x, groove.y() + 1, knob, knob), ctx.color(Role.KNOB));
    }

    @Override
    public boolean onInput(InputEvent event, InputContext ctx) {
        if (event instanceof InputEvent.PointerDown down && down.button() == InputEvent.PRIMARY_BUTTON) {
            onChange.accept(!value.getAsBoolean());
            return true;
        }
        return false;
    }
}
