package com.lia.mediaplayer.ui.widget;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.icon.Glyphs;
import com.lia.mediaplayer.ui.icon.Icon;
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
 * A glyph that does something when clicked. It can be a toggle ({@link #activeWhen}, drawn in
 * the active colour), be disabled ({@link #enabledWhen}), and warn on hover ({@link
 * #dangerous}) — the close button.
 *
 * <p>A click on a disabled button is still consumed, so it never falls through to whatever is
 * behind it (dragging the window, say).
 */
public final class IconButton extends Widget {

    /** The box around the glyph: a pixel of slack on every side makes small icons easier to hit. */
    static final int PADDING = 1;

    private final Icon icon;
    private final Consumer<Mods> action;
    private BooleanSupplier enabled = () -> true;
    private BooleanSupplier active = () -> false;
    private @Nullable Supplier<Text> tooltip;
    private boolean dangerous;

    public IconButton(Icon icon, Runnable action) {
        this(icon, mods -> action.run());
    }

    /** A button whose action depends on the modifiers held with the click. */
    public IconButton(Icon icon, Consumer<Mods> action) {
        this.icon = icon;
        this.action = action;
    }

    public IconButton enabledWhen(BooleanSupplier condition) {
        enabled = condition;
        return this;
    }

    public IconButton activeWhen(BooleanSupplier condition) {
        active = condition;
        return this;
    }

    public IconButton tooltip(Supplier<Text> text) {
        tooltip = text;
        return this;
    }

    public IconButton tooltip(Text text) {
        return tooltip(() -> text);
    }

    public IconButton dangerous() {
        dangerous = true;
        return this;
    }

    public boolean isEnabled() {
        return enabled.getAsBoolean();
    }

    @Override
    public Size measure(Constraints constraints) {
        int side = Glyphs.SIZE + 2 * PADDING;
        return new Size(side, side);
    }

    @Override
    public void render(Canvas canvas, RenderContext ctx) {
        Rect b = bounds();
        Role role;
        if (!isEnabled()) {
            role = Role.ICON_DISABLED;
        } else if (ctx.hovered(b)) {
            role = dangerous ? Role.DANGER : Role.ICON_HOVER;
        } else if (active.getAsBoolean()) {
            role = Role.ICON_ACTIVE;
        } else {
            role = Role.ICON;
        }
        int x = b.x() + (b.width() - Glyphs.SIZE) / 2;
        int y = b.y() + (b.height() - Glyphs.SIZE) / 2;
        icon.draw(canvas, x, y, ctx.color(role));
    }

    @Override
    public boolean onInput(InputEvent event, InputContext ctx) {
        if (event instanceof InputEvent.PointerDown down && down.button() == InputEvent.PRIMARY_BUTTON) {
            if (isEnabled()) {
                action.accept(down.mods());
            }
            return true;
        }
        return false;
    }

    @Override
    public Optional<List<Text>> tooltip(double x, double y) {
        Supplier<Text> text = tooltip;
        return text == null ? Optional.empty() : Optional.of(List.of(text.get()));
    }
}
