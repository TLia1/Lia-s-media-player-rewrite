package com.lia.mediaplayer.core.addon;

import com.lia.mediaplayer.api.library.HistoryView;
import com.lia.mediaplayer.api.library.LibraryView;
import com.lia.mediaplayer.api.library.PlaylistView;
import com.lia.mediaplayer.core.library.Library;
import java.util.List;

/** The API's read-only {@link LibraryView} over the library. */
public final class LibraryReader implements LibraryView {

    private final Library library;

    public LibraryReader(Library library) {
        this.library = library;
    }

    @Override
    public List<PlaylistView> playlists() {
        return library.playlists().stream()
                .map(playlist -> new PlaylistView(playlist.id(), playlist.name(), playlist.urls()))
                .toList();
    }

    @Override
    public List<HistoryView> history() {
        return library.history().stream()
                .map(entry -> new HistoryView(entry.url(), entry.kind(), entry.lastPlayed(), entry.favorite()))
                .toList();
    }

    @Override
    public boolean isFavorite(String url) {
        return library.isFavorite(url);
    }
}
