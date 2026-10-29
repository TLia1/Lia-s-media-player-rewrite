package com.lia.mediaplayer.core.lifecycle;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The engine's background threads, owned by the composition root.
 *
 * <ul>
 *   <li>{@link #io()}: virtual threads for blocking work (HTTP, yt-dlp, ffprobe, file
 *       writes), at most {@value #IO_PERMITS} running at once so a burst of thumbnails
 *       cannot open a hundred connections. Submitting never blocks; a task waits for its
 *       permit on its own virtual thread.
 *   <li>{@link #decode()}: platform threads for CPU-heavy decoding, so long computations
 *       do not sit on the carriers the virtual threads share.
 * </ul>
 *
 * <p>Threads that live as long as a player (its actor, a session's readers, the audio pump)
 * are started by their owners through {@link #virtualThread} and {@link #platformThread},
 * so every thread in the mod carries an {@code lmp-} name.
 */
public final class CoreExecutors implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoreExecutors.class);
    static final int IO_PERMITS = 8;
    private static final Duration SHUTDOWN_GRACE = Duration.ofSeconds(2);

    private final ExecutorService ioThreads = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("lmp-io-", 0).factory());
    private final Semaphore ioPermits = new Semaphore(IO_PERMITS);
    private final Executor io = this::runIo;
    private final ExecutorService decode;

    public CoreExecutors() {
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        this.decode = Executors.newFixedThreadPool(threads, daemonFactory("lmp-decode-"));
    }

    /** Blocking IO. Never touches textures or UI objects. */
    public Executor io() {
        return io;
    }

    /** CPU-heavy decoding. */
    public Executor decode() {
        return decode;
    }

    /** Starts a named virtual thread, for work that blocks on IO for as long as its owner lives. */
    public static Thread virtualThread(String name, Runnable task) {
        return Thread.ofVirtual().name(name).start(task);
    }

    /**
     * Starts a named daemon platform thread. For loops that block in native code, where a
     * virtual thread would pin its carrier: the audio pump writing to the sound card.
     */
    // The audio pump asks for a higher priority so a busy game does not starve the sound card;
    // nothing depends on it for correctness.
    @SuppressWarnings("ThreadPriorityCheck")
    public static Thread platformThread(String name, int priority, Runnable task) {
        return Thread.ofPlatform().name(name).daemon().priority(priority).start(task);
    }

    private void runIo(Runnable task) {
        ioThreads.execute(() -> {
            try {
                ioPermits.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                task.run();
            } finally {
                ioPermits.release();
            }
        });
    }

    private static ThreadFactory daemonFactory(String prefix) {
        var counter = new AtomicInteger();
        return task -> Thread.ofPlatform()
                .name(prefix + counter.getAndIncrement())
                .daemon()
                .unstarted(task);
    }

    /** Interrupts what is still running and waits briefly for it to stop. */
    @Override
    public void close() {
        ioThreads.shutdownNow();
        decode.shutdownNow();
        try {
            long deadline = System.nanoTime() + SHUTDOWN_GRACE.toNanos();
            boolean stopped = ioThreads.awaitTermination(SHUTDOWN_GRACE.toNanos(), TimeUnit.NANOSECONDS)
                    && decode.awaitTermination(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            if (!stopped) {
                LOGGER.warn("Background tasks still running at shutdown");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
