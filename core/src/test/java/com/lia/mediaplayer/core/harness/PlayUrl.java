package com.lia.mediaplayer.core.harness;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.source.ResolveOptions;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.CoreOptions;
import com.lia.mediaplayer.core.MediaPlayerCore;
import com.lia.mediaplayer.core.lifecycle.ClientThread;
import com.lia.mediaplayer.core.platform.Platform;
import com.lia.mediaplayer.core.platform.TextMeasurer;
import com.lia.mediaplayer.core.playback.Playback;
import com.lia.mediaplayer.core.playback.Player;
import com.lia.mediaplayer.core.playback.PlayerOptions;
import com.lia.mediaplayer.core.playback.PlayerSnapshot;
import com.lia.mediaplayer.core.playback.PlayerState;
import com.lia.mediaplayer.core.testing.FakePlatform;
import com.lia.mediaplayer.core.testing.FakeText;
import com.lia.mediaplayer.core.testing.FakeTextures;
import com.lia.mediaplayer.core.tools.ToolState;
import com.lia.mediaplayer.core.video.VideoFrame;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

/**
 * Plays a link through the real engine from a terminal — the fastest way to debug resolution,
 * sessions and sync without starting the game. Sound goes to the real sound card; the picture
 * is counted, and can be saved as a PNG.
 *
 * <pre>
 * ./gradlew :core:playUrl --args="[--audio] [--height 1080] [--script 'cmd; cmd'] &lt;url&gt;"
 * </pre>
 *
 * Commands (typed, or separated by {@code ;} in {@code --script}): {@code p} pause/resume,
 * {@code s <seconds>} seek, {@code f}/{@code b} ±10 s, {@code v <0..1>} volume, {@code n}
 * next, {@code shot [file.png]}, {@code wait <seconds>} (scripts), {@code q} quit.
 */
public final class PlayUrl {

    private final ScheduledExecutorService client = Executors.newSingleThreadScheduledExecutor(
            task -> Thread.ofPlatform().name("harness-client").daemon().unstarted(task));
    private volatile Thread clientThread = Thread.currentThread();
    private final CountDownLatch done = new CountDownLatch(1);

    private Playback playback;
    private VideoFrame shown;
    private long framesShown;
    private long lastFrames;
    private volatile boolean failed;

    public static void main(String[] args) throws Exception {
        System.exit(new PlayUrl().run(args));
    }

    private int run(String[] args) throws Exception {
        boolean audioOnly = false;
        int height = 720;
        String script = null;
        String url = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--audio" -> audioOnly = true;
                case "--height" -> height = Integer.parseInt(args[++i]);
                case "--script" -> script = args[++i];
                default -> url = args[i];
            }
        }
        if (url == null) {
            System.err.println("Usage: playUrl [--audio] [--height N] [--script 'cmd; cmd'] <url>");
            return 2;
        }
        client.execute(() -> clientThread = Thread.currentThread());

        Path gameDir = Path.of(System.getProperty("liasmediaplayer.harness.gamedir", "run/harness"));
        ClientThread thread = new ClientThread() {
            @Override
            public void execute(Runnable task) {
                client.execute(task);
            }

            @Override
            public boolean isCurrent() {
                return Thread.currentThread() == clientThread;
            }
        };
        var textures = new FakeTextures();
        var text = new FakeText();
        var clipboard = new FakePlatform(gameDir).clipboard();
        Platform platform = new Platform() {
            @Override
            public String describe() {
                return "CLI harness";
            }

            @Override
            public ClientThread clientThread() {
                return thread;
            }

            @Override
            public Path gameDirectory() {
                return gameDir;
            }

            // A terminal shows no picture and no text: the headless stand-ins will do.
            @Override
            public FakeTextures textures() {
                return textures;
            }

            @Override
            public TextMeasurer text() {
                return text;
            }

            @Override
            public Clipboard clipboard() {
                return clipboard;
            }

            @Override
            public void openInBrowser(String link) {
                say("open in browser: " + link);
            }

            @Override
            public float masterVolume() {
                return 1;
            }
        };

        try (var core = new MediaPlayerCore(platform, CoreOptions.defaults())) {
            core.events().subscribe(ToolState.class, state -> {
                if (!(state instanceof ToolState.Downloading downloading) || downloading.progress() < 0) {
                    say("tools: " + state);
                } else if ((int) (downloading.progress() * 100) % 10 == 0) {
                    say("tools: downloading " + downloading.tool() + " " + (int) (downloading.progress() * 100) + "%");
                }
            });
            String link = url;
            MediaRef ref = core.sources()
                    .match(link)
                    .orElseGet(() -> new MediaRef(link, MediaKind.VIDEO, "harness:generic", Text.literal("[link]")));
            say("link: " + ref.kind() + " via " + ref.sourceId());
            var options = PlayerOptions.DEFAULT.withResolve(new ResolveOptions(height, audioOnly));

            client.execute(() -> {
                playback = core.playbacks().open(ref, options);
                playback.setListener((player, state) -> onState(state));
            });
            var rendering = client.scheduleAtFixedRate(this::render, 0, 16, TimeUnit.MILLISECONDS);
            var reporting = client.scheduleAtFixedRate(this::status, 1, 1, TimeUnit.SECONDS);

            List<String> commands = script != null ? splitScript(script) : null;
            if (commands != null) {
                for (String command : commands) {
                    if (!execute(command.strip())) {
                        break;
                    }
                }
            } else {
                var in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                String line;
                while ((line = in.readLine()) != null && execute(line.strip())) {
                    // next command
                }
            }
            client.submit(() -> status()).get();
            rendering.cancel(false);
            reporting.cancel(false);
            say("frames shown: " + framesShown);
        }
        client.shutdownNow();
        return failed ? 1 : 0;
    }

    /** Runs one command; {@code false} to quit. */
    private boolean execute(String command) throws Exception {
        if (command.isEmpty()) {
            return true;
        }
        String[] parts = command.split("\\s+", 2);
        String argument = parts.length > 1 ? parts[1] : "";
        switch (parts[0].toLowerCase(Locale.ROOT)) {
            case "q", "quit" -> {
                return false;
            }
            case "wait" -> {
                long millis = (long) (Double.parseDouble(argument) * 1000);
                if (done.await(millis, TimeUnit.MILLISECONDS)) {
                    return false;
                }
            }
            case "p", "pause", "play" -> withPlayer(Player::togglePause);
            case "s", "seek" ->
                withPlayer(p -> p.seek(Duration.ofMillis((long) (Double.parseDouble(argument) * 1000))));
            case "f" -> withPlayer(p -> p.seekBy(Duration.ofSeconds(10)));
            case "b" -> withPlayer(p -> p.seekBy(Duration.ofSeconds(-10)));
            case "v" -> client.execute(() -> playback.setVolume(Float.parseFloat(argument)));
            case "n", "next" -> client.execute(() -> playback.next());
            case "shot" ->
                client.submit(() -> screenshot(argument.isEmpty() ? "frame.png" : argument))
                        .get();
            case "help" -> say("p | s <sec> | f | b | v <0..1> | n | shot [file] | wait <sec> | q");
            default -> say("unknown command: " + command + " (try help)");
        }
        return true;
    }

    private void withPlayer(java.util.function.Consumer<Player> action) {
        client.execute(() -> playback.player().ifPresent(action));
    }

    private void onState(PlayerState state) {
        say("state: " + describe(state));
        if (state instanceof PlayerState.Failed) {
            failed = true;
            done.countDown();
        }
        if (state instanceof PlayerState.Ended && !playback.queue().hasNext()) {
            done.countDown();
        }
    }

    /** What the game's video window would do each frame: upload the newest due frame. */
    private void render() {
        if (playback == null) {
            return;
        }
        Optional<Player> player = playback.player();
        if (player.isEmpty()) {
            return;
        }
        VideoFrame frame = player.get().takeFrame();
        if (frame != null) {
            if (shown != null) {
                player.get().releaseFrame(shown);
            }
            shown = frame;
            framesShown++;
        }
    }

    private void status() {
        if (playback == null || playback.player().isEmpty()) {
            return;
        }
        PlayerSnapshot snapshot = playback.player().get().snapshot();
        long frames = framesShown - lastFrames;
        lastFrames = framesShown;
        say(String.format(
                Locale.ROOT,
                "  %-10s %s / %s  %s  %d fps shown  %s",
                snapshot.state().getClass().getSimpleName(),
                time(snapshot.position()),
                snapshot.duration().map(PlayUrl::time).orElse("?"),
                snapshot.hasVideo() ? snapshot.videoWidth() + "x" + snapshot.videoHeight() : "no picture",
                frames,
                snapshot.title().orElse("")));
    }

    private Void screenshot(String file) throws IOException {
        VideoFrame frame = shown;
        if (frame == null) {
            say("no frame to save");
            return null;
        }
        var image = new BufferedImage(frame.width(), frame.height(), BufferedImage.TYPE_INT_ARGB);
        byte[] px = frame.pixels();
        for (int y = 0, i = 0; y < frame.height(); y++) {
            for (int x = 0; x < frame.width(); x++, i += 4) {
                int argb =
                        (px[i + 3] & 0xFF) << 24 | (px[i] & 0xFF) << 16 | (px[i + 1] & 0xFF) << 8 | (px[i + 2] & 0xFF);
                image.setRGB(x, y, argb);
            }
        }
        Path out = Path.of(file).toAbsolutePath();
        ImageIO.write(image, "png", out.toFile());
        say("saved " + frame.width() + "x" + frame.height() + " frame at "
                + time(Duration.ofNanos(frame.ptsMicros() * 1000)) + " to " + out);
        return null;
    }

    private static String describe(PlayerState state) {
        return switch (state) {
            case PlayerState.Failed failed -> "Failed — " + failed.error().shortText();
            case PlayerState.Playing playing -> "Playing from " + time(playing.position());
            case PlayerState.Paused paused -> "Paused at " + time(paused.position());
            case PlayerState.Seeking seeking -> "Seeking to " + time(seeking.target());
            case PlayerState.Buffering buffering -> "Buffering at " + time(buffering.position());
            case PlayerState.Idle idle -> "Idle";
            case PlayerState.Resolving resolving -> "Resolving";
            case PlayerState.Ended ended -> "Ended";
        };
    }

    private static String time(Duration d) {
        long millis = d.toMillis();
        return String.format(Locale.ROOT, "%d:%02d.%d", millis / 60_000, millis / 1000 % 60, millis / 100 % 10);
    }

    private static List<String> splitScript(String script) {
        var out = new ArrayList<String>();
        for (String part : script.split(";", -1)) {
            out.add(part);
        }
        return out;
    }

    private static void say(String line) {
        System.out.println("[" + String.format(Locale.ROOT, "%tT", System.currentTimeMillis()) + "] " + line);
    }
}
