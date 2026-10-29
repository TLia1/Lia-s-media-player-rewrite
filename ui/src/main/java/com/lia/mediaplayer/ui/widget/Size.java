package com.lia.mediaplayer.ui.widget;

/** A width and a height in GUI pixels, never negative. */
public record Size(int width, int height) {

    public static final Size ZERO = new Size(0, 0);

    public Size {
        width = Math.max(0, width);
        height = Math.max(0, height);
    }
}
