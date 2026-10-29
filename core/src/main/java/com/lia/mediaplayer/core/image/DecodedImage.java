package com.lia.mediaplayer.core.image;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * A picture ready for the GPU: one frame for a still, several for an animation, every frame
 * the full picture (a GIF's partial frames are composited when decoded).
 *
 * @param width in pixels
 * @param height in pixels
 */
public record DecodedImage(int width, int height, List<Frame> frames) {

    public DecodedImage {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Image size must be positive: " + width + "x" + height);
        }
        frames = List.copyOf(frames);
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("An image needs at least one frame");
        }
        for (Frame frame : frames) {
            if (frame.rgba().capacity() != width * height * 4) {
                throw new IllegalArgumentException(
                        "Frame of " + frame.rgba().capacity() + " bytes for a " + width + "x" + height + " image");
            }
        }
    }

    /**
     * One frame.
     *
     * @param rgba {@code width × height × 4} bytes, row by row
     * @param delayMillis how long it shows; 0 for a still
     */
    public record Frame(ByteBuffer rgba, int delayMillis) {}

    /** Bytes the frames take, on the heap now and on the GPU once uploaded. */
    public long bytes() {
        return (long) width * height * 4 * frames.size();
    }

    /** One frame from packed {@code 0xAARRGGBB} pixels. */
    static Frame frame(int[] argb, int delayMillis) {
        byte[] rgba = new byte[argb.length * 4];
        for (int i = 0, o = 0; i < argb.length; i++, o += 4) {
            int pixel = argb[i];
            rgba[o] = (byte) (pixel >>> 16);
            rgba[o + 1] = (byte) (pixel >>> 8);
            rgba[o + 2] = (byte) pixel;
            rgba[o + 3] = (byte) (pixel >>> 24);
        }
        return new Frame(ByteBuffer.wrap(rgba), delayMillis);
    }
}
