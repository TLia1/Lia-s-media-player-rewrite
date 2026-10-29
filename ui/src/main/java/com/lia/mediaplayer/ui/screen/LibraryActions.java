package com.lia.mediaplayer.ui.screen;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** What the playlist and history screens ask of the rest of the UI. */
public interface LibraryActions {

    /** Plays {@code urls} in order in a new audio bar, shuffled and looping if asked. */
    void playAll(List<String> urls, boolean shuffle, boolean loop);

    /** Plays one link as a click on it in chat would. */
    void play(String url);

    /** Opens the history over the screen that is open; {@code playlistId} is where its "add" button adds to. */
    void openHistory(@Nullable String playlistId);

    /** Closes the screen that is open, back to the one under it. */
    void close();
}
