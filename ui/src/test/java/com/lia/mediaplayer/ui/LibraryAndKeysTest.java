package com.lia.mediaplayer.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.media.RepeatMode;
import com.lia.mediaplayer.api.settings.ChoiceSetting;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.MediaPlayerCore;
import com.lia.mediaplayer.core.library.HistoryEntry;
import com.lia.mediaplayer.core.testing.FakePlatform;
import com.lia.mediaplayer.core.testing.TestCores;
import com.lia.mediaplayer.core.text.Keys;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.input.Key;
import com.lia.mediaplayer.ui.input.Mods;
import com.lia.mediaplayer.ui.screen.HistoryScreen;
import com.lia.mediaplayer.ui.screen.PlaylistScreen;
import com.lia.mediaplayer.ui.screen.ScreenContext;
import com.lia.mediaplayer.ui.settings.SettingsScreen;
import com.lia.mediaplayer.ui.settings.UiSettings;
import com.lia.mediaplayer.ui.testing.FakeHost;
import com.lia.mediaplayer.ui.testing.RecordingCanvas;
import com.lia.mediaplayer.ui.theme.Themes;
import com.lia.mediaplayer.ui.window.Window;
import com.lia.mediaplayer.ui.windows.AudioBar;
import com.lia.mediaplayer.ui.windows.ImageWindow;
import com.lia.mediaplayer.ui.windows.PlayerWindow;
import com.lia.mediaplayer.ui.windows.VideoWindow;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What M4 adds to the UI root: screens, hotkeys, shortcuts, caps, and what windows remember. */
class LibraryAndKeysTest {

    /** Refused at once on this machine: nothing in these tests reaches the network. */
    private static final String CLIP = "http://127.0.0.1:1/a.mp4";

    private static final String OTHER_CLIP = "http://127.0.0.1:1/b.mp4";
    private static final String SONG = "http://127.0.0.1:1/a.mp3";
    private static final String OTHER_SONG = "http://127.0.0.1:1/b.mp3";
    private static final ScreenContext CHAT = new ScreenContext(ScreenContext.Kind.CHAT, true);
    private static final ScreenContext TYPING = new ScreenContext(ScreenContext.Kind.CHAT, false);
    private static final Mods CTRL = Mods.ctrlOnly();

    @TempDir
    Path gameDir;

    private FakePlatform platform;
    private MediaPlayerCore core;
    private final FakeHost host = new FakeHost();
    private MediaPlayerUi ui;
    private final RecordingCanvas canvas = new RecordingCanvas(640, 360);

    @BeforeEach
    void start() {
        platform = new FakePlatform(gameDir);
        core = new MediaPlayerCore(platform, TestCores.OFFLINE);
        ui = new MediaPlayerUi(core, host, () -> 0L);
    }

    @AfterEach
    void stop() {
        core.close();
    }

    private void renderChat(double x, double y) {
        ui.renderScreen(CHAT, canvas.clear(), x, y, Mods.NONE);
    }

    private boolean key(ScreenContext screen, Key key, Mods mods) {
        return ui.input(screen, new InputEvent.KeyDown(key, mods));
    }

    private PlayerWindow front() {
        return (PlayerWindow) ui.windows().focused().orElseThrow();
    }

    @Test
    void hotkeysOpenAndCloseTheScreens() {
        ui.hotkeyPressed(Hotkey.PLAYLISTS, Mods.NONE);
        assertThat(host.currentScreen()).containsInstanceOf(PlaylistScreen.class);
        ui.hotkeyPressed(Hotkey.PLAYLISTS, Mods.NONE);
        assertThat(host.currentScreen()).isEmpty();
        ui.hotkeyPressed(Hotkey.SETTINGS, Mods.NONE);
        assertThat(host.currentScreen()).containsInstanceOf(SettingsScreen.class);
        ui.hotkeyPressed(Hotkey.SETTINGS, Mods.NONE);
        assertThat(host.currentScreen()).isEmpty();
        assertThat(Hotkey.values()).extracting(Hotkey::translationKey).doesNotHaveDuplicates();
    }

    @Test
    void theClipboardKeyPlaysAsAClickWould() {
        platform.clipboard().set("  " + CLIP + "\n");
        ui.hotkeyPressed(Hotkey.PLAY_CLIPBOARD, new Mods(false, false, true, false, false));
        assertThat(ui.windows().windows()).singleElement().isInstanceOf(AudioBar.class);
        platform.clipboard().set("not a link");
        ui.hotkeyPressed(Hotkey.PLAY_CLIPBOARD, Mods.NONE);
        assertThat(ui.windows().windows()).hasSize(1);
    }

    @Test
    void transportHotkeysDriveTheAudioBarFirst() {
        ui.showCommand().run("video", CLIP);
        ui.showCommand().run("audio", SONG);
        var bar = ui.windows().frontMost(AudioBar.class).orElseThrow();
        ui.hotkeyPressed(Hotkey.VOLUME_DOWN, Mods.NONE);
        assertThat(bar.control().volume()).isEqualTo(0.95f);
        ui.hotkeyPressed(Hotkey.VOLUME_UP, Mods.NONE);
        assertThat(bar.control().volume()).isEqualTo(1f);
        ui.hotkeyPressed(Hotkey.MUTE, Mods.NONE);
        assertThat(bar.control().volume()).isZero();
        ui.hotkeyPressed(Hotkey.MUTE, Mods.NONE);
        assertThat(bar.control().volume()).isEqualTo(1f);
        ui.hotkeyPressed(Hotkey.PLAY_PAUSE, Mods.NONE);
        ui.hotkeyPressed(Hotkey.NEXT, Mods.NONE);
        ui.hotkeyPressed(Hotkey.PREVIOUS, Mods.NONE);

        ui.hotkeyPressed(Hotkey.TOGGLE_WINDOWS, Mods.NONE);
        assertThat(ui.windows().allHidden()).isTrue();
        ui.renderHud(ScreenContext.NONE, canvas.clear());
        assertThat(canvas.fills()).isEmpty();
        ui.hotkeyPressed(Hotkey.TOGGLE_WINDOWS, Mods.NONE);
        assertThat(ui.windows().hiddenCount()).isZero();
        ui.hotkeyPressed(Hotkey.CLOSE_ALL, Mods.NONE);
        assertThat(ui.windows().isEmpty()).isTrue();
        assertThat(core.playbacks().playbacks()).isEmpty();
    }

    @Test
    void shortcutsOverTheChatDriveTheFrontPlayer() {
        ui.showCommand().run("video", CLIP);
        renderChat(-1, -1);
        var video = (VideoWindow) ui.windows().focused().orElseThrow();
        assertThat(key(CHAT, Key.DOWN, CTRL)).isTrue();
        assertThat(video.control().volume()).isEqualTo(0.95f);
        assertThat(key(CHAT, Key.L, CTRL)).isTrue();
        assertThat(video.control().repeat()).isEqualTo(RepeatMode.ALL);
        assertThat(key(CHAT, Key.S, CTRL)).isTrue();
        assertThat(video.control().shuffle()).isTrue();
        assertThat(key(CHAT, Key.F, CTRL)).isTrue();
        assertThat(video.inTheater()).isTrue();
        assertThat(key(CHAT, Key.M, CTRL)).isTrue();
        assertThat(video.control().volume()).isZero();
        assertThat(key(CHAT, Key.SPACE, Mods.NONE)).isTrue();
        assertThat(key(CHAT, Key.RIGHT, Mods.shiftOnly())).isTrue();

        assertThat(key(TYPING, Key.SPACE, Mods.NONE))
                .as("a message is being typed")
                .isFalse();
        assertThat(key(CHAT, Key.ESCAPE, Mods.NONE)).isFalse();
        video.hide();
        assertThat(key(CHAT, Key.SPACE, Mods.NONE)).as("no player shows").isFalse();
        assertThat(key(CHAT, Key.F, CTRL)).isFalse();
    }

    @Test
    void thePauseMenuGetsASettingsButton() {
        var added = new ArrayList<Runnable>();
        var sink = new ButtonSink() {
            @Override
            public int screenWidth() {
                return 400;
            }

            @Override
            public void add(Text label, int x, int y, int width, int height, Runnable onPress) {
                assertThat(label).isEqualTo(Text.translatable(Keys.SETTINGS_BUTTON));
                assertThat(x + width).isEqualTo(390);
                added.add(onPress);
            }
        };
        ui.screenOpened(new ScreenContext(ScreenContext.Kind.PAUSE, true), sink);
        ui.screenOpened(CHAT, sink);
        assertThat(added).hasSize(1);
        added.getFirst().run();
        assertThat(host.currentScreen()).containsInstanceOf(SettingsScreen.class);
        host.closeScreen();
        assertThat(host.depth()).isZero();
    }

    @Test
    void theWindowCapsQueueOrMakeRoom() {
        core.settings().set(UiSettings.MAX_VIDEO_WINDOWS, 1);
        ui.showCommand().run("video", CLIP);
        ui.showCommand().run("video", OTHER_CLIP + " true");
        assertThat(ui.windows().windows()).hasSize(1);

        core.settings().set(UiSettings.MAX_IMAGE_WINDOWS, 2);
        for (String name : List.of("a", "b", "c")) {
            ui.showCommand().run("image", "http://127.0.0.1:1/" + name + ".png");
        }
        assertThat(ui.windows().windows().stream().filter(ImageWindow.class::isInstance))
                .extracting(w -> ((ImageWindow) w).ref().url())
                .containsExactly("http://127.0.0.1:1/b.png", "http://127.0.0.1:1/c.png");
        assertThat(core.library().history()).extracting(HistoryEntry::kind).contains(MediaKind.IMAGE, MediaKind.VIDEO);
    }

    @Test
    void aPlaylistPlaysInANewAudioBarWithItsModes() {
        ui.playAll(List.of(SONG, "http://127.0.0.1:1/x.png", OTHER_SONG, "nothing"), true, true);
        var bar = ui.windows().frontMost(AudioBar.class).orElseThrow();
        assertThat(bar.control().repeat()).isEqualTo(RepeatMode.ALL);
        assertThat(bar.control().shuffle()).isTrue();
        assertThat(bar.control().queued()).isEqualTo(1);
        ui.playAll(List.of("nothing"), false, false);
        assertThat(ui.windows().windows()).hasSize(1);
    }

    @Test
    void theChatChipsOpenThePlaylistsAndBringWindowsBack() {
        renderChat(8, 8);
        assertThat(canvas.strings()).contains(Keys.CHIP_PLAYLISTS);
        assertThat(ui.input(CHAT, new InputEvent.PointerDown(8, 8, 0, Mods.NONE, 1)))
                .isTrue();
        assertThat(host.currentScreen()).containsInstanceOf(PlaylistScreen.class);
        host.closeScreen();

        ui.showCommand().run("video", CLIP);
        ui.windows().toggleHidden();
        renderChat(8, 30);
        assertThat(canvas.strings()).contains("gui.liasmediaplayer.chip.hidden[1]");
        assertThat(ui.input(CHAT, new InputEvent.PointerDown(8, 30, 0, Mods.NONE, 1)))
                .isTrue();
        assertThat(ui.windows().hiddenCount()).isZero();
    }

    @Test
    void theHistoryOpensOverThePlaylists() {
        ui.openPlaylists();
        var playlists = (PlaylistScreen) host.currentScreen().orElseThrow();
        playlists.render(canvas.clear(), -1, -1, Mods.NONE);
        var history = canvas.texts().stream()
                .filter(t -> t.text().equals(Keys.PLAYLISTS_HISTORY))
                .findFirst()
                .orElseThrow();
        playlists.input(new InputEvent.PointerDown(history.x() + 1, history.y() + 1, 0, Mods.NONE, 1));
        assertThat(host.currentScreen()).containsInstanceOf(HistoryScreen.class);
        assertThat(host.depth()).isEqualTo(2);
        host.closeScreen();
        assertThat(host.currentScreen()).containsSame(playlists);
    }

    @Test
    void theNextWindowOfAKindOpensWhereTheLastWasLeftWithItsModes() {
        ui.showCommand().run("video", CLIP);
        renderChat(-1, -1);
        Window window = ui.windows().windows().getFirst();
        Rect before = window.frame();
        renderChat(before.x() + 20, before.y() + 5);
        ui.input(CHAT, new InputEvent.PointerDown(before.x() + 20, before.y() + 5, 0, Mods.NONE, 1));
        renderChat(before.x() - 180, before.y() + 105);
        ui.input(CHAT, new InputEvent.PointerUp(before.x() - 180, before.y() + 105, 0, Mods.NONE));
        Rect moved = window.frame();
        ((PlayerWindow) window).control().cycleRepeat();
        ((PlayerWindow) window).setQueueOpen(true);
        ui.tick();
        assertThat(core.windowStates().get(VideoWindow.KIND).placed()).isTrue();
        window.close();

        ui.showCommand().run("video", OTHER_CLIP);
        renderChat(-1, -1);
        PlayerWindow next = front();
        assertThat(((Window) next).frame().x()).isEqualTo(moved.x());
        assertThat(next.control().repeat()).isEqualTo(RepeatMode.ALL);
        assertThat(next.isQueueOpen()).isTrue();
    }

    @Test
    void theThemeAndThePreviewFollowTheirSettings() {
        var theme = (ChoiceSetting)
                core.settings().spec("liasmediaplayer:appearance.theme").orElseThrow();
        core.settings().set(theme, Themes.LIGHT.id());
        assertThat(ui.theme()).isSameAs(Themes.LIGHT);
        core.settings().set(theme, "someaddon:gone");
        assertThat(ui.theme()).isSameAs(Themes.DARK);
        assertThat(ui.themes().all()).hasSize(4);
        core.settings().set(UiSettings.HOVER_PREVIEW, false);
        assertThat(ui.settingsScreen()).isInstanceOf(SettingsScreen.class);
    }
}
