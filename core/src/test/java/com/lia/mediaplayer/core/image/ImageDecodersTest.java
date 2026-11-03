package com.lia.mediaplayer.core.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lia.mediaplayer.core.testing.TestImages;
import com.lia.mediaplayer.core.testing.TestImages.GifFrame;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImageDecodersTest {

    /** The RGBA bytes of pixel {@code i}. */
    private static int[] pixel(DecodedImage.Frame frame, int i) {
        ByteBuffer rgba = frame.rgba();
        return new int[] {
            rgba.get(i * 4) & 0xFF, rgba.get(i * 4 + 1) & 0xFF, rgba.get(i * 4 + 2) & 0xFF, rgba.get(i * 4 + 3) & 0xFF
        };
    }

    @Test
    void decodesAPngIntoOneRgbaFrame() throws IOException {
        DecodedImage image = ImageDecoders.decode(TestImages.png(4, 3, 0xFF112233));
        assertThat(image.width()).isEqualTo(4);
        assertThat(image.height()).isEqualTo(3);
        assertThat(image.frames()).singleElement().satisfies(frame -> {
            assertThat(frame.delayMillis()).isZero();
            assertThat(pixel(frame, 5)).containsExactly(0x11, 0x22, 0x33, 0xFF);
        });
        assertThat(image.bytes()).isEqualTo(4 * 3 * 4);
    }

    @Test
    void decodesAJpegByItsBytesWhateverTheLinkSays() throws IOException {
        DecodedImage image = ImageDecoders.decode(TestImages.jpeg(16, 8, 0xFF0000));
        assertThat(image.width()).isEqualTo(16);
        int[] red = pixel(image.frames().getFirst(), 0);
        assertThat(red[0]).isGreaterThan(200);
        assertThat(red[3]).isEqualTo(0xFF);
    }

    @Test
    void scalesALargeStillDownToItsLongSide() throws IOException {
        DecodedImage image = ImageDecoders.decode(TestImages.png(4096, 1024, 0xFFFFFFFF));
        assertThat(image.width()).isEqualTo(ImageDecoders.MAX_SIDE);
        assertThat(image.height()).isEqualTo(512);
    }

    @Test
    void refusesWhatIsNotAPicture() {
        assertThatThrownBy(() -> ImageDecoders.decode("<html>nope</html>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ImageDecoders.UnsupportedImageException.class);
        assertThatThrownBy(() -> ImageDecoders.decode(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0}))
                .isInstanceOf(IOException.class);
        assertThat(ImageDecoders.Format.sniff(new byte[] {'R', 'I', 'F', 'F'})).isEqualTo(ImageDecoders.Format.UNKNOWN);
        assertThat(ImageDecoders.Format.sniff(new byte[] {'B', 'M', 0})).isEqualTo(ImageDecoders.Format.BMP);
    }

    @Test
    void decodesAnAnimatedGifIntoWholeFramesWithTheirDelays() throws IOException {
        byte[] gif = TestImages.gif(
                8,
                8,
                List.of(
                        GifFrame.full(8, 8, 0xFFFF0000, 5),
                        // A patch over the first frame: the rest of the picture stays red.
                        new GifFrame(2, 2, 2, 2, 0xFF0000FF, 0, "none"),
                        new GifFrame(0, 0, 8, 8, 0xFF00FF00, 1, "none")));

        DecodedImage image = ImageDecoders.decode(gif);

        assertThat(image.frames()).hasSize(3);
        assertThat(image.frames())
                .extracting(DecodedImage.Frame::delayMillis)
                // 5 cs; none given shows as browsers do; one centisecond is a quirk, slowed.
                .containsExactly(50, GifDecoder.DEFAULT_DELAY_MS, GifDecoder.MIN_DELAY_MS);
        DecodedImage.Frame patched = image.frames().get(1);
        assertThat(pixel(patched, 0)).containsExactly(0xFF, 0, 0, 0xFF);
        assertThat(pixel(patched, 2 * 8 + 2)).containsExactly(0, 0, 0xFF, 0xFF);
    }

    @Test
    void restoresTheBackgroundWhereAFrameAsksForIt() throws IOException {
        byte[] gif = TestImages.gif(
                4,
                4,
                List.of(
                        new GifFrame(0, 0, 2, 2, 0xFFFF0000, 10, "restoreToBackgroundColor"),
                        new GifFrame(2, 2, 2, 2, 0xFF00FF00, 10, "none")));

        DecodedImage image = ImageDecoders.decode(gif);

        DecodedImage.Frame second = image.frames().get(1);
        assertThat(pixel(second, 0)[3]).as("the first patch was cleared").isZero();
        assertThat(pixel(second, 15)).containsExactly(0, 0xFF, 0, 0xFF);
    }

    @Test
    void dropsFramesOverThePixelBudgetAndKeepsTheirTime() throws IOException {
        // 2000 × 2000 is 4 M pixels a frame: the budget keeps six of ten.
        var frames = new java.util.ArrayList<GifFrame>();
        for (int i = 0; i < 10; i++) {
            frames.add(GifFrame.full(2000, 2000, i % 2 == 0 ? 0xFFFF0000 : 0xFF0000FF, 10));
        }
        DecodedImage image = ImageDecoders.decode(TestImages.gif(2000, 2000, frames));

        assertThat(image.frames()).hasSize((int) (GifDecoder.MAX_TOTAL_PIXELS / (2000L * 2000)));
        assertThat(image.frames().stream()
                        .mapToInt(DecodedImage.Frame::delayMillis)
                        .sum())
                .isEqualTo(10 * 100);
    }

    @Test
    void aOneFrameGifIsAStill() throws IOException {
        DecodedImage image = ImageDecoders.decode(TestImages.gif(2, 2, List.of(GifFrame.full(2, 2, 0xFFFFFFFF, 50))));
        assertThat(image.frames())
                .singleElement()
                .extracting(DecodedImage.Frame::delayMillis)
                .isEqualTo(0);
    }

    @Test
    void tellsTheFormatsOnlyFfmpegReadsByTheirBytes() throws IOException {
        byte[] apng = {
            (byte) 0x89,
            'P',
            'N',
            'G',
            13,
            10,
            26,
            10,
            0,
            0,
            0,
            13,
            'I',
            'H',
            'D',
            'R',
            0,
            0,
            0,
            1,
            0,
            0,
            0,
            1,
            8,
            6,
            0,
            0,
            0,
            1,
            2,
            3,
            4,
            0,
            0,
            0,
            8,
            'a',
            'c',
            'T',
            'L',
            0,
            0,
            0,
            2,
            0,
            0,
            0,
            0,
            9,
            9,
            9,
            9
        };
        assertThat(ImageDecoders.Format.sniff(apng)).isEqualTo(ImageDecoders.Format.APNG);
        assertThat(ImageDecoders.Format.sniff(TestImages.png(2, 2, 0xFF000000))).isEqualTo(ImageDecoders.Format.PNG);
        byte[] animatedWebp = "RIFF\0\0\0\0WEBPVP8X\0\0\0\0\u0002\0\0\0".getBytes(StandardCharsets.ISO_8859_1);
        assertThat(ImageDecoders.Format.sniff(animatedWebp)).isEqualTo(ImageDecoders.Format.WEBP);
        assertThat(ImageDecoders.Format.animated(animatedWebp)).isTrue();
        byte[] avif = "\0\0\0\u0018ftypmif1\0\0\0\0mif1avif".getBytes(StandardCharsets.ISO_8859_1);
        assertThat(ImageDecoders.Format.sniff(avif)).isEqualTo(ImageDecoders.Format.AVIF);
        assertThat(ImageDecoders.Format.animated(avif)).isFalse();
        byte[] mp4 = "\0\0\0\u0018ftypisom\0\0\0\0isomavc1".getBytes(StandardCharsets.ISO_8859_1);
        assertThat(ImageDecoders.Format.sniff(mp4)).isEqualTo(ImageDecoders.Format.UNKNOWN);
        // Without a fallback, refused rather than misread.
        assertThatThrownBy(() -> ImageDecoders.decode(avif))
                .isInstanceOf(ImageDecoders.UnsupportedImageException.class);
        assertThat(ImageDecoders.decode(avif, data -> ImageDecoders.decode(TestImages.png(3, 1, 0xFFFFFFFF)))
                        .width())
                .isEqualTo(3);
    }

    @Test
    void shrinksAPictureIntoASmallJpeg() throws IOException {
        DecodedImage big = ImageDecoders.decode(TestImages.png(1280, 720, 0xFF00FF00));
        byte[] jpeg = ImageDecoders.jpeg(big, 192, 108);
        assertThat(ImageDecoders.Format.sniff(jpeg)).isEqualTo(ImageDecoders.Format.JPEG);
        DecodedImage small = ImageDecoders.decode(jpeg);
        assertThat(small.width()).isEqualTo(192);
        assertThat(small.height()).isEqualTo(108);
        assertThat(pixel(small.frames().getFirst(), 0)[1]).isGreaterThan(200);
        // Never enlarged.
        assertThat(ImageDecoders.decode(
                                ImageDecoders.jpeg(ImageDecoders.decode(TestImages.png(40, 30, 0xFF000000)), 192, 108))
                        .width())
                .isEqualTo(40);
    }
}
