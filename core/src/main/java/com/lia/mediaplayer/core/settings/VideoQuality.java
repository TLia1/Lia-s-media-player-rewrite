package com.lia.mediaplayer.core.settings;

import com.lia.mediaplayer.api.text.Text;

/** The picture heights a player may be limited to. */
public enum VideoQuality {
    P360(360),
    P480(480),
    P720(720),
    P1080(1080);

    private final int height;

    VideoQuality(int height) {
        this.height = height;
    }

    public int height() {
        return height;
    }

    public Text label() {
        return Text.literal(height + "p");
    }

    /** The highest quality no taller than {@code height}, and at least the lowest. */
    public static VideoQuality atMost(int height) {
        VideoQuality best = P360;
        for (VideoQuality quality : values()) {
            if (quality.height <= height) {
                best = quality;
            }
        }
        return best;
    }
}
