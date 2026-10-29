package com.lia.mediaplayer.ui.icon;

import com.lia.mediaplayer.ui.canvas.Canvas;

/** Draws one icon in its {@link Glyphs#SIZE} box at {@code (x, y)}, in {@code color}. */
@FunctionalInterface
public interface Icon {

    void draw(Canvas canvas, int x, int y, int color);
}
