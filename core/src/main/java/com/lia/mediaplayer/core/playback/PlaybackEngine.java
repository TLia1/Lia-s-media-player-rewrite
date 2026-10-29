package com.lia.mediaplayer.core.playback;

import com.lia.mediaplayer.api.source.MediaRef;
import java.util.concurrent.atomic.AtomicLong;

/** Creates players, numbering them, with everything they need from the engine. */
public final class PlaybackEngine {

    private final PlayerServices services;
    private final AtomicLong ids = new AtomicLong();

    public PlaybackEngine(PlayerServices services) {
        this.services = services;
    }

    /** A started player for {@code ref}. Its listener is called on the client thread. */
    public Player play(MediaRef ref, PlayerOptions options, PlayerListener listener) {
        var player = new Player(ids.incrementAndGet(), ref, options, services, listener);
        player.start();
        return player;
    }

    public PlayerServices services() {
        return services;
    }
}
