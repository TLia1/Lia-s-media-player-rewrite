package com.lia.mediaplayer.ui.window;

import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.widget.Size;
import java.util.List;

/**
 * Where a new window goes: the top-right corner, clear of the chat in the bottom left, and a
 * step down and left for each window already sitting at that spot, so a second window never
 * hides the first exactly.
 */
public final class Placement {

    /** Gap kept between a window and the screen edge. */
    public static final int MARGIN = 6;
    /** How far each cascaded window is offset from the one before. */
    public static final int CASCADE = 14;

    private Placement() {}

    /**
     * The frame for a new window.
     *
     * @param screen the area windows live in
     * @param size the new window's size
     * @param others where the windows already open are
     */
    public static Rect initial(Rect screen, Size size, List<Rect> others) {
        int width = Math.min(size.width(), Math.max(1, screen.width() - 2 * MARGIN));
        int height = Math.min(size.height(), Math.max(1, screen.height() - 2 * MARGIN));
        int x = screen.right() - MARGIN - width;
        int y = screen.y() + MARGIN;
        // Bounded by the number of windows: each step moves past at least one of them.
        for (int step = 0; step <= others.size(); step++) {
            final int cx = x;
            final int cy = y;
            boolean taken =
                    others.stream().anyMatch(r -> Math.abs(r.x() - cx) < CASCADE && Math.abs(r.y() - cy) < CASCADE);
            if (!taken) {
                break;
            }
            x -= CASCADE;
            y += CASCADE;
        }
        return new Rect(x, y, width, height).clampInto(screen);
    }
}
