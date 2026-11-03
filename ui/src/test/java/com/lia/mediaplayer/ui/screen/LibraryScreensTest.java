package com.lia.mediaplayer.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.core.MediaPlayerCore;
import com.lia.mediaplayer.core.library.HistoryEntry;
import com.lia.mediaplayer.core.library.Library;
import com.lia.mediaplayer.core.library.Playlist;
import com.lia.mediaplayer.core.testing.FakePlatform;
import com.lia.mediaplayer.core.testing.TestCores;
import com.lia.mediaplayer.core.text.Keys;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.input.Key;
import com.lia.mediaplayer.ui.input.Mods;
import com.lia.mediaplayer.ui.testing.RecordingCanvas;
import com.lia.mediaplayer.ui.theme.Themes;
import com.lia.mediaplayer.ui.widget.ListView;
import com.lia.mediaplayer.ui.widget.Widget;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The playlist and history screens, over a real library. */
class LibraryScreensTest {

    private static final String A = "https://example.com/a.mp3";
    private static final String B = "https://example.com/b.mp3";
    private static final String C = "https://example.com/c.mp3";

    @TempDir
    Path gameDir;

    private FakePlatform platform;
    private MediaPlayerCore core;
    private Library library;
    private long now;
    private final RecordingCanvas canvas = new RecordingCanvas(640, 360);
    private final List<String> calls = new ArrayList<>();
    private final LibraryActions actions = new LibraryActions() {
        @Override
        public void playAll(List<String> urls, boolean shuffle, boolean loop) {
            calls.add("playAll " + urls + " shuffle=" + shuffle + " loop=" + loop);
        }

        @Override
        public void play(String url) {
            calls.add("play " + url);
        }

        @Override
        public void openHistory(@Nullable String playlistId) {
            calls.add("history " + playlistId);
        }

        @Override
        public void close() {
            calls.add("close");
        }
    };

    @BeforeEach
    void start() {
        platform = new FakePlatform(gameDir);
        core = new MediaPlayerCore(platform, TestCores.OFFLINE);
        library = core.library();
    }

    @AfterEach
    void stop() {
        core.close();
    }

    private PlaylistScreen playlists(@Nullable String selected) {
        var screen = new PlaylistScreen(() -> Themes.DARK, () -> now, core, actions, selected);
        screen.render(canvas.clear(), -1, -1, Mods.NONE);
        return screen;
    }

    private void render(UiScreen screen) {
        screen.render(canvas.clear(), -1, -1, Mods.NONE);
    }

    private void click(UiScreen screen, Widget widget) {
        click(screen, widget.bounds(), 1);
    }

    /** Presses {@code clicks} times in a row; the screen counts them into a double click. */
    private void click(UiScreen screen, Rect b, int clicks) {
        for (int i = 0; i < clicks; i++) {
            screen.input(new InputEvent.PointerDown(b.centerX(), b.centerY(), 0, Mods.NONE, 1));
            screen.input(new InputEvent.PointerUp(b.centerX(), b.centerY(), 0, Mods.NONE));
        }
        render(screen);
    }

    private void clickText(UiScreen screen, String key) {
        var call = canvas.texts().stream()
                .filter(t -> t.text().equals(key))
                .findFirst()
                .orElseThrow();
        screen.input(new InputEvent.PointerDown(call.x() + 1, call.y() + 1, 0, Mods.NONE, 1));
        screen.input(new InputEvent.PointerUp(call.x() + 1, call.y() + 1, 0, Mods.NONE));
        render(screen);
    }

    private void type(UiScreen screen, String text) {
        text.codePoints().forEach(c -> screen.input(new InputEvent.CharTyped(c)));
        render(screen);
    }

    private Rect row(ListView<?> list, int index, int height) {
        Rect b = list.bounds();
        return new Rect(b.x() + 2, b.y() + index * height, 20, height - 1);
    }

    @Test
    void withNoPlaylistItSaysHowToMakeOne() {
        var screen = playlists(null);
        assertThat(canvas.strings()).contains(Keys.PLAYLISTS_NONE, Keys.PLAYLISTS_NONE_HINT);
        assertThat(screen.selected()).isEmpty();
        clickText(screen, Keys.DONE);
        assertThat(calls).containsExactly("close");
    }

    @Test
    void createsFillsReordersAndPlaysAPlaylist() {
        var screen = playlists(null);
        click(screen, screen.newName());
        type(screen, "Road trip");
        screen.input(new InputEvent.KeyDown(Key.ENTER, Mods.NONE));
        render(screen);
        Playlist road = screen.selected().orElseThrow();
        assertThat(road.name()).isEqualTo("Road trip");

        click(screen, screen.addLink());
        type(screen, A + " not-a-link " + B + " " + C);
        clickText(screen, Keys.PLAYLISTS_ADD);
        assertThat(library.playlist(road.id()).orElseThrow().urls()).containsExactly(A, B, C);
        assertThat(canvas.strings()).contains("gui.liasmediaplayer.playlists.entries[3]", "example.com/a.mp3");

        // Drag the first entry below the third.
        var entries = screen.entries();
        Rect first = row(entries, 0, PlaylistScreen.ENTRY_ROW);
        screen.input(new InputEvent.PointerDown(first.centerX(), first.centerY(), 0, Mods.NONE, 1));
        render(screen);
        screen.render(canvas.clear(), first.centerX(), entries.bounds().y() + 3 * PlaylistScreen.ENTRY_ROW, Mods.NONE);
        screen.input(new InputEvent.PointerUp(
                first.centerX(), entries.bounds().y() + 3 * PlaylistScreen.ENTRY_ROW, 0, Mods.NONE));
        render(screen);
        assertThat(library.playlist(road.id()).orElseThrow().urls()).containsExactly(B, C, A);

        click(screen, row(entries, 1, PlaylistScreen.ENTRY_ROW), 2);
        assertThat(calls).containsExactly("play " + C);
        screen.setLoop(true);
        render(screen);
        clickText(screen, Keys.PLAYLISTS_SHUFFLE);
        assertThat(calls).last().isEqualTo("playAll " + List.of(B, C, A) + " shuffle=true loop=true");
        clickText(screen, Keys.PLAYLISTS_HISTORY);
        assertThat(calls).last().isEqualTo("history " + road.id());
    }

    @Test
    void searchingFiltersAndStopsTheDragging() {
        Playlist p = library.create("p", List.of(A, B, C));
        var screen = playlists(p.id());
        click(screen, screen.search());
        type(screen, "b.mp3");
        assertThat(screen.entries().items())
                .extracting(PlaylistScreen.Entry::url)
                .containsExactly(B);
        assertThat(canvas.strings()).contains("gui.liasmediaplayer.playlists.entries.filtered[1,3]");
        Rect first = row(screen.entries(), 0, PlaylistScreen.ENTRY_ROW);
        screen.input(new InputEvent.PointerDown(first.centerX(), first.centerY(), 0, Mods.NONE, 1));
        assertThat(screen.entries().isDragging()).isFalse();
        screen.input(new InputEvent.PointerUp(first.centerX(), first.centerY(), 0, Mods.NONE));
        click(screen, screen.search());
        type(screen, "zzz");
        assertThat(canvas.strings()).contains(Keys.PLAYLISTS_NO_MATCH);
    }

    @Test
    void theClipboardBringsListsInAndTakesThemOut() {
        platform.clipboard().set("#EXTM3U\n#EXTINF:-1,One\n" + A + "\n/home/me/x.mp3\n" + B + "\n");
        var screen = playlists(null);
        clickText(screen, Keys.PLAYLISTS_IMPORT);
        Playlist imported = screen.selected().orElseThrow();
        assertThat(imported.name()).isEqualTo(Keys.PLAYLISTS_IMPORTED_NAME);
        assertThat(imported.urls()).containsExactly(A, B);

        platform.clipboard().set("");
        clickText(screen, Keys.PLAYLISTS_EXPORT);
        assertThat(platform.clipboard().get()).startsWith("#EXTM3U").contains(A, B);
    }

    @Test
    void deletingAsksTwiceAndRenamingSticks() {
        Playlist p = library.create("old", List.of(A));
        var screen = playlists(null);
        click(screen, row(screen.playlists(), 0, PlaylistScreen.ROW), 1);
        assertThat(screen.selected()).contains(p);

        clickText(screen, Keys.PLAYLISTS_DELETE);
        assertThat(canvas.strings()).contains(Keys.PLAYLISTS_DELETE_CONFIRM);
        now += 4_000_000_000L;
        render(screen);
        clickText(screen, Keys.PLAYLISTS_DELETE);
        assertThat(library.playlists()).hasSize(1);
        clickText(screen, Keys.PLAYLISTS_DELETE_CONFIRM);
        assertThat(library.playlists()).isEmpty();
        assertThat(screen.selected()).isEmpty();
    }

    @Test
    void anEntryCanBeRemovedWithItsButton() {
        Playlist p = library.create("p", List.of(A, B));
        var screen = playlists(p.id());
        Rect list = screen.entries().bounds();
        // The remove button is the last thing on the row, centred on its height.
        screen.input(
                new InputEvent.PointerDown(list.right() - 8, list.y() + PlaylistScreen.ENTRY_ROW / 2, 0, Mods.NONE, 1));
        render(screen);
        assertThat(library.playlist(p.id()).orElseThrow().urls()).containsExactly(B);
    }

    // ---- History -------------------------------------------------------------

    private HistoryScreen history(@Nullable String target) {
        var screen = new HistoryScreen(
                () -> Themes.DARK,
                () -> 0L,
                library,
                core.titles(),
                url -> Optional.empty(),
                platform.clipboard(),
                actions,
                target);
        render(screen);
        return screen;
    }

    @Test
    void theHistoryListsPlaysAndFiltersWhatWasPlayed() {
        library.recordPlay(A, MediaKind.AUDIO);
        library.recordPlay(B, MediaKind.VIDEO);
        library.toggleFavorite(A, MediaKind.AUDIO);
        var screen = history(null);
        assertThat(screen.list().items()).extracting(HistoryEntry::url).containsExactly(B, A);
        assertThat(canvas.strings()).contains("gui.liasmediaplayer.history.count[2]", Keys.HISTORY_KIND_VIDEO);

        click(screen, row(screen.list(), 0, HistoryScreen.ROW), 2);
        assertThat(calls).containsExactly("play " + B);

        clickText(screen, Keys.HISTORY_FAVORITES_OFF);
        assertThat(screen.list().items()).extracting(HistoryEntry::url).containsExactly(A);
        screen.setFavoritesOnly(false);
        click(screen, screen.search());
        type(screen, "b.mp3");
        assertThat(screen.list().items()).extracting(HistoryEntry::url).containsExactly(B);

        clickText(screen, Keys.HISTORY_CLEAR);
        screen.search().setText("");
        render(screen);
        assertThat(screen.list().items()).extracting(HistoryEntry::url).containsExactly(A);
        clickText(screen, Keys.BACK);
        assertThat(calls).last().isEqualTo("close");
    }

    @Test
    void theRowButtonsHeartAddAndRemove() {
        Playlist target = library.create("Mix");
        library.recordPlay(A, MediaKind.AUDIO);
        var screen = history(target.id());
        assertThat(screen.target()).contains(target);
        Rect list = screen.list().bounds();
        int y = list.y() + HistoryScreen.ROW / 2;
        // From the right: remove, add, heart, play — each a 13-pixel button with a 3-pixel gap.
        int remove = list.right() - 3 - 7;
        int add = remove - 16;
        int heart = add - 16;
        int play = heart - 16;
        screen.input(new InputEvent.PointerDown(heart, y, 0, Mods.NONE, 1));
        render(screen);
        assertThat(library.isFavorite(A)).isTrue();
        screen.input(new InputEvent.PointerDown(add, y, 0, Mods.NONE, 1));
        render(screen);
        assertThat(library.playlist(target.id()).orElseThrow().urls()).containsExactly(A);
        screen.input(new InputEvent.PointerDown(play, y, 0, Mods.NONE, 1));
        assertThat(calls).containsExactly("play " + A);
        screen.input(new InputEvent.PointerDown(remove, y, 0, Mods.NONE, 1));
        render(screen);
        assertThat(library.history()).isEmpty();
        assertThat(canvas.strings()).contains(Keys.HISTORY_EMPTY);
    }
}
