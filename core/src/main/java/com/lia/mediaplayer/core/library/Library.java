package com.lia.mediaplayer.core.library;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.WebUrls;
import com.lia.mediaplayer.core.storage.JsonStore;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * The player's playlists and history, kept in {@code library.json}. Every change is a new
 * {@link LibraryData}, written a moment later; {@link #version} moves with each one, so a
 * screen can tell when to read again. Client thread only.
 *
 * <p>The history holds at most {@value #MAX_HISTORY} ordinary entries, most recent first.
 * Favourites do not count against it and are never evicted: the heart is how an entry stops
 * being something that scrolls off the end.
 */
public final class Library {

    public static final int MAX_HISTORY = 200;

    private final JsonStore<LibraryData> store;
    private final InstantSource clock;
    private int version;

    public Library(JsonStore<LibraryData> store, InstantSource clock) {
        this.store = store;
        this.clock = clock;
    }

    /** Bumped by every change. */
    public int version() {
        return version;
    }

    // ---- Playlists ----------------------------------------------------------

    public List<Playlist> playlists() {
        return store.get().playlists();
    }

    public Optional<Playlist> playlist(String id) {
        return playlists().stream().filter(p -> p.id().equals(id)).findFirst();
    }

    /** Creates an empty playlist, last in the list. */
    public Playlist create(String name) {
        return create(name, List.of());
    }

    /** Creates a playlist holding {@code urls}, last in the list. */
    public Playlist create(String name, Collection<String> urls) {
        var playlist = new Playlist(LibraryCodec.newId(), name, List.copyOf(urls));
        change(data -> {
            var list = new ArrayList<>(data.playlists());
            list.add(playlist);
            return new LibraryData(list, data.history());
        });
        return playlist;
    }

    public void delete(String id) {
        change(data -> new LibraryData(
                data.playlists().stream().filter(p -> !p.id().equals(id)).toList(), data.history()));
    }

    public void rename(String id, String name) {
        edit(id, p -> p.withName(name));
    }

    public void add(String id, Collection<String> urls) {
        edit(id, p -> p.withAdded(urls));
    }

    public void removeEntry(String id, int index) {
        edit(id, p -> p.withRemoved(index));
    }

    public void moveEntry(String id, int from, int insertBefore) {
        edit(id, p -> p.withMoved(from, insertBefore));
    }

    private void edit(String id, UnaryOperator<Playlist> change) {
        change(data -> new LibraryData(
                data.playlists().stream()
                        .map(p -> p.id().equals(id) ? change.apply(p) : p)
                        .toList(),
                data.history()));
    }

    // ---- History ------------------------------------------------------------

    /** Most recently played first. */
    public List<HistoryEntry> history() {
        return store.get().history();
    }

    public boolean isFavorite(String url) {
        return find(url).map(HistoryEntry::favorite).orElse(false);
    }

    /**
     * Records that {@code url} started. An entry already there moves back to the top, keeping
     * its heart, rather than being listed twice.
     */
    public void recordPlay(String url, MediaKind kind) {
        if (!WebUrls.isWebUrl(url)) {
            return;
        }
        changeHistory(history -> {
            var existing = history.stream().filter(e -> e.url().equals(url)).findFirst();
            history.removeIf(e -> e.url().equals(url));
            history.addFirst(existing.map(e -> e.playedAt(clock.instant()))
                    .orElseGet(() -> new HistoryEntry(url, kind, clock.instant(), false)));
        });
    }

    /**
     * Flips the heart on {@code url}, adding it to the history if it is not there.
     *
     * @return whether it is a favourite now
     */
    public boolean toggleFavorite(String url, MediaKind kind) {
        if (!WebUrls.isWebUrl(url)) {
            return false;
        }
        boolean now = !isFavorite(url);
        changeHistory(history -> {
            for (int i = 0; i < history.size(); i++) {
                if (history.get(i).url().equals(url)) {
                    history.set(i, history.get(i).withFavorite(now));
                    return;
                }
            }
            history.addFirst(new HistoryEntry(url, kind, clock.instant(), now));
        });
        return now;
    }

    public void removeFromHistory(String url) {
        changeHistory(history -> history.removeIf(e -> e.url().equals(url)));
    }

    /** Empties the history except for the favourites, which were kept on purpose. */
    public void clearHistory() {
        changeHistory(history -> history.removeIf(e -> !e.favorite()));
    }

    private Optional<HistoryEntry> find(String url) {
        return history().stream().filter(e -> e.url().equals(url)).findFirst();
    }

    private void changeHistory(Consumer<List<HistoryEntry>> change) {
        change(data -> {
            var history = new ArrayList<>(data.history());
            change.accept(history);
            trim(history);
            return new LibraryData(data.playlists(), history);
        });
    }

    /** Drops the oldest ordinary entries past {@link #MAX_HISTORY}, never a favourite. */
    static void trim(List<HistoryEntry> history) {
        long ordinary = history.stream().filter(e -> !e.favorite()).count();
        for (int i = history.size() - 1; i >= 0 && ordinary > MAX_HISTORY; i--) {
            if (!history.get(i).favorite()) {
                history.remove(i);
                ordinary--;
            }
        }
    }

    private void change(UnaryOperator<LibraryData> change) {
        LibraryData before = store.get();
        store.update(change);
        if (!store.get().equals(before)) {
            version++;
        }
    }
}
