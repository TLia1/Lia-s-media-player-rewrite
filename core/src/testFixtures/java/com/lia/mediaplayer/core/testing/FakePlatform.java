package com.lia.mediaplayer.core.testing;

import com.lia.mediaplayer.core.lifecycle.ClientThread;
import com.lia.mediaplayer.core.platform.Platform;
import com.lia.mediaplayer.core.platform.TextMeasurer;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * A {@link Platform} for tests. Its client thread is a queue the test drains with
 * {@link #runClientTasks()}, and the thread that created it counts as the client thread.
 * Textures live in memory ({@link FakeTextures}), the font is {@link FakeText}, and the
 * clipboard and browser only remember what they were given.
 */
public final class FakePlatform implements Platform {

    private final Path gameDirectory;
    private final Thread owner = Thread.currentThread();
    private final Queue<Runnable> clientTasks = new ArrayDeque<>();
    private final ClientThread clientThread = new ClientThread() {
        @Override
        public void execute(Runnable task) {
            synchronized (clientTasks) {
                clientTasks.add(task);
            }
        }

        @Override
        public boolean isCurrent() {
            return Thread.currentThread() == owner;
        }
    };
    private final FakeTextures textures = new FakeTextures();
    private final FakeText text = new FakeText();
    private final List<String> opened = new ArrayList<>();
    private String clipboardText = "";
    private float masterVolume = 1;
    private final Clipboard clipboard = new Clipboard() {
        @Override
        public String get() {
            return clipboardText;
        }

        @Override
        public void set(String newText) {
            clipboardText = newText;
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

    @Override
    public FakeTextures textures() {
        return textures;
    }

    @Override
    public TextMeasurer text() {
        return text;
    }

    @Override
    public Clipboard clipboard() {
        return clipboard;
    }

    @Override
    public void openInBrowser(String url) {
        opened.add(url);
    }

    @Override
    public float masterVolume() {
        return masterVolume;
    }

    /** Moves the game's master volume slider. */
    public void setMasterVolume(float volume) {
        masterVolume = volume;
    }

    /** The links {@link #openInBrowser} was asked to open, in order. */
    public List<String> openedInBrowser() {
        return List.copyOf(opened);
    }

    /** Runs every task handed to the client thread so far, and those they queue in turn. */
    public int runClientTasks() {
        int ran = 0;
        Runnable task;
        while ((task = poll()) != null) {
            task.run();
            ran++;
        }
        return ran;
    }

    private Runnable poll() {
        synchronized (clientTasks) {
            return clientTasks.poll();
        }
    }
}
