package com.lia.mediaplayer.core.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.lia.mediaplayer.core.testing.TestClientThread;
import com.lia.mediaplayer.core.testing.TestMedia;
import com.lia.mediaplayer.core.tools.LocatedTool;
import com.lia.mediaplayer.core.tools.ProcessRunner;
import com.lia.mediaplayer.core.tools.Tool;
import com.lia.mediaplayer.core.tools.ToolMissingException;
import com.lia.mediaplayer.core.tools.ToolProvider;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FfmpegImageDecoderTest {

    @TempDir
    Path dir;

    private final TestClientThread client = new TestClientThread();
    private final ProcessRunner processes = new ProcessRunner(client);

    @AfterEach
    void close() {
        processes.close();
        client.close();
    }

    /** {@code count} PAM frames of one grey level each. */
    private static byte[] pam(int width, int height, int... greys) {
        var out = new ByteArrayOutputStream();
        for (int grey : greys) {
            out.writeBytes(("P7\nWIDTH " + width + "\nHEIGHT " + height
                            + "\nDEPTH 4\nMAXVAL 255\nTUPLTYPE RGB_ALPHA\nENDHDR\n")
                    .getBytes(StandardCharsets.US_ASCII));
            for (int i = 0; i < width * height; i++) {
                out.write(grey);
                out.write(grey);
                out.write(grey);
                out.write(255);
            }
        }
        return out.toByteArray();
    }

    @Test
    void repeatedFramesFoldIntoOneThatShowsLonger() throws IOException {
        DecodedImage image = FfmpegImageDecoder.read(new ByteArrayInputStream(pam(2, 2, 10, 10, 10, 200, 200)));
        assertThat(image.frames()).extracting(DecodedImage.Frame::delayMillis).containsExactly(60, 40);
        assertThat(image.frames().getFirst().rgba().get(0) & 0xFF).isEqualTo(10);

        DecodedImage still = FfmpegImageDecoder.read(new ByteArrayInputStream(pam(2, 2, 7, 7, 7)));
        assertThat(still.frames())
                .singleElement()
                .satisfies(f -> assertThat(f.delayMillis()).isZero());

        assertThatThrownBy(() -> FfmpegImageDecoder.read(new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(IOException.class);
    }

    @Test
    void aLongAnimationIsCutAtTheFrameLimit() throws IOException {
        int[] greys = new int[GifDecoder.MAX_FRAMES + 20];
        for (int i = 0; i < greys.length; i++) {
            greys[i] = i % 256;
        }
        DecodedImage image = FfmpegImageDecoder.read(new ByteArrayInputStream(pam(1, 1, greys)));
        assertThat(image.frames()).hasSize(GifDecoder.MAX_FRAMES);
    }

    @Test
    void withoutFfmpegTheFormatsItReadsFailCleanly() {
        ToolProvider none = ToolProvider.fixed(Map.of());
        var decoder = new FfmpegImageDecoder(none, processes);
        byte[] webp = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> ImageDecoders.decode(webp, decoder)).isInstanceOf(ToolMissingException.class);
    }

    /** Encodes a red-then-blue picture with ffmpeg: 300 ms of red and 200 of blue when animated. */
    private Path picture(String name, boolean animated) throws IOException {
        assumeTrue(TestMedia.ffmpeg().isPresent(), "ffmpeg is not installed");
        Path out = dir.resolve(name);
        var command = new ArrayList<>(List.of(TestMedia.ffmpeg().orElseThrow().toString(), "-y", "-loglevel", "error"));
        if (animated) {
            command.addAll(List.of(
                    "-f",
                    "lavfi",
                    "-i",
                    "color=red:s=64x48:d=0.3",
                    "-f",
                    "lavfi",
                    "-i",
                    "color=blue:s=64x48:d=0.2",
                    "-filter_complex",
                    "[0][1]concat=n=2:v=1,fps=10,format=rgba"));
        } else {
            command.addAll(List.of("-f", "lavfi", "-i", "color=red:s=64x48", "-frames:v", "1"));
        }
        command.addAll(
                switch (name.substring(name.indexOf('.') + 1)) {
                    case "webp" ->
                        animated ? List.of("-c:v", "libwebp_anim", "-loop", "0") : List.of("-c:v", "libwebp");
                    case "png" -> List.of("-f", "apng", "-plays", "0");
                    case "avif" -> List.of("-c:v", "libaom-av1", "-still-picture", "1");
                    default -> throw new IllegalArgumentException(name);
                });
        command.add(out.toString());
        try {
            Process process =
                    new ProcessBuilder(command).redirectErrorStream(true).start();
            String log = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assumeTrue(
                    process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0,
                    "ffmpeg cannot write " + name + ": " + log);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        return out;
    }

    private DecodedImage decode(Path file) throws IOException {
        var ffmpeg = new LocatedTool(Tool.FFMPEG, TestMedia.ffmpeg().orElseThrow(), "test");
        var decoder = new FfmpegImageDecoder(ToolProvider.fixed(Map.of(Tool.FFMPEG, ffmpeg)), processes);
        return ImageDecoders.decode(Files.readAllBytes(file), decoder);
    }

    private static int red(DecodedImage.Frame frame) {
        return frame.rgba().get(0) & 0xFF;
    }

    @Test
    @Tag("integration")
    void readsAnimatedWebpWithItsTiming() throws IOException {
        DecodedImage image = decode(picture("anim.webp", true));
        assertThat(image.width()).isEqualTo(64);
        assertThat(image.height()).isEqualTo(48);
        assertThat(image.frames()).extracting(DecodedImage.Frame::delayMillis).containsExactly(300, 200);
        assertThat(red(image.frames().getFirst())).isGreaterThan(200);
        assertThat(red(image.frames().getLast())).isLessThan(50);
    }

    @Test
    @Tag("integration")
    void readsAnimatedPngWhereImageIoWouldStopAtTheFirstFrame() throws IOException {
        byte[] bytes = Files.readAllBytes(picture("anim.png", true));
        assertThat(ImageDecoders.Format.sniff(bytes)).isEqualTo(ImageDecoders.Format.APNG);
        DecodedImage image = decode(picture("anim.png", true));
        assertThat(image.frames()).extracting(DecodedImage.Frame::delayMillis).containsExactly(300, 200);
    }

    @Test
    @Tag("integration")
    void readsStillWebpAndAvifAsOneFrame() throws IOException {
        for (String name : List.of("still.webp", "still.avif")) {
            Path file = picture(name, false);
            assertThat(ImageDecoders.Format.animated(Files.readAllBytes(file)))
                    .as(name)
                    .isFalse();
            DecodedImage image = decode(file);
            assertThat(image.frames()).as(name).singleElement().satisfies(frame -> {
                assertThat(frame.delayMillis()).isZero();
                assertThat(red(frame)).isGreaterThan(200);
            });
        }
    }

    @Test
    @Tag("integration")
    void aPictureFfmpegCannotReadIsUnsupported() throws IOException {
        assumeTrue(TestMedia.ffmpeg().isPresent(), "ffmpeg is not installed");
        byte[] fake = "RIFF\0\0\0\0WEBPnot really".getBytes(StandardCharsets.US_ASCII);
        Path file = Files.write(dir.resolve("fake.webp"), fake);
        assertThatThrownBy(() -> decode(file)).isInstanceOf(ImageDecoders.UnsupportedImageException.class);
    }
}
