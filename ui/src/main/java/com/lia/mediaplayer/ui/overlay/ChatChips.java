package com.lia.mediaplayer.ui.overlay;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.text.Keys;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.icon.Glyphs;
import com.lia.mediaplayer.ui.icon.Icon;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.theme.Role;
import com.lia.mediaplayer.ui.widget.RenderContext;
import java.util.function.IntSupplier;

/**
 * The small buttons in the top-left corner of the chat: "Playlists", and while windows are
 * hidden, one that shows them again. Drawn by the mod rather than added as vanilla buttons,
 * because the second one comes and goes and says how many windows it brings back.
 */
public final class ChatChips {

    static final int X = 4;
    static final int Y = 4;
    static final int HEIGHT = 14;
    static final int GAP = 4;

    private final Runnable openPlaylists;
    private final Runnable showHidden;
    private final IntSupplier hiddenCount;
    private Rect playlists = Rect.EMPTY;
    private Rect hidden = Rect.EMPTY;

    public ChatChips(Runnable openPlaylists, Runnable showHidden, IntSupplier hiddenCount) {
        this.openPlaylists = openPlaylists;
        this.showHidden = showHidden;
        this.hiddenCount = hiddenCount;
    }

    public void render(Canvas canvas, RenderContext ctx) {
        playlists = chip(canvas, ctx, Glyphs::note, Text.translatable(Keys.CHIP_PLAYLISTS), Y);
        int count = hiddenCount.getAsInt();
        hidden = count > 0
                ? chip(canvas, ctx, Glyphs::theater, Text.translatable(Keys.CHIP_HIDDEN, count), Y + HEIGHT + GAP)
                : Rect.EMPTY;
    }

    private static Rect chip(Canvas canvas, RenderContext ctx, Icon icon, Text label, int y) {
        int width = 2 + Glyphs.SIZE + 2 + canvas.measurer().width(label) + 4;
        var area = new Rect(X, y, width, HEIGHT);
        boolean over = ctx.hovered(area);
        int color = ctx.color(over ? Role.ICON_HOVER : Role.TEXT);
        canvas.fill(area, ctx.color(over ? Role.CHIP_HOVER : Role.CHIP_BG));
        icon.draw(canvas, X + 2, y + (HEIGHT - Glyphs.SIZE) / 2, color);
        canvas.text(label, X + 4 + Glyphs.SIZE, y + (HEIGHT - canvas.measurer().lineHeight() + 1) / 2, color, false);
        return area;
    }

    /** Whether a chip is under {@code (x, y)}. */
    public boolean covers(double x, double y) {
        return playlists.contains(x, y) || hidden.contains(x, y);
    }

    /** Acts on a press over a chip. Returns whether there was one. */
    public boolean press(InputEvent.PointerDown down) {
        if (down.button() != InputEvent.PRIMARY_BUTTON) {
            return false;
        }
        if (playlists.contains(down.x(), down.y())) {
            openPlaylists.run();
            return true;
        }
        if (hidden.contains(down.x(), down.y())) {
            showHidden.run();
            return true;
        }
        return false;
    }
}
