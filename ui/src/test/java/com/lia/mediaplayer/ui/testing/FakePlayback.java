package com.lia.mediaplayer.ui.testing;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.media.RepeatMode;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.playback.PlayerSnapshot;
import com.lia.mediaplayer.core.playback.PlayerState;
import com.lia.mediaplayer.core.playback.PlayerStats;
import com.lia.mediaplayer.core.video.FramePool;
import com.lia.mediaplayer.core.video.VideoFrame;
import com.lia.mediaplayer.ui.windows.PlaybackControl;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** A playback driven by the test: it sets the state and queues frames, and reads what was asked. */
public final class FakePlayback implements PlaybackControl {

    private final FramePool pool = new FramePool(4);
    private final Deque<VideoFrame> frames = new ArrayDeque<>();
    public final List<String> calls = new ArrayList<>();
    public final List<MediaRef> enqueued = new ArrayList<>();
    public PlayerSnapshot snapshot = PlayerSnapshot.initial();
    public @Nullable String url = "https://example.com/clip.mp4";
    public float volume = 1;
    public boolean hasNext;
    public boolean hasPrevious;
    public int queued;
    public RepeatMode repeat = RepeatMode.OFF;
    public boolean shuffle;
    public boolean closed;
    public String sourceId = "test:video";
    public final List<MediaRef> upcoming = new ArrayList<>();
    public int queueVersion;
    public int released;
    public int viewHeight;
    public boolean videoEnabled = true;
    public PlayerStats stats = PlayerStats.NONE;

    /** A snapshot in {@code state} at {@code position} of {@code duration}, with a title. */
    public static PlayerSnapshot at(PlayerState state, Duration position, @Nullable Duration duration) {
        return new PlayerSnapshot(
                state, position, Optional.ofNullable(duration), Optional.of("Clip"), true, true, 0, 0);
    }

    /** Queues a frame of one colour, due at once. */
    public void pushFrame(int width, int height, byte red) {
        VideoFrame frame = pool.acquire(width, height);
        byte[] pixels = frame.pixels();
        for (int i = 0; i < pixels.length; i += 4) {
            pixels[i] = red;
            pixels[i + 3] = (byte) 0xFF;
        }
        frame.stamp(1, 0);
        frames.add(frame);
    }

    @Override
    public PlayerSnapshot snapshot() {
        return snapshot;
    }

    @Override
    public Optional<MediaRef> currentRef() {
        String current = url;
        return current == null
                ? Optional.empty()
                : Optional.of(new MediaRef(current, MediaKind.VIDEO, sourceId, Text.literal("[video]")));
    }

    @Override
    public List<MediaRef> upcoming() {
        return List.copyOf(upcoming);
    }

    @Override
    public int queueVersion() {
        return queueVersion;
    }

    @Override
    public void jumpTo(int index) {
        calls.add("jumpTo " + index);
    }

    @Override
    public void removeQueued(int index) {
        upcoming.remove(index);
        queueVersion++;
        calls.add("remove " + index);
    }

    @Override
    public void moveQueued(int from, int insertBefore) {
        calls.add("move " + from + " " + insertBefore);
    }

    @Override
    public void setRepeat(RepeatMode mode) {
        repeat = mode;
    }

    @Override
    public void setShuffle(boolean on) {
        shuffle = on;
    }

    @Override
    public @Nullable VideoFrame takeFrame() {
        return frames.poll();
    }

    @Override
    public void releaseFrame(VideoFrame frame) {
        released++;
        pool.release(frame);
    }

    @Override
    public void setViewHeight(int pixels) {
        viewHeight = pixels;
    }

    @Override
    public void setVideoEnabled(boolean on) {
        videoEnabled = on;
    }

    @Override
    public PlayerStats stats() {
        return stats;
    }

    @Override
    public void togglePause() {
        calls.add("togglePause");
    }

    @Override
    public void seek(Duration target) {
        calls.add("seek " + target.toMillis());
    }

    @Override
    public void seekBy(Duration delta) {
        calls.add("seekBy " + delta.toMillis());
    }

    @Override
    public float volume() {
        return volume;
    }

    @Override
    public void setVolume(float newVolume) {
        volume = newVolume;
    }

    @Override
    public boolean hasNext() {
        return hasNext;
    }

    @Override
    public void next() {
        calls.add("next");
    }

    @Override
    public boolean hasPrevious() {
        return hasPrevious;
    }

    @Override
    public void previous() {
        calls.add("previous");
    }

    @Override
    public int queued() {
        return queued;
    }

    @Override
    public RepeatMode repeat() {
        return repeat;
    }

    @Override
    public void cycleRepeat() {
        repeat = repeat.next();
    }

    @Override
    public boolean shuffle() {
        return shuffle;
    }

    @Override
    public void toggleShuffle() {
        shuffle = !shuffle;
    }

    @Override
    public void retry() {
        calls.add("retry");
    }

    @Override
    public void enqueue(MediaRef ref) {
        enqueued.add(ref);
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        closed = true;
        calls.add("close");
    }
}
