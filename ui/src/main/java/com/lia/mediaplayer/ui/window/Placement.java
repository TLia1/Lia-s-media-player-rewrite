package com.lia.mediaplayer.ui.window;

import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.widget.Size;
import java.util.List;

/**
 * Where a new window goes: the default corner (the top right unless the player chose another,
 * clear of the chat in the bottom left), or where the last window of its kind was left — and a
 * step towards the middle of the screen for each window already sitting at that spot, so a
 * second window never hides the first exactly.
 */
public final class Placement {

    /** Gap kept between a window and the screen edge. */
    public static final int MARGIN = 6;
    /** How far each cascaded window is offset from the one before. */
    public static final int CASCADE = 14;

    private Placement() {}

    /** The frame for a new window in the top-right corner. */
    public static Rect initial(Rect screen, Size size, List<Rect> others) {
        return initial(screen, size, others, Corner.TOP_RIGHT);
    }

    /**
     * The frame for a new window in {@code corner}.
     *
     * @param screen the area windows live in
     * @param size the new window's size
     * @param others where the open windows of its kind are
     */
    public static Rect initial(Rect screen, Size size, List<Rect> others, Corner corner) {
        int width = Math.min(size.width(), Math.max(1, screen.width() - 2 * MARGIN));
        int height = Math.min(size.height(), Math.max(1, screen.height() - 2 * MARGIN));
        int left = screen.x() + MARGIN;
        int right = screen.right() - MARGIN - width;
        int top = screen.y() + MARGIN;
        int bottom = screen.bottom() - MARGIN - height;
        Rect start = switch (corner) {
            case TOP_RIGHT -> new Rect(right, top, width, height);
            case TOP_LEFT -> new Rect(left, top, width, height);
            case BOTTOM_RIGHT -> new Rect(right, bottom, width, height);
            case BOTTOM_LEFT -> new Rect(left, bottom, width, height);
            case CENTER ->
                new Rect(
                        screen.x() + (screen.width() - width) / 2,
                        screen.y() + (screen.height() - height) / 2,
                        width,
                        height);
        };
        return cascade(screen, start, others);
    }

    /** {@code start}, or a step further towards the middle for each window already at that spot. */
    public static Rect cascade(Rect screen, Rect start, List<Rect> others) {
        Rect sized = start.withSize(Math.min(start.width(), screen.width()), Math.min(start.height(), screen.height()));
        int dx = sized.centerX() > screen.centerX() ? -CASCADE : CASCADE;
        int dy = sized.centerY() > screen.centerY() ? -CASCADE : CASCADE;
        int x = sized.x();
        int y = sized.y();
        // Bounded by the number of windows: each step moves past at least one of them.
        for (int step = 0; step <= others.size(); step++) {
            final int cx = x;
            final int cy = y;
            boolean taken =
                    others.stream().anyMatch(r -> Math.abs(r.x() - cx) < CASCADE && Math.abs(r.y() - cy) < CASCADE);
            if (!taken) {
                break;
            }
            x += dx;
            y += dy;
        }
        return new Rect(x, y, sized.width(), sized.height()).clampInto(screen);
    }
}
