package com.lia.mediaplayer.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.settings.BoolSetting;
import com.lia.mediaplayer.api.settings.SettingInfo;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.testing.FakePlatform;
import com.lia.mediaplayer.core.testing.TestCores;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaPlayerCoreTest {

    @TempDir
    Path gameDir;

    @Test
    void keepsItsDataUnderTheGameDirectory() {
        var platform = new FakePlatform(gameDir);
        try (var core = new MediaPlayerCore(platform, TestCores.OFFLINE)) {
            assertThat(core.platform()).isSameAs(platform);
            assertThat(core.paths().root()).isEqualTo(gameDir.resolve(ModInfo.MOD_ID));
        }
    }

    @Test
    void survivesWorldsComingAndGoingThenShutsDown() {
        var core = new MediaPlayerCore(new FakePlatform(gameDir), TestCores.OFFLINE);
        assertThatCode(() -> {
                    core.onWorldLeft();
                    core.onWorldLeft();
                    core.close();
                })
                .doesNotThrowAnyException();
    }

    @Test
    void addonsReadTheirSettingsAndTheLibraryThroughViews() throws Exception {
        try (var core = new MediaPlayerCore(new FakePlatform(gameDir), TestCores.OFFLINE)) {
            var spec = core.settings()
                    .register(
                            new BoolSetting(SettingInfo.of("myaddon:on", "myaddon:general", Text.literal("On")), true));
            var values = core.settingValues();
            var seen = new ArrayList<Boolean>();
            var subscription = values.onChange(spec, seen::add);
            values.set(spec, false);
            subscription.close();
            values.set(spec, true);
            assertThat(values.get(spec)).isTrue();
            assertThat(seen).containsExactly(false);
            var offThread = new Thread(() -> values.set(spec, false));
            var failure = new AtomicReference<Throwable>();
            offThread.setUncaughtExceptionHandler((thread, error) -> failure.set(error));
            offThread.start();
            offThread.join();
            assertThat(failure.get()).isInstanceOf(WrongThreadException.class);

            var playlist = core.library().create("Mix", List.of("https://example.com/a.mp3"));
            core.library().recordPlay("https://example.com/a.mp3", MediaKind.AUDIO);
            var unused = core.library().toggleFavorite("https://example.com/a.mp3", MediaKind.AUDIO);
            var library = core.libraryReader();
            assertThat(library.playlists()).singleElement().satisfies(view -> {
                assertThat(view.id()).isEqualTo(playlist.id());
                assertThat(view.urls()).containsExactly("https://example.com/a.mp3");
            });
            assertThat(library.history())
                    .singleElement()
                    .satisfies(entry -> assertThat(entry.favorite()).isTrue());
            assertThat(library.isFavorite("https://example.com/a.mp3")).isTrue();
            assertThatThrownBy(() -> values.get(new BoolSetting(
                            SettingInfo.of("myaddon:never", "myaddon:general", Text.literal("x")), true)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
