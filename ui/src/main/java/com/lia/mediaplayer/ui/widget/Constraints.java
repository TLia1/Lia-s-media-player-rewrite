package com.lia.mediaplayer.ui.widget;

import com.lia.mediaplayer.core.platform.TextMeasurer;

/**
 * What a widget may take when it {@linkplain Widget#measure measures} itself: at most this
 * much room, and the font it will be drawn in.
 */
public record Constraints(int maxWidth, int maxHeight, TextMeasurer text) {

    public Constraints {
        maxWidth = Math.max(0, maxWidth);
        maxHeight = Math.max(0, maxHeight);
    }

    public Constraints withMax(int width, int height) {
        return new Constraints(width, height, text);
    }
}
