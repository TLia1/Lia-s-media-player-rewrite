package com.lia.mediaplayer.core;

import com.lia.mediaplayer.api.ApiVersion;
import com.lia.mediaplayer.core.lifecycle.Disposer;
import com.lia.mediaplayer.core.platform.Platform;
import com.lia.mediaplayer.core.storage.DataPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The composition root of the engine: builds every core service once, in dependency order,
 * and owns their lifetimes. It holds no logic of its own.
 *
 * <p>Two lifecycles meet here. The <em>game session</em> lasts from construction to
 * {@link #close()}: tools, settings, the library. The <em>world session</em> ends at each
 * {@link #onWorldLeft()}: players, windows, in-memory caches.
 */
public final class MediaPlayerCore implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(MediaPlayerCore.class);

    private final Platform platform;
    private final DataPaths paths;
    private final Disposer disposer = new Disposer();

    public MediaPlayerCore(Platform platform) {
        this.platform = platform;
        this.paths = DataPaths.under(platform.gameDirectory());
        LOGGER.info(
                "{} starting on {} (API {}), data in {}",
                ModInfo.NAME,
                platform.describe(),
                ApiVersion.CURRENT,
                paths.root());
    }

    public Platform platform() {
        return platform;
    }

    public DataPaths paths() {
        return paths;
    }

    /** Stops everything that belongs to the world the player just left. */
    public void onWorldLeft() {
        LOGGER.debug("World left");
    }

    /** Releases everything; called once when the game shuts down. */
    @Override
    public void close() {
        LOGGER.info("{} shutting down", ModInfo.NAME);
        disposer.close();
    }
}
