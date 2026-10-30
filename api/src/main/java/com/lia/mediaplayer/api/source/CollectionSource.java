package com.lia.mediaplayer.api.source;

import com.lia.mediaplayer.api.media.MediaKind;

/**
 * A source whose links are {@link MediaKind#COLLECTION}s, and says what their entries are: a
 * SoundCloud set is a list of sounds and opens the audio bar, a YouTube playlist a list of
 * videos. A collection source that does not implement this plays as videos.
 *
 * <p>The entries are found by yt-dlp's playlist reading, capped at 500.
 *
 * @since 4.0.0
 */
public interface CollectionSource extends LinkSource {

    /** What the entries are: {@link MediaKind#VIDEO} or {@link MediaKind#AUDIO}. */
    MediaKind entryKind();
}
