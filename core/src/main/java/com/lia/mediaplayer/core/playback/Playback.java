package com.lia.mediaplayer.core.playback;

import com.lia.mediaplayer.api.source.MediaRef;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.jspecify.annotations.Nullable;

/**
 * "What plays": a player, its queue and its volume. It knows nothing about windows — a video
 * window, the audio bar, an addon surface or nothing at all (headless audio) can show it.
 *
 * <p>When a track ends the next one starts in a fresh player; the volume carries over. What the
 * player hears is that volume scaled by the game's master volume, which the {@link
 * PlaybackRegistry} keeps up to date. Client thread only, except {@link #player()}, {@link
 * #isClosed()} and {@link #audioOnly()}, which the API's handles read from any thread.
 */
public final class Playback implements AutoCloseable {

    private final long id;
    private final PlaybackEngine engine;
    private final PlayQueue<MediaRef> queue;
    private final PlaybackRegistry.Listener owner;
    private final boolean audioOnly;
    private PlayerOptions options;
    private float masterVolume = 1;
    private PlayerListener listener = PlayerListener.NONE;
    private volatile @Nullable Player player;
    private volatile boolean closed;

    /** @param owner told of every track, state and the close, after this playback's own listener */
    Playback(
            long id,
            PlaybackEngine engine,
            PlayerOptions options,
            RandomGenerator random,
            PlaybackRegistry.Listener owner) {
        this.id = id;
        this.engine = engine;
        this.options = options;
        this.audioOnly = options.resolve().audioOnly();
        this.queue = new PlayQueue<>(random);
        this.owner = owner;
    }

    public long id() {
        return id;
    }

    public PlayQueue<MediaRef> queue() {
        return queue;
    }

    /** The current player; empty before the first track and after the last. */
    public Optional<Player> player() {
        return Optional.ofNullable(player);
    }

    /** Told about the state changes of whichever player is current. */
    public void setListener(PlayerListener newListener) {
        listener = newListener;
    }

    /** Plays {@code ref} now, replacing the current track. */
    public void play(MediaRef ref) {
        if (closed) {
            throw new IllegalStateException("Playback " + id + " is closed");
        }
        Player previous = player;
        if (previous != null) {
            previous.close();
        }
        player = engine.play(ref, options.withVolume(gain()), this::onPlayerState);
        owner.trackStarted(this, ref);
    }

    /** Whether this playback fetches sound only, the picture never being shown. */
    public boolean audioOnly() {
        return audioOnly;
    }

    /**
     * Appends {@code ref} to the queue. If nothing is playing — no track yet, or the last one
     * ended — it starts at once.
     */
    public void enqueue(MediaRef ref) {
        if (closed) {
            throw new IllegalStateException("Playback " + id + " is closed");
        }
        queue.add(ref);
        Player current = player;
        if (current == null || current.snapshot().state() instanceof PlayerState.Ended) {
            next();
        }
    }

    public boolean isClosed() {
        return closed;
    }

    /** Skips to the next track. {@code false} when there is none and nothing changed. */
    public boolean next() {
        Player current = player;
        Optional<MediaRef> next = current == null ? takeFirst() : queue.next(current.ref());
        next.ifPresent(this::play);
        return next.isPresent();
    }

    public boolean previous() {
        Player current = player;
        if (current == null) {
            return false;
        }
        Optional<MediaRef> previous = queue.previous(current.ref());
        previous.ifPresent(this::play);
        return previous.isPresent();
    }

    /** Plays the queued entry at {@code index} now. */
    public boolean jumpTo(int index) {
        Optional<MediaRef> entry = queue.take(index);
        entry.ifPresent(this::play);
        return entry.isPresent();
    }

    /** Linear volume, kept for the tracks that follow. */
    public void setVolume(float volume) {
        options = options.withVolume(volume);
        applyGain();
    }

    /** This playback's own volume, before the game's master volume. */
    public float volume() {
        return options.volume();
    }

    void setMasterVolume(float volume) {
        masterVolume = volume;
        applyGain();
    }

    /** What the player plays at: this playback's volume times the game's. */
    float gain() {
        return options.volume() * masterVolume;
    }

    private void applyGain() {
        Player current = player;
        if (current != null) {
            current.setVolume(gain());
        }
    }

    private Optional<MediaRef> takeFirst() {
        return queue.take(0);
    }

    private void onPlayerState(Player source, PlayerState state) {
        if (source != player) {
            return;
        }
        listener.stateChanged(source, state);
        owner.stateChanged(this, state);
        if (state instanceof PlayerState.Ended) {
            // After the last track the player stays, showing its end, until closed.
            next();
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        Player current = player;
        player = null;
        if (current != null) {
            current.close();
        }
        owner.closed(this);
    }
}
