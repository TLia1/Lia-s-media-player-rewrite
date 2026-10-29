package com.lia.mediaplayer.ui;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.source.ResolveOptions;
import com.lia.mediaplayer.core.MediaPlayerCore;
import com.lia.mediaplayer.core.link.ChatLink;
import com.lia.mediaplayer.core.playback.PlayerOptions;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.command.ShowCommand;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.input.Mods;
import com.lia.mediaplayer.ui.input.PointerTracker;
import com.lia.mediaplayer.ui.overlay.ChatOverlay;
import com.lia.mediaplayer.ui.screen.MediaControlsScreen;
import com.lia.mediaplayer.ui.screen.ScreenContext;
import com.lia.mediaplayer.ui.screen.UiScreen;
import com.lia.mediaplayer.ui.theme.Theme;
import com.lia.mediaplayer.ui.theme.Themes;
import com.lia.mediaplayer.ui.widget.RenderContext;
import com.lia.mediaplayer.ui.window.Window;
import com.lia.mediaplayer.ui.window.WindowManager;
import com.lia.mediaplayer.ui.windows.AudioBar;
import com.lia.mediaplayer.ui.windows.CorePlaybackControl;
import com.lia.mediaplayer.ui.windows.ImageWindow;
import com.lia.mediaplayer.ui.windows.VideoWindow;
import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

/**
 * The root of the UI layer: owns the windows and the mod's screens, and is what the loader
 * bridges call through {@link ClientEvents}. Client thread only.
 *
 * <p>Windows are live — hovered, clicked, dragged — over the chat and over a screen of the mod
 * that shows them. Everywhere else they are drawn on the HUD, picture only. Over the chat, the
 * {@link ChatOverlay} also previews picture labels and plays the labels clicked.
 */
public final class MediaPlayerUi implements ClientEvents {

    private final MediaPlayerCore core;
    private final UiHost host;
    private final LongSupplier nanoTime;
    private final WindowManager windows = new WindowManager();
    private final PointerTracker pointer = new PointerTracker();
    private final ShowCommand show;
    private final ChatOverlay chat;
    private Theme theme = Themes.DARK;
    private long ticks;

    public MediaPlayerUi(MediaPlayerCore core, UiHost host) {
        this(core, host, System::nanoTime);
    }

    public MediaPlayerUi(MediaPlayerCore core, UiHost host, LongSupplier nanoTime) {
        this.core = core;
        this.host = host;
        this.nanoTime = nanoTime;
        this.show = new ShowCommand(core.sources(), this::play);
        this.chat = new ChatOverlay(host, core.chat(), core.sources(), core.images(), this::play);
    }

    /** At most this many pictures are pinned; the oldest gives way. */
    static final int MAX_IMAGE_WINDOWS = 8;

    public WindowManager windows() {
        return windows;
    }

    public ShowCommand showCommand() {
        return show;
    }

    public Theme theme() {
        return theme;
    }

    /** Client ticks seen since startup. */
    public long ticks() {
        return ticks;
    }

    // ---- Playing ------------------------------------------------------------

    /**
     * Plays {@code ref}: in the front window of its kind, queued after what plays there, or
     * in a new window when there is none or {@code newWindow} asks for one. A picture is pinned
     * in a window of its own, once; a sound, or a list of sounds, always plays in an audio bar.
     */
    public void play(MediaRef ref, boolean audioOnly, boolean newWindow) {
        core.platform().clientThread().checkCurrent();
        if (ref.kind() == MediaKind.IMAGE) {
            pin(ref);
            return;
        }
        if (core.sources().playsAs(ref) == MediaKind.AUDIO) {
            audioOnly = true;
        }
        Optional<? extends Window> front = Optional.empty();
        if (!newWindow) {
            front = audioOnly ? windows.frontMost(AudioBar.class) : windows.frontMost(VideoWindow.class);
        }
        if (front.isPresent()) {
            Window window = front.get();
            if (window instanceof AudioBar bar) {
                bar.control().enqueue(ref);
            } else if (window instanceof VideoWindow video) {
                video.control().enqueue(ref);
            }
            window.raise();
            return;
        }
        PlayerOptions options = audioOnly
                ? PlayerOptions.DEFAULT.withResolve(new ResolveOptions(ResolveOptions.DEFAULT.maxHeight(), true))
                : PlayerOptions.DEFAULT;
        var playback = core.playbacks().open(ref, options);
        var control = new CorePlaybackControl(playback, core.playbacks());
        windows.open(audioOnly ? new AudioBar(control, core.platform()) : new VideoWindow(control, core.platform()));
    }

    /** Shows a picture in a window, bringing forward the one that already shows it. */
    private void pin(MediaRef ref) {
        Optional<ImageWindow> shown = imageWindow(ref.url());
        if (shown.isPresent()) {
            shown.get().raise();
            return;
        }
        List<ImageWindow> pinned = windows.windows().stream()
                .filter(ImageWindow.class::isInstance)
                .map(ImageWindow.class::cast)
                .toList();
        // Bottom-most first: the one looked at least recently gives way.
        for (int i = 0; i <= pinned.size() - MAX_IMAGE_WINDOWS; i++) {
            pinned.get(i).close();
        }
        windows.open(new ImageWindow(ref, core.images(), core.platform()));
    }

    private Optional<ImageWindow> imageWindow(String url) {
        return windows.windows().stream()
                .filter(w -> w instanceof ImageWindow image && image.ref().url().equals(url))
                .map(ImageWindow.class::cast)
                .findFirst();
    }

    /** Opens the windows, live, without the chat. */
    public void openMediaControls() {
        host.openScreen(new MediaControlsScreen(this::theme, nanoTime, windows::isEmpty));
    }

    // ---- ClientEvents ---------------------------------------------------------

    @Override
    public void tick() {
        core.platform().clientThread().checkCurrent();
        ticks++;
        core.images().tick();
        windows.tick();
    }

    @Override
    public void renderScreen(ScreenContext screen, Canvas canvas, double mouseX, double mouseY, Mods mods) {
        if (!windowsLive(screen)) {
            return;
        }
        InputEvent moved = pointer.moveTo(mouseX, mouseY, mods);
        if (moved != null) {
            windows.input(moved);
        }
        var ctx = new RenderContext(theme, nanoTime.getAsLong(), mouseX, mouseY, true);
        windows.render(canvas, ctx);
        if (screen.kind() == ScreenContext.Kind.CHAT && !windows.covers(mouseX, mouseY)) {
            chat.render(canvas, ctx, url -> imageWindow(url).isPresent());
        }
    }

    @Override
    public void renderHud(ScreenContext screen, Canvas canvas) {
        if (windowsLive(screen)) {
            // The screen draws them, live, over itself.
            return;
        }
        windows.renderHud(canvas, new RenderContext(theme, nanoTime.getAsLong(), Double.NaN, Double.NaN, false));
    }

    @Override
    public boolean input(ScreenContext screen, InputEvent event) {
        if (!windowsLive(screen)) {
            return false;
        }
        InputEvent stamped = switch (event) {
            case InputEvent.PointerDown down ->
                pointer.press(down.x(), down.y(), down.button(), down.mods(), nanoTime.getAsLong());
            case InputEvent.PointerUp up -> pointer.release(up.x(), up.y(), up.button(), up.mods());
            default -> event;
        };
        if (windows.input(stamped)) {
            return true;
        }
        return screen.kind() == ScreenContext.Kind.CHAT
                && stamped instanceof InputEvent.PointerDown down
                && chat.press(down);
    }

    @Override
    public void screenOpened(ScreenContext screen) {
        pointer.reset();
        windows.cancelGestures();
    }

    @Override
    public void hotkeyPressed(Hotkey hotkey) {
        switch (hotkey) {
            case MEDIA_CONTROLS -> {
                if (host.currentScreen().orElse(null) instanceof MediaControlsScreen) {
                    host.closeScreen();
                } else {
                    openMediaControls();
                }
            }
        }
    }

    @Override
    public List<ChatLink> chatDisplayed(List<String> runs, @Nullable String sender) {
        return core.chat().rewrite(runs, sender);
    }

    @Override
    public void worldLeft() {
        windows.closeAll();
        core.onWorldLeft();
    }

    /** Whether windows are drawn live over {@code screen}, rather than on the HUD under it. */
    private boolean windowsLive(ScreenContext screen) {
        return switch (screen.kind()) {
            case CHAT -> true;
            case HOSTED -> host.currentScreen().map(UiScreen::showsWindows).orElse(false);
            case NONE, PAUSE, OTHER -> false;
        };
    }
}
