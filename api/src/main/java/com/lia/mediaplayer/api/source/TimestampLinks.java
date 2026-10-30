package com.lia.mediaplayer.api.source;

import java.time.Duration;
import java.util.Optional;

/**
 * A source that can say how to link to a moment of its media: what the copy button puts on the
 * clipboard when Shift is held. A {@link LinkSource} implements it beside {@link
 * LinkSource#match}; the mod asks it only for the refs that source claimed.
 *
 * @since 4.0.0
 */
public interface TimestampLinks {

    /**
     * The link that opens {@code ref} at {@code position}, or empty when there is none (a live
     * stream, a position of zero). Cheap, no IO, client thread.
     */
    Optional<String> linkAt(MediaRef ref, Duration position);
}
