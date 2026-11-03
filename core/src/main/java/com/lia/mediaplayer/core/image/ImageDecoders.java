package com.lia.mediaplayer.core.image;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Bytes to a {@link DecodedImage}, by what the bytes are rather than what the link says: PNG,
 * JPEG and BMP through ImageIO, GIF through {@link GifDecoder}, and WebP, AVIF and animated PNG
 * through a {@link Fallback} — {@link FfmpegImageDecoder} in the game. Anything else is refused
 * without starting a process.
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
        /** A PNG with an animation control chunk, which ImageIO would read as its first frame. */
        APNG,
        JPEG,
        GIF,
        BMP,
        WEBP,
        /** An ISO media file whose brands say AVIF. */
        AVIF,
        UNKNOWN;

        static Format sniff(byte[] data) {
            if (startsWith(data, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
                return animatedPng(data) ? APNG : PNG;
            }
            if (startsWith(data, 'R', 'I', 'F', 'F') && matches(data, 8, "WEBP")) {
                return WEBP;
            }
            if (matches(data, 4, "ftyp") && (avifBrand(data, "avif") || avifBrand(data, "avis"))) {
                return AVIF;
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

        /**
         * Whether the picture may have more than one frame, as far as its header says: an
         * animated PNG, a WebP with its animation flag, an AVIF image sequence, a GIF.
         */
        static boolean animated(byte[] data) {
            return switch (sniff(data)) {
                case APNG, GIF -> true;
                // VP8X header: flags at byte 20, animation is bit 1.
                case WEBP -> matches(data, 12, "VP8X") && data.length > 20 && (data[20] & 0x02) != 0;
                case AVIF -> avifBrand(data, "avis");
                case PNG, JPEG, BMP, UNKNOWN -> false;
            };
        }

        /** Whether an {@code acTL} chunk comes before the first {@code IDAT}. */
        private static boolean animatedPng(byte[] data) {
            int at = 8;
            while (at + 8 <= data.length) {
                long length = ((data[at] & 0xFFL) << 24)
                        | ((data[at + 1] & 0xFF) << 16)
                        | ((data[at + 2] & 0xFF) << 8)
                        | (data[at + 3] & 0xFF);
                if (matches(data, at + 4, "acTL")) {
                    return true;
                }
                if (matches(data, at + 4, "IDAT")) {
                    return false;
                }
                long next = at + 12 + length;
                if (next > data.length) {
                    return false;
                }
                at = (int) next;
            }
            return false;
        }

        /** Whether the {@code ftyp} box names {@code brand} as its major brand or a compatible one. */
        private static boolean avifBrand(byte[] data, String brand) {
            int size = ((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16) | ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
            int end = Math.min(data.length, Math.max(16, size));
            for (int at = 8; at + 4 <= end; at += 4) {
                if (matches(data, at, brand)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean matches(byte[] data, int at, String ascii) {
            if (at < 0 || at + ascii.length() > data.length) {
                return false;
            }
            for (int i = 0; i < ascii.length(); i++) {
                if (data[at + i] != ascii.charAt(i)) {
                    return false;
                }
            }
            return true;
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

    /** Reads the formats the JDK cannot: WebP, AVIF, animated PNG. */
    @FunctionalInterface
    public interface Fallback {

        /** No fallback: those formats are refused. */
        Fallback NONE = data -> {
            throw new UnsupportedImageException("No decoder for this picture format");
        };

        DecodedImage decode(byte[] data) throws IOException;
    }

    /** Decodes {@code data} with the built-in decoders alone. CPU-heavy: call it on the decode executor. */
    public static DecodedImage decode(byte[] data) throws IOException {
        return decode(data, Fallback.NONE);
    }

    /** Decodes {@code data}, handing what the JDK cannot read to {@code fallback}. Blocking. */
    public static DecodedImage decode(byte[] data, Fallback fallback) throws IOException {
        return switch (Format.sniff(data)) {
            case GIF -> GifDecoder.decode(data);
            case PNG, JPEG, BMP -> still(data);
            case APNG, WEBP, AVIF -> fallback.decode(data);
            case UNKNOWN -> throw new UnsupportedImageException("Not a picture format the mod reads");
        };
    }

    /**
     * The first frame of {@code image}, scaled down to fit {@code maxWidth × maxHeight} and
     * encoded as a JPEG: what the disk cache keeps of a thumbnail.
     */
    public static byte[] jpeg(DecodedImage image, int maxWidth, int maxHeight) throws IOException {
        int w = image.width();
        int h = image.height();
        double scale = Math.min(1, Math.min((double) maxWidth / w, (double) maxHeight / h));
        int sw = Math.max(1, (int) Math.round(w * scale));
        int sh = Math.max(1, (int) Math.round(h * scale));
        var source = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        ByteBuffer rgba = image.frames().getFirst().rgba();
        int[] argb = new int[w * h];
        for (int i = 0; i < argb.length; i++) {
            int o = i * 4;
            argb[i] = (rgba.get(o + 3) & 0xFF) << 24
                    | (rgba.get(o) & 0xFF) << 16
                    | (rgba.get(o + 1) & 0xFF) << 8
                    | (rgba.get(o + 2) & 0xFF);
        }
        source.setRGB(0, 0, w, h, argb, 0, w);
        // JPEG has no alpha: transparent parts come out black, as on a dark list.
        var scaled = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, sw, sh, null);
        } finally {
            g.dispose();
        }
        var out = new ByteArrayOutputStream();
        if (!ImageIO.write(scaled, "jpg", out)) {
            throw new IOException("No JPEG writer available");
        }
        return out.toByteArray();
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
