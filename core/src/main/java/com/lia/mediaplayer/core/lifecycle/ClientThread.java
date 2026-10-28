package com.lia.mediaplayer.core.lifecycle;

import java.util.concurrent.Executor;

/**
 * The game's client thread: where rendering, input, textures and every UI object live.
 *
 * <p>Background work hands its result back through {@link #execute(Runnable)}. Nothing
 * outside the platform layer reaches the game's own executor.
 */
public interface ClientThread extends Executor {

    /** Whether the calling thread is the client thread. */
    boolean isCurrent();

    /**
     * Fails fast when called off the client thread.
     *
     * @throws IllegalStateException if the calling thread is not the client thread
     */
    default void checkCurrent() {
        if (!isCurrent()) {
            throw new IllegalStateException("Must be called on the client thread, not "
                    + Thread.currentThread().getName());
        }
    }
}
