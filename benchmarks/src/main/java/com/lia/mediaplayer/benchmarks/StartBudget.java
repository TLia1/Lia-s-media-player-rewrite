package com.lia.mediaplayer.benchmarks;

import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.core.CoreOptions;
import com.lia.mediaplayer.core.MediaPlayerCore;
import com.lia.mediaplayer.core.playback.Playback;
import com.lia.mediaplayer.core.playback.Player;
import com.lia.mediaplayer.core.playback.PlayerStats;
import com.lia.mediaplayer.core.testing.FakeAudioSink;
import com.lia.mediaplayer.core.testing.FakePlatform;
import com.lia.mediaplayer.core.testing.MediaServer;
import com.lia.mediaplayer.core.testing.TestMedia;
import com.lia.mediaplayer.core.tools.DownloadSources;
import com.lia.mediaplayer.core.tools.HostSystem;
import com.lia.mediaplayer.core.tools.ToolManager;
import com.lia.mediaplayer.core.tools.ToolState;
import com.lia.mediaplayer.core.video.VideoFrame;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * Times what the budget (§12) asks of a start and a seek, through the whole engine — resolve,
 * probe, ffmpeg, the rings, the clock — on a 720p clip served over loopback: a "fast link".
 * Prints the median of a few runs, and what a 720p player holds in memory.
 *
 * <pre>./gradlew :benchmarks:startBudget</pre>
 *
 * <p>Needs ffmpeg and ffprobe on the machine. A link given as argument is timed instead, which
 * needs yt-dlp too for anything but a direct file.
 */
public final class StartBudget {

    private static final int RUNS = 5;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private StartBudget() {}

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("lmp-budget");
        var platform = new FakePlatform(dir.resolve("game"));
        var options = new CoreOptions(
                true,
                new ToolManager.Policy(false, false),
                HostSystem.current(),
                DownloadSources.official(),
                FakeAudioSink::new);
        try (var server = new MediaServer();
                var core = new MediaPlayerCore(platform, options)) {
            String url = args.length > 0
                    ? args[0]
                    : server.serve("/clip.mkv", TestMedia.audioVideo(dir, "clip.mkv", 1280, 720, 30));
            // A direct file needs ffmpeg and ffprobe; yt-dlp may well be missing.
            await(platform, "the tools", () -> core.tools().state() instanceof ToolState.Ready
                    || core.tools().state() instanceof ToolState.Missing);
            MediaRef ref = core.sources().match(url).orElseThrow();

            var starts = new ArrayList<Double>();
            var seeks = new ArrayList<Double>();
            PlayerStats stats = PlayerStats.NONE;
            for (int run = 0; run < RUNS; run++) {
                // A cold start each run: nothing resolved or probed yet.
                core.resolver().invalidate(ref);
                long started = System.nanoTime();
                Playback playback = core.playbacks().open(ref, core.playerOptions(false).withViewHeight(720));
                Player player = await(platform, "a player", () -> playback.player().isPresent())
                        ? playback.player().orElseThrow()
                        : null;
                await(platform, "the first frame", () -> took(player, 0));
                starts.add(millisSince(started));

                long seekAt = System.nanoTime();
                player.seek(Duration.ofSeconds(15));
                await(platform, "a frame after the seek", () -> took(player, 15_000_000));
                seeks.add(millisSince(seekAt));
                stats = player.stats();
                playback.close();
                platform.runClientTasks();
            }
            long frameBytes = 4L * 1280 * 720;
            System.out.printf(
                    Locale.ROOT,
                    "%s%nfirst frame: median %.0f ms %s%nseek to picture: median %.0f ms %s%n"
                            + "720p ring: %d frames, %.0f MB of frames (+ up to 2 in flight), %d ms of sound%n",
                    url,
                    median(starts),
                    starts,
                    median(seeks),
                    seeks,
                    stats.frameCapacity(),
                    stats.frameCapacity() * frameBytes / 1048576.0,
                    stats.soundCapacityMillis());
        }
    }

    /** Takes a due frame, as a window does, and says whether it was at or past {@code micros}. */
    private static boolean took(Player player, long micros) {
        VideoFrame frame = player.takeFrame();
        if (frame == null) {
            return false;
        }
        boolean reached = frame.ptsMicros() >= micros;
        player.releaseFrame(frame);
        return reached;
    }

    private static boolean await(FakePlatform platform, String what, BooleanSupplier condition)
            throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!condition.getAsBoolean()) {
            platform.runClientTasks();
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("Timed out waiting for " + what);
            }
            Thread.sleep(1);
        }
        return true;
    }

    private static double millisSince(long nanos) {
        return (System.nanoTime() - nanos) / 1e6;
    }

    private static double median(List<Double> values) {
        var sorted = values.stream().sorted().toList();
        return sorted.get(sorted.size() / 2);
    }
}
