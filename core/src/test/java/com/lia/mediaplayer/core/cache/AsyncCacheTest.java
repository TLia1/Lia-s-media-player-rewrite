package com.lia.mediaplayer.core.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lia.mediaplayer.core.lifecycle.ClientThread;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class AsyncCacheTest {

    /** A client thread that is the test's own, with a queue the test drains. */
    private final Queue<Runnable> tasks = new ArrayDeque<>();

    private final ClientThread clientThread = new ClientThread() {
        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        @Override
        public boolean isCurrent() {
            return true;
        }
    };

    private final Map<String, CompletableFuture<String>> loads = new HashMap<>();
    private final List<String> evicted = new ArrayList<>();
    private int started;

    private AsyncCache<String, String> cache(long maxWeight, int maxEntries) {
        return new AsyncCache<>(
                clientThread,
                key -> {
                    started++;
                    return loads.computeIfAbsent(key, k -> new CompletableFuture<>());
                },
                String::length,
                evicted::add,
                maxWeight,
                maxEntries);
    }

    private void runTasks() {
        Runnable task;
        while ((task = tasks.poll()) != null) {
            task.run();
        }
    }

    @Test
    void loadsOnceAndAnswersWhenTheValueArrives() {
        var cache = cache(100, 10);
        assertThat(cache.get("a")).isInstanceOf(AsyncCache.Entry.Loading.class);
        assertThat(cache.get("a")).isInstanceOf(AsyncCache.Entry.Loading.class);
        assertThat(started).isEqualTo(1);

        loads.get("a").complete("apple");
        assertThat(cache.get("a")).as("settled on the client thread").isInstanceOf(AsyncCache.Entry.Loading.class);
        runTasks();

        assertThat(cache.get("a")).isEqualTo(new AsyncCache.Entry.Ready<>("apple"));
        assertThat(cache.weight()).isEqualTo(5);
        assertThat(cache.peek("b")).isEmpty();
    }

    @Test
    void keepsAFailureUntilItIsInvalidated() {
        var cache = cache(100, 10);
        cache.get("a");
        loads.remove("a").completeExceptionally(new IOException("404"));
        runTasks();

        assertThat(cache.get("a"))
                .isInstanceOfSatisfying(
                        AsyncCache.Entry.Failed.class,
                        failed -> assertThat(failed.cause()).hasMessage("404"));
        assertThat(started).isEqualTo(1);

        cache.invalidate("a");
        assertThat(cache.get("a")).isInstanceOf(AsyncCache.Entry.Loading.class);
        assertThat(started).isEqualTo(2);
    }

    @Test
    void evictsTheLeastRecentlyUsedBeyondTheWeight() {
        var cache = cache(10, 10);
        for (String key : List.of("a", "b", "c")) {
            cache.get(key);
            loads.get(key).complete(key.repeat(4));
            runTasks();
        }
        // 4 + 4 fit; the third pushes out the oldest.
        assertThat(evicted).containsExactly("aaaa");
        assertThat(cache.peek("a")).isEmpty();

        // Using b makes c the oldest.
        cache.get("b");
        cache.get("d");
        loads.get("d").complete("dddd");
        runTasks();
        assertThat(evicted).containsExactly("aaaa", "cccc");
        assertThat(cache.weight()).isEqualTo(8);

        cache.setMaxWeight(4);
        assertThat(evicted).containsExactly("aaaa", "cccc", "bbbb");
    }

    @Test
    void aValueTooBigForTheCacheIsStillKeptAlone() {
        var cache = cache(3, 10);
        cache.get("a");
        loads.get("a").complete("huge value");
        runTasks();
        assertThat(cache.get("a")).isEqualTo(new AsyncCache.Entry.Ready<>("huge value"));
    }

    @Test
    void aValueThatArrivesAfterItsKeyWasDroppedIsReleased() {
        var cache = cache(100, 2);
        cache.get("a");
        cache.get("b");
        cache.get("c");
        assertThat(cache.size()).isEqualTo(2);
        assertThat(loads.get("a")).isCancelled();

        // Arrived, but not yet settled on the client thread, when the cache was cleared.
        cache.get("d");
        loads.get("d").complete("late");
        cache.clear();
        runTasks();
        assertThat(evicted).containsExactly("late");
        assertThat(cache.size()).isZero();
        assertThat(cache.weight()).isZero();
    }

    @Test
    void aLoaderThatThrowsIsAFailure() {
        var cache = new AsyncCache<String, String>(
                clientThread,
                key -> {
                    throw new IllegalStateException("broken");
                },
                String::length,
                evicted::add,
                10,
                10);
        cache.get("a");
        runTasks();
        assertThat(cache.get("a")).isInstanceOf(AsyncCache.Entry.Failed.class);
        assertThatThrownBy(() -> new AsyncCache<String, String>(
                        clientThread, k -> new CompletableFuture<>(), String::length, v -> {}, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void visitsTheReadyValues() {
        var cache = cache(100, 10);
        cache.get("a");
        cache.get("b");
        loads.get("b").complete("bee");
        runTasks();
        var seen = new ArrayList<String>();
        cache.forEachReady(seen::add);
        assertThat(seen).containsExactly("bee");
    }
}
