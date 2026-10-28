package com.lia.mediaplayer.platform.mc;

import com.lia.mediaplayer.core.MediaPlayerCore;
import com.lia.mediaplayer.core.platform.Platform;
import com.lia.mediaplayer.ui.ClientEvents;
import com.lia.mediaplayer.ui.MediaPlayerUi;

/**
 * The running mod: the engine and the UI on top of it, built from a platform. Each loader's
 * entrypoint creates one, keeps it in a field, and forwards its events to {@link #events()}.
 */
public final class ModBootstrap implements AutoCloseable {

    private final MediaPlayerCore core;
    private final MediaPlayerUi ui;

    private ModBootstrap(MediaPlayerCore core, MediaPlayerUi ui) {
        this.core = core;
        this.ui = ui;
    }

    public static ModBootstrap start(Platform platform) {
        var core = new MediaPlayerCore(platform);
        return new ModBootstrap(core, new MediaPlayerUi(core));
    }

    public ClientEvents events() {
        return ui;
    }

    @Override
    public void close() {
        core.close();
    }
}
