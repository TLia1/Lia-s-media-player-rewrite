package com.lia.mediaplayer.platform.mc;

import com.lia.mediaplayer.core.lifecycle.ClientThread;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;

/**
 * The game's client thread, as core sees it.
 *
 * <p>The mod is built before the client exists on some loaders (NeoForge 26.3 constructs mods
 * while the game is still starting), and a background task can finish in between. Its result
 * waits here and is handed to the client with the first post, or the first tick, once there
 * is one.
 */
final class McClientThread implements ClientThread {

    private final Queue<Runnable> early = new ConcurrentLinkedQueue<>();

    @Override
    public void execute(Runnable task) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            early.add(task);
            return;
        }
        flushEarly(minecraft);
        minecraft.execute(task);
    }

    @Override
    public boolean isCurrent() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.isSameThread();
    }

    /** Hands what was posted before the client existed to it. Once a tick. */
    void flushEarly() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            flushEarly(minecraft);
        }
    }

    private void flushEarly(Minecraft minecraft) {
        for (Runnable task; (task = early.poll()) != null; ) {
            minecraft.execute(task);
        }
    }
}
