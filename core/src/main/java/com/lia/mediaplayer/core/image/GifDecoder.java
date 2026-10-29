package com.lia.mediaplayer.core.image;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Node;

/**
 * Decodes an animated GIF into whole frames, once, off the client thread.
 *
 * <p>A GIF stores each frame as a patch over the previous picture, with a disposal method
 * saying how to reset it before the next one. Every patch is drawn onto one canvas here and
 * the canvas is snapshotted, so drawing a frame later is one texture, never a decode.
 *
 * <p>The bytes come from a link anyone can post, so nothing in them is trusted: the canvas a
 * header asks for is checked before it is allocated, frame rectangles are clipped to it, and
 * the pixels kept across frames are capped — frames are then dropped evenly, their time folded
 * into the frames kept, so the animation keeps its length.
 */
final class GifDecoder {

    /** Pixels kept across every frame: about 96 MB of texture. */
    static final long MAX_TOTAL_PIXELS = 24_000_000L;

    static final int MAX_FRAMES = 256;
    /** A two-byte header field can ask for a 65535² canvas, 17 GB; nothing real is that big. */
    static final int MAX_CANVAS_SIDE = 8_192;

    static final long MAX_CANVAS_PIXELS = 40_000_000L;
    /** A frame with no delay shows this long, as in browsers. */
    static final int DEFAULT_DELAY_MS = 100;
    /** Nothing animates faster than this: one-centisecond frames are a GIF quirk, not intent. */
    static final int MIN_DELAY_MS = 20;

    private static final int DISPOSE_NONE = 0;
    private static final int DISPOSE_BACKGROUND = 2;
    private static final int DISPOSE_PREVIOUS = 3;

    private GifDecoder() {}

    static DecodedImage decode(byte[] data) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) {
            throw new IOException("No GIF reader available");
        }
        ImageReader reader = readers.next();
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            reader.setInput(input, false);
            return decode(reader);
        } finally {
            reader.dispose();
        }
    }

    private static DecodedImage decode(ImageReader reader) throws IOException {
        int frameCount = reader.getNumImages(true);
        if (frameCount <= 0) {
            throw new IOException("GIF has no frames");
        }
        int frameLimit = Math.min(frameCount, MAX_FRAMES);
        int[] screen = logicalScreen(reader);
        int width = screen[0];
        int height = screen[1];

        var kept = new ArrayList<DecodedImage.Frame>();
        @Nullable BufferedImage canvas = null;
        @Nullable Graphics2D g = null;
        // The previous frame's disposal, applied before the next frame is drawn.
        var previous = new Rectangle();
        int previousDisposal = DISPOSE_NONE;
        @Nullable BufferedImage restorePoint = null;
        int maxKept = 1;
        int pendingDelay = 0;
        try {
            for (int i = 0; i < frameLimit; i++) {
                BufferedImage frame = reader.read(i);
                FrameMeta meta = frameMeta(reader, i);
                if (canvas == null || g == null) {
                    if (width <= 0 || height <= 0) {
                        width = meta.x + frame.getWidth();
                        height = meta.y + frame.getHeight();
                    }
                    requireSaneCanvas(width, height);
                    canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                    g = canvas.createGraphics();
                    maxKept = (int) Math.max(1, Math.min(frameLimit, MAX_TOTAL_PIXELS / ((long) width * height)));
                } else if (previousDisposal == DISPOSE_BACKGROUND) {
                    clear(g, previous);
                } else if (previousDisposal == DISPOSE_PREVIOUS && restorePoint != null) {
                    g.setComposite(AlphaComposite.Src);
                    g.drawImage(restorePoint, previous.x, previous.y, null);
                    g.setComposite(AlphaComposite.SrcOver);
                }

                // A frame partly off the canvas is malformed but common: clip it, do not fail.
                Rectangle area = new Rectangle(meta.x, meta.y, frame.getWidth(), frame.getHeight())
                        .intersection(new Rectangle(0, 0, width, height));
                restorePoint = meta.disposal == DISPOSE_PREVIOUS && !area.isEmpty() ? copy(canvas, area) : null;
                previous = area;
                previousDisposal = meta.disposal;

                g.drawImage(frame, meta.x, meta.y, null);

                pendingDelay += normalise(meta.delayMs);
                // Keep frames spread evenly; a dropped frame's time goes to the next one kept.
                boolean keep = kept.size() < maxKept && i >= Math.round(kept.size() * (frameLimit / (double) maxKept));
                if (keep) {
                    kept.add(DecodedImage.frame(pixels(canvas), pendingDelay));
                    pendingDelay = 0;
                }
            }
        } finally {
            if (g != null) {
                g.dispose();
            }
        }
        if (kept.isEmpty()) {
            throw new IOException("GIF produced no frames");
        }
        if (pendingDelay > 0) {
            var last = kept.getLast();
            kept.set(kept.size() - 1, new DecodedImage.Frame(last.rgba(), last.delayMillis() + pendingDelay));
        }
        if (kept.size() == 1) {
            // A one-frame GIF is a still: it never needs to change.
            kept.set(0, new DecodedImage.Frame(kept.getFirst().rgba(), 0));
        }
        return new DecodedImage(width, height, kept);
    }

    static int normalise(int delayMs) {
        return delayMs <= 0 ? DEFAULT_DELAY_MS : Math.max(MIN_DELAY_MS, delayMs);
    }

    private static void requireSaneCanvas(int width, int height) throws IOException {
        if (width <= 0
                || height <= 0
                || width > MAX_CANVAS_SIDE
                || height > MAX_CANVAS_SIDE
                || (long) width * height > MAX_CANVAS_PIXELS) {
            throw new IOException("GIF canvas is implausibly large (" + width + "x" + height + ")");
        }
    }

    private static int[] pixels(BufferedImage canvas) {
        return ((DataBufferInt) canvas.getRaster().getDataBuffer()).getData();
    }

    private static BufferedImage copy(BufferedImage canvas, Rectangle area) {
        var copy = new BufferedImage(area.width, area.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(canvas.getSubimage(area.x, area.y, area.width, area.height), 0, 0, null);
        g.dispose();
        return copy;
    }

    private static void clear(Graphics2D g, Rectangle area) {
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(area.x, area.y, area.width, area.height);
        g.setComposite(AlphaComposite.SrcOver);
    }

    private static int[] logicalScreen(ImageReader reader) {
        try {
            IIOMetadata stream = reader.getStreamMetadata();
            if (stream != null) {
                Node root = stream.getAsTree("javax_imageio_gif_stream_1.0");
                Node screen = child(root, "LogicalScreenDescriptor");
                if (screen != null) {
                    return new int[] {
                        intAttr(screen, "logicalScreenWidth", -1), intAttr(screen, "logicalScreenHeight", -1)
                    };
                }
            }
        } catch (IOException | RuntimeException e) {
            // The first frame gives the size instead.
        }
        return new int[] {-1, -1};
    }

    private static FrameMeta frameMeta(ImageReader reader, int index) {
        var meta = new FrameMeta();
        try {
            IIOMetadata frame = reader.getImageMetadata(index);
            if (frame == null) {
                return meta;
            }
            var root = (IIOMetadataNode) frame.getAsTree("javax_imageio_gif_image_1.0");
            Node descriptor = child(root, "ImageDescriptor");
            if (descriptor != null) {
                meta.x = Math.max(0, intAttr(descriptor, "imageLeftPosition", 0));
                meta.y = Math.max(0, intAttr(descriptor, "imageTopPosition", 0));
            }
            Node control = child(root, "GraphicControlExtension");
            if (control != null) {
                meta.delayMs = intAttr(control, "delayTime", 0) * 10;
                meta.disposal = switch (String.valueOf(stringAttr(control, "disposalMethod"))) {
                    case "restoreToBackgroundColor" -> DISPOSE_BACKGROUND;
                    case "restoreToPrevious" -> DISPOSE_PREVIOUS;
                    default -> DISPOSE_NONE;
                };
            }
        } catch (IOException | RuntimeException e) {
            // Defaults: drawn at the origin, kept, default delay.
        }
        return meta;
    }

    private static @Nullable Node child(Node parent, String name) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (name.equalsIgnoreCase(child.getNodeName())) {
                return child;
            }
        }
        return null;
    }

    private static int intAttr(Node node, String name, int fallback) {
        String value = stringAttr(node, name);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static @Nullable String stringAttr(Node node, String name) {
        var attributes = node.getAttributes();
        Node attr = attributes == null ? null : attributes.getNamedItem(name);
        return attr == null ? null : attr.getNodeValue();
    }

    private static final class FrameMeta {
        int x;
        int y;
        int delayMs;
        int disposal = DISPOSE_NONE;
    }
}
