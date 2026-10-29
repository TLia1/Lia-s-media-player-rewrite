package com.lia.mediaplayer.ui.windows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.lia.mediaplayer.core.MediaPlayerCore;
import com.lia.mediaplayer.core.platform.TextureRef;
import com.lia.mediaplayer.core.playback.PlayerState;
import com.lia.mediaplayer.core.testing.FakePlatform;
import com.lia.mediaplayer.core.testing.MediaServer;
import com.lia.mediaplayer.core.testing.TestCores;
import com.lia.mediaplayer.core.testing.TestMedia;
import com.lia.mediaplayer.ui.MediaPlayerUi;
import com.lia.mediaplayer.ui.input.Mods;
import com.lia.mediaplayer.ui.screen.ScreenContext;
import com.lia.mediaplayer.ui.testing.FakeHost;
import com.lia.mediaplayer.ui.testing.RecordingCanvas;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The milestone, end to end without the game: {@code /show video <link>} opens a window, the
 * real engine plays a clip generated for the test through the machine's ffmpeg, and its frames
 * reach a texture that the window draws — the same path the game takes, with the canvas and
 * the textures in memory.
 */
@Tag("integration")
class VideoWindowIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final ScreenContext CHAT = new ScreenContext(ScreenContext.Kind.CHAT, true);

    @TempDir
    Path gameDir;

    @TempDir
    Path mediaDir;

    @Test
    void aVideoPlaysInAWindowFromTheShowCommand() throws Exception {
        assumeTrue(TestMedia.ffmpeg().isPresent() && TestMedia.ffprobe().isPresent(), "ffmpeg is not installed");
        Path clip = TestMedia.videoOnly(mediaDir, "clip.mp4", 160, 90, 3);
        var platform = new FakePlatform(gameDir);
        try (var server = new MediaServer();
                var core = new MediaPlayerCore(platform, TestCores.OFFLINE)) {
            String url = server.serve("/clip.mp4", clip);
            var ui = new MediaPlayerUi(core, new FakeHost());
            var canvas = new RecordingCanvas(640, 360);

            assertThat(ui.showCommand().run("video", url)).isEmpty();
            var window = (VideoWindow) ui.windows().windows().getFirst();

            BooleanSupplier drawsAPicture = () -> !canvas.textures().isEmpty();
            frameUntil(platform, ui, canvas, drawsAPicture);

            TextureRef texture = canvas.textures().getLast().texture();
            assertThat(texture.width()).isEqualTo(160);
            assertThat(texture.height()).isEqualTo(90);
            byte[] pixels = platform.textures().pixels(texture);
            boolean anyLight = false;
            for (int i = 0; i < pixels.length && !anyLight; i += 4) {
                anyLight = (pixels[i] & 0xFF) > 32 || (pixels[i + 1] & 0xFF) > 32 || (pixels[i + 2] & 0xFF) > 32;
            }
            assertThat(anyLight).as("the test pattern reached the texture").isTrue();

            // It keeps playing: new frames keep arriving, and the clock moves.
            int uploads = window.uploads();
            frameUntil(platform, ui, canvas, () -> window.uploads() > uploads + 10);
            assertThat(window.control().snapshot().state()).isInstanceOf(PlayerState.Playing.class);
            // The window took the picture's 16:9 shape.
            assertThat(window.frame().height()).isEqualTo(144 + 2 + 13 + TransportBar.HEIGHT);

            ui.worldLeft();
            platform.runClientTasks();
            platform.textures().flushReleases();
            assertThat(platform.textures().liveCount()).isZero();
            assertThat(core.playbacks().playbacks()).isEmpty();
        }
    }

    /** Draws frames over the chat, running the client thread's tasks between them, until done. */
    private static void frameUntil(
            FakePlatform platform, MediaPlayerUi ui, RecordingCanvas canvas, BooleanSupplier done)
            throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            platform.runClientTasks();
            ui.tick();
            ui.renderScreen(CHAT, canvas.clear(), -1, -1, Mods.NONE);
            if (done.getAsBoolean()) {
                return;
            }
            Thread.sleep(16);
        }
        throw new AssertionError("Timed out; the window shows " + canvas.strings());
    }
}
