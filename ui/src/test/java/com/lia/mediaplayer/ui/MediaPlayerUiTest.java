package com.lia.mediaplayer.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.core.MediaPlayerCore;
import com.lia.mediaplayer.core.testing.FakePlatform;
import com.lia.mediaplayer.core.testing.TestCores;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.input.Mods;
import com.lia.mediaplayer.ui.screen.MediaControlsScreen;
import com.lia.mediaplayer.ui.screen.ScreenContext;
import com.lia.mediaplayer.ui.testing.FakeHost;
import com.lia.mediaplayer.ui.testing.RecordingCanvas;
import com.lia.mediaplayer.ui.window.Window;
import com.lia.mediaplayer.ui.windows.AudioBar;
import com.lia.mediaplayer.ui.windows.VideoWindow;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaPlayerUiTest {

    /** Refused at once on this machine: nothing in these tests reaches the network. */
    private static final String CLIP = "http://127.0.0.1:1/a.mp4";

    private static final String OTHER_CLIP = "http://127.0.0.1:1/b.mp4";
    private static final ScreenContext CHAT = new ScreenContext(ScreenContext.Kind.CHAT, true);
    private static final ScreenContext HOSTED = new ScreenContext(ScreenContext.Kind.HOSTED, true);
    private static final ScreenContext OTHER = new ScreenContext(ScreenContext.Kind.OTHER, true);

    @TempDir
    Path gameDir;

    private MediaPlayerCore core;
    private final FakeHost host = new FakeHost();
    private MediaPlayerUi ui;
    private final RecordingCanvas canvas = new RecordingCanvas(640, 360);

    @BeforeEach
    void start() {
        core = new MediaPlayerCore(new FakePlatform(gameDir), TestCores.OFFLINE);
        ui = new MediaPlayerUi(core, host, () -> 0L);
    }

    @AfterEach
    void stop() {
        core.close();
    }

    private void renderOver(ScreenContext screen, double x, double y) {
        ui.renderScreen(screen, canvas.clear(), x, y, Mods.NONE);
    }

    @Test
    void countsTicks() {
        ui.tick();
        ui.tick();
        assertThat(ui.ticks()).isEqualTo(2);
        assertThat(ui.theme().id()).isEqualTo("liasmediaplayer:dark");
    }

    @Test
    void aLinkJoinsTheFrontWindowOfItsKindUnlessANewOneIsAskedFor() {
        assertThat(ui.showCommand().run("video", CLIP)).isEmpty();
        assertThat(ui.showCommand().run("video", OTHER_CLIP)).isEmpty();
        assertThat(ui.windows().windows()).hasSize(1).allMatch(VideoWindow.class::isInstance);
        assertThat(core.playbacks().playbacks()).hasSize(1);

        assertThat(ui.showCommand().run("video", OTHER_CLIP + " true")).isEmpty();
        assertThat(ui.showCommand().run("audio", CLIP)).isEmpty();
        assertThat(ui.showCommand().run("audio", OTHER_CLIP)).isEmpty();
        assertThat(ui.windows().windows()).hasSize(3);
        assertThat(ui.windows().frontMost(AudioBar.class)).isPresent();
        assertThat(core.playbacks().playbacks()).hasSize(3);

        // A link for the video window brings it back to the front.
        ui.showCommand().run("video", CLIP);
        assertThat(ui.windows().focused()).containsInstanceOf(VideoWindow.class);
    }

    @Test
    void windowsAreLiveOverTheChatAndPictureOnlyElsewhere() {
        ui.showCommand().run("video", CLIP);
        renderOver(CHAT, -1, -1);
        assertThat(canvas.drewText("127.0.0.1")).isTrue();
        ui.renderHud(CHAT, canvas.clear());
        assertThat(canvas.calls()).isEmpty();

        ui.renderHud(ScreenContext.NONE, canvas.clear());
        assertThat(canvas.fills()).isNotEmpty();
        assertThat(canvas.texts()).isEmpty();
        renderOver(OTHER, -1, -1);
        assertThat(canvas.calls()).isEmpty();

        Rect frame = ui.windows().windows().getFirst().frame();
        var press = new InputEvent.PointerDown(frame.centerX(), frame.centerY(), 0, Mods.NONE, 1);
        assertThat(ui.input(ScreenContext.NONE, press)).isFalse();
        assertThat(ui.input(CHAT, press)).isTrue();
        assertThat(ui.input(CHAT, new InputEvent.PointerUp(frame.centerX(), frame.centerY(), 0, Mods.NONE)))
                .isTrue();
        assertThat(ui.input(CHAT, new InputEvent.PointerDown(1, 300, 0, Mods.NONE, 1)))
                .isFalse();
    }

    @Test
    void aWindowIsDraggedByMovesMadeFromTheCursor() {
        ui.showCommand().run("video", CLIP);
        renderOver(CHAT, -1, -1);
        Window window = ui.windows().windows().getFirst();
        Rect before = window.frame();
        renderOver(CHAT, before.x() + 20, before.y() + 5);
        ui.input(CHAT, new InputEvent.PointerDown(before.x() + 20, before.y() + 5, 0, Mods.NONE, 1));
        renderOver(CHAT, before.x() - 80, before.y() + 105);
        assertThat(window.frame().x()).isEqualTo(before.x() - 100);
        assertThat(window.frame().y()).isEqualTo(before.y() + 100);

        // A new screen lets go of the window.
        ui.screenOpened(CHAT, ButtonSink.NONE);
        renderOver(CHAT, before.x() - 180, before.y() + 105);
        assertThat(window.frame().x()).isEqualTo(before.x() - 100);
    }

    @Test
    void theHotkeyOpensAndClosesTheMediaControls() {
        ui.hotkeyPressed(Hotkey.MEDIA_CONTROLS, Mods.NONE);
        assertThat(host.currentScreen()).containsInstanceOf(MediaControlsScreen.class);
        ui.showCommand().run("video", CLIP);
        renderOver(HOSTED, -1, -1);
        assertThat(canvas.drewText("127.0.0.1")).isTrue();
        ui.hotkeyPressed(Hotkey.MEDIA_CONTROLS, Mods.NONE);
        assertThat(host.currentScreen()).isEmpty();
        renderOver(HOSTED, -1, -1);
        assertThat(canvas.calls()).isEmpty();
        assertThat(host.opened()).isEqualTo(1);
        assertThat(Hotkey.MEDIA_CONTROLS.translationKey()).isEqualTo("key.liasmediaplayer.controls");
        assertThat(Hotkey.categoryKey()).isEqualTo("key.category.liasmediaplayer.main");
    }

    @Test
    void leavingTheWorldClosesEveryWindowAndPlayback() {
        ui.showCommand().run("video", CLIP);
        ui.showCommand().run("audio", CLIP);
        ui.worldLeft();
        assertThat(ui.windows().isEmpty()).isTrue();
        assertThat(core.playbacks().playbacks()).isEmpty();
    }
}
