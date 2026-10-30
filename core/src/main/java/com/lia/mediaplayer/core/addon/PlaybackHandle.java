package com.lia.mediaplayer.core.addon;

import com.lia.mediaplayer.api.media.MediaHandle;
import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.media.PlaybackSnapshot;
import com.lia.mediaplayer.api.media.PlaybackState;
import com.lia.mediaplayer.api.media.RepeatMode;
import com.lia.mediaplayer.api.queue.QueueEntry;
import com.lia.mediaplayer.api.queue.QueueView;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.core.lifecycle.ClientThread;
import com.lia.mediaplayer.core.link.SourceRegistry;
import com.lia.mediaplayer.core.playback.Playback;
import com.lia.mediaplayer.core.playback.PlaybackRegistry;
import com.lia.mediaplayer.core.playback.Player;
import com.lia.mediaplayer.core.playback.PlayerSnapshot;
import com.lia.mediaplayer.core.playback.PlayerState;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * The API's handle on one {@link Playback}. The read side goes through the playback's volatile
 * fields and the player's published snapshot, so it is safe from any thread; the controls
 * check for the client thread first, and do nothing once the playback is closed.
 */
final class PlaybackHandle implements MediaHandle {

    private final Playback playback;
    private final PlaybackRegistry registry;
    private final SourceRegistry sources;
    private final ClientThread clientThread;
    private final Queue queue = new Queue();

    PlaybackHandle(Playback playback, PlaybackRegistry registry, SourceRegistry sources, ClientThread clientThread) {
        this.playback = playback;
        this.registry = registry;
        this.sources = sources;
        this.clientThread = clientThread;
    }

    Playback playback() {
        return playback;
    }

    @Override
    public long id() {
        return playback.id();
    }

    @Override
    public MediaKind kind() {
        return playback.audioOnly() ? MediaKind.AUDIO : MediaKind.VIDEO;
    }

    @Override
    public Optional<String> url() {
        return playback.player().map(player -> player.ref().url());
    }

    @Override
    public PlaybackSnapshot snapshot() {
        return playback.player().map(player -> snapshotOf(player.snapshot())).orElse(PlaybackSnapshot.IDLE);
    }

    static PlaybackSnapshot snapshotOf(PlayerSnapshot snapshot) {
        return new PlaybackSnapshot(
                stateOf(snapshot.state()),
                snapshot.position(),
                snapshot.duration(),
                snapshot.title(),
                snapshot.hasVideo(),
                snapshot.hasAudio());
    }

    static PlaybackState stateOf(PlayerState state) {
        return switch (state) {
            case PlayerState.Idle idle -> PlaybackState.IDLE;
            case PlayerState.Resolving resolving -> PlaybackState.RESOLVING;
            case PlayerState.Buffering buffering -> PlaybackState.BUFFERING;
            case PlayerState.Playing playing -> PlaybackState.PLAYING;
            case PlayerState.Paused paused -> PlaybackState.PAUSED;
            case PlayerState.Seeking seeking -> PlaybackState.SEEKING;
            case PlayerState.Ended ended -> PlaybackState.ENDED;
            case PlayerState.Failed failed -> PlaybackState.FAILED;
        };
    }

    @Override
    public boolean isAlive() {
        return !playback.isClosed();
    }

    @Override
    public void play() {
        current().ifPresent(Player::play);
    }

    @Override
    public void pause() {
        current().ifPresent(Player::pause);
    }

    @Override
    public void togglePause() {
        current().ifPresent(Player::togglePause);
    }

    @Override
    public void seek(Duration position) {
        current().ifPresent(player -> player.seek(position.isNegative() ? Duration.ZERO : position));
    }

    @Override
    public boolean next() {
        return controllable() && playback.next();
    }

    @Override
    public boolean previous() {
        return controllable() && playback.previous();
    }

    @Override
    public float volume() {
        checkThread();
        return playback.volume();
    }

    @Override
    public void setVolume(float volume) {
        if (controllable()) {
            playback.setVolume(Float.isNaN(volume) ? 1 : Math.max(0, Math.min(1, volume)));
        }
    }

    @Override
    public QueueView queue() {
        return queue;
    }

    @Override
    public void close() {
        if (controllable()) {
            playback.close();
        }
    }

    /** The current player, if the playback can be controlled now. */
    private Optional<Player> current() {
        return controllable() ? playback.player() : Optional.empty();
    }

    /** Checks the thread, then says whether the playback is still open. */
    private boolean controllable() {
        checkThread();
        return !playback.isClosed();
    }

    private void checkThread() {
        if (!clientThread.isCurrent()) {
            throw new WrongThreadException("Media handles are controlled on the client thread, not "
                    + Thread.currentThread().getName());
        }
    }

    @Override
    public String toString() {
        return "MediaHandle[" + playback.id() + "]";
    }

    /** The playback's queue, through the same checks. */
    private final class Queue implements QueueView {

        @Override
        public List<QueueEntry> entries() {
            checkThread();
            return playback.queue().upcoming().stream()
                    .map(ref -> new QueueEntry(ref.url(), ref.kind(), ref.sourceId()))
                    .toList();
        }

        @Override
        public int size() {
            checkThread();
            return playback.queue().size();
        }

        @Override
        public void add(String url) {
            checkThread();
            MediaRef ref = sources.match(url.strip())
                    .filter(found -> found.kind() != MediaKind.IMAGE)
                    .orElseThrow(() -> new IllegalArgumentException("No source plays " + url));
            if (controllable()) {
                registry.enqueue(playback, ref);
            }
        }

        @Override
        public void jumpTo(int index) {
            if (controllable()) {
                var unused = playback.jumpTo(index);
            }
        }

        @Override
        public void remove(int index) {
            if (controllable()) {
                var unused = playback.queue().remove(index);
            }
        }

        @Override
        public void move(int from, int to) {
            if (controllable()) {
                playback.queue().move(from, to);
            }
        }

        @Override
        public void clear() {
            if (controllable()) {
                playback.queue().clear();
            }
        }

        @Override
        public RepeatMode repeat() {
            checkThread();
            return playback.queue().repeat();
        }

        @Override
        public void setRepeat(RepeatMode mode) {
            if (controllable()) {
                playback.queue().setRepeat(mode);
            }
        }

        @Override
        public boolean shuffle() {
            checkThread();
            return playback.queue().shuffle();
        }

        @Override
        public void setShuffle(boolean on) {
            if (controllable() && playback.queue().shuffle() != on) {
                playback.queue().setShuffle(on);
            }
        }
    }
}
