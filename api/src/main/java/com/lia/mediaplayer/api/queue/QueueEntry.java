package com.lia.mediaplayer.api.queue;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.WebUrls;

/**
 * A track waiting in a queue.
 *
 * @param sourceId the source that recognised the link, e.g. {@code "liasmediaplayer:youtube"}
 * @since 4.0.0
 */
public record QueueEntry(String url, MediaKind kind, String sourceId) {

    public QueueEntry {
        WebUrls.require(url);
    }
}
