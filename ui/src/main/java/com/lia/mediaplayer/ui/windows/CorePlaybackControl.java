package com.lia.mediaplayer.ui.windows;

import com.lia.mediaplayer.api.media.MediaHandle;
import com.lia.mediaplayer.api.media.RepeatMode;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.core.playback.Playback;
import com.lia.mediaplayer.core.playback.PlaybackRegistry;
import com.lia.mediaplayer.core.playback.Player;
import com.lia.mediaplayer.core.playback.PlayerSnapshot;
import com.lia.mediaplayer.core.playback.PlayerStats;
import com.lia.mediaplayer.core.video.VideoFrame;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** A window's view of one engine {@link Playback}. */
public final class CorePlaybackControl implements PlaybackControl {

    private final Playback playback;
    private final PlaybackRegistry registry;
    private final Optional<MediaHandle> handle;

    public CorePlaybackControl(Playback playback, PlaybackRegistry registry) {
        this(playback, registry, Optional.empty());
    }

    public CorePlaybackControl(Playback playback, PlaybackRegistry registry, Optional<MediaHandle> handle) {
        this.playback = playback;
        this.registry = registry;
        this.handle = handle;
    }

    @Override
    public Optional<MediaHandle> handle() {
        return handle;
    }

    @Override
    public PlayerSnapshot snapshot() {
        return playback.player().map(Player::snapshot).orElseGet(PlayerSnapshot::initial);
    }

    @Override
    public Optional<MediaRef> currentRef() {
        return playback.player().map(Player::ref);
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
    public void setViewHeight(int pixels) {
        playback.setViewHeight(pixels);
    }

    @Override
    public void setVideoEnabled(boolean on) {
        playback.setVideoEnabled(on);
    }

    @Override
    public PlayerStats stats() {
        return playback.player().map(Player::stats).orElse(PlayerStats.NONE);
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
    public List<MediaRef> upcoming() {
        return playback.queue().upcoming();
    }

    @Override
    public int queueVersion() {
        return playback.queue().version();
    }

    @Override
    public void jumpTo(int index) {
        playback.jumpTo(index);
    }

    @Override
    public void removeQueued(int index) {
        if (index >= 0 && index < playback.queue().size()) {
            playback.queue().remove(index);
        }
    }

    @Override
    public void moveQueued(int from, int insertBefore) {
        int to = insertBefore > from ? insertBefore - 1 : insertBefore;
        playback.queue().move(from, Math.min(to, playback.queue().size() - 1));
    }

    @Override
    public void setRepeat(RepeatMode mode) {
        playback.queue().setRepeat(mode);
    }

    @Override
    public void setShuffle(boolean on) {
        if (playback.queue().shuffle() != on) {
            playback.queue().setShuffle(on);
        }
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
