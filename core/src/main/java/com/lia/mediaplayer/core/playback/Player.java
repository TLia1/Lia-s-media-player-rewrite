package com.lia.mediaplayer.core.playback;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.MediaMetadata;
import com.lia.mediaplayer.api.source.MediaMetadata.Tracks;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.source.ResolveOptions;
import com.lia.mediaplayer.api.source.ResolvedMedia;
import com.lia.mediaplayer.api.source.StreamUrl;
import com.lia.mediaplayer.core.audio.AudioException;
import com.lia.mediaplayer.core.audio.AudioPump;
import com.lia.mediaplayer.core.audio.AudioSink;
import com.lia.mediaplayer.core.audio.PcmFormat;
import com.lia.mediaplayer.core.audio.PcmRing;
import com.lia.mediaplayer.core.diag.PlaybackError;
import com.lia.mediaplayer.core.diag.jfr.RingUnderrunEvent;
import com.lia.mediaplayer.core.diag.jfr.SessionStartEvent;
import com.lia.mediaplayer.core.ffmpeg.ProbeResult;
import com.lia.mediaplayer.core.ffmpeg.Session;
import com.lia.mediaplayer.core.ffmpeg.SessionPlan;
import com.lia.mediaplayer.core.link.BuiltinSources;
import com.lia.mediaplayer.core.tools.Tool;
import com.lia.mediaplayer.core.video.FramePool;
import com.lia.mediaplayer.core.video.FrameRing;
import com.lia.mediaplayer.core.video.VideoFrame;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plays one piece of media, with sound, picture or both: the same class for a video window,
 * the audio bar and headless audio.
 *
 * <p><b>Actor.</b> Every state change happens on the player's own virtual thread, which drains
 * a queue of commands — the public methods only enqueue — and of messages from its session
 * (first frame, first sound, exit) and its pump (sound drained). Nothing else writes the
 * player's fields, so they need no lock and no {@code volatile}. Other threads read an
 * immutable {@link PlayerSnapshot}, published after each change, and the clock.
 *
 * <p><b>Clock.</b> With sound, the sound card is the master clock and the picture follows it;
 * without, the wall clock. Frames are due at their timestamp; the view takes the newest due
 * frame each render ({@link #takeFrame()}), and frames it never asked for are recycled.
 *
 * <p><b>Seek</b> starts a new session at the target, reporting {@code Seeking(target)} until
 * that session's first frame. <b>Pause</b> stops the sound and the clock; the readers keep
 * filling the rings until they are full, and then ffmpeg waits. A pause shorter than
 * {@link PlayerOptions#longPause()} resumes in place; a longer one starts a new session at the
 * paused position, because remote servers drop idle connections.
 *
 * <p>A stream URL refused with HTTP 403 has usually expired: the player resolves the link once
 * more, bypassing the cache, before reporting the failure.
 *
 * <p><b>Picture size.</b> Frames are decoded no larger than the view shows them: at the smallest
 * of {@link PlayerOptions#DECODE_STEPS} that covers the view's height, and at 30 frames a second
 * below 720 lines. A resize that crosses a step starts a new session at the current position
 * once the size has been still for {@value #RESIZE_SETTLE_MILLIS} ms. Turning the picture off
 * ({@link #setVideoEnabled}) likewise starts a session with sound alone, and turning it back on
 * one with both.
 */
public final class Player implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(Player.class);

    private static final long HOUSEKEEPING_MILLIS = 20;
    /** A view must keep its new size this long before the picture is decoded at it. */
    static final long RESIZE_SETTLE_MILLIS = 500;

    /** Once one track has data, the other gets this long before playback starts without it. */
    private static final long TRACK_WAIT_NANOS = 1_500_000_000L;

    private static final long MAX_VIDEO_BUFFER_BYTES = 96L * 1024 * 1024;
    private static final double DEFAULT_FPS = 30;

    private sealed interface Message {}

    private record Start() implements Message {}

    private record Play() implements Message {}

    private record Pause() implements Message {}

    private record TogglePause() implements Message {}

    private record Seek(Duration target) implements Message {}

    private record SeekBy(Duration delta) implements Message {}

    private record SetVolume(float volume) implements Message {}

    private record SetViewHeight(int pixels) implements Message {}

    private record SetVideo(boolean on) implements Message {}

    private record Close() implements Message {}

    private record Resolved(long attempt, ResolvedMedia media, MediaMetadata metadata) implements Message {}

    private record ResolveFailed(long attempt, Throwable error) implements Message {}

    private record FirstVideo(long generation, int width, int height) implements Message {}

    private record FirstAudio(long generation) implements Message {}

    private record Finished(long generation, Session.Outcome outcome) implements Message {}

    private record Drained(long generation) implements Message {}

    private final long id;
    private final MediaRef ref;
    private final PlayerOptions options;
    private final PlayerServices services;
    private final PlayerListener listener;
    private final BlockingQueue<Message> inbox = new LinkedBlockingQueue<>();
    private final AtomicReference<PlayerSnapshot> published = new AtomicReference<>(PlayerSnapshot.initial());
    private final CompletableFuture<Void> closedFuture = new CompletableFuture<>();
    private final FramePool framePool = new FramePool(8);
    private final WallClock wallClock;
    private final Thread actor;

    // Read by other threads.
    private volatile @Nullable FrameRing frames;
    private volatile @Nullable AudioPump pump;
    private volatile boolean audioClock;

    // Counters for stats(); written by the actor, except lateMicros (the view's thread).
    private volatile int decodeHeight;
    private volatile double decodeFps;
    private volatile long droppedEarlier;
    private volatile long lateMicros;
    private volatile long pictureUnderruns;
    private volatile long sessions;

    // Actor only.
    private PlayerState state = new PlayerState.Idle();
    private @Nullable ResolvedMedia media;
    private MediaMetadata metadata = MediaMetadata.UNKNOWN;
    private boolean canVideo;
    private boolean playVideo;
    private boolean playAudio;
    private boolean videoWanted;
    private int viewHeight;
    private int requestedViewHeight;
    private long viewChangedNanos;
    private boolean pictureStarved;
    private long reportedSoundUnderruns;
    private @Nullable SessionStartEvent sessionEvent;
    private @Nullable Session session;
    private long generation;
    private long resolveAttempt;
    private boolean wantPlaying;
    private boolean gotVideo;
    private boolean gotAudio;
    private long firstDataNanos;
    private boolean sessionFinished;
    private String sessionStderr = "";
    private boolean audioDrained;
    private long pausedAtNanos;
    private boolean retriedExpired;
    private Duration pendingStart;
    private float volume;
    private int videoWidth;
    private int videoHeight;
    private boolean closed;

    Player(long id, MediaRef ref, PlayerOptions options, PlayerServices services, PlayerListener listener) {
        if (ref.kind() != MediaKind.VIDEO && ref.kind() != MediaKind.AUDIO) {
            throw new IllegalArgumentException("A player plays video or audio, not " + ref.kind());
        }
        this.id = id;
        this.ref = ref;
        this.options = options;
        this.services = services;
        this.listener = listener;
        this.wallClock = new WallClock(services.nanoTime());
        this.pendingStart = options.startAt();
        this.wantPlaying = !options.startPaused();
        this.volume = options.volume();
        this.videoWanted = options.video();
        this.viewHeight = options.viewHeight();
        this.requestedViewHeight = options.viewHeight();
        this.wallClock.reset(micros(options.startAt()));
        this.actor = Thread.ofVirtual().name("lmp-player-" + id).unstarted(this::run);
    }

    // ---- Commands (any thread) ----------------------------------------------

    /** Resolves the link and starts playing. Called once, by whoever created the player. */
    void start() {
        actor.start();
        inbox.add(new Start());
    }

    public void play() {
        inbox.add(new Play());
    }

    public void pause() {
        inbox.add(new Pause());
    }

    public void togglePause() {
        inbox.add(new TogglePause());
    }

    /** Jumps to {@code target}, clamped to the media. */
    public void seek(Duration target) {
        inbox.add(new Seek(target));
    }

    /** Jumps by {@code delta} from wherever playback is when the command runs. */
    public void seekBy(Duration delta) {
        inbox.add(new SeekBy(delta));
    }

    /** Linear volume, 0 to 1. */
    public void setVolume(float linear) {
        inbox.add(new SetVolume(Math.max(0, Math.min(1, linear))));
    }

    /**
     * The on-screen height of the view, in real pixels: the picture is decoded at the smallest
     * step that covers it. Cheap to call every frame with the same value.
     */
    public void setViewHeight(int pixels) {
        if (pixels > 0) {
            inbox.add(new SetViewHeight(pixels));
        }
    }

    /** Whether to decode the picture; off, a video plays its sound alone. */
    public void setVideoEnabled(boolean on) {
        inbox.add(new SetVideo(on));
    }

    /** Stops playback and releases the process, the sound line and the frames. Returns at once. */
    @Override
    public void close() {
        inbox.add(new Close());
    }

    /** Completes once the player has released everything. */
    public CompletableFuture<Void> closed() {
        return closedFuture.copy();
    }

    // ---- Reads (any thread) -------------------------------------------------

    public long id() {
        return id;
    }

    public MediaRef ref() {
        return ref;
    }

    public PlayerSnapshot snapshot() {
        return published.get().withPosition(position());
    }

    public Duration position() {
        return Duration.ofNanos(positionMicros() * 1000);
    }

    /** Where playback is, from the master clock. */
    public long positionMicros() {
        AudioPump current = pump;
        return audioClock && current != null ? current.positionMicros() : wallClock.nowMicros();
    }

    /**
     * The newest frame due now, or {@code null} if there is no new one. Frames it skipped are
     * recycled. Give it back with {@link #releaseFrame} once uploaded. For the one view that
     * shows this player, on the client thread.
     */
    public @Nullable VideoFrame takeFrame() {
        FrameRing ring = frames;
        if (ring == null) {
            return null;
        }
        long now = positionMicros();
        VideoFrame frame = ring.takeDue(now);
        if (frame != null) {
            lateMicros = now - frame.ptsMicros();
        }
        return frame;
    }

    /** The counters of the debug overlay. */
    public PlayerStats stats() {
        FrameRing ring = frames;
        AudioPump currentPump = pump;
        return new PlayerStats(
                decodeHeight,
                decodeFps,
                ring == null ? 0 : ring.size(),
                ring == null ? 0 : ring.capacity(),
                currentPump == null ? 0 : currentPump.bufferedMicros() / 1000,
                currentPump == null ? 0 : currentPump.capacityMicros() / 1000,
                droppedEarlier + (ring == null ? 0 : ring.dropped()),
                lateMicros,
                currentPump == null ? 0 : currentPump.underruns(),
                pictureUnderruns,
                sessions);
    }

    public void releaseFrame(VideoFrame frame) {
        framePool.release(frame);
    }

    // ---- The actor ----------------------------------------------------------

    private void run() {
        try {
            while (!closed) {
                Message message = inbox.poll(HOUSEKEEPING_MILLIS, TimeUnit.MILLISECONDS);
                if (message != null) {
                    handle(message);
                }
                if (!closed) {
                    housekeep();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            LOGGER.error("Player {} failed", id, e);
        } finally {
            teardown();
            closedFuture.complete(null);
        }
    }

    private void handle(Message message) {
        switch (message) {
            case Start start -> {
                if (state instanceof PlayerState.Idle) {
                    beginResolve();
                }
            }
            case Play play -> onPlay();
            case Pause pause -> onPause();
            case TogglePause toggle -> {
                if (wantPlaying && !(state instanceof PlayerState.Ended || state instanceof PlayerState.Failed)) {
                    onPause();
                } else {
                    onPlay();
                }
            }
            case Seek seek -> onSeek(seek.target());
            case SeekBy seekBy ->
                onSeek(Duration.ofNanos(positionMicros() * 1000).plus(seekBy.delta()));
            case SetVolume set -> {
                volume = set.volume();
                AudioPump current = pump;
                if (current != null) {
                    current.setGain(volume);
                }
            }
            case SetViewHeight view -> {
                if (view.pixels() != requestedViewHeight) {
                    requestedViewHeight = view.pixels();
                    viewChangedNanos = services.nanoTime().getAsLong();
                    if (media == null) {
                        // Not decoding yet: the first session starts at the right size.
                        viewHeight = view.pixels();
                    }
                }
            }
            case SetVideo video -> onSetVideo(video.on());
            case Close close -> closed = true;
            case Resolved resolved -> {
                if (resolved.attempt() == resolveAttempt) {
                    onResolved(resolved.media(), resolved.metadata());
                }
            }
            case ResolveFailed failed -> {
                if (failed.attempt() == resolveAttempt) {
                    fail(PlaybackError.of(failed.error()));
                }
            }
            case FirstVideo first -> {
                if (first.generation() == generation) {
                    gotVideo = true;
                    videoWidth = first.width();
                    videoHeight = first.height();
                    noteFirstData();
                }
            }
            case FirstAudio first -> {
                if (first.generation() == generation) {
                    gotAudio = true;
                    noteFirstData();
                }
            }
            case Finished finished -> {
                if (finished.generation() == generation) {
                    onFinished(finished.outcome());
                }
            }
            case Drained drained -> {
                if (drained.generation() == generation) {
                    audioDrained = true;
                }
            }
        }
    }

    private void beginResolve() {
        long attempt = ++resolveAttempt;
        setState(new PlayerState.Resolving());
        boolean audioOnly = ref.kind() == MediaKind.AUDIO || options.resolve().audioOnly();
        ResolveOptions resolveOptions = new ResolveOptions(options.resolve().maxHeight(), audioOnly);
        var unused = services.resolver()
                .resolve(ref, resolveOptions)
                .thenApplyAsync(this::withProbedMetadata, services.io())
                .whenComplete((resolved, error) -> {
                    if (error != null) {
                        inbox.add(new ResolveFailed(attempt, unwrap(error)));
                    } else {
                        inbox.add(new Resolved(attempt, resolved.media(), resolved.metadata()));
                    }
                });
    }

    private record Probed(ResolvedMedia media, MediaMetadata metadata) {}

    /** Probes the stream when the resolver could not say what it contains. On the IO executor. */
    private Probed withProbedMetadata(ResolvedMedia resolved) {
        MediaMetadata known = resolved.metadata();
        if (known.tracks() != Tracks.UNKNOWN) {
            return new Probed(resolved, known);
        }
        ProbeResult probe;
        try {
            probe = services.probes().probe(resolved.primary(), resolved.httpHeaders());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        var merged = new MediaMetadata(
                known.title(),
                known.duration().or(probe::duration),
                known.thumbnail(),
                known.live(),
                known.width() > 0 ? known.width() : probe.width(),
                known.height() > 0 ? known.height() : probe.height(),
                known.fps() > 0 ? known.fps() : probe.fps(),
                Tracks.of(probe.hasVideo(), probe.hasAudio()));
        return new Probed(resolved, merged);
    }

    private void onResolved(ResolvedMedia resolved, MediaMetadata resolvedMetadata) {
        media = resolved;
        metadata = resolvedMetadata;
        boolean audioOnly = ref.kind() == MediaKind.AUDIO || options.resolve().audioOnly();
        canVideo = metadata.tracks().hasVideo() && !audioOnly;
        playAudio = metadata.tracks().hasAudio();
        playVideo = canVideo && (videoWanted || !playAudio);
        if (playAudio && !openSound()) {
            if (!canVideo) {
                fail(new PlaybackError(PlaybackError.Cause.NO_AUDIO_DEVICE, "No audio device could be opened"));
                return;
            }
            LOGGER.warn("Player {}: no sound device, playing {} without sound", id, ref.url());
            playAudio = false;
            playVideo = true;
        }
        if (!playVideo && !playAudio) {
            fail(PlaybackError.classify(ref.url() + " does not contain any stream ffmpeg can play"));
            return;
        }
        launch(pendingStart, false);
    }

    /** Opens the sound line once per player; it is reused by every session. */
    private boolean openSound() {
        if (pump != null) {
            return true;
        }
        AudioSink sink = services.audioSinks().get();
        try {
            sink.open(PcmFormat.STEREO_48K);
        } catch (AudioException e) {
            LOGGER.warn("Player {}: {}", id, e.getMessage());
            sink.close();
            return false;
        }
        sink.setGain(volume);
        pump = new AudioPump(
                sink, PcmFormat.STEREO_48K, "lmp-player-" + id + "-audio", gen -> inbox.add(new Drained(gen)));
        return true;
    }

    private void launch(Duration at, boolean seeking) {
        ResolvedMedia current = media;
        if (current == null) {
            pendingStart = at;
            return;
        }
        closeSession();
        long gen = ++generation;
        String ffmpeg;
        try {
            ffmpeg = services.tools().require(Tool.FFMPEG).command();
        } catch (IOException e) {
            fail(PlaybackError.of(e));
            return;
        }

        StreamUrl primary;
        Optional<StreamUrl> separateAudio = Optional.empty();
        if (playVideo) {
            primary = current.video().orElseThrow();
            if (playAudio && current.audio().isPresent()) {
                separateAudio = current.audio();
            }
        } else {
            primary = current.audio().or(current::video).orElseThrow();
        }
        int step = PlayerOptions.decodeHeight(viewHeight, options.resolve().maxHeight());
        double fps = Math.min(metadata.fps() > 0 ? metadata.fps() : DEFAULT_FPS, options.fpsFor(step));
        int[] box = decodeBox(step);
        var plan = new SessionPlan(
                primary, separateAudio, current.httpHeaders(), playVideo, playAudio, at, box[0], box[1], fps);

        FrameRing ring = playVideo ? new FrameRing(frameCapacity(fps, box), framePool) : null;
        PcmRing pcm = playAudio ? PcmRing.forDuration(PcmFormat.STEREO_48K, seconds(options.audioBuffer())) : null;
        long startMicros = micros(at);
        AudioPump currentPump = pump;
        if (currentPump != null) {
            currentPump.pause();
            if (pcm != null) {
                currentPump.switchTo(pcm, gen, startMicros);
            }
        }
        audioClock = pcm != null;
        wallClock.reset(startMicros);
        gotVideo = false;
        gotAudio = false;
        firstDataNanos = 0;
        sessionFinished = false;
        sessionStderr = "";
        audioDrained = false;
        pictureStarved = false;
        retireFrames();
        frames = ring;
        decodeHeight = playVideo ? box[1] : 0;
        decodeFps = playVideo ? fps : 0;
        sessions = gen;
        sessionEvent = SessionStartEvent.start(id, gen, seeking, playVideo, playAudio);

        try {
            session = Session.start(
                    "session-" + id + "." + gen,
                    gen,
                    ffmpeg,
                    plan,
                    services.processes(),
                    ring,
                    framePool,
                    pcm,
                    new Session.Listener() {
                        @Override
                        public void firstVideoFrame(long generation, int width, int height) {
                            inbox.add(new FirstVideo(generation, width, height));
                        }

                        @Override
                        public void firstAudio(long generation) {
                            inbox.add(new FirstAudio(generation));
                        }

                        @Override
                        public void finished(long generation, Session.Outcome outcome) {
                            inbox.add(new Finished(generation, outcome));
                        }
                    });
        } catch (IOException e) {
            if (ring != null) {
                ring.close();
            }
            retireFrames();
            endSessionEvent(false);
            fail(PlaybackError.of(e));
            return;
        }
        setState(seeking ? new PlayerState.Seeking(at) : new PlayerState.Buffering(at));
    }

    /**
     * The box frames are scaled into: {@code height} lines at 16:9, but never larger than
     * the source, so a small video is not scaled up just to be uploaded bigger. A rotated video
     * reports its coded size, so the box takes its longer side both ways.
     */
    private int[] decodeBox(int step) {
        int height = step;
        int width = (height * 16 / 9 + 1) & ~1;
        int longest = Math.max(metadata.width(), metadata.height());
        if (longest > 0) {
            width = Math.min(width, longest);
            height = Math.min(height, longest);
        }
        return new int[] {width, height};
    }

    private int frameCapacity(double fps, int[] box) {
        long frameBytes = 4L * box[0] * box[1];
        long byTime = (long) Math.ceil(fps * seconds(options.videoBuffer()));
        long byMemory = MAX_VIDEO_BUFFER_BYTES / frameBytes;
        return (int) Math.max(3, Math.min(byTime, byMemory));
    }

    private void noteFirstData() {
        if (firstDataNanos == 0) {
            firstDataNanos = services.nanoTime().getAsLong();
            endSessionEvent(true);
        }
        becomeReadyIfDue();
    }

    /** Leaves {@code Buffering}/{@code Seeking} once every track has data, or one has waited long enough. */
    private void becomeReadyIfDue() {
        if (!(state instanceof PlayerState.Buffering || state instanceof PlayerState.Seeking)) {
            return;
        }
        boolean allTracks = (!playVideo || gotVideo) && (!playAudio || gotAudio);
        boolean waitedEnough =
                firstDataNanos != 0 && services.nanoTime().getAsLong() - firstDataNanos > TRACK_WAIT_NANOS;
        if (!allTracks && !waitedEnough && !sessionFinished) {
            return;
        }
        if (!gotVideo && !gotAudio) {
            fail(PlaybackError.classify("ffmpeg exited without producing anything for " + ref.url()
                    + (sessionStderr.isBlank() ? "" : "\n" + sessionStderr)));
            return;
        }
        Duration at = position();
        if (wantPlaying) {
            startOutput();
            setState(new PlayerState.Playing(at));
        } else {
            pausedAtNanos = services.nanoTime().getAsLong();
            setState(new PlayerState.Paused(at));
        }
    }

    private void onPlay() {
        wantPlaying = true;
        switch (state) {
            case PlayerState.Paused paused -> resume();
            case PlayerState.Ended ended -> launch(Duration.ZERO, false);
            case PlayerState.Failed failed -> {
                retriedExpired = false;
                services.resolver().invalidate(ref);
                beginResolve();
            }
            default -> {
                // Idle, Resolving, Buffering, Seeking: wantPlaying applies once ready.
            }
        }
    }

    private void onPause() {
        wantPlaying = false;
        if (state instanceof PlayerState.Playing) {
            Duration at = position();
            stopOutput();
            pausedAtNanos = services.nanoTime().getAsLong();
            setState(new PlayerState.Paused(at));
        }
    }

    private void resume() {
        long pausedFor = services.nanoTime().getAsLong() - pausedAtNanos;
        // Everything an ended session wrote is still in the rings, however long the pause.
        if (session != null
                && (sessionFinished || pausedFor < options.longPause().toNanos())) {
            Duration at = position();
            startOutput();
            setState(new PlayerState.Playing(at));
        } else {
            launch(position(), false);
        }
    }

    private void onSetVideo(boolean on) {
        videoWanted = on;
        boolean wanted = canVideo && (on || !playAudio);
        if (media == null || wanted == playVideo) {
            return;
        }
        playVideo = wanted;
        relaunchInPlace();
    }

    /**
     * Starts a session at the current position with the current picture settings, where one is
     * running; otherwise they apply to the next. Not a seek: the state goes through {@code
     * Buffering}, and listeners see no jump.
     */
    private void relaunchInPlace() {
        switch (state) {
            case PlayerState.Playing playing -> launch(position(), false);
            case PlayerState.Paused paused -> launch(position(), false);
            case PlayerState.Buffering buffering -> launch(buffering.position(), false);
            case PlayerState.Seeking seeking -> launch(seeking.target(), true);
            default -> {
                // Idle, Resolving, Ended, Failed: the next session uses the new settings.
            }
        }
    }

    /** Decodes at the view's new size once it has kept it long enough. Housekeeping. */
    private void followViewHeight() {
        if (requestedViewHeight == viewHeight
                || services.nanoTime().getAsLong() - viewChangedNanos < RESIZE_SETTLE_MILLIS * 1_000_000) {
            return;
        }
        if (state instanceof PlayerState.Buffering || state instanceof PlayerState.Seeking) {
            // A session is starting; look again once it runs.
            return;
        }
        int max = options.resolve().maxHeight();
        boolean crossesStep =
                PlayerOptions.decodeHeight(requestedViewHeight, max) != PlayerOptions.decodeHeight(viewHeight, max);
        viewHeight = requestedViewHeight;
        if (crossesStep && playVideo && session != null && !sessionFinished) {
            relaunchInPlace();
        }
    }

    private void onSeek(Duration requested) {
        Duration target = clamp(requested);
        switch (state) {
            case PlayerState.Idle idle -> pendingStart = target;
            case PlayerState.Resolving resolving -> pendingStart = target;
            case PlayerState.Failed failed -> pendingStart = target;
            default -> launch(target, true);
        }
    }

    private Duration clamp(Duration target) {
        if (target.isNegative()) {
            return Duration.ZERO;
        }
        Optional<Duration> duration = metadata.duration();
        if (duration.isPresent() && target.compareTo(duration.get()) >= 0) {
            // Just before the end, so a seek past it still shows the last frames.
            Duration last = duration.get().minusMillis(500);
            return last.isNegative() ? Duration.ZERO : last;
        }
        return target;
    }

    private void onFinished(Session.Outcome outcome) {
        sessionFinished = true;
        sessionStderr = outcome.stderr();
        if (outcome.closed()) {
            return;
        }
        if (outcome.exitCode() != 0) {
            onSessionFailed(outcome);
            return;
        }
        becomeReadyIfDue();
    }

    private void onSessionFailed(Session.Outcome outcome) {
        String detail = outcome.stderr().isBlank() ? "ffmpeg exited with code " + outcome.exitCode() : outcome.stderr();
        PlaybackError error = PlaybackError.classify(detail);
        if (error.cause() == PlaybackError.Cause.LINK_EXPIRED
                && !retriedExpired
                && !BuiltinSources.isDirect(ref.sourceId())) {
            LOGGER.info("Player {}: stream refused, resolving {} again", id, ref.url());
            retriedExpired = true;
            pendingStart = position();
            closeSession();
            services.resolver().invalidate(ref);
            beginResolve();
            return;
        }
        fail(error);
    }

    /** Runs every {@value #HOUSEKEEPING_MILLIS} ms on the actor. */
    private void housekeep() {
        FrameRing ring = frames;
        if (ring != null) {
            ring.discardSuperseded(positionMicros());
        }
        followViewHeight();
        switch (state) {
            case PlayerState.Buffering buffering -> becomeReadyIfDue();
            case PlayerState.Seeking seeking -> becomeReadyIfDue();
            case PlayerState.Playing playing -> {
                countUnderruns(ring);
                // With sound, the end is when the sound has played out; the picture follows it.
                if (sessionFinished && (playAudio ? audioDrained : ring == null || ring.allDue(positionMicros()))) {
                    ended();
                }
            }
            default -> {
                // Nothing moves in the other states.
            }
        }
    }

    /** Notes the picture or the sound running dry mid-stream, for the counters and the flight recorder. */
    @SuppressWarnings("NonAtomicVolatileUpdate") // pictureUnderruns has a single writer: the actor
    private void countUnderruns(@Nullable FrameRing ring) {
        if (playVideo && ring != null && !sessionFinished) {
            boolean empty = ring.size() == 0;
            if (empty && !pictureStarved) {
                pictureUnderruns++;
                RingUnderrunEvent.report(id, "video", 1);
            }
            pictureStarved = empty;
        }
        AudioPump currentPump = pump;
        if (currentPump != null) {
            long underruns = currentPump.underruns();
            if (underruns > reportedSoundUnderruns) {
                RingUnderrunEvent.report(id, "audio", underruns - reportedSoundUnderruns);
                reportedSoundUnderruns = underruns;
            }
        }
    }

    private void ended() {
        stopOutput();
        closeSession();
        setState(new PlayerState.Ended());
    }

    private void fail(PlaybackError error) {
        LOGGER.warn("Player {} failed on {}: {}", id, ref.url(), error.shortText());
        stopOutput();
        closeSession();
        setState(new PlayerState.Failed(error));
    }

    private void startOutput() {
        AudioPump current = pump;
        if (playAudio && current != null) {
            current.play();
        } else {
            wallClock.resume();
        }
    }

    private void stopOutput() {
        AudioPump current = pump;
        if (current != null) {
            current.pause();
        }
        wallClock.pause();
    }

    private void closeSession() {
        endSessionEvent(false);
        Session current = session;
        session = null;
        if (current != null) {
            current.close();
        }
    }

    private void endSessionEvent(boolean producedData) {
        SessionStartEvent event = sessionEvent;
        sessionEvent = null;
        if (event != null) {
            event.finish(producedData ? decodeHeight : 0, producedData);
        }
    }

    /** Counts what the outgoing frame ring dropped before it goes. */
    @SuppressWarnings("NonAtomicVolatileUpdate") // droppedEarlier has a single writer: the actor
    private void retireFrames() {
        FrameRing old = frames;
        frames = null;
        if (old != null) {
            droppedEarlier += old.dropped();
        }
    }

    private void teardown() {
        stopOutput();
        closeSession();
        retireFrames();
        AudioPump current = pump;
        pump = null;
        audioClock = false;
        if (current != null) {
            current.close();
        }
    }

    private void setState(PlayerState newState) {
        state = newState;
        boolean video = playVideo;
        boolean audio = playAudio;
        published.set(new PlayerSnapshot(
                newState,
                Duration.ofNanos(positionMicros() * 1000),
                metadata.duration(),
                metadata.title(),
                video,
                audio,
                videoWidth,
                videoHeight));
        services.clientThread().execute(() -> listener.stateChanged(this, newState));
    }

    private static Throwable unwrap(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause instanceof UncheckedIOException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static long micros(Duration duration) {
        return duration.toNanos() / 1000;
    }

    private static double seconds(Duration duration) {
        return duration.toNanos() / 1e9;
    }

    @Override
    public String toString() {
        return "Player " + id + " (" + ref.url() + ")";
    }
}
