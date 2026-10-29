package com.lia.mediaplayer.core.playback;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.core.lifecycle.ClientThread;
import com.lia.mediaplayer.core.resolve.CollectionExpander;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
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
    private final List<BiConsumer<Playback, MediaRef>> trackListeners = new CopyOnWriteArrayList<>();

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
        var playback =
                new Playback(ids.incrementAndGet(), engine, options, random, playbacks::remove, this::trackStarted);
        playbacks.add(playback);
        if (ref.kind() == MediaKind.COLLECTION) {
            enqueue(playback, ref);
        } else {
            playback.play(ref);
        }
        return playback;
    }

    /**
     * Appends {@code ref} to {@code playback}'s queue, starting it if nothing plays. A collection
     * is expanded first, off the client thread, and its entries are appended in order once
     * known; if the playback has closed by then, they are dropped.
     */
    public void enqueue(Playback playback, MediaRef ref) {
        clientThread.checkCurrent();
        if (ref.kind() != MediaKind.COLLECTION) {
            playback.enqueue(ref);
            return;
        }
        var unused = collections
                .expand(ref)
                .whenCompleteAsync(
                        (collection, error) -> {
                            if (error != null) {
                                LOGGER.warn("Could not expand {}: {}", ref.url(), error.toString());
                                return;
                            }
                            if (playback.isClosed() || collection.entries().isEmpty()) {
                                return;
                            }
                            var entries = collection.entries();
                            playback.enqueue(entries.getFirst());
                            playback.queue().addAll(entries.subList(1, entries.size()));
                        },
                        clientThread);
    }

    /**
     * Calls {@code listener} each time any playback starts a track — the first, the next one,
     * a jump in its queue — on the client thread. Where history is recorded, once for every
     * kind of owner.
     */
    public void onTrackStarted(BiConsumer<Playback, MediaRef> listener) {
        trackListeners.add(listener);
    }

    private void trackStarted(Playback playback, MediaRef ref) {
        for (var listener : trackListeners) {
            try {
                listener.accept(playback, ref);
            } catch (RuntimeException e) {
                LOGGER.error("Track listener failed on {}", ref.url(), e);
            }
        }
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
