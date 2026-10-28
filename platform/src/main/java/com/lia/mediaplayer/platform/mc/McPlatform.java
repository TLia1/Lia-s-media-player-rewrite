package com.lia.mediaplayer.platform.mc;

import com.lia.mediaplayer.core.lifecycle.ClientThread;
import com.lia.mediaplayer.core.platform.Platform;
import java.nio.file.Path;

/**
 * The {@link Platform} for one Minecraft version, shared by both loaders. What only a loader
 * knows (its name, the game directory, the Minecraft version it loaded) is passed in by the
 * loader's entrypoint.
 */
public final class McPlatform implements Platform {

    private final String loader;
    private final String minecraftVersion;
    private final Path gameDirectory;
    private final ClientThread clientThread = new McClientThread();

    public McPlatform(String loader, String minecraftVersion, Path gameDirectory) {
        this.loader = loader;
        this.minecraftVersion = minecraftVersion;
        this.gameDirectory = gameDirectory;
    }

    @Override
    public String describe() {
        return loader + ", Minecraft " + minecraftVersion;
    }

    @Override
    public ClientThread clientThread() {
        return clientThread;
    }

    @Override
    public Path gameDirectory() {
        return gameDirectory;
    }
}
