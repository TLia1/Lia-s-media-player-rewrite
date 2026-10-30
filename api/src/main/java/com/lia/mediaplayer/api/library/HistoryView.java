package com.lia.mediaplayer.api.library;

import com.lia.mediaplayer.api.media.MediaKind;
import java.time.Instant;

/**
 * One line of the play history. Personal data: what the player watched and when. Do not send it
 * anywhere without asking them.
 *
 * @param favorite hearted; favourites are never dropped from the history
 * @since 4.0.0
 */
public record HistoryView(String url, MediaKind kind, Instant lastPlayed, boolean favorite) {}
