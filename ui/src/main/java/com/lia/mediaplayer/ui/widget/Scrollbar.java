package com.lia.mediaplayer.ui.widget;

import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.theme.Role;

/**
 * The arithmetic and drawing of a vertical scroll bar, shared by {@link ListView} and {@link
 * ScrollPane}: a groove down the right edge and a thumb as tall as the share of the content
 * that shows.
 */
final class Scrollbar {

    static final int WIDTH = 4;
    private static final int MIN_THUMB = 8;

    private Scrollbar() {}

    /** The groove, down the right edge of {@code view}. */
    static Rect track(Rect view) {
        return new Rect(view.right() - WIDTH, view.y(), WIDTH, view.height());
    }

    static Rect thumb(Rect view, int content, double scroll) {
        Rect track = track(view);
        if (content <= view.height()) {
            return Rect.EMPTY;
        }
        int height = Math.max(MIN_THUMB, (int) ((long) track.height() * view.height() / content));
        int travel = track.height() - height;
        int y = track.y() + (int) Math.round(travel * scroll / (content - view.height()));
        return new Rect(track.x(), y, WIDTH, height);
    }

    /** The scroll that puts the thumb's centre at {@code y}. */
    static double scrollFor(Rect view, int content, double y) {
        Rect thumb = thumb(view, content, 0);
        int travel = view.height() - thumb.height();
        if (travel <= 0) {
            return 0;
        }
        double fraction = (y - view.y() - thumb.height() / 2.0) / travel;
        return clamp(fraction * (content - view.height()), view, content);
    }

    static double clamp(double scroll, Rect view, int content) {
        return Math.max(0, Math.min(scroll, Math.max(0, content - view.height())));
    }

    static void draw(Canvas canvas, RenderContext ctx, Rect view, int content, double scroll) {
        if (content <= view.height()) {
            return;
        }
        canvas.fill(track(view), ctx.color(Role.SCROLL_TRACK));
        canvas.fill(thumb(view, content, scroll), ctx.color(Role.SCROLL_THUMB));
    }
}
