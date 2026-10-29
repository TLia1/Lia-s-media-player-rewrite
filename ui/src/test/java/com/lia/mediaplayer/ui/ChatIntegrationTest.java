package com.lia.mediaplayer.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.core.MediaPlayerCore;
import com.lia.mediaplayer.core.link.ChatLink;
import com.lia.mediaplayer.core.policy.LinkFilter;
import com.lia.mediaplayer.core.testing.FakePlatform;
import com.lia.mediaplayer.core.testing.MediaServer;
import com.lia.mediaplayer.core.testing.TestCores;
import com.lia.mediaplayer.core.testing.TestImages;
import com.lia.mediaplayer.core.text.Keys;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.input.Mods;
import com.lia.mediaplayer.ui.screen.ScreenContext;
import com.lia.mediaplayer.ui.testing.FakeHost;
import com.lia.mediaplayer.ui.testing.RecordingCanvas;
import com.lia.mediaplayer.ui.window.Window;
import com.lia.mediaplayer.ui.windows.AudioBar;
import com.lia.mediaplayer.ui.windows.ImageWindow;
import com.lia.mediaplayer.ui.windows.VideoWindow;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Links in chat, from the message being shown to the window a click opens. */
class ChatIntegrationTest {

    /** Refused at once on this machine: nothing here reaches the network. */
    private static final String CLIP = "http://127.0.0.1:1/a.mp4";

    private static final String SONG = "http://127.0.0.1:1/b.mp3";
    private static final ScreenContext CHAT = new ScreenContext(ScreenContext.Kind.CHAT, true);

    @TempDir
    Path gameDir;

    @TempDir
    Path files;

    private FakePlatform platform;
    private MediaPlayerCore core;
    private final FakeHost host = new FakeHost();
    private MediaPlayerUi ui;
    private final RecordingCanvas canvas = new RecordingCanvas(640, 360);

    @BeforeEach
    void start() {
        platform = new FakePlatform(gameDir);
        core = new MediaPlayerCore(platform, TestCores.OFFLINE);
        ui = new MediaPlayerUi(core, host, System::nanoTime);
    }

    @AfterEach
    void stop() {
        core.close();
    }

    /** Shows {@code url} in chat and puts its label at {@code (x, y)}. */
    private void post(String url, double x, double y) {
        List<ChatLink> links = ui.chatDisplayed(List.of("<Alex> " + url), "Alex");
        assertThat(links).extracting(ChatLink::url).containsExactly(url);
        host.chatLink(x, y, url);
    }

    private boolean click(double x, double y, Mods mods) {
        return ui.input(CHAT, new InputEvent.PointerDown(x, y, InputEvent.PRIMARY_BUTTON, mods, 1));
    }

    private void frameUntil(double x, double y, BooleanSupplier done) throws InterruptedException {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < deadline) {
            platform.runClientTasks();
            ui.tick();
            ui.renderScreen(CHAT, canvas.clear(), x, y, Mods.NONE);
            if (done.getAsBoolean()) {
                return;
            }
            Thread.sleep(5);
        }
        throw new AssertionError("Timed out; drew " + canvas.calls());
    }

    @Test
    void aClickOnALabelPlaysItAndTheModifiersChooseHow() {
        post(CLIP, 10, 300);
        post(SONG, 10, 310);

        assertThat(click(10, 300, Mods.NONE)).isTrue();
        assertThat(ui.windows().windows()).singleElement().isInstanceOf(VideoWindow.class);
        assertThat(click(10, 300, Mods.NONE)).isTrue();
        assertThat(ui.windows().windows()).as("queued in the open window").hasSize(1);

        assertThat(click(10, 300, new Mods(false, false, true, false, false))).isTrue();
        assertThat(ui.windows().frontMost(AudioBar.class))
                .as("Alt: the sound only")
                .isPresent();
        assertThat(click(10, 300, Mods.shiftOnly())).isTrue();
        assertThat(ui.windows().windows()).as("Shift: a window of its own").hasSize(3);
        assertThat(click(10, 310, Mods.NONE)).isTrue();
        assertThat(ui.windows().windows()).as("a sound joins the audio bar").hasSize(3);
    }

    @Test
    void onlyTheModsOwnLabelsAreTaken() {
        // A link the filter refused stays text; a click on it is the game's.
        core.chat().setFilter(new LinkFilter(LinkFilter.Mode.OFF, List.of(), List.of(), List.of("alex")));
        assertThat(ui.chatDisplayed(List.of("<Alex> " + CLIP), "Alex")).isEmpty();
        host.chatLink(10, 300, CLIP);
        assertThat(click(10, 300, Mods.NONE)).isFalse();

        core.chat().setFilter(LinkFilter.NONE);
        post(CLIP, 10, 300);
        assertThat(ui.input(CHAT, new InputEvent.PointerDown(10, 300, 1, Mods.NONE, 1)))
                .as("the right button")
                .isFalse();
        assertThat(click(10, 200, Mods.NONE)).as("no link there").isFalse();
        assertThat(ui.input(ScreenContext.NONE, new InputEvent.PointerDown(10, 300, 0, Mods.NONE, 1)))
                .isFalse();
        assertThat(ui.windows().isEmpty()).isTrue();
    }

    @Test
    void aPictureIsPreviewedOnHoverThenPinnedOnce() throws Exception {
        try (var server = new MediaServer()) {
            String url =
                    server.serve("/cat.png", TestImages.write(files, "cat.png", TestImages.png(60, 40, 0xFF336699)));
            post(url, 100, 300);

            ui.renderScreen(CHAT, canvas.clear(), 100, 300, Mods.NONE);
            assertThat(canvas.tooltip())
                    .hasValueSatisfying(tip -> assertThat(tip.lines()).containsExactly(Keys.IMAGE_LOADING));

            frameUntil(100, 300, () -> !canvas.textures().isEmpty());
            var preview = canvas.textures().getFirst();
            assertThat(preview.rect()).isEqualTo(new Rect(108, 252, 60, 40));
            assertThat(preview.layer()).as("above the chat and the windows").isPositive();
            assertThat(server.hits("/cat.png")).isEqualTo(1);

            assertThat(click(100, 300, Mods.NONE)).isTrue();
            assertThat(click(100, 300, Mods.NONE)).isTrue();
            assertThat(ui.windows().windows()).singleElement().isInstanceOf(ImageWindow.class);
            Window window = ui.windows().windows().getFirst();

            // Pinned, the window is the preview; it took the picture's own size.
            ui.renderScreen(CHAT, canvas.clear(), 100, 300, Mods.NONE);
            ui.renderScreen(CHAT, canvas.clear(), 100, 300, Mods.NONE);
            assertThat(window.frame().width()).isEqualTo(62);
            assertThat(window.frame().height()).isEqualTo(40 + 2 + Window.TITLE_BAR);
            assertThat(canvas.textures())
                    .singleElement()
                    .satisfies(t -> assertThat(t.rect().width()).isEqualTo(60));
            assertThat(server.hits("/cat.png")).isEqualTo(1);
        }
    }

    @Test
    void aBrokenPictureSaysSo() throws Exception {
        String url = "http://127.0.0.1:1/gone.png";
        post(url, 100, 300);
        frameUntil(
                100,
                300,
                () -> canvas.tooltip()
                        .map(tip -> tip.lines().contains(Keys.IMAGE_FAILED))
                        .orElse(false));
        assertThat(canvas.textures()).isEmpty();
    }
}
