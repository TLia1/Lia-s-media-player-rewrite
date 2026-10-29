package com.lia.mediaplayer.core.playback;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.core.lifecycle.ClientThread;
import com.lia.mediaplayer.core.resolve.CollectionExpander;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns every {@link Playback}: opens them, and closes them all when the player leaves the
 * world. Client thread only.
 */
public final class PlaybackRegistry implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlaybackRegistry.class);

    private final PlaybackEngine engine;
    private final CollectionExpander collections;
    private final ClientThread clientThread;
    private final RandomGenerator random;
    private final List<Playback> playbacks = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong();

    public PlaybackRegistry(
            PlaybackEngine engine, CollectionExpander collections, ClientThread clientThread, RandomGenerator random) {
        this.engine = engine;
        this.collections = collections;
        this.clientThread = clientThread;
        this.random = random;
    }

    /**
     * Opens a playback of {@code ref}. A collection starts empty and fills once it has been
     * expanded; its first entry then plays.
     */
    public Playback open(MediaRef ref, PlayerOptions options) {
        clientThread.checkCurrent();
        var playback = new Playback(ids.incrementAndGet(), engine, options, random, playbacks::remove);
        playbacks.add(playback);
        if (ref.kind() == MediaKind.COLLECTION) {
            var unused = collections
                    .expand(ref)
                    .whenCompleteAsync(
                            (collection, error) -> {
                                if (error != null) {
                                    LOGGER.warn("Could not expand {}: {}", ref.url(), error.toString());
                                    return;
                                }
                                if (!playbacks.contains(playback)) {
                                    return;
                                }
                                playback.queue().addAll(collection.entries());
                                if (playback.player().isEmpty()) {
                                    playback.next();
                                }
                            },
                            clientThread);
        } else {
            playback.play(ref);
        }
        return playback;
    }

    public List<Playback> playbacks() {
        return List.copyOf(playbacks);
    }

    /** Closes every playback: the player left the world. */
    public void closeAll() {
        for (Playback playback : List.copyOf(playbacks)) {
            playback.close();
        }
    }

    @Override
    public void close() {
        closeAll();
    }
}
