package com.lia.mediaplayer.core.link;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.LinkSource;

/**
 * A source whose links are {@link MediaKind#COLLECTION}s, and says what their entries are: a
 * SoundCloud set is a list of sounds and opens the audio bar, a YouTube playlist a list of
 * videos. Read through {@link SourceRegistry#playsAs}, so nothing asks "is this SoundCloud?".
 */
public interface CollectionSource extends LinkSource {

    /** What the entries are: {@link MediaKind#VIDEO} or {@link MediaKind#AUDIO}. */
    MediaKind entryKind();
}
