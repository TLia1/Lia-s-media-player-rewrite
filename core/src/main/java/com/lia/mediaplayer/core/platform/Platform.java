package com.lia.mediaplayer.core.platform;

import com.lia.mediaplayer.core.lifecycle.ClientThread;
import java.nio.file.Path;

/**
 * What the engine needs from the game, implemented once per Minecraft version and loader in
 * the platform layer. This is the only way core and ui reach the game.
 *
 * <p>Grows with the milestones: key mappings, the camera and toasts are added as the code
 * that needs them is written.
 */
public interface Platform {

    /** Loader and game version, for logs and diagnostics — e.g. {@code "Fabric, Minecraft 1.21.1"}. */
    String describe();

    ClientThread clientThread();

    /** The game directory ({@code .minecraft} or the instance folder). */
    Path gameDirectory();

    TextureService textures();

    TextMeasurer text();

    /** The system clipboard. Client thread only. */
    Clipboard clipboard();

    /** Opens {@code url}, an {@code http(s)} link, in the system browser. Client thread only. */
    void openInBrowser(String url);

    /** The system clipboard, as text. */
    interface Clipboard {

        String get();

        void set(String text);
    }
}
