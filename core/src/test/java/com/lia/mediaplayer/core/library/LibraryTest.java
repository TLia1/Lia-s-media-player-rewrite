package com.lia.mediaplayer.core.library;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonParser;
import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.core.storage.JsonStore;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibraryTest {

    private static final String A = "https://example.com/a.mp3";
    private static final String B = "https://example.com/b.mp3";
    private static final String C = "https://example.com/c.mp3";

    @TempDir
    Path dir;

    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private Instant now = Instant.parse("2026-10-29T12:00:00Z");
    private JsonStore<LibraryData> store;

    @AfterEach
    void stopTimer() {
        timer.shutdownNow();
    }

    private Library library() {
        store = new JsonStore<>(
                dir.resolve("library.json"),
                new LibraryCodec(),
                1,
                List.of(),
                Runnable::run,
                timer,
                Duration.ofHours(1));
        InstantSource clock = () -> now;
        return new Library(store, clock);
    }

    @Test
    void playlistsAreCreatedEditedAndKept() {
        var library = library();
        Playlist road = library.create("  Road trip  ");
        library.add(road.id(), List.of(A, "file:///etc/passwd", B, C));
        library.moveEntry(road.id(), 2, 0);
        library.removeEntry(road.id(), 1);
        library.rename(road.id(), "Drive");
        library.create("");
        store.flush();

        var again = library();
        assertThat(again.playlists()).hasSize(2);
        Playlist kept = again.playlist(road.id()).orElseThrow();
        assertThat(kept.name()).isEqualTo("Drive");
        assertThat(kept.urls()).containsExactly(C, B);
        assertThat(again.playlists().get(1).name()).isEqualTo("Playlist");
        again.delete(road.id());
        assertThat(again.playlist(road.id())).isEmpty();
    }

    @Test
    void movingIsAboutTheGapDroppedOn() {
        var p = new Playlist("x", "p", List.of(A, B, C));
        assertThat(p.withMoved(0, 3).urls()).containsExactly(B, C, A);
        assertThat(p.withMoved(2, 0).urls()).containsExactly(C, A, B);
        assertThat(p.withMoved(1, 1)).isSameAs(p);
        assertThat(p.withMoved(1, 2)).isSameAs(p);
        assertThat(p.withMoved(7, 0)).isSameAs(p);
        assertThat(p.withRemoved(9)).isSameAs(p);
        assertThat(p.withName("x".repeat(100)).name()).hasSize(Playlist.MAX_NAME);
    }

    @Test
    void versionMovesWithEveryRealChange() {
        var library = library();
        int before = library.version();
        library.rename("nothing", "x");
        assertThat(library.version()).isEqualTo(before);
        library.create("p");
        assertThat(library.version()).isEqualTo(before + 1);
    }

    @Test
    void playingAgainMovesAnEntryToTheTop() {
        var library = library();
        library.recordPlay(A, MediaKind.AUDIO);
        now = now.plusSeconds(1);
        library.recordPlay(B, MediaKind.VIDEO);
        now = now.plusSeconds(1);
        library.recordPlay(A, MediaKind.AUDIO);
        library.recordPlay("ftp://nope", MediaKind.AUDIO);
        assertThat(library.history()).extracting(HistoryEntry::url).containsExactly(A, B);
        assertThat(library.history().getFirst().lastPlayed()).isEqualTo(now);
    }

    @Test
    void favouritesSurviveTrimmingAndClearing() {
        var library = library();
        assertThat(library.toggleFavorite(A, MediaKind.AUDIO)).isTrue();
        for (int i = 0; i < Library.MAX_HISTORY + 5; i++) {
            library.recordPlay("https://example.com/" + i, MediaKind.VIDEO);
        }
        assertThat(library.history()).hasSize(Library.MAX_HISTORY + 1);
        assertThat(library.isFavorite(A)).isTrue();
        assertThat(library.history()).extracting(HistoryEntry::url).doesNotContain("https://example.com/0");

        library.clearHistory();
        assertThat(library.history()).extracting(HistoryEntry::url).containsExactly(A);
        assertThat(library.toggleFavorite(A, MediaKind.AUDIO)).isFalse();
        library.removeFromHistory(A);
        assertThat(library.history()).isEmpty();
        assertThat(library.toggleFavorite("not a link", MediaKind.AUDIO)).isFalse();
    }

    @Test
    void historySurvivesTheFile() {
        var library = library();
        library.recordPlay(A, MediaKind.IMAGE);
        library.toggleFavorite(B, MediaKind.AUDIO);
        store.flush();
        assertThat(library().history())
                .containsExactly(
                        new HistoryEntry(B, MediaKind.AUDIO, now, true),
                        new HistoryEntry(A, MediaKind.IMAGE, now, false));
    }

    @Test
    void aHandEditedFileKeepsWhatItCan() {
        var json = JsonParser.parseString("""
                        {"playlists": [{"name": "no id", "urls": ["https://x.org/1", 4, {"a": 1}]}, 7],
                         "history": [{"url": "file:///x"}, {"url": "https://x.org/2", "kind": "MOVIE"}]}
                        """).getAsJsonObject();
        LibraryData data = new LibraryCodec().decode(json);
        assertThat(data.playlists()).singleElement().satisfies(p -> {
            assertThat(p.id()).isNotBlank();
            assertThat(p.urls()).containsExactly("https://x.org/1");
        });
        assertThat(data.history())
                .singleElement()
                .satisfies(e -> assertThat(e.kind()).isEqualTo(MediaKind.VIDEO));
    }

    @Test
    void m3uGoesBothWays() {
        var playlist = new Playlist("x", "p", List.of(A, B));
        String text = M3u.export(playlist, url -> url.equals(A) ? "Song\nOne" : null);
        assertThat(text).isEqualTo("#EXTM3U\n#EXTINF:-1,Song One\n" + A + "\n#EXTINF:-1," + B + "\n" + B + "\n");
        assertThat(M3u.parse(text + "\r\n  /home/me/song.mp3\r\n# comment\n" + C))
                .containsExactly(A, B, C);
        assertThat(M3u.parse(null)).isEmpty();
    }

    @Test
    void shortFormsDropTheScheme() {
        assertThat(Titles.shortForm("https://www.example.com/a/")).isEqualTo("example.com/a");
        assertThat(Titles.shortForm("HTTP://x.org")).isEqualTo("x.org");
    }
}
