package com.lia.mediaplayer.core.testing;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

/** Pictures made in the test: solid colours, as PNG, JPEG or animated GIF bytes. */
public final class TestImages {

    private TestImages() {}

    /** One frame of a GIF: a {@code w × h} patch of {@code argb} at {@code (x, y)}. */
    public record GifFrame(int x, int y, int w, int h, int argb, int delayCentis, String disposal) {

        public static GifFrame full(int w, int h, int argb, int delayCentis) {
            return new GifFrame(0, 0, w, h, argb, delayCentis, "none");
        }
    }

    public static BufferedImage solid(int width, int height, int argb) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }

    public static byte[] png(int width, int height, int argb) {
        return encode(solid(width, height, argb), "png");
    }

    public static byte[] jpeg(int width, int height, int rgb) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, rgb);
            }
        }
        return encode(image, "jpeg");
    }

    /** An animated GIF on a {@code width × height} screen. */
    public static byte[] gif(int width, int height, List<GifFrame> frames) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        var out = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            writer.prepareWriteSequence(streamMetadata(writer, width, height));
            for (GifFrame frame : frames) {
                BufferedImage image = solid(frame.w(), frame.h(), frame.argb());
                IIOMetadata metadata =
                        writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), null);
                String format = metadata.getNativeMetadataFormatName();
                var root = (IIOMetadataNode) metadata.getAsTree(format);
                var control = child(root, "GraphicControlExtension");
                control.setAttribute("disposalMethod", frame.disposal());
                control.setAttribute("userInputFlag", "FALSE");
                control.setAttribute("transparentColorFlag", "FALSE");
                control.setAttribute("delayTime", Integer.toString(frame.delayCentis()));
                control.setAttribute("transparentColorIndex", "0");
                var descriptor = child(root, "ImageDescriptor");
                descriptor.setAttribute("imageLeftPosition", Integer.toString(frame.x()));
                descriptor.setAttribute("imageTopPosition", Integer.toString(frame.y()));
                descriptor.setAttribute("imageWidth", Integer.toString(frame.w()));
                descriptor.setAttribute("imageHeight", Integer.toString(frame.h()));
                descriptor.setAttribute("interlaceFlag", "FALSE");
                metadata.setFromTree(format, root);
                writer.writeToSequence(new IIOImage(image, null, metadata), null);
            }
            writer.endWriteSequence();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    public static Path write(Path dir, String name, byte[] bytes) {
        try {
            return Files.write(dir.resolve(name), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static IIOMetadata streamMetadata(ImageWriter writer, int width, int height) throws IOException {
        IIOMetadata metadata = writer.getDefaultStreamMetadata(null);
        String format = metadata.getNativeMetadataFormatName();
        var root = (IIOMetadataNode) metadata.getAsTree(format);
        var screen = child(root, "LogicalScreenDescriptor");
        screen.setAttribute("logicalScreenWidth", Integer.toString(width));
        screen.setAttribute("logicalScreenHeight", Integer.toString(height));
        screen.setAttribute("colorResolution", "8");
        screen.setAttribute("pixelAspectRatio", "0");
        metadata.setFromTree(format, root);
        return metadata;
    }

    private static IIOMetadataNode child(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++) {
            if (root.item(i).getNodeName().equalsIgnoreCase(name)) {
                return (IIOMetadataNode) root.item(i);
            }
        }
        var node = new IIOMetadataNode(name);
        root.appendChild(node);
        return node;
    }

    private static byte[] encode(BufferedImage image, String format) {
        var out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, format, out)) {
                throw new IOException("No writer for " + format);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
