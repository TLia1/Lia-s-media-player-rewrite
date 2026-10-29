package com.lia.mediaplayer.api.source;

import java.net.URI;
import java.util.Optional;

/**
 * Recognises one family of links (a site, a file type) and claims them as a {@link MediaRef}.
 * Sources are asked in descending {@link #priority()}; the first match wins, so a link
 * belongs to exactly one source.
 */
public interface LinkSource {

    /** Namespaced id, e.g. {@code "liasmediaplayer:youtube"}. Becomes {@link MediaRef#sourceId()}. */
    String id();

    /**
     * Claims {@code raw} if it is one of this source's links. Called for every link in every
     * chat message, so it must be cheap: no IO, no blocking, thread-safe.
     *
     * @param uri {@code raw}, already parsed and known to pass {@link WebUrls#isWebUrl}
     */
    Optional<MediaRef> match(URI uri, String raw);

    /** Higher is asked first. Built-in sources use 0, or 10 for the narrower of two overlapping ones. */
    default int priority() {
        return 0;
    }
}
