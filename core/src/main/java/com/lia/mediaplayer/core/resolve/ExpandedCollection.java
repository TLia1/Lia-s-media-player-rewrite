package com.lia.mediaplayer.core.resolve;

import com.lia.mediaplayer.api.source.MediaRef;
import java.util.List;
import java.util.Optional;

/**
 * A playlist, set or album, expanded into the links it lists.
 *
 * @param entries in playlist order, at most {@value CollectionExpander#MAX_ENTRIES}
 */
public record ExpandedCollection(Optional<String> title, List<MediaRef> entries) {

    public ExpandedCollection {
        entries = List.copyOf(entries);
    }
}
