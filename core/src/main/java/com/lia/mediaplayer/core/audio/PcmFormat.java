package com.lia.mediaplayer.core.audio;

/**
 * Signed 16-bit little-endian PCM, interleaved.
 *
 * <p>Every session asks ffmpeg for {@link #STEREO_48K}, whatever the source, so a player
 * opens its sound line once and keeps it across seeks and tracks.
 */
public record PcmFormat(int sampleRate, int channels) {

    public static final PcmFormat STEREO_48K = new PcmFormat(48_000, 2);

    public PcmFormat {
        if (sampleRate < 8000 || sampleRate > 192_000 || channels < 1 || channels > 8) {
            throw new IllegalArgumentException("Unsupported PCM format: " + sampleRate + " Hz, " + channels + " ch");
        }
    }

    /** Bytes per frame: one sample for each channel. */
    public int frameBytes() {
        return channels * 2;
    }

    public int bytesPerSecond() {
        return sampleRate * frameBytes();
    }

    /** {@code frames} as microseconds of sound. */
    public long framesToMicros(long frames) {
        return frames * 1_000_000L / sampleRate;
    }
}
