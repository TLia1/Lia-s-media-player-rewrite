package com.lia.mediaplayer.core.library;

import com.lia.mediaplayer.api.media.MediaKind;
import java.time.Instant;

/**
 * One link that was played. Only the link is kept, never a title, which would go stale the day
 * the video is renamed; titles are looked up for the rows on screen.
 *
 * @param kind how it played: a picture, a video, or a sound
 * @param favorite marked with the heart: never evicted, and kept when the history is cleared
 */
public record HistoryEntry(String url, MediaKind kind, Instant lastPlayed, boolean favorite) {

    public HistoryEntry withFavorite(boolean value) {
        return new HistoryEntry(url, kind, lastPlayed, value);
    }

    public HistoryEntry playedAt(Instant when) {
        return new HistoryEntry(url, kind, when, favorite);
    }
}
