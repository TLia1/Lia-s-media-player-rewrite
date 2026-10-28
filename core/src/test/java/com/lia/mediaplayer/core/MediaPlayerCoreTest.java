package com.lia.mediaplayer.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.lia.mediaplayer.core.testing.FakePlatform;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaPlayerCoreTest {

    @TempDir
    Path gameDir;

    @Test
    void keepsItsDataUnderTheGameDirectory() {
        var platform = new FakePlatform(gameDir);
        try (var core = new MediaPlayerCore(platform)) {
            assertThat(core.platform()).isSameAs(platform);
            assertThat(core.paths().root()).isEqualTo(gameDir.resolve(ModInfo.MOD_ID));
        }
    }

    @Test
    void survivesWorldsComingAndGoingThenShutsDown() {
        var core = new MediaPlayerCore(new FakePlatform(gameDir));
        assertThatCode(() -> {
                    core.onWorldLeft();
                    core.onWorldLeft();
                    core.close();
                })
                .doesNotThrowAnyException();
    }
}
