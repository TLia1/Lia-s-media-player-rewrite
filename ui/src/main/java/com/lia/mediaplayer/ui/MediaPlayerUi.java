package com.lia.mediaplayer.ui;

import com.lia.mediaplayer.api.LiasMediaPlayerApi.PendingAddon;
import com.lia.mediaplayer.api.media.MediaHandle;
import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.media.MediaRequest;
import com.lia.mediaplayer.api.media.RepeatMode;
import com.lia.mediaplayer.api.policy.PlayOrigin;
import com.lia.mediaplayer.api.settings.ChoiceSetting;
import com.lia.mediaplayer.api.settings.SettingSpec;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.MediaPlayerCore;
import com.lia.mediaplayer.core.library.Titles;
import com.lia.mediaplayer.core.link.ChatLink;
import com.lia.mediaplayer.core.playback.PlayerSnapshot;
import com.lia.mediaplayer.core.playback.PlayerState;
import com.lia.mediaplayer.core.storage.WindowState;
import com.lia.mediaplayer.core.text.Keys;
import com.lia.mediaplayer.ui.addon.UiAddons;
import com.lia.mediaplayer.ui.addon.UiMediaPlayer;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.command.ShowCommand;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.input.Mods;
import com.lia.mediaplayer.ui.input.PointerTracker;
import com.lia.mediaplayer.ui.overlay.ChatChips;
import com.lia.mediaplayer.ui.overlay.ChatOverlay;
import com.lia.mediaplayer.ui.overlay.NowPlayingBanner;
import com.lia.mediaplayer.ui.screen.HistoryScreen;
import com.lia.mediaplayer.ui.screen.LibraryActions;
import com.lia.mediaplayer.ui.screen.MediaControlsScreen;
import com.lia.mediaplayer.ui.screen.PlaylistScreen;
import com.lia.mediaplayer.ui.screen.ScreenContext;
import com.lia.mediaplayer.ui.screen.UiScreen;
import com.lia.mediaplayer.ui.settings.SettingsScreen;
import com.lia.mediaplayer.ui.settings.UiSettings;
import com.lia.mediaplayer.ui.shortcut.ShortcutTable;
import com.lia.mediaplayer.ui.theme.Theme;
import com.lia.mediaplayer.ui.theme.ThemeRegistry;
import com.lia.mediaplayer.ui.theme.Themes;
import com.lia.mediaplayer.ui.widget.RenderContext;
import com.lia.mediaplayer.ui.window.Window;
import com.lia.mediaplayer.ui.window.WindowManager;
import com.lia.mediaplayer.ui.window.WindowMemory;
import com.lia.mediaplayer.ui.windows.AudioBar;
import com.lia.mediaplayer.ui.windows.CorePlaybackControl;
import com.lia.mediaplayer.ui.windows.ImageWindow;
import com.lia.mediaplayer.ui.windows.PlaybackControl;
import com.lia.mediaplayer.ui.windows.PlayerWindow;
import com.lia.mediaplayer.ui.windows.VideoWindow;
import com.lia.mediaplayer.ui.windows.WindowServices;
import java.util.ArrayList;
import java.util.IdentityHashMap;
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
 * {@link ChatOverlay} also previews picture labels and plays the labels clicked, the chips in
 * the corner open the playlists or bring hidden windows back, and the {@link ShortcutTable}
 * turns keys into transport.
 *
 * <p>It also holds the {@linkplain UiAddons addons}, which the platform hands over once at
 * startup: their themes, buttons, shortcuts and keys join the mod's own.
 */
public final class MediaPlayerUi implements ClientEvents {

    /** Where the pause menu's settings button goes, from the top-right corner. */
    static final int SETTINGS_BUTTON_WIDTH = 112;

    private final MediaPlayerCore core;
    private final UiHost host;
    private final LongSupplier nanoTime;
    private final ThemeRegistry themes = new ThemeRegistry();
    private final ChoiceSetting themeSetting;
    private final UiAddons addons;
    private final WindowServices services;
    private final WindowManager windows;
    private final PointerTracker pointer = new PointerTracker();
    private final ShowCommand show;
    private final ChatOverlay chat;
    private final ChatChips chips;
    private final NowPlayingBanner banner = new NowPlayingBanner();
    private final IdentityHashMap<Window, String> announced = new IdentityHashMap<>();
    private final LibraryActions libraryActions = new Actions();
    private Theme theme = Themes.DARK;
    private long ticks;

    public MediaPlayerUi(MediaPlayerCore core, UiHost host) {
        this(core, host, System::nanoTime);
    }

    public MediaPlayerUi(MediaPlayerCore core, UiHost host, LongSupplier nanoTime) {
        this.core = core;
        this.host = host;
        this.nanoTime = nanoTime;
        this.themeSetting = UiSettings.theme(themes);
        this.addons = new UiAddons(core, themes, addonId -> new UiMediaPlayer(core, this::playFor, addonId));
        this.services = WindowServices.of(core, addons.windowActions());
        for (SettingSpec<?> spec : UiSettings.all(themeSetting)) {
            if (core.settings().spec(spec.id()).isEmpty()) {
                core.settings().register(spec);
            }
        }
        this.windows = new WindowManager(new Memory(), () -> core.settings().get(UiSettings.DEFAULT_POSITION));
        this.show = new ShowCommand(
                core.sources(), (ref, audioOnly, newWindow) -> play(ref, audioOnly, newWindow, PlayOrigin.COMMAND));
        this.chat = new ChatOverlay(
                host,
                core.chat(),
                core.sources(),
                core.images(),
                (ref, audioOnly, newWindow) -> play(ref, audioOnly, newWindow, PlayOrigin.CHAT_CLICK));
        this.chips = new ChatChips(
                () -> host.openScreenOver(playlistScreen(null)), windows::toggleHidden, windows::hiddenCount);
        var unusedTheme = core.settings().apply(themeSetting, id -> theme = themes.byId(id));
        var unusedPreview = core.settings().apply(UiSettings.HOVER_PREVIEW, chat::setPreview);
    }

    public WindowManager windows() {
        return windows;
    }

    public ShowCommand showCommand() {
        return show;
    }

    public Theme theme() {
        return theme;
    }

    /** The themes that can be chosen; addons add theirs here. */
    public ThemeRegistry themes() {
        return themes;
    }

    public UiAddons addons() {
        return addons;
    }

    /**
     * Registers the addons, once, at startup: before the platform hands its key mappings to the
     * game, since the addons' key bindings join them. A theme chosen from an addon is applied now
     * that it exists.
     */
    public void loadAddons(List<PendingAddon> pending) {
        addons.load(pending);
        theme = themes.byId(core.settings().get(themeSetting));
    }

    /** Client ticks seen since startup. */
    public long ticks() {
        return ticks;
    }

    // ---- Playing ------------------------------------------------------------

    /**
     * Plays {@code ref}: in the front window of its kind, queued after what plays there, or
     * in a new window when there is none, when {@code newWindow} asks for one and the window
     * cap allows it. A picture is pinned in a window of its own, once; a sound, or a list of
     * sounds, always plays in an audio bar. The addons' interceptors may refuse it first.
     *
     * @return the playback it plays or waits in; empty for a picture, or when refused
     */
    public Optional<PlaybackControl> play(MediaRef ref, boolean audioOnly, boolean newWindow, PlayOrigin origin) {
        core.platform().clientThread().checkCurrent();
        if (!core.interceptors().allowPlay(ref, origin)) {
            return Optional.empty();
        }
        if (ref.kind() == MediaKind.IMAGE) {
            pin(ref);
            return Optional.empty();
        }
        boolean sound = audioOnly || core.sources().playsAs(ref) == MediaKind.AUDIO;
        Class<? extends Window> type = sound ? AudioBar.class : VideoWindow.class;
        Optional<? extends Window> front = windows.frontMost(type);
        boolean atCap =
                count(type) >= core.settings().get(sound ? UiSettings.MAX_AUDIO_WINDOWS : UiSettings.MAX_VIDEO_WINDOWS);
        if (front.isPresent() && (!newWindow || atCap)) {
            Window window = front.get();
            PlaybackControl control = ((PlayerWindow) window).control();
            control.enqueue(ref);
            window.setHidden(false);
            window.raise();
            return Optional.of(control);
        }
        return Optional.of(openPlayer(ref, sound).control());
    }

    /** An addon's {@link com.lia.mediaplayer.api.MediaPlayer#play}. */
    private Optional<MediaHandle> playFor(MediaRef ref, MediaRequest request) {
        return play(ref, request.audioOnly(), request.newWindow(), PlayOrigin.API)
                .flatMap(PlaybackControl::handle);
    }

    private PlayerWindow openPlayer(MediaRef ref, boolean sound) {
        // The first track already starts at the volume the last window of its kind was left at.
        WindowState remembered = core.windowStates().get(sound ? AudioBar.KIND : VideoWindow.KIND);
        var playback = core.playbacks().open(ref, core.playerOptions(sound).withVolume(remembered.volume()));
        var control = new CorePlaybackControl(
                playback, core.playbacks(), Optional.of(core.handles().of(playback)));
        Window window = sound ? new AudioBar(control, services) : new VideoWindow(control, services);
        control.setRepeat(remembered.repeat());
        control.setShuffle(remembered.shuffle());
        ((PlayerWindow) window).setQueueOpen(remembered.queueOpen());
        windows.open(window);
        return (PlayerWindow) window;
    }

    /** Plays a list of links in a new audio bar: a playlist's "Play" and "Shuffle". */
    public void playAll(List<String> urls, boolean shuffle, boolean loop) {
        var refs = new ArrayList<MediaRef>();
        for (String url : urls) {
            core.sources()
                    .match(url)
                    .filter(ref -> ref.kind() != MediaKind.IMAGE)
                    .filter(ref -> core.interceptors().allowPlay(ref, PlayOrigin.PLAYLIST))
                    .ifPresent(refs::add);
        }
        if (refs.isEmpty()) {
            return;
        }
        List<AudioBar> bars = windowsOf(AudioBar.class);
        // Bottom-most first: the one looked at least recently gives way to the playlist.
        for (int i = 0; i <= bars.size() - core.settings().get(UiSettings.MAX_AUDIO_WINDOWS); i++) {
            bars.get(i).close();
        }
        PlaybackControl control = openPlayer(refs.getFirst(), true).control();
        refs.subList(1, refs.size()).forEach(control::enqueue);
        control.setShuffle(shuffle);
        control.setRepeat(loop ? RepeatMode.ALL : RepeatMode.OFF);
    }

    /** Plays a link as a click on it in chat would, if a source knows it. */
    public void playUrl(String url, boolean audioOnly, boolean newWindow, PlayOrigin origin) {
        core.sources().match(url.strip()).ifPresent(ref -> play(ref, audioOnly, newWindow, origin));
    }

    /** Shows a picture in a window, bringing forward the one that already shows it. */
    private void pin(MediaRef ref) {
        core.library().recordPlay(ref.url(), MediaKind.IMAGE);
        Optional<ImageWindow> shown = imageWindow(ref.url());
        if (shown.isPresent()) {
            shown.get().setHidden(false);
            shown.get().raise();
            return;
        }
        List<ImageWindow> pinned = windowsOf(ImageWindow.class);
        // Bottom-most first: the one looked at least recently gives way.
        for (int i = 0; i <= pinned.size() - core.settings().get(UiSettings.MAX_IMAGE_WINDOWS); i++) {
            pinned.get(i).close();
        }
        windows.open(new ImageWindow(ref, core.images(), services));
    }

    private Optional<ImageWindow> imageWindow(String url) {
        return windowsOf(ImageWindow.class).stream()
                .filter(image -> image.ref().url().equals(url))
                .findFirst();
    }

    private <W extends Window> List<W> windowsOf(Class<W> type) {
        return windows.windows().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }

    private int count(Class<? extends Window> type) {
        return (int) windows.windows().stream().filter(type::isInstance).count();
    }

    /** The front-most player that shows: what shortcuts drive. */
    private Optional<PlayerWindow> frontPlayer() {
        List<Window> all = windows.windows();
        for (int i = all.size() - 1; i >= 0; i--) {
            if (!all.get(i).isHidden() && all.get(i) instanceof PlayerWindow player) {
                return Optional.of(player);
            }
        }
        return Optional.empty();
    }

    /** The front-most player, shown or not, the audio bar before a video window: what hotkeys drive. */
    private Optional<PlayerWindow> hotkeyPlayer() {
        return windows.frontMost(AudioBar.class)
                .<PlayerWindow>map(bar -> bar)
                .or(() -> windows.frontMost(VideoWindow.class));
    }

    // ---- Screens ------------------------------------------------------------

    /** Opens the windows, live, without the chat. */
    public void openMediaControls() {
        host.openScreen(new MediaControlsScreen(this::theme, nanoTime, windows::isEmpty));
    }

    public void openPlaylists() {
        host.openScreen(playlistScreen(null));
    }

    public void openSettings() {
        host.openScreen(settingsScreen());
    }

    /** The settings, for a screen of the game that asks for the mod's (the mod list). */
    public UiScreen settingsScreen() {
        return new SettingsScreen(
                this::theme,
                nanoTime,
                core.settings(),
                core.platform().text(),
                core.platform().clipboard(),
                core.tools()::state,
                () -> {
                    var unused = core.tools().update();
                },
                host::closeScreen);
    }

    private PlaylistScreen playlistScreen(@Nullable String selected) {
        return new PlaylistScreen(this::theme, nanoTime, core, libraryActions, selected);
    }

    /** What the playlist and history screens ask for. */
    private final class Actions implements LibraryActions {
        @Override
        public void playAll(List<String> urls, boolean shuffle, boolean loop) {
            MediaPlayerUi.this.playAll(urls, shuffle, loop);
        }

        @Override
        public void play(String url) {
            playUrl(url, false, false, PlayOrigin.HISTORY);
        }

        @Override
        public void openHistory(@Nullable String playlistId) {
            host.openScreenOver(new HistoryScreen(
                    MediaPlayerUi.this::theme,
                    nanoTime,
                    core.library(),
                    core.titles(),
                    core.platform().clipboard(),
                    this,
                    playlistId));
        }

        @Override
        public void close() {
            host.closeScreen();
        }
    }

    /** Opens {@code screen}, or closes it if a screen of its type is already open: the hotkeys. */
    private void toggle(Class<? extends UiScreen> type, Runnable open) {
        if (type.isInstance(host.currentScreen().orElse(null))) {
            host.closeScreen();
        } else {
            open.run();
        }
    }

    // ---- ClientEvents ---------------------------------------------------------

    @Override
    public void tick() {
        core.platform().clientThread().checkCurrent();
        ticks++;
        core.images().tick();
        core.playbacks().setMasterVolume(core.platform().masterVolume());
        core.mediaEvents().tick();
        windows.tick();
        rememberModes();
        announceHiddenTracks();
    }

    /** Keeps the loop, shuffle, queue and volume of the front window of each kind for the next one. */
    private void rememberModes() {
        for (Class<? extends Window> type : List.of(VideoWindow.class, AudioBar.class)) {
            windows.frontMost(type).ifPresent(window -> {
                PlaybackControl control = ((PlayerWindow) window).control();
                WindowState state = core.windowStates().get(window.kind());
                core.windowStates()
                        .put(
                                window.kind(),
                                state.withModes(control.repeat(), control.shuffle())
                                        .withQueueOpen(((PlayerWindow) window).isQueueOpen())
                                        .withVolume(control.volume()));
            });
        }
    }

    /** Says what a hidden player started, once it plays. */
    private void announceHiddenTracks() {
        announced.keySet().removeIf(Window::isClosed);
        for (Window window : windows.windows()) {
            if (!(window instanceof PlayerWindow player)) {
                continue;
            }
            PlaybackControl control = player.control();
            Optional<String> url = control.currentUrl();
            PlayerSnapshot snapshot = control.snapshot();
            if (url.isEmpty()
                    || url.get().equals(announced.get(window))
                    || !(snapshot.state() instanceof PlayerState.Playing)) {
                continue;
            }
            announced.put(window, url.get());
            if (window.isHidden()) {
                banner.announce(
                        Text.literal(snapshot.title().orElse(Titles.shortForm(url.get()))), nanoTime.getAsLong());
            }
        }
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
        if (screen.kind() == ScreenContext.Kind.CHAT) {
            boolean overWindow = windows.covers(mouseX, mouseY);
            canvas.nextLayer();
            chips.render(canvas, overWindow ? ctx.withoutPointer() : ctx);
            if (!overWindow && !chips.covers(mouseX, mouseY)) {
                chat.render(canvas, ctx, url -> imageWindow(url).isPresent());
            }
        }
        banner.render(canvas, ctx);
    }

    @Override
    public void renderHud(ScreenContext screen, Canvas canvas) {
        if (windowsLive(screen)) {
            // The screen draws them, live, over itself.
            return;
        }
        var ctx = new RenderContext(theme, nanoTime.getAsLong(), Double.NaN, Double.NaN, false);
        windows.renderHud(canvas, ctx);
        banner.render(canvas, ctx);
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
        if (stamped instanceof InputEvent.KeyDown key) {
            Optional<ShortcutTable.Action> action =
                    ShortcutTable.actionFor(key.key(), key.mods(), !screen.chatFieldEmpty());
            if (action.isPresent()) {
                return shortcut(action.get());
            }
            return addons.shortcut(
                    key.key(),
                    key.mods(),
                    frontPlayer().flatMap(p -> p.control().handle()));
        }
        return screen.kind() == ScreenContext.Kind.CHAT
                && stamped instanceof InputEvent.PointerDown down
                && (chips.press(down) || chat.press(down));
    }

    /** Runs a shortcut on the front-most player, and says whether there was one to run it on. */
    private boolean shortcut(ShortcutTable.Action action) {
        if (action == ShortcutTable.Action.THEATER) {
            return windows.windows().reversed().stream()
                    .filter(w -> !w.isHidden() && w instanceof VideoWindow)
                    .findFirst()
                    .map(w -> ((VideoWindow) w).toggleTheater())
                    .orElse(false);
        }
        Optional<PlayerWindow> target = frontPlayer();
        target.ifPresent(player -> transport(player, action));
        return target.isPresent();
    }

    private static void transport(PlayerWindow player, ShortcutTable.Action action) {
        PlaybackControl control = player.control();
        switch (action) {
            case PLAY_PAUSE -> control.togglePause();
            case SEEK_BACK -> control.seekBy(ShortcutTable.SEEK.negated());
            case SEEK_FORWARD -> control.seekBy(ShortcutTable.SEEK);
            case SEEK_BACK_FAR -> control.seekBy(ShortcutTable.SEEK_FAR.negated());
            case SEEK_FORWARD_FAR -> control.seekBy(ShortcutTable.SEEK_FAR);
            case VOLUME_UP -> control.setVolume(control.volume() + ShortcutTable.VOLUME_STEP);
            case VOLUME_DOWN -> control.setVolume(control.volume() - ShortcutTable.VOLUME_STEP);
            case MUTE -> player.toggleMute();
            case LOOP -> control.cycleRepeat();
            case SHUFFLE -> control.toggleShuffle();
            case NEXT -> control.next();
            case PREVIOUS -> control.previous();
            case THEATER -> {
                // Handled before a player is looked for: it needs a picture, not a transport.
            }
        }
    }

    @Override
    public void screenOpened(ScreenContext screen, ButtonSink buttons) {
        pointer.reset();
        windows.cancelGestures();
        if (screen.kind() == ScreenContext.Kind.PAUSE) {
            buttons.add(
                    Text.translatable(Keys.SETTINGS_BUTTON),
                    buttons.screenWidth() - 10 - SETTINGS_BUTTON_WIDTH,
                    10,
                    SETTINGS_BUTTON_WIDTH,
                    20,
                    () -> host.openScreenOver(settingsScreen()));
        }
    }

    @Override
    public void hotkeyPressed(Hotkey hotkey, Mods mods) {
        switch (hotkey) {
            case MEDIA_CONTROLS -> toggle(MediaControlsScreen.class, this::openMediaControls);
            case PLAYLISTS -> toggle(PlaylistScreen.class, this::openPlaylists);
            case SETTINGS -> toggle(SettingsScreen.class, this::openSettings);
            case PLAY_CLIPBOARD ->
                playUrl(core.platform().clipboard().get(), mods.alt(), mods.shift(), PlayOrigin.KEYBIND);
            case TOGGLE_WINDOWS -> windows.toggleHidden();
            case CLOSE_ALL -> windows.closeAll();
            case PLAY_PAUSE -> hotkeyPlayer().ifPresent(p -> p.control().togglePause());
            case NEXT -> hotkeyPlayer().ifPresent(p -> p.control().next());
            case PREVIOUS -> hotkeyPlayer().ifPresent(p -> p.control().previous());
            case VOLUME_UP -> hotkeyPlayer().ifPresent(p -> transport(p, ShortcutTable.Action.VOLUME_UP));
            case VOLUME_DOWN -> hotkeyPlayer().ifPresent(p -> transport(p, ShortcutTable.Action.VOLUME_DOWN));
            case MUTE -> hotkeyPlayer().ifPresent(PlayerWindow::toggleMute);
        }
    }

    @Override
    public void keyBindingPressed(String id, Mods mods) {
        addons.keyBindingPressed(id, hotkeyPlayer().flatMap(p -> p.control().handle()));
    }

    @Override
    public List<ChatLink> chatDisplayed(List<String> runs, @Nullable String sender) {
        return core.chat().rewrite(runs, sender);
    }

    @Override
    public void worldLeft() {
        windows.closeAll();
        announced.clear();
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

    /** Where the windows of each kind were left, in {@code windows.json}. */
    private final class Memory implements WindowMemory {
        @Override
        public Optional<Rect> frame(String kind) {
            WindowState state = core.windowStates().get(kind);
            return state.placed()
                    ? Optional.of(new Rect(state.x(), state.y(), state.width(), state.height()))
                    : Optional.empty();
        }

        @Override
        public void remember(String kind, Rect frame) {
            // A player's height follows its picture or its controls; only a picture window's is kept.
            int height = kind.equals(ImageWindow.KIND) ? frame.height() : 0;
            WindowState state = core.windowStates().get(kind);
            core.windowStates().put(kind, state.withFrame(frame.x(), frame.y(), frame.width(), height));
        }
    }
}
