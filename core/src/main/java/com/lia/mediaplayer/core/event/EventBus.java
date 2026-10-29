package com.lia.mediaplayer.core.event;

import com.lia.mediaplayer.core.lifecycle.ClientThread;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Delivers engine events to listeners on the client thread.
 *
 * <p>{@link #post} may be called from any thread and returns at once. Listeners subscribe
 * to a type and receive every event that is an instance of it, in the order events were
 * posted. A listener that throws is logged and keeps its subscription.
 */
public final class EventBus {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventBus.class);

    private final ClientThread clientThread;
    private final List<Subscriber<?>> subscribers = new CopyOnWriteArrayList<>();

    public EventBus(ClientThread clientThread) {
        this.clientThread = clientThread;
    }

    /**
     * Calls {@code listener} on the client thread for every later event of type {@code type}.
     *
     * @return closing it ends the subscription
     */
    public <E> Subscription subscribe(Class<E> type, Consumer<? super E> listener) {
        var subscriber = new Subscriber<>(type, listener);
        subscribers.add(subscriber);
        return () -> subscribers.remove(subscriber);
    }

    /** Queues {@code event} for delivery on the client thread. */
    public void post(Object event) {
        clientThread.execute(() -> deliver(event));
    }

    private void deliver(Object event) {
        for (var subscriber : subscribers) {
            subscriber.offer(event);
        }
    }

    /** An active subscription. Closing it twice is harmless. */
    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        @Override
        void close();
    }

    private record Subscriber<E>(Class<E> type, Consumer<? super E> listener) {
        void offer(Object event) {
            if (!type.isInstance(event)) {
                return;
            }
            try {
                listener.accept(type.cast(event));
            } catch (RuntimeException e) {
                LOGGER.error("Listener for {} failed on {}", type.getSimpleName(), event, e);
            }
        }
    }
}
