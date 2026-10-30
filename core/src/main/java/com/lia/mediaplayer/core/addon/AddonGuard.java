package com.lia.mediaplayer.core.addon;

import com.lia.mediaplayer.api.Ids;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every call into addon code goes through here, so a bug in an addon is logged and contained,
 * never thrown at the game. Calls are counted per registration (its namespaced id, whose owner
 * is the addon): the first failure is logged with its stack trace, and once a registration has
 * failed {@link #limit} times it is disabled with a warning and never called again.
 *
 * <p>Thread-safe: link sources run where chat is shown, resolvers on the IO executor.
 */
public final class AddonGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger(AddonGuard.class);

    public static final int DEFAULT_LIMIT = 10;

    private final int limit;
    private final Map<String, AtomicInteger> failures = new ConcurrentHashMap<>();

    public AddonGuard() {
        this(DEFAULT_LIMIT);
    }

    /** @param limit failures after which a registration is disabled */
    public AddonGuard(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1, was " + limit);
        }
        this.limit = limit;
    }

    /**
     * Runs {@code action} for the registration {@code owner}.
     *
     * @return whether it ran and returned normally
     */
    public boolean run(String owner, Runnable action) {
        if (isDisabled(owner)) {
            return false;
        }
        try {
            action.run();
            return true;
        } catch (RuntimeException | LinkageError e) {
            failed(owner, e);
            return false;
        }
    }

    /**
     * Calls {@code action} for the registration {@code owner}.
     *
     * @return its answer, or {@code fallback} if it failed, answered {@code null}, or the
     *     registration is disabled
     */
    public <T> T call(String owner, Supplier<? extends T> action, T fallback) {
        if (isDisabled(owner)) {
            return fallback;
        }
        try {
            T value = action.get();
            return value == null ? fallback : value;
        } catch (RuntimeException | LinkageError e) {
            failed(owner, e);
            return fallback;
        }
    }

    public boolean isDisabled(String owner) {
        AtomicInteger count = failures.get(owner);
        return count != null && count.get() >= limit;
    }

    /** How often {@code owner} has failed. */
    public int failures(String owner) {
        AtomicInteger count = failures.get(owner);
        return count == null ? 0 : count.get();
    }

    private void failed(String owner, Throwable error) {
        int count =
                failures.computeIfAbsent(owner, unused -> new AtomicInteger()).incrementAndGet();
        String addon = Ids.isNamespaced(owner) ? Ids.owner(owner) : owner;
        if (count == 1) {
            LOGGER.error("Addon {} failed in {}", addon, owner, error);
        }
        if (count == limit) {
            LOGGER.warn("Addon {}: {} failed {} times and is disabled until the game restarts", addon, owner, count);
        }
    }
}
