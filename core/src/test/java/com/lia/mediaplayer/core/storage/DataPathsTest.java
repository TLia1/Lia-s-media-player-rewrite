package com.lia.mediaplayer.core.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DataPathsTest {

    private final Path gameDir = Path.of("instance").toAbsolutePath();
    private final DataPaths paths = DataPaths.under(gameDir);

    @Test
    void livesInTheModFolderUnderTheGameDirectory() {
        assertThat(paths.root()).isEqualTo(gameDir.resolve("liasmediaplayer"));
    }

    @Test
    void namesEveryFileAndFolderInsideTheRoot() {
        assertThat(paths.settings()).isEqualTo(paths.root().resolve("settings.json"));
        assertThat(paths.library()).isEqualTo(paths.root().resolve("library.json"));
        assertThat(paths.windows()).isEqualTo(paths.root().resolve("windows.json"));
        assertThat(paths.cache()).isEqualTo(paths.root().resolve("cache"));
        assertThat(paths.bin()).isEqualTo(paths.root().resolve("bin"));
        assertThat(paths.logs()).isEqualTo(paths.root().resolve("logs"));
    }

    @Test
    void normalisesTheRoot() {
        var messy = new DataPaths(Path.of("a", "..", "b", ".", "liasmediaplayer"));
        assertThat(messy.root()).isEqualTo(Path.of("b", "liasmediaplayer").toAbsolutePath());
    }
}
