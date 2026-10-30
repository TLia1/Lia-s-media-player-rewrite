package com.lia.mediaplayer.api.library;

import java.util.List;

/**
 * The player's playlists and history, read only. Safe from any thread; each call reads the
 * library as it is now.
 *
 * @since 4.0.0
 */
public interface LibraryView {

    List<PlaylistView> playlists();

    /** Most recent first. */
    List<HistoryView> history();

    boolean isFavorite(String url);
}
