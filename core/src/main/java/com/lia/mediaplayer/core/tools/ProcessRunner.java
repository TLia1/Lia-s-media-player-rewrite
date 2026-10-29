package com.lia.mediaplayer.core.tools;

import com.lia.mediaplayer.core.lifecycle.ClientThread;
import com.lia.mediaplayer.core.lifecycle.CoreExecutors;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The only class that starts processes.
 *
 * <p>Every program it starts has its stderr drained into a bounded tail, never reads from
 * stdin, and is killed together with its descendants when the runner closes or the JVM
 * exits — ffmpeg never outlives the game. Starting or waiting for a program on the client
 * thread is a bug, and fails at once.
 */
public final class ProcessRunner implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessRunner.class);

    private final ClientThread clientThread;
    private final Set<ManagedProcess> running = ConcurrentHashMap.newKeySet();
    private final Thread shutdownHook = new Thread(this::killAll, "lmp-process-reaper");
    private volatile boolean closed;

    public ProcessRunner(ClientThread clientThread) {
        this.clientThread = clientThread;
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    /**
     * Starts {@code command} and returns at once. The caller reads its stdout and closes it.
     *
     * @param name a short label for thread names and logs, e.g. {@code "session-3.1"}
     */
    public ManagedProcess start(String name, List<String> command) throws IOException {
        checkNotClientThread();
        if (closed) {
            throw new IOException("Process runner is closed");
        }
        Process process;
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException e) {
            throw new IOException("Cannot run program '" + command.getFirst() + "': " + e.getMessage(), e);
        }
        // Nothing is ever typed into these programs; an immediate end of input makes one
        // that asks for it fail instead of waiting forever.
        process.getOutputStream().close();
        var managed = new ManagedProcess(name, process, running::remove);
        running.add(managed);
        if (closed) {
            managed.close();
            throw new IOException("Process runner is closed");
        }
        LOGGER.debug("Started {}: {}", managed, command);
        return managed;
    }

    /**
     * Runs {@code command} to completion and returns what it printed. Blocks; call it from a
     * background thread.
     *
     * @param maxStdoutBytes output beyond this kills the program and fails the call
     * @throws IOException if it cannot start, times out or prints too much
     */
    public ProcessResult run(List<String> command, Duration timeout, int maxStdoutBytes) throws IOException {
        String program = programName(command);
        ManagedProcess process = start(program, command);
        try {
            var stdout = new AtomicReference<byte @Nullable []>();
            var failure = new AtomicReference<@Nullable IOException>();
            Thread reader = CoreExecutors.virtualThread("lmp-" + program + "-stdout", () -> {
                try {
                    stdout.set(readCapped(process.stdout(), maxStdoutBytes));
                } catch (IOException e) {
                    failure.set(e);
                    process.close();
                }
            });
            try {
                if (!process.waitFor(timeout)) {
                    throw new IOException(program + " timed out after " + timeout.toSeconds() + " s");
                }
                reader.join(Duration.ofSeconds(2));
                process.awaitStderr();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while running " + program, e);
            }
            IOException readFailure = failure.get();
            if (readFailure != null) {
                throw readFailure;
            }
            byte[] out = stdout.get();
            String text = out == null ? "" : new String(out, StandardCharsets.UTF_8);
            return new ProcessResult(exitCodeOf(process), text, process.stderrTail());
        } finally {
            process.close();
        }
    }

    /** How many started programs are still running. */
    public int runningCount() {
        return (int) running.stream().filter(ManagedProcess::isAlive).count();
    }

    private static int exitCodeOf(ManagedProcess process) {
        try {
            return process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    private static byte[] readCapped(InputStream in, int maxBytes) throws IOException {
        var out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            if (out.size() + read > maxBytes) {
                throw new IOException("Program output exceeded " + maxBytes + " bytes");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String programName(List<String> command) {
        String path = command.getFirst().replace('\\', '/');
        String file = path.substring(path.lastIndexOf('/') + 1);
        return file.endsWith(".exe") ? file.substring(0, file.length() - 4) : file;
    }

    private void checkNotClientThread() {
        if (clientThread.isCurrent()) {
            throw new IllegalStateException("Processes must not be started or awaited on the client thread");
        }
    }

    private void killAll() {
        for (var process : List.copyOf(running)) {
            process.close();
        }
    }

    /** Kills every program still running. */
    @Override
    public void close() {
        closed = true;
        killAll();
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException e) {
            // Already shutting down; the hook is running or has run.
        }
    }
}
