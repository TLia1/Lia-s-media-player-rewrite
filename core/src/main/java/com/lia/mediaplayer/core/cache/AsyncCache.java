package com.lia.mediaplayer.core.cache;

import com.lia.mediaplayer.core.diag.jfr.CacheLoadEvent;
import com.lia.mediaplayer.core.lifecycle.ClientThread;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import org.jspecify.annotations.Nullable;

/**
 * Values that take a while to make, kept by weight, least recently used first out. Client
 * thread only; {@link #get} never blocks.
 *
 * <p>The first {@code get} of a key starts its load and answers {@link Entry.Loading}; later
 * calls share that load. The result is settled on the client thread, which is also where an
 * evicted value is handed to the eviction hook (a texture is released there). A value whose
 * key was evicted or cleared while it loaded is handed to the hook as soon as it arrives.
 * Failures are kept, weightless, so a broken link is not fetched again every frame; {@link
 * #invalidate} forgets one.
 */
public final class AsyncCache<K, V> {

    /** What {@link #get} knows about a key. */
    public sealed interface Entry<V> {

        record Loading<V>() implements Entry<V> {}

        record Ready<V>(V value) implements Entry<V> {}

        record Failed<V>(Throwable cause) implements Entry<V> {}
    }

    private final String name;
    private final ClientThread clientThread;
    private final Function<K, CompletableFuture<V>> loader;
    private final ToLongFunction<V> weigher;
    private final Consumer<V> onEvict;
    private final int maxEntries;
    private final LinkedHashMap<K, Slot<V>> slots = new LinkedHashMap<>(16, 0.75f, true);
    private long maxWeight;
    private long weight;

    /**
     * @param name what the cache holds, for the flight recorder's {@code CacheLoad} events
     * @param loader starts making the value of a key; called on the client thread, must not
     *     block
     * @param weigher what a value costs against {@code maxWeight}
     * @param onEvict releases a value that left the cache
     * @param maxEntries caps the keys kept, loading and failed ones included
     */
    public AsyncCache(
            String name,
            ClientThread clientThread,
            Function<K, CompletableFuture<V>> loader,
            ToLongFunction<V> weigher,
            Consumer<V> onEvict,
            long maxWeight,
            int maxEntries) {
        if (maxEntries < 1 || maxWeight < 0) {
            throw new IllegalArgumentException("Limits must be positive");
        }
        this.name = name;
        this.clientThread = clientThread;
        this.loader = loader;
        this.weigher = weigher;
        this.onEvict = onEvict;
        this.maxWeight = maxWeight;
        this.maxEntries = maxEntries;
    }

    /** The entry of {@code key}, starting its load if it has none. Counts as a use. */
    public Entry<V> get(K key) {
        clientThread.checkCurrent();
        Slot<V> slot = slots.get(key);
        if (slot == null) {
            slot = new Slot<>();
            slots.put(key, slot);
            Slot<V> started = slot;
            started.event = CacheLoadEvent.start(name, key);
            CompletableFuture<V> future;
            try {
                future = loader.apply(key);
            } catch (RuntimeException e) {
                future = CompletableFuture.failedFuture(e);
            }
            started.future = future;
            var unused = future.whenComplete(
                    (value, error) -> clientThread.execute(() -> settle(key, started, value, error)));
            evict(started);
        }
        return slot.entry;
    }

    /** The entry of {@code key}, without loading it or counting a use. */
    public Optional<Entry<V>> peek(K key) {
        Slot<V> slot = slots.get(key);
        return slot == null ? Optional.empty() : Optional.of(slot.entry);
    }

    /** Forgets {@code key}: the next {@link #get} loads it again. */
    public void invalidate(K key) {
        clientThread.checkCurrent();
        Slot<V> slot = slots.remove(key);
        if (slot != null) {
            discard(slot);
        }
    }

    /** Forgets everything. */
    public void clear() {
        for (Slot<V> slot : new ArrayList<>(slots.values())) {
            discard(slot);
        }
        slots.clear();
        weight = 0;
    }

    /** Calls {@code action} with every ready value, least recently used first. */
    public void forEachReady(Consumer<? super V> action) {
        for (Slot<V> slot : new ArrayList<>(slots.values())) {
            if (slot.entry instanceof Entry.Ready<V> ready) {
                action.accept(ready.value());
            }
        }
    }

    /** Changes the weight limit, evicting at once if it shrank. */
    public void setMaxWeight(long newMax) {
        maxWeight = newMax;
        evict(null);
    }

    public long weight() {
        return weight;
    }

    public int size() {
        return slots.size();
    }

    private void settle(K key, Slot<V> slot, @Nullable V value, @Nullable Throwable error) {
        CacheLoadEvent event = slot.event;
        slot.event = null;
        if (event != null) {
            event.finish(value == null ? 0 : weigher.applyAsLong(value), error == null && value != null);
        }
        if (slots.get(key) != slot || slot.discarded) {
            // Evicted or cleared while it loaded.
            if (value != null) {
                onEvict.accept(value);
            }
            return;
        }
        if (error != null || value == null) {
            slot.entry = new Entry.Failed<>(unwrap(error));
            return;
        }
        slot.weight = weigher.applyAsLong(value);
        slot.entry = new Entry.Ready<>(value);
        weight += slot.weight;
        evict(slot);
    }

    /** Evicts least recently used slots until within the limits, never {@code keep}. */
    private void evict(@Nullable Slot<V> keep) {
        Iterator<Map.Entry<K, Slot<V>>> it = slots.entrySet().iterator();
        while (it.hasNext() && (weight > maxWeight || slots.size() > maxEntries)) {
            Slot<V> slot = it.next().getValue();
            if (slot == keep) {
                continue;
            }
            it.remove();
            discard(slot);
        }
    }

    private void discard(Slot<V> slot) {
        slot.discarded = true;
        weight -= slot.weight;
        slot.weight = 0;
        if (slot.entry instanceof Entry.Ready<V> ready) {
            onEvict.accept(ready.value());
        } else if (slot.future != null) {
            slot.future.cancel(false);
        }
        slot.entry = new Entry.Loading<>();
    }

    private static Throwable unwrap(@Nullable Throwable error) {
        if (error == null) {
            return new IllegalStateException("Loaded nothing");
        }
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause instanceof UncheckedIOException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static final class Slot<V> {
        Entry<V> entry = new Entry.Loading<>();

        @Nullable
        CompletableFuture<V> future;

        long weight;
        boolean discarded;

        @Nullable
        CacheLoadEvent event;
    }
}
