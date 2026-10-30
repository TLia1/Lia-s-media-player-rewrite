package com.lia.mediaplayer.api.policy;

import com.lia.mediaplayer.api.source.MediaRef;
import java.util.Optional;

/**
 * An addon's say over what the mod does with links. Every interceptor is asked; the first
 * {@code false} wins. Both methods default to allowing, so an interceptor overrides the one it
 * cares about.
 *
 * <p>The player's own filter (blocked hosts and senders) is applied first: an interceptor can
 * refuse more, never allow what the player refused. An interceptor that throws is taken to
 * allow, and is removed if it keeps failing.
 *
 * @since 4.0.0
 */
public interface Interceptor {

    /** Namespaced, {@code "myaddon:no_ads"}. */
    String id();

    /**
     * Whether a link in a chat message becomes a label. Called for every recognised link of every
     * message shown, on the client thread: no IO, no blocking.
     *
     * @param sender the name of the player who sent the message, empty for a server message
     */
    default boolean allowChatLink(MediaRef ref, Optional<String> sender) {
        return true;
    }

    /** Whether {@code ref} may play, or be pinned for a picture. Client thread. */
    default boolean allowPlay(MediaRef ref, PlayOrigin origin) {
        return true;
    }
}
