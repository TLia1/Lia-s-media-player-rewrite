package com.lia.mediaplayer.ui.settings;

import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.MediaPlayerCore;
import com.lia.mediaplayer.core.policy.LinkFilter;
import com.lia.mediaplayer.core.settings.CoreSettings;
import com.lia.mediaplayer.core.settings.Settings;
import com.lia.mediaplayer.core.settings.VideoQuality;
import com.lia.mediaplayer.core.testing.FakePlatform;
import com.lia.mediaplayer.core.testing.TestCores;
import com.lia.mediaplayer.core.text.Keys;
import com.lia.mediaplayer.core.tools.LocatedTool;
import com.lia.mediaplayer.core.tools.Tool;
import com.lia.mediaplayer.core.tools.ToolState;
import com.lia.mediaplayer.ui.MediaPlayerUi;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.input.Mods;
import com.lia.mediaplayer.ui.testing.FakeHost;
import com.lia.mediaplayer.ui.testing.RecordingCanvas;
import com.lia.mediaplayer.ui.theme.Themes;
import com.lia.mediaplayer.ui.widget.Widget;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsScreenTest {

    @TempDir
    Path gameDir;

    private MediaPlayerCore core;
    private MediaPlayerUi ui;
    private final FakeHost host = new FakeHost();
    private SettingsScreen screen;
    private final RecordingCanvas canvas = new RecordingCanvas(640, 1200);

    @BeforeEach
    void open() {
        core = new MediaPlayerCore(new FakePlatform(gameDir), TestCores.OFFLINE);
        ui = new MediaPlayerUi(core, host, () -> 0L);
        ui.openSettings();
        screen = (SettingsScreen) host.currentScreen().orElseThrow();
        render(-1, -1);
    }

    @AfterEach
    void close() {
        core.close();
    }

    private void render(double x, double y) {
        screen.render(canvas.clear(), x, y, Mods.NONE);
    }

    private void click(Widget widget, int button) {
        Rect b = widget.bounds();
        screen.input(new InputEvent.PointerDown(b.centerX(), b.centerY(), button, Mods.NONE, 1));
        screen.input(new InputEvent.PointerUp(b.centerX(), b.centerY(), button, Mods.NONE));
        render(-1, -1);
    }

    private SettingsScreen.SettingRow row(String id) {
        return screen.rows().stream()
                .filter(r -> r.spec().id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void oneRowPerSettingWithItsGroupsOnTheLeft() {
        assertThat(screen.rows()).hasSameSizeAs(core.settings().specs());
        assertThat(canvas.strings())
                .contains(Keys.SETTINGS_ALL, Settings.groupKey(Settings.VIDEO), Settings.groupKey(Settings.WINDOWS))
                .contains("720p", "theme.liasmediaplayer.dark");
        // Long names are cut to fit, with an ellipsis.
        assertThat(canvas.drewText("settings.liasmediaplayer.video.m…")).isTrue();
        assertThat(screen.title()).isEqualTo(Text.translatable(Keys.SETTINGS_TITLE));
    }

    @Test
    void searchAndGroupsFilterTheRows() {
        screen.search().setText("hosts");
        render(-1, -1);
        assertThat(screen.rows().stream()
                        .filter(Widget::isVisible)
                        .map(r -> r.spec().id()))
                .containsExactly(CoreSettings.BLOCKED_HOSTS.id(), CoreSettings.ALLOWED_HOSTS.id());
        screen.search().setText("nothing like this");
        render(-1, -1);
        assertThat(canvas.strings()).contains(Keys.SETTINGS_NO_MATCH);

        screen.search().setText("");
        screen.showGroup(Settings.TOOLS);
        render(-1, -1);
        assertThat(screen.rows().stream()
                        .filter(Widget::isVisible)
                        .map(r -> r.spec().id()))
                .containsExactly(CoreSettings.AUTO_UPDATE_TOOLS.id());
    }

    @Test
    void eachControlChangesItsSettingAndResetPutsItBack() {
        click(row(CoreSettings.MAX_HEIGHT.id()).control(), InputEvent.PRIMARY_BUTTON);
        assertThat(core.settings().get(CoreSettings.MAX_HEIGHT)).isEqualTo(VideoQuality.P1080);
        click(row(CoreSettings.MAX_HEIGHT.id()).control(), 1);
        click(row(CoreSettings.MAX_HEIGHT.id()).control(), 1);
        assertThat(core.settings().get(CoreSettings.MAX_HEIGHT)).isEqualTo(VideoQuality.P480);

        click(row(CoreSettings.AUTO_UPDATE_TOOLS.id()).control(), InputEvent.PRIMARY_BUTTON);
        assertThat(core.settings().get(CoreSettings.AUTO_UPDATE_TOOLS)).isFalse();

        click(row("liasmediaplayer:appearance.theme").control(), InputEvent.PRIMARY_BUTTON);
        assertThat(ui.theme()).isSameAs(Themes.CONTRAST);

        Rect slider = row(UiSettings.MAX_VIDEO_WINDOWS.id()).control().bounds();
        screen.input(new InputEvent.PointerDown(slider.x() + 1, slider.centerY(), 0, Mods.NONE, 1));
        screen.input(new InputEvent.PointerUp(slider.x() + 1, slider.centerY(), 0, Mods.NONE));
        assertThat(core.settings().get(UiSettings.MAX_VIDEO_WINDOWS)).isEqualTo(1);

        var hosts = (SettingsScreen.SyncedField)
                row(CoreSettings.BLOCKED_HOSTS.id()).control();
        click(hosts.field(), InputEvent.PRIMARY_BUTTON);
        assertThat(screen.isTyping()).isTrue();
        "a.com, b.org".codePoints().forEach(c -> screen.input(new InputEvent.CharTyped(c)));
        assertThat(core.settings().get(CoreSettings.BLOCKED_HOSTS)).containsExactly("a.com", "b.org");
        click(row(CoreSettings.LINK_FILTER.id()).control(), InputEvent.PRIMARY_BUTTON);
        assertThat(core.chat().filter().mode()).isEqualTo(LinkFilter.Mode.BLOCKLIST);
        assertThat(core.chat().filter().allowsUrl("https://a.com/x.png")).isFalse();

        var maxHeight = row(CoreSettings.MAX_HEIGHT.id());
        click(maxHeight.children().getLast(), InputEvent.PRIMARY_BUTTON);
        assertThat(core.settings().isDefault(CoreSettings.MAX_HEIGHT)).isTrue();
    }

    @Test
    void aRowExplainsItselfAndTheFooterSaysWhereTheToolsStand() {
        Rect b = row(CoreSettings.IMAGE_CACHE_MB.id()).children().getFirst().bounds();
        render(b.x() + 2, b.centerY());
        assertThat(canvas.tooltip())
                .hasValueSatisfying(t -> assertThat(t.lines())
                        .containsExactly(
                                "settings.liasmediaplayer.performance.image_cache_mb.description",
                                "settings.liasmediaplayer.performance.image_cache_mb.warning"));
        Rect slider = ((SettingsScreen.IntControl)
                        row(CoreSettings.IMAGE_CACHE_MB.id()).control())
                .children()
                .getFirst()
                .bounds();
        render(slider.centerX(), slider.centerY());
        assertThat(canvas.tooltip())
                .hasValueSatisfying(t -> assertThat(t.lines())
                        .containsExactly("settings.liasmediaplayer.performance.image_cache_mb.value[256]"));

        assertThat(SettingsScreen.describe(new ToolState.Locating())).isEqualTo(Text.translatable(Keys.TOOLS_LOCATING));
        assertThat(SettingsScreen.describe(new ToolState.Downloading(Tool.YT_DLP, 0.5)))
                .isEqualTo(Text.translatable(Keys.TOOLS_DOWNLOADING, "yt-dlp", "50%"));
        assertThat(SettingsScreen.describe(new ToolState.Downloading(Tool.FFMPEG, -1)))
                .isEqualTo(Text.translatable(Keys.TOOLS_DOWNLOADING, "ffmpeg", "…"));
        assertThat(SettingsScreen.describe(new ToolState.Ready(Map.<Tool, LocatedTool>of())))
                .isEqualTo(Text.translatable(Keys.TOOLS_READY));
        assertThat(SettingsScreen.describe(
                        new ToolState.Missing(Map.of(), Map.of(Tool.FFPROBE, List.of(), Tool.YT_DLP, List.of()))))
                .isEqualTo(Text.translatable(Keys.TOOLS_MISSING, "yt-dlp, ffprobe"));
    }

    @Test
    void resetAllPutsTheShownSettingsBack() {
        core.settings().set(CoreSettings.AUTO_UPDATE_TOOLS, false);
        core.settings().set(UiSettings.MAX_AUDIO_WINDOWS, 2);
        screen.showGroup(Settings.TOOLS);
        render(-1, -1);
        canvas.texts().stream()
                .filter(t -> t.text().equals(Keys.SETTINGS_RESET_ALL))
                .findFirst()
                .ifPresent(t -> {
                    screen.input(new InputEvent.PointerDown(t.x() + 2, t.y() + 2, 0, Mods.NONE, 1));
                    screen.input(new InputEvent.PointerUp(t.x() + 2, t.y() + 2, 0, Mods.NONE));
                });
        assertThat(core.settings().get(CoreSettings.AUTO_UPDATE_TOOLS)).isTrue();
        assertThat(core.settings().get(UiSettings.MAX_AUDIO_WINDOWS)).isEqualTo(2);
    }
}
