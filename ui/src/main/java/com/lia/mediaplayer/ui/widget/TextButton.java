package com.lia.mediaplayer.ui.widget;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.theme.Role;

/** A word in a box that does something when clicked: "Retry". */
public final class TextButton extends Widget {

    static final int PAD_X = 6;
    static final int PAD_Y = 3;

    private final Text label;
    private final Runnable action;

    public TextButton(Text label, Runnable action) {
        this.label = label;
        this.action = action;
        setAccessibleName(label);
    }

    @Override
    public Size measure(Constraints constraints) {
        return new Size(
                constraints.text().width(label) + 2 * PAD_X, constraints.text().lineHeight() + 2 * PAD_Y);
    }

    @Override
    public void render(Canvas canvas, RenderContext ctx) {
        Rect b = bounds();
        boolean hovered = ctx.hovered(b);
        canvas.fill(b, ctx.color(Role.CONTROL_BAR_BG));
        canvas.outline(b, ctx.color(hovered ? Role.ICON_HOVER : Role.BORDER));
        int width = canvas.measurer().width(label);
        int y = b.y() + (b.height() - canvas.measurer().lineHeight() + 1) / 2;
        canvas.text(label, b.x() + (b.width() - width) / 2, y, ctx.color(hovered ? Role.ICON_HOVER : Role.TEXT), false);
    }

    @Override
    public boolean onInput(InputEvent event, InputContext ctx) {
        if (event instanceof InputEvent.PointerDown down && down.button() == InputEvent.PRIMARY_BUTTON) {
            action.run();
            return true;
        }
        return false;
    }
}
