package com.lia.mediaplayer.core.image;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Bytes to a {@link DecodedImage}, by what the bytes are rather than what the link says: PNG,
 * JPEG and BMP through ImageIO, GIF through {@link GifDecoder}. Other formats (WebP, AVIF)
 * are refused here until the ffmpeg fallback decoder reads them.
 *
 * <p>The size a header claims is checked before anything is decoded. A still larger than
 * {@value #MAX_SIDE} pixels on its long side is scaled down once: it is shown at most at
 * screen size, and a 12-megapixel photo would otherwise hold 48 MB of texture.
 */
public final class ImageDecoders {

    /** The long side of a still, once decoded. */
    static final int MAX_SIDE = 2048;
    /** What a header may claim before decoding is refused. */
    static final long MAX_PIXELS = 40_000_000L;

    private ImageDecoders() {}

    /** The image format, from its first bytes. */
    enum Format {
        PNG,
        JPEG,
        GIF,
        BMP,
        UNKNOWN;

        static Format sniff(byte[] data) {
            if (startsWith(data, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
                return PNG;
            }
            if (startsWith(data, 0xFF, 0xD8, 0xFF)) {
                return JPEG;
            }
            if (startsWith(data, 'G', 'I', 'F', '8')) {
                return GIF;
            }
            if (startsWith(data, 'B', 'M')) {
                return BMP;
            }
            return UNKNOWN;
        }

        private static boolean startsWith(byte[] data, int... prefix) {
            if (data.length < prefix.length) {
                return false;
            }
            for (int i = 0; i < prefix.length; i++) {
                if ((data[i] & 0xFF) != prefix[i]) {
                    return false;
                }
            }
            return true;
        }
    }

    /** Thrown for bytes that are not a picture the decoders read. */
    public static final class UnsupportedImageException extends IOException {
        private static final long serialVersionUID = 1L;

        UnsupportedImageException(String message) {
            super(message);
        }
    }

    /** Decodes {@code data}. CPU-heavy: call it on the decode executor. */
    public static DecodedImage decode(byte[] data) throws IOException {
        return switch (Format.sniff(data)) {
            case GIF -> GifDecoder.decode(data);
            case PNG, JPEG, BMP -> still(data);
            case UNKNOWN -> throw new UnsupportedImageException("Not a PNG, JPEG, GIF or BMP image");
        };
    }

    private static DecodedImage still(byte[] data) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new UnsupportedImageException("No reader for this image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
                    throw new IOException("Image is implausibly large (" + width + "x" + height + ")");
                }
                BufferedImage image = fit(reader.read(0));
                int w = image.getWidth();
                int h = image.getHeight();
                int[] argb = image.getRGB(0, 0, w, h, null, 0, w);
                return new DecodedImage(w, h, List.of(DecodedImage.frame(argb, 0)));
            } finally {
                reader.dispose();
            }
        }
    }

    /** {@code image}, scaled down to {@value #MAX_SIDE} on its long side if it is larger. */
    static BufferedImage fit(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int longest = Math.max(w, h);
        if (longest <= MAX_SIDE) {
            return image;
        }
        double scale = (double) MAX_SIDE / longest;
        int sw = Math.max(1, (int) Math.round(w * scale));
        int sh = Math.max(1, (int) Math.round(h * scale));
        var scaled = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(image, 0, 0, sw, sh, null);
        } finally {
            g.dispose();
        }
        return scaled;
    }
}
