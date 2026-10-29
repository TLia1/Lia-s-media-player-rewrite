package com.lia.mediaplayer.core.testing;

import com.lia.mediaplayer.core.lifecycle.ClientThread;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A real thread standing in for the game's client thread: tasks run on it in order, and only
 * it answers {@code true} to {@link #isCurrent()}, so engine code that must not block it is
 * held to that.
 */
public final class TestClientThread implements ClientThread, AutoCloseable {

    private volatile Thread thread = Thread.currentThread();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread created = Thread.ofPlatform().name("test-client").daemon().unstarted(task);
        thread = created;
        return created;
    });

    public TestClientThread() {
        // Start the thread now, so isCurrent() is false on the caller from the beginning.
        call(() -> null);
    }

    @Override
    public void execute(Runnable task) {
        executor.execute(task);
    }

    @Override
    public boolean isCurrent() {
        return Thread.currentThread() == thread;
    }

    /** Runs {@code task} on the client thread and waits for its result. */
    public <T> T call(Callable<T> task) {
        try {
            return executor.submit(task).get(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Client task failed", e);
        }
    }

    public void run(Runnable task) {
        call(() -> {
            task.run();
            return null;
        });
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
