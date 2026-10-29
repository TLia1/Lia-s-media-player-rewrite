package com.lia.mediaplayer.core.library;

import java.util.List;

/**
 * Everything in {@code library.json}.
 *
 * @param history most recently played first
 */
public record LibraryData(List<Playlist> playlists, List<HistoryEntry> history) {

    public static final LibraryData EMPTY = new LibraryData(List.of(), List.of());

    public LibraryData {
        playlists = List.copyOf(playlists);
        history = List.copyOf(history);
    }
}
