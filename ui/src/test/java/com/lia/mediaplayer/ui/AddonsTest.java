package com.lia.mediaplayer.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lia.mediaplayer.api.AddonContext;
import com.lia.mediaplayer.api.ApiVersion;
import com.lia.mediaplayer.api.Capability;
import com.lia.mediaplayer.api.LiasMediaPlayerApi.PendingAddon;
import com.lia.mediaplayer.api.MediaPlayer;
import com.lia.mediaplayer.api.MediaPlayerAddon;
import com.lia.mediaplayer.api.event.MediaEvent;
import com.lia.mediaplayer.api.media.MediaHandle;
import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.media.MediaRequest;
import com.lia.mediaplayer.api.policy.Interceptor;
import com.lia.mediaplayer.api.policy.PlayOrigin;
import com.lia.mediaplayer.api.settings.BoolSetting;
import com.lia.mediaplayer.api.settings.ChoiceSetting;
import com.lia.mediaplayer.api.settings.SettingInfo;
import com.lia.mediaplayer.api.source.LinkSource;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.api.ui.ActionIcon;
import com.lia.mediaplayer.api.ui.KeyBindingSpec;
import com.lia.mediaplayer.api.ui.MediaWindow;
import com.lia.mediaplayer.api.ui.ShortcutSpec;
import com.lia.mediaplayer.api.ui.ThemeRole;
import com.lia.mediaplayer.api.ui.ThemeSpec;
import com.lia.mediaplayer.api.ui.WindowAction;
import com.lia.mediaplayer.core.MediaPlayerCore;
import com.lia.mediaplayer.core.testing.FakePlatform;
import com.lia.mediaplayer.core.testing.TestCores;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.input.Key;
import com.lia.mediaplayer.ui.input.Mods;
import com.lia.mediaplayer.ui.screen.ScreenContext;
import com.lia.mediaplayer.ui.testing.FakeHost;
import com.lia.mediaplayer.ui.testing.RecordingCanvas;
import com.lia.mediaplayer.ui.theme.Role;
import com.lia.mediaplayer.ui.windows.VideoWindow;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** An addon registered through the API, seen from the UI: everything it adds, and the rules it is held to. */
class AddonsTest {

    /** Refused at once on this machine: nothing in these tests reaches the network. */
    private static final String CLIP = "http://127.0.0.1:1/a.mp4";

    private static final String OTHER_CLIP = "http://127.0.0.1:1/b.mp4";
    private static final String TUBE = "http://tube.example/watch/1";
    private static final String BLOCKED = "http://127.0.0.1:1/blocked.mp4";
    private static final ScreenContext CHAT = new ScreenContext(ScreenContext.Kind.CHAT, true);

    @TempDir
    Path gameDir;

    private FakePlatform platform;
    private MediaPlayerCore core;
    private MediaPlayerUi ui;
    private final RecordingCanvas canvas = new RecordingCanvas(640, 360);
    private final List<String> log = new ArrayList<>();
    private final AtomicReference<MediaPlayer> ready = new AtomicReference<>();
    private final List<Optional<MediaHandle>> windowHandles = new ArrayList<>();

    @BeforeEach
    void start() {
        platform = new FakePlatform(gameDir);
        core = new MediaPlayerCore(platform, TestCores.OFFLINE);
        ui = new MediaPlayerUi(core, new FakeHost(), () -> 0L);
    }

    @AfterEach
    void stop() {
        core.close();
    }

    static final BoolSetting ENABLED =
            new BoolSetting(SettingInfo.of("myaddon:enabled", "myaddon:general", Text.literal("Enabled")), true);

    /** Registers one of everything. */
    private final MediaPlayerAddon addon = context -> {
        context.sources().register(new TubeSource());
        context.themes()
                .register(ThemeSpec.of("myaddon:sunset", Text.literal("Sunset")).with(ThemeRole.FILL, 0xFFFF8A50));
        context.settings().register(ENABLED);
        context.windowActions().register(new LogAction());
        context.shortcuts().register(ShortcutSpec.ctrl("myaddon:j", 'J', player -> {
            log.add("ctrl+j " + player.isPresent());
            return true;
        }));
        // Ctrl+M is the mod's own mute: an addon cannot take it.
        context.shortcuts().register(ShortcutSpec.ctrl("myaddon:m", 'M', player -> {
            log.add("ctrl+m");
            return true;
        }));
        context.keyBindings()
                .register(new KeyBindingSpec(
                        "myaddon:key", "key.myaddon.key", player -> log.add("key " + player.isPresent())));
        context.interceptors().register(new Interceptor() {
            @Override
            public String id() {
                return "myaddon:no_blocked";
            }

            @Override
            public boolean allowChatLink(MediaRef ref, Optional<String> sender) {
                return !ref.url().contains("blocked");
            }

            @Override
            public boolean allowPlay(MediaRef ref, PlayOrigin origin) {
                log.add("asked " + origin);
                return !ref.url().contains("blocked");
            }
        });
        context.events().on(MediaEvent.TrackChanged.class, event -> log.add("track " + event.url()));
        context.onReady(ready::set);
    };

    private static final class TubeSource implements LinkSource {
        @Override
        public String id() {
            return "myaddon:tube";
        }

        @Override
        public Optional<MediaRef> match(URI uri, String raw) {
            return "tube.example".equals(uri.getHost())
                    ? Optional.of(new MediaRef(raw, MediaKind.VIDEO, id(), Text.literal("[tube]")))
                    : Optional.empty();
        }
    }

    private final class LogAction implements WindowAction {
        @Override
        public String id() {
            return "myaddon:log";
        }

        @Override
        public ActionIcon icon() {
            return ActionIcon.NOTE;
        }

        @Override
        public Text tooltip() {
            return Text.literal("Log");
        }

        @Override
        public boolean appliesTo(MediaWindow window) {
            windowHandles.add(window.handle());
            return true;
        }

        @Override
        public void onClick(MediaWindow window, boolean shift) {}
    }

    private void load(MediaPlayerAddon... addons) {
        var pending = new ArrayList<PendingAddon>();
        for (int i = 0; i < addons.length; i++) {
            pending.add(new PendingAddon(i == 0 ? "myaddon" : "other" + i, addons[i]));
        }
        ui.loadAddons(pending);
    }

    @Test
    void anAddonsThemeChosenBeforeItWasRegisteredAppliesOnceItIs() {
        var themeSetting = (ChoiceSetting)
                core.settings().spec("liasmediaplayer:appearance.theme").orElseThrow();
        core.settings().set(themeSetting, "myaddon:sunset");
        assertThat(ui.theme().id()).as("not registered yet").isEqualTo("liasmediaplayer:dark");

        load(addon);
        assertThat(ui.addons().loaded()).containsExactly("myaddon");
        assertThat(ui.theme().id()).isEqualTo("myaddon:sunset");
        assertThat(ui.theme().color(Role.FILL)).isEqualTo(0xFFFF8A50);
        assertThat(ui.themes().choices()).extracting(ChoiceSetting.Choice::id).contains("myaddon:sunset");
        assertThat(core.settings().spec("myaddon:enabled")).isPresent();
    }

    @Test
    void anAddonsSourceLabelsChatLinksAndItsInterceptorRefusesWhatItWill() {
        load(addon);
        assertThat(ui.chatDisplayed(List.of("look " + TUBE), "Steve"))
                .singleElement()
                .satisfies(link -> assertThat(link.label()).isEqualTo(Text.literal("[tube]")));
        assertThat(ui.chatDisplayed(List.of(BLOCKED), "Steve")).isEmpty();

        assertThat(ui.showCommand().run("video", BLOCKED)).isEmpty();
        assertThat(ui.windows().windows()).isEmpty();
        assertThat(ui.showCommand().run("video", CLIP)).isEmpty();
        assertThat(ui.windows().windows()).hasSize(1);
        assertThat(log).containsSubsequence("asked COMMAND", "asked COMMAND");
    }

    @Test
    void windowButtonsShortcutsAndKeysAreToldTheFrontPlayer() {
        load(addon);
        ui.showCommand().run("video", CLIP);
        ui.renderScreen(CHAT, canvas.clear(), -1, -1, Mods.NONE);
        MediaHandle handle = core.handles().all().getFirst();
        assertThat(windowHandles).isNotEmpty().allMatch(found -> found.equals(Optional.of(handle)));

        assertThat(ui.input(CHAT, new InputEvent.KeyDown(Key.J, Mods.ctrlOnly())))
                .isTrue();
        assertThat(ui.input(CHAT, new InputEvent.KeyDown(Key.J, Mods.NONE)))
                .as("a bare letter is the player's typing")
                .isFalse();
        assertThat(ui.input(CHAT, new InputEvent.KeyDown(Key.J, new Mods(false, true, true, false, false))))
                .isFalse();
        assertThat(ui.input(CHAT, new InputEvent.KeyDown(Key.M, Mods.ctrlOnly())))
                .isTrue();
        ui.keyBindingPressed("myaddon:key", Mods.NONE);
        ui.keyBindingPressed("myaddon:unknown", Mods.NONE);
        assertThat(log).contains("ctrl+j true", "key true").doesNotContain("ctrl+m");
        assertThat(ui.addons().keyBindings())
                .extracting(KeyBindingSpec::nameKey)
                .containsExactly("key.myaddon.key");
    }

    @Test
    void theRunningModIsHandedOverOnceUpAndPlaysAsAClickWould() throws InterruptedException {
        load(addon);
        assertThat(ready.get()).as("only on the next client task").isNull();
        platform.runClientTasks();
        MediaPlayer mod = ready.get();
        assertThat(mod.apiVersion()).isEqualTo(ApiVersion.CURRENT);
        assertThat(mod.supports(Capability.WINDOW_ACTIONS)).isTrue();
        assertThat(mod.supports(Capability.SURFACES)).isFalse();
        assertThat(mod.recognize(" " + TUBE)).isPresent();
        assertThat(mod.settings().get(ENABLED)).isTrue();
        assertThat(mod.library().playlists()).isEmpty();

        Optional<MediaHandle> first = mod.play(MediaRequest.of(CLIP));
        Optional<MediaHandle> queued = mod.play(MediaRequest.of(OTHER_CLIP));
        assertThat(first).isPresent().isEqualTo(queued);
        assertThat(mod.handles()).containsExactly(first.get());
        assertThat(mod.play(MediaRequest.of(OTHER_CLIP).withNewWindow(true)))
                .isPresent()
                .isNotEqualTo(first);
        assertThat(ui.windows().windows()).hasSize(2).allMatch(VideoWindow.class::isInstance);
        assertThat(mod.play(MediaRequest.of("http://127.0.0.1:1/cat.png")))
                .as("a picture is pinned")
                .isEmpty();
        assertThat(mod.play(MediaRequest.of(BLOCKED))).isEmpty();
        assertThat(mod.play(MediaRequest.of("http://127.0.0.1:1/page"))).isEmpty();
        assertThat(log).contains("asked API");

        platform.runClientTasks();
        assertThat(log).contains("track " + CLIP);
        var subscribed = new ArrayList<MediaEvent>();
        var subscription = mod.events().onAny(subscribed::add);
        first.get().close();
        platform.runClientTasks();
        subscription.close();
        assertThat(subscribed).contains(new MediaEvent.Closed(first.get()));

        var failure = new AtomicReference<Throwable>();
        var offThread = new Thread(() -> mod.play(MediaRequest.of(CLIP)));
        offThread.setUncaughtExceptionHandler((thread, error) -> failure.set(error));
        offThread.start();
        offThread.join();
        assertThat(failure.get()).isInstanceOf(WrongThreadException.class);
    }

    @Test
    void idsMustBeTheAddonsOwnAndTheRegistriesCloseWhenRegisterReturns() {
        var kept = new AtomicReference<AddonContext>();
        var errors = new ArrayList<String>();
        MediaPlayerAddon careful = context -> {
            kept.set(context);
            try {
                context.themes().register(ThemeSpec.of("liasmediaplayer:dark2", Text.literal("x")));
            } catch (IllegalArgumentException e) {
                errors.add("namespace");
            }
            context.themes().register(ThemeSpec.of("myaddon:one", Text.literal("One")));
            try {
                context.themes().register(ThemeSpec.of("myaddon:one", Text.literal("Again")));
            } catch (IllegalArgumentException e) {
                errors.add("duplicate");
            }
            try {
                context.keyBindings()
                        .register(new KeyBindingSpec("myaddon:k", "key.liasmediaplayer.playpause", player -> {}));
            } catch (IllegalArgumentException e) {
                errors.add("key name");
            }
        };
        MediaPlayerAddon broken = context -> {
            throw new IllegalStateException("bug in register");
        };
        MediaPlayerAddon after = context -> context.themes().register(ThemeSpec.of("other2:two", Text.literal("Two")));
        load(careful, broken, after);

        assertThat(errors).containsExactly("namespace", "duplicate", "key name");
        assertThat(ui.addons().loaded()).containsExactly("myaddon", "other2");
        assertThat(ui.themes().choices()).extracting(ChoiceSetting.Choice::id).contains("myaddon:one", "other2:two");
        assertThatThrownBy(() -> kept.get().themes().register(ThemeSpec.of("myaddon:late", Text.literal("x"))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> kept.get().onReady(mod -> {})).isInstanceOf(IllegalStateException.class);
        assertThat(kept.get().addonId()).isEqualTo("myaddon");

        // Startup is over: later addons are not registered.
        ui.loadAddons(List.of(new PendingAddon("late", addon)));
        assertThat(ui.addons().loaded()).containsExactly("myaddon", "other2");
    }

    @Test
    void theModsOwnIdsAreReserved() {
        ui.loadAddons(List.of(new PendingAddon("liasmediaplayer", addon), new PendingAddon("minecraft", addon)));
        assertThat(ui.addons().loaded()).isEmpty();
        assertThat(ui.chatDisplayed(List.of(TUBE), null)).isEmpty();
    }
}
