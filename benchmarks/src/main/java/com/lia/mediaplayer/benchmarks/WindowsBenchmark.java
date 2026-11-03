package com.lia.mediaplayer.benchmarks;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.media.RepeatMode;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.platform.TextMeasurer;
import com.lia.mediaplayer.core.platform.TextureRef;
import com.lia.mediaplayer.core.playback.PlayerSnapshot;
import com.lia.mediaplayer.core.playback.PlayerState;
import com.lia.mediaplayer.core.playback.PlayerStats;
import com.lia.mediaplayer.core.testing.FakePlatform;
import com.lia.mediaplayer.core.video.FramePool;
import com.lia.mediaplayer.core.video.VideoFrame;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.theme.Themes;
import com.lia.mediaplayer.ui.widget.RenderContext;
import com.lia.mediaplayer.ui.window.WindowManager;
import com.lia.mediaplayer.ui.windows.PlaybackControl;
import com.lia.mediaplayer.ui.windows.VideoWindow;
import com.lia.mediaplayer.ui.windows.WindowServices;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * What four video windows cost the client thread per frame (§12: under 0.3 ms): layout, the
 * draw calls, taking each player's due frame and copying it to its texture — everything but
 * the GPU's own work, which the platform's {@code glTexSubImage2D} hands to the driver. The
 * texture copy here is {@code FakeTextures}', a heap copy like the staging-buffer copy of the
 * real one.
 *
 * <p>{@code newFrames} says whether every player has a new frame at every render: the worst
 * case of a 60 fps video in a 60 fps game, against the steady state of the picture between two
 * frames. {@code decodeHeight} is 360 for windows of the default size at GUI scale 2, which
 * is what decoding at the view's size gives them; 720 is what they cost before M7, decoded at
 * the quality setting whatever their size.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class WindowsBenchmark {

    @Param({"true", "false"})
    public boolean newFrames;

    @Param({"360", "720"})
    public int decodeHeight;

    private final WindowManager windows = new WindowManager();
    private final Canvas canvas = new NullCanvas();
    private long nanos;

    @Setup
    public void setUp() throws IOException {
        var platform = new FakePlatform(Files.createTempDirectory("lmp-bench"));
        var services = WindowServices.of(platform);
        for (int i = 0; i < 4; i++) {
            windows.open(new VideoWindow(new Playing(this), services));
        }
        render();
    }

    @Benchmark
    public long fourWindows() {
        nanos += 16_666_667;
        render();
        return nanos;
    }

    private void render() {
        windows.render(canvas, new RenderContext(Themes.DARK, nanos, 100, 100, true));
    }

    /** A playback that is playing, with a frame due at every render or none. */
    private static final class Playing implements PlaybackControl {
        private final WindowsBenchmark bench;
        private final FramePool pool = new FramePool(2);
        private final MediaRef ref = new MediaRef(
                "https://cdn.example/clip.mp4", MediaKind.VIDEO, "liasmediaplayer:direct", Text.literal("clip"));
        private final PlayerSnapshot snapshot = new PlayerSnapshot(
                new PlayerState.Playing(Duration.ofSeconds(42)),
                Duration.ofSeconds(42),
                Optional.of(Duration.ofMinutes(3)),
                Optional.of("A clip with a title of ordinary length"),
                true,
                true,
                640,
                360);

        Playing(WindowsBenchmark bench) {
            this.bench = bench;
        }

        @Override
        public PlayerSnapshot snapshot() {
            return snapshot;
        }

        @Override
        public Optional<MediaRef> currentRef() {
            return Optional.of(ref);
        }

        @Override
        public @Nullable VideoFrame takeFrame() {
            if (!bench.newFrames && bench.nanos > 0) {
                return null;
            }
            VideoFrame frame = pool.acquire(bench.decodeHeight * 16 / 9, bench.decodeHeight);
            frame.stamp(1, bench.nanos / 1000);
            return frame;
        }

        @Override
        public void releaseFrame(VideoFrame frame) {
            pool.release(frame);
        }

        @Override
        public void setViewHeight(int pixels) {}

        @Override
        public void setVideoEnabled(boolean on) {}

        @Override
        public PlayerStats stats() {
            return PlayerStats.NONE;
        }

        @Override
        public void togglePause() {}

        @Override
        public void seek(Duration target) {}

        @Override
        public void seekBy(Duration delta) {}

        @Override
        public float volume() {
            return 1;
        }

        @Override
        public void setVolume(float volume) {}

        @Override
        public boolean hasNext() {
            return true;
        }

        @Override
        public void next() {}

        @Override
        public boolean hasPrevious() {
            return false;
        }

        @Override
        public void previous() {}

        @Override
        public int queued() {
            return 3;
        }

        @Override
        public List<MediaRef> upcoming() {
            return List.of(ref, ref, ref);
        }

        @Override
        public int queueVersion() {
            return 0;
        }

        @Override
        public void jumpTo(int index) {}

        @Override
        public void removeQueued(int index) {}

        @Override
        public void moveQueued(int from, int insertBefore) {}

        @Override
        public void setRepeat(RepeatMode mode) {}

        @Override
        public void setShuffle(boolean on) {}

        @Override
        public RepeatMode repeat() {
            return RepeatMode.OFF;
        }

        @Override
        public void cycleRepeat() {}

        @Override
        public boolean shuffle() {
            return false;
        }

        @Override
        public void toggleShuffle() {}

        @Override
        public void retry() {}

        @Override
        public void enqueue(MediaRef ref) {}

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public void close() {}
    }

    /** A screen that draws nothing: what is measured is the mod's side of every call. */
    private static final class NullCanvas implements Canvas {
        private final TextMeasurer measurer = new TextMeasurer() {
            @Override
            public int width(Text text) {
                return text instanceof Text.Literal literal ? literal.text().length() * 6 : 40;
            }

            @Override
            public int lineHeight() {
                return 9;
            }

            @Override
            public String resolve(Text text) {
                return text instanceof Text.Literal literal ? literal.text() : "";
            }
        };

        @Override
        public int width() {
            return 1280;
        }

        @Override
        public int height() {
            return 720;
        }

        @Override
        public double pixelScale() {
            return 2;
        }

        @Override
        public void fill(int x0, int y0, int x1, int y1, int argb) {}

        @Override
        public void texture(TextureRef texture, int x, int y, int w, int h) {}

        @Override
        public void text(Text text, int x, int y, int argb, boolean shadow) {}

        @Override
        public TextMeasurer measurer() {
            return measurer;
        }

        @Override
        public void pushClip(int x0, int y0, int x1, int y1) {}

        @Override
        public void popClip() {}

        @Override
        public void nextLayer() {}

        @Override
        public void tooltip(List<Text> lines, int x, int y) {}
    }
}
