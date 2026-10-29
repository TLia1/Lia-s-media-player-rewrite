package com.lia.mediaplayer.ui.icon;

import com.lia.mediaplayer.ui.canvas.Argb;
import com.lia.mediaplayer.ui.canvas.Canvas;

/**
 * Every icon the mod draws, as filled rectangles inside an {@value #SIZE}×{@value #SIZE} box
 * whose top-left corner is {@code (x, y)}. They need no texture, scale with the GUI, and
 * cannot change shape between Minecraft versions. The caller picks the colour.
 */
public final class Glyphs {

    /** The side of an icon's box, in GUI pixels. */
    public static final int SIZE = 11;

    private Glyphs() {}

    /** A play triangle, or two pause bars while playing. */
    public static void playPause(Canvas c, int x, int y, boolean playing, int color) {
        if (playing) {
            c.fill(x + 2, y + 1, x + 5, y + SIZE - 1, color);
            c.fill(x + 7, y + 1, x + 10, y + SIZE - 1, color);
            return;
        }
        for (int i = 0; i < SIZE; i++) {
            int len = 2 + Math.min(i, SIZE - 1 - i);
            c.fill(x + 2, y + i, x + 2 + len, y + i + 1, color);
        }
    }

    /** A play triangle on its own: play this. */
    public static void play(Canvas c, int x, int y, int color) {
        playPause(c, x, y, false, color);
    }

    /** Skip to next: a triangle and a bar. */
    public static void next(Canvas c, int x, int y, int color) {
        for (int i = 0; i < SIZE; i++) {
            int len = 1 + Math.min(i, SIZE - 1 - i);
            c.fill(x + 1, y + i, x + 1 + len, y + i + 1, color);
        }
        c.fill(x + SIZE - 3, y, x + SIZE - 1, y + SIZE, color);
    }

    /** Skip to previous: a bar and a triangle. */
    public static void previous(Canvas c, int x, int y, int color) {
        c.fill(x + 2, y, x + 4, y + SIZE, color);
        for (int i = 0; i < SIZE; i++) {
            int len = 1 + Math.min(i, SIZE - 1 - i);
            c.fill(x + SIZE - 1 - len, y + i, x + SIZE - 1, y + i + 1, color);
        }
    }

    /** A speaker with its sound waves, or with a cross in {@code mutedColor} when muted. */
    public static void speaker(Canvas c, int x, int y, boolean muted, int color, int mutedColor) {
        int mid = y + SIZE / 2;
        c.fill(x + 1, mid - 2, x + 3, mid + 2, color);
        for (int i = 0; i < 3; i++) {
            c.fill(x + 3, mid - 1 - i, x + 4 + i, mid + 1 + i, color);
        }
        if (muted) {
            c.fill(x + 7, y + 2, x + 8, y + 3, mutedColor);
            c.fill(x + 9, y + 2, x + 10, y + 3, mutedColor);
            c.fill(x + 8, mid, x + 9, mid + 1, mutedColor);
            c.fill(x + 7, y + SIZE - 3, x + 8, y + SIZE - 2, mutedColor);
            c.fill(x + 9, y + SIZE - 3, x + 10, y + SIZE - 2, mutedColor);
            return;
        }
        c.fill(x + 7, mid - 2, x + 8, mid + 2, color);
        c.fill(x + 9, mid - 3, x + 10, mid + 3, color);
    }

    /** Repeat: two bars running opposite ways, with a "1" between them when one track repeats. */
    public static void loop(Canvas c, int x, int y, boolean single, int color) {
        c.fill(x + 1, y + 2, x + 10, y + 3, color);
        c.fill(x + 9, y + 3, x + 10, y + 5, color);
        c.fill(x + 2, y + 1, x + 3, y + 2, color);
        c.fill(x + 2, y + 3, x + 3, y + 4, color);
        c.fill(x + 1, y + 8, x + 10, y + 9, color);
        c.fill(x + 1, y + 6, x + 2, y + 8, color);
        c.fill(x + 8, y + 7, x + 9, y + 8, color);
        c.fill(x + 8, y + 9, x + 9, y + 10, color);
        if (single) {
            c.fill(x + 5, y + 4, x + 6, y + 8, color);
            c.fill(x + 4, y + 5, x + 5, y + 6, color);
        }
    }

    /** Shuffle: two crossing paths with arrow heads. */
    public static void shuffle(Canvas c, int x, int y, int color) {
        for (int i = 0; i < 7; i++) {
            c.fill(x + 1 + i, y + 2 + i, x + 2 + i, y + 3 + i, color);
            c.fill(x + 1 + i, y + 8 - i, x + 2 + i, y + 9 - i, color);
        }
        c.fill(x + 6, y + 1, x + 10, y + 2, color);
        c.fill(x + 9, y + 1, x + 10, y + 4, color);
        c.fill(x + 6, y + 9, x + 10, y + 10, color);
        c.fill(x + 9, y + 7, x + 10, y + 10, color);
    }

    /** A cross: close, remove. */
    public static void close(Canvas c, int x, int y, int color) {
        for (int i = 0; i < 7; i++) {
            c.fill(x + 2 + i, y + 2 + i, x + 3 + i, y + 3 + i, color);
            c.fill(x + 8 - i, y + 2 + i, x + 9 - i, y + 3 + i, color);
        }
    }

    /** An arrow out of the corner: open in the browser. */
    public static void externalLink(Canvas c, int x, int y, int color) {
        for (int i = 0; i < 6; i++) {
            c.fill(x + 2 + i, y + 8 - i, x + 3 + i, y + 9 - i, color);
        }
        c.fill(x + 5, y + 2, x + 9, y + 3, color);
        c.fill(x + 8, y + 2, x + 9, y + 6, color);
    }

    /** Two sheets, one outlined behind a filled one: copy. */
    public static void copy(Canvas c, int x, int y, int color) {
        c.fill(x + 1, y + 1, x + 7, y + 2, color);
        c.fill(x + 1, y + 1, x + 2, y + 7, color);
        c.fill(x + 1, y + 6, x + 4, y + 7, color);
        c.fill(x + 4, y + 4, x + 10, y + 10, color);
    }

    /** An almost-closed ring with an arrow head: try again. */
    public static void retry(Canvas c, int x, int y, int color) {
        c.fill(x + 3, y + 1, x + 8, y + 2, color);
        c.fill(x + 1, y + 3, x + 2, y + 8, color);
        c.fill(x + 2, y + 2, x + 3, y + 3, color);
        c.fill(x + 2, y + 8, x + 3, y + 9, color);
        c.fill(x + 3, y + 9, x + 8, y + 10, color);
        c.fill(x + 8, y + 8, x + 9, y + 9, color);
        c.fill(x + 9, y + 5, x + 10, y + 8, color);
        c.fill(x + 7, y, x + 8, y + 4, color);
        c.fill(x + 8, y + 1, x + 10, y + 2, color);
    }

    /** A heart, filled when it is on (a favourite), outlined when not. */
    public static void heart(Canvas c, int x, int y, boolean filled, int color) {
        int[][] rows = {{2, 4, 6, 8}, {1, 5, 5, 9}, {1, 9}, {1, 9}, {2, 8}, {3, 7}, {4, 6}, {5, 5}};
        for (int r = 0; r < rows.length; r++) {
            int[] spans = rows[r];
            int py = y + 2 + r;
            if (filled) {
                for (int i = 0; i < spans.length; i += 2) {
                    c.fill(x + spans[i], py, x + spans[i + 1] + 1, py + 1, color);
                }
            } else {
                int left = spans[0];
                int right = spans[spans.length - 1];
                if (r == 0) {
                    for (int i = 0; i < spans.length; i += 2) {
                        c.fill(x + spans[i], py, x + spans[i + 1] + 1, py + 1, color);
                    }
                } else {
                    c.fill(x + left, py, x + left + 1, py + 1, color);
                    c.fill(x + right, py, x + right + 1, py + 1, color);
                }
                if (r == 1) {
                    c.fill(x + 5, py, x + 6, py + 1, color);
                }
            }
        }
    }

    /** A bar at the bottom of the box: hide, minimise. */
    public static void hide(Canvas c, int x, int y, int color) {
        c.fill(x + 2, y + 7, x + 9, y + 9, color);
    }

    /** Four corners pointing out: the picture as large as the screen allows. */
    public static void theater(Canvas c, int x, int y, int color) {
        c.fill(x + 1, y + 1, x + 4, y + 2, color);
        c.fill(x + 1, y + 1, x + 2, y + 4, color);
        c.fill(x + 7, y + 1, x + 10, y + 2, color);
        c.fill(x + 9, y + 1, x + 10, y + 4, color);
        c.fill(x + 1, y + 9, x + 4, y + 10, color);
        c.fill(x + 1, y + 7, x + 2, y + 10, color);
        c.fill(x + 7, y + 9, x + 10, y + 10, color);
        c.fill(x + 9, y + 7, x + 10, y + 10, color);
    }

    /** Three lines with a dot before each: the queue. */
    public static void queue(Canvas c, int x, int y, int color) {
        for (int i = 0; i < 3; i++) {
            int py = y + 2 + i * 3;
            c.fill(x + 1, py, x + 2, py + 1, color);
            c.fill(x + 3, py, x + 10, py + 1, color);
        }
    }

    /** A plus: add. */
    public static void plus(Canvas c, int x, int y, int color) {
        c.fill(x + 5, y + 2, x + 6, y + 9, color);
        c.fill(x + 2, y + 5, x + 9, y + 6, color);
    }

    /** A triangle pointing up: move up. */
    public static void up(Canvas c, int x, int y, int color) {
        for (int i = 0; i < 4; i++) {
            c.fill(x + 5 - i, y + 3 + i, x + 6 + i, y + 4 + i, color);
        }
    }

    /** A triangle pointing down: move down. */
    public static void down(Canvas c, int x, int y, int color) {
        for (int i = 0; i < 4; i++) {
            c.fill(x + 2 + i, y + 4 + i, x + 9 - i, y + 5 + i, color);
        }
    }

    /** A quaver: music, the playlists. */
    public static void note(Canvas c, int x, int y, int color) {
        c.fill(x + 6, y + 1, x + 7, y + 8, color);
        c.fill(x + 7, y + 1, x + 9, y + 2, color);
        c.fill(x + 8, y + 2, x + 10, y + 3, color);
        c.fill(x + 3, y + 7, x + 7, y + 10, color);
    }

    /**
     * A ring of eight dots with a bright head going round once a second: loading. Centred on
     * {@code (cx, cy)} with radius {@code r}.
     */
    public static void spinner(Canvas c, int cx, int cy, int r, long nanos, int color) {
        int head = (int) ((nanos / 125_000_000L) % 8);
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8;
            int px = cx + (int) Math.round(Math.cos(angle) * r);
            int py = cy + (int) Math.round(Math.sin(angle) * r);
            int age = Math.floorMod(head - i, 8);
            int dot = Argb.fade(color, 1f - age / 8f);
            c.fill(px - 1, py - 1, px + 1, py + 1, dot);
        }
    }
}
