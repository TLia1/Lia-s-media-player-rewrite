package com.lia.mediaplayer.ui.overlay;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.text.Keys;
import com.lia.mediaplayer.ui.canvas.Argb;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.theme.Role;
import com.lia.mediaplayer.ui.widget.RenderContext;
import org.jspecify.annotations.Nullable;

/**
 * A line at the top of the screen saying what started playing, for a track no window shows:
 * a hidden player moved on in its queue. It stays a few seconds and fades.
 */
public final class NowPlayingBanner {

    static final long SHOWN_NANOS = 4_000_000_000L;
    private static final long FADE_NANOS = 500_000_000L;
    private static final int PAD = 4;
    private static final int TOP = 6;

    private @Nullable Text title;
    private long since;

    /** Shows {@code title} from {@code nanos} on. */
    public void announce(Text newTitle, long nanos) {
        title = newTitle;
        since = nanos;
    }

    public boolean isShowing(long nanos) {
        return title != null && nanos - since < SHOWN_NANOS;
    }

    public void render(Canvas canvas, RenderContext ctx) {
        Text shown = title;
        long age = ctx.nanos() - since;
        if (shown == null || age >= SHOWN_NANOS) {
            return;
        }
        float alpha = age > SHOWN_NANOS - FADE_NANOS ? (SHOWN_NANOS - age) / (float) FADE_NANOS : 1f;
        Text line = Text.translatable(Keys.NOW_PLAYING, shown);
        int width = Math.min(canvas.width() - 2 * PAD, canvas.measurer().width(line) + 2 * PAD);
        int height = canvas.measurer().lineHeight() + 2 * PAD;
        var area = new Rect((canvas.width() - width) / 2, TOP, width, height);
        canvas.nextLayer();
        canvas.fill(area, Argb.fade(ctx.color(Role.BANNER_BG), alpha));
        canvas.outline(area, Argb.fade(ctx.color(Role.BORDER), alpha));
        canvas.pushClip(area.inset(PAD - 1));
        canvas.text(line, area.x() + PAD, area.y() + PAD, Argb.fade(ctx.color(Role.TEXT), alpha), false);
        canvas.popClip();
    }
}
