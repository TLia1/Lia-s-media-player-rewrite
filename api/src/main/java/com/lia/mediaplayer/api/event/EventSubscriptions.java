package com.lia.mediaplayer.api.event;

import java.util.function.Consumer;

/**
 * Where an addon listens to playbacks. Listeners run on the client thread, in the order events
 * happened; one that throws is logged and keeps its subscription.
 *
 * @since 4.0.0
 */
public interface EventSubscriptions {

    /** Calls {@code listener} for every later event of type {@code type}. */
    <E extends MediaEvent> Subscription on(Class<E> type, Consumer<? super E> listener);

    /** Calls {@code listener} for every later event. */
    default Subscription onAny(Consumer<? super MediaEvent> listener) {
        return on(MediaEvent.class, listener);
    }
}
