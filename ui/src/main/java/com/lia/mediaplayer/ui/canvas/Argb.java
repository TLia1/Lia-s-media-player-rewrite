package com.lia.mediaplayer.ui.canvas;

/** Colours as {@code 0xAARRGGBB} ints. */
public final class Argb {

    private Argb() {}

    public static int alpha(int argb) {
        return argb >>> 24;
    }

    /** {@code argb} with its alpha multiplied by {@code factor}, clamped to 0..1. */
    public static int fade(int argb, float factor) {
        float f = Math.max(0, Math.min(1, factor));
        int a = Math.round(alpha(argb) * f);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** The colour {@code t} of the way from {@code from} to {@code to}, channel by channel. */
    public static int mix(int from, int to, float t) {
        float f = Math.max(0, Math.min(1, t));
        int a = channel(from >>> 24, to >>> 24, f);
        int r = channel((from >> 16) & 0xFF, (to >> 16) & 0xFF, f);
        int g = channel((from >> 8) & 0xFF, (to >> 8) & 0xFF, f);
        int b = channel(from & 0xFF, to & 0xFF, f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int channel(int from, int to, float t) {
        return Math.round(from + (to - from) * t);
    }
}
