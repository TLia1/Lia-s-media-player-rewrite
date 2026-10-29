package com.lia.mediaplayer.ui.windows;

import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.core.MediaPlayerCore;
import com.lia.mediaplayer.core.testing.FakePlatform;
import com.lia.mediaplayer.core.testing.MediaServer;
import com.lia.mediaplayer.core.testing.TestCores;
import com.lia.mediaplayer.core.testing.TestImages;
import com.lia.mediaplayer.core.testing.TestImages.GifFrame;
import com.lia.mediaplayer.core.text.Keys;
import com.lia.mediaplayer.ui.MediaPlayerUi;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.input.Mods;
import com.lia.mediaplayer.ui.screen.ScreenContext;
import com.lia.mediaplayer.ui.testing.FakeHost;
import com.lia.mediaplayer.ui.testing.RecordingCanvas;
import com.lia.mediaplayer.ui.window.Window;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageWindowTest {

    private static final ScreenContext CHAT = new ScreenContext(ScreenContext.Kind.CHAT, true);

    @TempDir
    Path gameDir;

    @TempDir
    Path files;

    private FakePlatform platform;
    private MediaPlayerCore core;
    private MediaPlayerUi ui;
    private MediaServer server;
    private final RecordingCanvas canvas = new RecordingCanvas(640, 360);

    @BeforeEach
    void start() throws Exception {
        platform = new FakePlatform(gameDir);
        core = new MediaPlayerCore(platform, TestCores.OFFLINE);
        ui = new MediaPlayerUi(core, new FakeHost(), System::nanoTime);
        server = new MediaServer();
    }

    @AfterEach
    void stop() {
        server.close();
        core.close();
    }

    private ImageWindow show(String url) {
        assertThat(ui.showCommand().run("image", url)).isEmpty();
        return (ImageWindow) ui.windows().focused().orElseThrow();
    }

    private void frameUntil(BooleanSupplier done) throws InterruptedException {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < deadline) {
            platform.runClientTasks();
            ui.tick();
            ui.renderScreen(CHAT, canvas.clear(), -1, -1, Mods.NONE);
            if (done.getAsBoolean()) {
                return;
            }
            Thread.sleep(5);
        }
        throw new AssertionError("Timed out; drew " + canvas.calls());
    }

    @Test
    void aBigPictureTakesHalfTheScreenFromTheTopRightCorner() throws Exception {
        String url =
                server.serve("/photo.png", TestImages.write(files, "photo.png", TestImages.png(1280, 720, 0xFF808080)));
        ImageWindow window = show(url);
        assertThat(window.title()).isEqualTo(com.lia.mediaplayer.api.text.Text.literal("photo.png"));

        frameUntil(() -> !canvas.textures().isEmpty());
        ui.renderScreen(CHAT, canvas.clear(), -1, -1, Mods.NONE);

        Rect frame = window.frame();
        assertThat(frame.width()).isEqualTo(320 + 2);
        assertThat(frame.height()).isEqualTo(180 + 2 + Window.TITLE_BAR);
        assertThat(frame.right()).isEqualTo(640 - 6);
        assertThat(canvas.textures().getFirst().rect())
                .isEqualTo(new Rect(frame.x() + 1, frame.y() + 1 + Window.TITLE_BAR, 320, 180));

        // On the HUD: the picture alone, no title.
        ui.renderHud(ScreenContext.NONE, canvas.clear());
        assertThat(canvas.textures()).hasSize(1);
        assertThat(canvas.texts()).isEmpty();
    }

    @Test
    void theWheelAloneZooms() throws Exception {
        String url = server.serve("/a.png", TestImages.write(files, "a.png", TestImages.png(100, 100, 0xFF808080)));
        ImageWindow window = show(url);
        frameUntil(() -> !canvas.textures().isEmpty());
        ui.renderScreen(CHAT, canvas.clear(), -1, -1, Mods.NONE);
        Rect before = window.frame();
        ui.input(CHAT, new InputEvent.Scroll(before.centerX(), before.centerY(), 1, Mods.NONE));
        assertThat(window.frame().width()).isGreaterThan(before.width());
    }

    @Test
    void aGifAnimates() throws Exception {
        byte[] gif =
                TestImages.gif(4, 4, List.of(GifFrame.full(4, 4, 0xFFFF0000, 2), GifFrame.full(4, 4, 0xFF0000FF, 2)));
        String url = server.serve("/a.gif", TestImages.write(files, "a.gif", gif));
        show(url);
        var seen = new java.util.HashSet<Long>();
        frameUntil(() -> {
            canvas.textures().forEach(t -> seen.add(t.texture().id()));
            return seen.size() == 2;
        });
    }

    @Test
    void aFailureOffersRetryAndAPictureIsPinnedOnce() throws Exception {
        String url = server.fail("/gone.png", 404);
        ImageWindow window = show(url);
        frameUntil(() -> canvas.drewText(Keys.IMAGE_FAILED));
        assertThat(window.view().tooltip(0, 0)).isPresent();
        assertThat(canvas.drewText(Keys.RETRY)).isTrue();

        server.serve("/gone.png", TestImages.write(files, "gone.png", TestImages.png(8, 8, 0xFFFFFFFF)));
        // Retry, through the button.
        var retry = canvas.texts().stream()
                .filter(t -> t.text().equals(Keys.RETRY))
                .findFirst()
                .orElseThrow();
        ui.input(CHAT, new InputEvent.PointerDown(retry.x() + 2, retry.y() + 2, 0, Mods.NONE, 1));
        ui.input(CHAT, new InputEvent.PointerUp(retry.x() + 2, retry.y() + 2, 0, Mods.NONE));
        frameUntil(() -> !canvas.textures().isEmpty());

        show(url);
        assertThat(ui.windows().windows()).hasSize(1);
        window.close();
        assertThat(ui.windows().isEmpty()).isTrue();
    }

    @Test
    void theOldestPictureGivesWayPastTheCap() {
        for (int i = 0; i < 10; i++) {
            ui.showCommand().run("image", "http://127.0.0.1:1/" + i + ".png");
        }
        assertThat(ui.windows().windows()).hasSize(8);
        assertThat(((ImageWindow) ui.windows().windows().getFirst()).ref().url())
                .endsWith("/2.png");
    }

    @Test
    void namesAPictureByItsFile() {
        assertThat(ImageWindow.titleOf("https://cdn.example/a/b/cat.png?x=1")).isEqualTo("cat.png");
        assertThat(ImageWindow.titleOf("https://tenor.com/view/")).isEqualTo("view");
        assertThat(ImageWindow.titleOf("https://cdn.example/")).isEqualTo("cdn.example");
    }
}
