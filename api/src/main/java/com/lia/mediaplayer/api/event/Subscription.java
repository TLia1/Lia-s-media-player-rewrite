package com.lia.mediaplayer.api.event;

/**
 * A listener's registration. Closing it ends the subscription; closing it twice is harmless.
 *
 * @since 4.0.0
 */
@FunctionalInterface
public interface Subscription extends AutoCloseable {

    @Override
    void close();
}
