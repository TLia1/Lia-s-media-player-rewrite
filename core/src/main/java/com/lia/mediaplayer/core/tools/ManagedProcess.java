package com.lia.mediaplayer.core.tools;

import com.lia.mediaplayer.core.lifecycle.CoreExecutors;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A running external program started by {@link ProcessRunner}.
 *
 * <p>Its stderr is drained on a thread of its own into a bounded {@link OutputTail} — a pipe
 * nobody reads fills up and stalls the program behind it. Closing it kills the program and
 * everything the program started.
 */
public final class ManagedProcess implements AutoCloseable {

    /** ffmpeg writes a handful of lines before giving up; 16 KB keeps them all. */
    static final int STDERR_TAIL_CHARS = 16 * 1024;

    private final String name;
    private final Process process;
    private final OutputTail stderr = new OutputTail(STDERR_TAIL_CHARS);
    private final Thread stderrReader;
    private final Consumer<ManagedProcess> onClosed;
    private volatile boolean killed;

    ManagedProcess(String name, Process process, Consumer<ManagedProcess> onClosed) {
        this.name = name;
        this.process = process;
        this.onClosed = onClosed;
        this.stderrReader = CoreExecutors.virtualThread("lmp-" + name + "-stderr", this::drainStderr);
        var unused = process.onExit().thenRun(() -> onClosed.accept(this));
    }

    public String name() {
        return name;
    }

    /** What the program writes to stdout. */
    public InputStream stdout() {
        return process.getInputStream();
    }

    /** The last lines it wrote to stderr. Complete once {@link #awaitStderr()} has returned. */
    public String stderrTail() {
        return stderr.text();
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    /** Whether {@link #close()} ended it, as opposed to the program exiting on its own. */
    public boolean wasKilled() {
        return killed;
    }

    public CompletableFuture<Process> onExit() {
        return process.onExit();
    }

    /** Waits for the program to exit and returns its exit code. */
    public int waitFor() throws InterruptedException {
        return process.waitFor();
    }

    /** Waits up to {@code timeout} for the program to exit; {@code true} if it did. */
    public boolean waitFor(Duration timeout) throws InterruptedException {
        return process.waitFor(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    /** Waits until everything the program wrote to stderr has been read. */
    public void awaitStderr() throws InterruptedException {
        stderrReader.join(1000);
    }

    private void drainStderr() {
        try (var reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stderr.append(line);
            }
        } catch (IOException e) {
            // The program was killed, or closed its stderr: nothing more to read.
        }
    }

    /** Kills the program and its descendants. Idempotent. */
    @Override
    public void close() {
        if (process.isAlive()) {
            killed = true;
            // Descendants first: once the parent is gone they are reparented and no longer
            // reachable from it.
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
        onClosed.accept(this);
    }

    @Override
    public String toString() {
        return name + " (pid " + process.pid() + ")";
    }
}
