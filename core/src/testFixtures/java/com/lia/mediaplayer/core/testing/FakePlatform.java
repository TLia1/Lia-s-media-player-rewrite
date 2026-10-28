package com.lia.mediaplayer.core.testing;

import com.lia.mediaplayer.core.lifecycle.ClientThread;
import com.lia.mediaplayer.core.platform.Platform;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * A {@link Platform} for tests. Its client thread is a queue the test drains with
 * {@link #runClientTasks()}, and the calling thread counts as the client thread.
 */
public final class FakePlatform implements Platform {

    private final Path gameDirectory;
    private final Queue<Runnable> clientTasks = new ArrayDeque<>();
    private final ClientThread clientThread = new ClientThread() {
        @Override
        public void execute(Runnable task) {
            clientTasks.add(task);
        }

        @Override
        public boolean isCurrent() {
            return true;
        }
    };

    public FakePlatform(Path gameDirectory) {
        this.gameDirectory = gameDirectory;
    }

    @Override
    public String describe() {
        return "Test platform";
    }

    @Override
    public ClientThread clientThread() {
        return clientThread;
    }

    @Override
    public Path gameDirectory() {
        return gameDirectory;
    }

    /** Runs every task handed to the client thread so far, and those they queue in turn. */
    public int runClientTasks() {
        int ran = 0;
        Runnable task;
        while ((task = clientTasks.poll()) != null) {
            task.run();
            ran++;
        }
        return ran;
    }
}
