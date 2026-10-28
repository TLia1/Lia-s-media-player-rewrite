package com.lia.mediaplayer.ui;

import com.lia.mediaplayer.core.MediaPlayerCore;

/**
 * The root of the UI layer: owns windows, screens and overlays, and is what the loader
 * bridges call through {@link ClientEvents}.
 */
public final class MediaPlayerUi implements ClientEvents {

    private final MediaPlayerCore core;
    private long ticks;

    public MediaPlayerUi(MediaPlayerCore core) {
        this.core = core;
    }

    @Override
    public void tick() {
        core.platform().clientThread().checkCurrent();
        ticks++;
    }

    @Override
    public void worldLeft() {
        core.onWorldLeft();
    }

    /** Client ticks seen since startup. */
    public long ticks() {
        return ticks;
    }
}
