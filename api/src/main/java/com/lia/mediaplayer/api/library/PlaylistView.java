package com.lia.mediaplayer.api.library;

import java.util.List;

/**
 * One of the player's playlists, as it was when read.
 *
 * @param id stable across renames; names need not be unique
 * @since 4.0.0
 */
public record PlaylistView(String id, String name, List<String> urls) {

    public PlaylistView {
        urls = List.copyOf(urls);
    }
}
