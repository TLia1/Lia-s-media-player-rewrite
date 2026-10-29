package com.lia.mediaplayer.core.migrate;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonParser;
import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.media.RepeatMode;
import com.lia.mediaplayer.core.library.LibraryCodec;
import com.lia.mediaplayer.core.library.LibraryData;
import com.lia.mediaplayer.core.storage.DataPaths;
import com.lia.mediaplayer.core.storage.WindowState;
import com.lia.mediaplayer.core.storage.WindowStates;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** v2's files, as v2 wrote them, become v3's. */
class V2ImportTest {

    @TempDir
    Path gameDir;

    private DataPaths paths() throws IOException {
        var paths = DataPaths.under(gameDir);
        Files.createDirectories(paths.root());
        return paths;
    }

    private void write(DataPaths paths, String name, String text) throws IOException {
        Files.writeString(paths.root().resolve(name), text, StandardCharsets.UTF_8);
    }

    private String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    @Test
    void importsEveryFileAndKeepsTheOriginals() throws IOException {
        var paths = paths();
        write(paths, "config.json", """
                {
                  "liasmediaplayer:video_resolution": 4,
                  "liasmediaplayer:max_video_windows": 3,
                  "liasmediaplayer:theme": "contrast",
                  "liasmediaplayer:link_filter_mode": "BLOCKLIST",
                  "liasmediaplayer:blocked_domains": "example.com, i.redd.it",
                  "liasmediaplayer:frame_queue_capacity": 24,
                  "liasmediaplayer:something_new": 1,
                  "someaddon:volume": 7
                }
                """);
        write(paths, "playlists.json", """
                [{"name": "Road trip", "urls": ["https://example.com/a.mp3", "file:///etc/passwd"]}, "junk"]
                """);
        write(paths, "history.json", """
                [{"url": "https://example.com/a.mp3", "kind": "AUDIO", "playedAt": 1700000000000, "favorite": true},
                 {"url": "file:///x", "kind": "VIDEO"}]
                """);
        write(paths, "windows.json", """
                {"video": {"placed": true, "x": 5, "y": 6, "sized": true, "width": 320,
                           "queuePanel": true, "repeat": "ALL", "shuffle": true},
                 "audio": {"placed": false}}
                """);

        var result = V2Import.runIfNeeded(paths);
        assertThat(result).isEqualTo(new V2Import.Result(6, 1, 1, 2));

        String settings = read(paths.settings());
        assertThat(settings)
                .contains("\"liasmediaplayer:video.max_height\": \"P720\"")
                .contains("\"liasmediaplayer:windows.max_video\": 3")
                .contains("\"liasmediaplayer:appearance.theme\": \"liasmediaplayer:contrast\"")
                .contains("\"liasmediaplayer:chat.link_filter\": \"BLOCKLIST\"")
                .contains("\"someaddon:volume\": 7")
                .doesNotContain("frame_queue")
                .doesNotContain("something_new");
        assertThat(JsonParser.parseString(settings)
                        .getAsJsonObject()
                        .getAsJsonObject("values")
                        .getAsJsonArray("liasmediaplayer:chat.blocked_hosts"))
                .hasSize(2);

        LibraryData library = new LibraryCodec()
                .decode(JsonParser.parseString(read(paths.library())).getAsJsonObject());
        assertThat(library.playlists()).singleElement().satisfies(p -> {
            assertThat(p.name()).isEqualTo("Road trip");
            assertThat(p.urls()).containsExactly("https://example.com/a.mp3");
        });
        assertThat(library.history()).singleElement().satisfies(e -> {
            assertThat(e.kind()).isEqualTo(MediaKind.AUDIO);
            assertThat(e.favorite()).isTrue();
            assertThat(e.lastPlayed().toEpochMilli()).isEqualTo(1700000000000L);
        });

        var windows = WindowStates.codec()
                .decode(JsonParser.parseString(read(paths.windows())).getAsJsonObject());
        assertThat(windows.get("video")).isEqualTo(new WindowState(true, 5, 6, 322, 0, RepeatMode.ALL, true, true, 1));
        assertThat(windows.get("audio")).isEqualTo(WindowState.DEFAULT);

        for (String name : new String[] {"config.json", "playlists.json", "history.json", "windows.json"}) {
            assertThat(paths.root().resolve(name + ".v2.bak")).exists();
        }
        assertThat(paths.root().resolve("config.json")).doesNotExist();
        assertThat(V2Import.runIfNeeded(paths)).isEqualTo(new V2Import.Result(0, 0, 0, 0));
    }

    @Test
    void nothingHappensWithoutV2Files() throws IOException {
        var paths = paths();
        assertThat(V2Import.runIfNeeded(paths)).isEqualTo(new V2Import.Result(0, 0, 0, 0));
        assertThat(paths.settings()).doesNotExist();
    }

    @Test
    void aV3WindowsFileIsNotMistakenForV2() throws IOException {
        var paths = paths();
        write(paths, "windows.json", "{\"schema\": 1, \"kinds\": {}}");
        assertThat(V2Import.runIfNeeded(paths).windowKinds()).isZero();
        assertThat(paths.settings()).doesNotExist();
    }

    @Test
    void anUnreadableFileStaysWhereItIs() throws IOException {
        var paths = paths();
        write(paths, "config.json", "{ broken");
        write(paths, "playlists.json", "[{\"name\": \"Kept\", \"urls\": []}]");
        assertThat(V2Import.runIfNeeded(paths).playlists()).isEqualTo(1);
        assertThat(paths.root().resolve("config.json")).exists();
        assertThat(paths.root().resolve("playlists.json.v2.bak")).exists();
        assertThat(paths.settings()).exists();
    }
}
