package com.lia.mediaplayer.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.lia.mediaplayer.core.MediaPlayerCore;
import com.lia.mediaplayer.core.testing.FakePlatform;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaPlayerUiTest {

    @TempDir
    Path gameDir;

    @Test
    void countsTicks() {
        try (var core = new MediaPlayerCore(new FakePlatform(gameDir))) {
            var ui = new MediaPlayerUi(core);
            ui.tick();
            ui.tick();
            assertThat(ui.ticks()).isEqualTo(2);
        }
    }

    @Test
    void forwardsWorldLeftToTheCore() {
        try (var core = new MediaPlayerCore(new FakePlatform(gameDir))) {
            assertThatCode(() -> new MediaPlayerUi(core).worldLeft()).doesNotThrowAnyException();
        }
    }
}
