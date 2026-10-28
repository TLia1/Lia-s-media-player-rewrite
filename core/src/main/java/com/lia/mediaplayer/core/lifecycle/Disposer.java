package com.lia.mediaplayer.core.lifecycle;

import java.util.ArrayDeque;
import java.util.Deque;
import org.jspecify.annotations.Nullable;

/**
 * Closes the resources registered with it, in reverse order of registration.
 *
 * <p>A service that owns other closeable things (threads, processes, textures, files) adds
 * each one here as it creates it, and closes the disposer in its own {@code close()}. Reverse
 * order means a resource is always closed before the things it was built from.
 *
 * <p>Every resource is closed even when an earlier one throws: the first failure is rethrown
 * with the later ones attached as suppressed exceptions. Closing twice is a no-op. Not
 * thread-safe; confine it to the thread that owns the service.
 */
public final class Disposer implements AutoCloseable {

    private final Deque<AutoCloseable> resources = new ArrayDeque<>();
    private boolean closed;

    /**
     * Registers {@code resource} to be closed with this disposer, and returns it so creation
     * and registration can be one expression.
     *
     * @throws IllegalStateException if this disposer is already closed
     */
    public <T extends AutoCloseable> T add(T resource) {
        if (closed) {
            throw new IllegalStateException("Disposer is already closed");
        }
        resources.push(resource);
        return resource;
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        @Nullable RuntimeException failure = null;
        while (!resources.isEmpty()) {
            try {
                resources.pop().close();
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                if (failure == null) {
                    failure = new DisposeException("Failed to close a resource", e);
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /** Thrown by {@link #close()} when at least one resource failed to close. */
    public static final class DisposeException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        DisposeException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
