package com.lia.mediaplayer.ui.windows;

import com.lia.mediaplayer.api.media.RepeatMode;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.core.playback.Playback;
import com.lia.mediaplayer.core.playback.PlaybackRegistry;
import com.lia.mediaplayer.core.playback.Player;
import com.lia.mediaplayer.core.playback.PlayerSnapshot;
import com.lia.mediaplayer.core.video.VideoFrame;
import java.time.Duration;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** A window's view of one engine {@link Playback}. */
public final class CorePlaybackControl implements PlaybackControl {

    private final Playback playback;
    private final PlaybackRegistry registry;

    public CorePlaybackControl(Playback playback, PlaybackRegistry registry) {
        this.playback = playback;
        this.registry = registry;
    }

    @Override
    public PlayerSnapshot snapshot() {
        return playback.player().map(Player::snapshot).orElseGet(PlayerSnapshot::initial);
    }

    @Override
    public Optional<String> currentUrl() {
        return playback.player().map(player -> player.ref().url());
    }

    @Override
    public @Nullable VideoFrame takeFrame() {
        Optional<Player> player = playback.player();
        return player.isPresent() ? player.get().takeFrame() : null;
    }

    @Override
    public void releaseFrame(VideoFrame frame) {
        // The frame is given back in the same call that took it, before any track change.
        playback.player().ifPresent(player -> player.releaseFrame(frame));
    }

    @Override
    public void togglePause() {
        playback.player().ifPresent(Player::togglePause);
    }

    @Override
    public void seek(Duration target) {
        playback.player().ifPresent(player -> player.seek(target));
    }

    @Override
    public void seekBy(Duration delta) {
        playback.player().ifPresent(player -> player.seekBy(delta));
    }

    @Override
    public float volume() {
        return playback.volume();
    }

    @Override
    public void setVolume(float volume) {
        playback.setVolume(volume);
    }

    @Override
    public boolean hasNext() {
        return playback.queue().hasNext();
    }

    @Override
    public void next() {
        playback.next();
    }

    @Override
    public boolean hasPrevious() {
        return playback.queue().hasPrevious();
    }

    @Override
    public void previous() {
        playback.previous();
    }

    @Override
    public int queued() {
        return playback.queue().size();
    }

    @Override
    public RepeatMode repeat() {
        return playback.queue().repeat();
    }

    @Override
    public void cycleRepeat() {
        playback.queue().cycleRepeat();
    }

    @Override
    public boolean shuffle() {
        return playback.queue().shuffle();
    }

    @Override
    public void toggleShuffle() {
        playback.queue().toggleShuffle();
    }

    @Override
    public void retry() {
        // From Failed, play() resolves the link again, bypassing the cache.
        playback.player().ifPresent(Player::play);
    }

    @Override
    public void enqueue(MediaRef ref) {
        registry.enqueue(playback, ref);
    }

    @Override
    public boolean isClosed() {
        return playback.isClosed();
    }

    @Override
    public void close() {
        playback.close();
    }
}
