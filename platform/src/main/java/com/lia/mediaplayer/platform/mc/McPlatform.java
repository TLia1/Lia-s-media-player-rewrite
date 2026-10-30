package com.lia.mediaplayer.platform.mc;

import com.lia.mediaplayer.core.lifecycle.ClientThread;
import com.lia.mediaplayer.core.platform.Platform;
import com.lia.mediaplayer.core.platform.TextMeasurer;
import com.lia.mediaplayer.core.platform.TextureService;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
//? if <26.3 {
import net.minecraft.Util;
//?} else
/*import com.mojang.blaze3d.Blaze3D;*/
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link Platform} for one Minecraft version, shared by both loaders. What only a loader
 * knows (its name, the game directory, the Minecraft version it loaded) is passed in by the
 * loader's entrypoint.
 */
public final class McPlatform implements Platform {

    private static final Logger LOGGER = LoggerFactory.getLogger(McPlatform.class);

    private final String loader;
    private final String minecraftVersion;
    private final Path gameDirectory;
    private final McClientThread clientThread = new McClientThread();
    private final McTextures textures = new McTextures();
    private final TextMeasurer text = new McTextMeasurer();
    private final Clipboard clipboard = new Clipboard() {
        @Override
        public String get() {
            return Minecraft.getInstance().keyboardHandler.getClipboard();
        }

        @Override
        public void set(String value) {
            Minecraft.getInstance().keyboardHandler.setClipboard(value);
        }
    };

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

    /** Between ticks: what was posted before the client existed goes to it now. */
    void tick() {
        clientThread.flushEarly();
    }

    @Override
    public Path gameDirectory() {
        return gameDirectory;
    }

    @Override
    public McTextures textures() {
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
    public float masterVolume() {
        return Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MASTER);
    }

    @Override
    public void openInBrowser(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            // Only web links leave the game; anything else could open a local program.
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                //? if <26.3 {
                Util.getPlatform().openUri(uri);
                //?} else
                /*Blaze3D.openUri(uri);*/
            }
        } catch (URISyntaxException e) {
            LOGGER.warn("Not opening malformed link {}", url);
        }
    }
}
