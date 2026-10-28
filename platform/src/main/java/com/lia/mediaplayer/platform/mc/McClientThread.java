package com.lia.mediaplayer.platform.mc;

import com.lia.mediaplayer.core.lifecycle.ClientThread;
import net.minecraft.client.Minecraft;

/** The game's client thread, as core sees it. */
final class McClientThread implements ClientThread {

    @Override
    public void execute(Runnable task) {
        Minecraft.getInstance().execute(task);
    }

    @Override
    public boolean isCurrent() {
        return Minecraft.getInstance().isSameThread();
    }
}
