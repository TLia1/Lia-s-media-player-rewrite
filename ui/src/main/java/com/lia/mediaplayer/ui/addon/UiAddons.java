package com.lia.mediaplayer.ui.addon;

import com.lia.mediaplayer.api.AddonContext;
import com.lia.mediaplayer.api.LiasMediaPlayerApi.PendingAddon;
import com.lia.mediaplayer.api.MediaPlayer;
import com.lia.mediaplayer.api.MediaPlayerAddon;
import com.lia.mediaplayer.api.Registry;
import com.lia.mediaplayer.api.event.EventSubscriptions;
import com.lia.mediaplayer.api.media.MediaHandle;
import com.lia.mediaplayer.api.policy.Interceptor;
import com.lia.mediaplayer.api.settings.SettingSpec;
import com.lia.mediaplayer.api.source.LinkSource;
import com.lia.mediaplayer.api.source.Resolver;
import com.lia.mediaplayer.api.ui.KeyBindingSpec;
import com.lia.mediaplayer.api.ui.ShortcutSpec;
import com.lia.mediaplayer.api.ui.ThemeSpec;
import com.lia.mediaplayer.api.ui.WindowAction;
import com.lia.mediaplayer.core.MediaPlayerCore;
import com.lia.mediaplayer.core.ModInfo;
import com.lia.mediaplayer.core.addon.AddonGuard;
import com.lia.mediaplayer.ui.input.Key;
import com.lia.mediaplayer.ui.input.Mods;
import com.lia.mediaplayer.ui.theme.ThemeRegistry;
import com.lia.mediaplayer.ui.theme.Themes;
import com.lia.mediaplayer.ui.windows.AddonActions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The addons: registers each one once at startup through an {@link AddonContext} of its own,
 * keeps what the UI asks for later — window buttons, shortcuts, key bindings — and tells the
 * addons when the mod is up. Engine registrations (sources, resolvers, settings, interceptors)
 * go straight to the engine. Every call into an addon goes through the engine's {@link
 * AddonGuard}. Client thread only (at startup, the thread the loader starts mods on).
 */
public final class UiAddons {

    private static final Logger LOGGER = LoggerFactory.getLogger(UiAddons.class);

    /** Ids no addon can take: they would pass the addon's things off as the mod's or the game's. */
    private static final Set<String> RESERVED = Set.of(ModInfo.MOD_ID, ModInfo.MOD_ID + "api", "minecraft");

    private final MediaPlayerCore core;
    private final ThemeRegistry themes;
    private final Function<String, MediaPlayer> players;
    private final AddonGuard guard;
    private final List<WindowAction> windowActions = new CopyOnWriteArrayList<>();
    private final List<ShortcutSpec> shortcuts = new ArrayList<>();
    private final Map<String, KeyBindingSpec> keyBindings = new LinkedHashMap<>();
    private final Map<String, Set<String>> taken = new HashMap<>();
    private final List<String> loaded = new ArrayList<>();
    private boolean done;

    /** @param players the running mod as the addon of a given id sees it */
    public UiAddons(MediaPlayerCore core, ThemeRegistry themes, Function<String, MediaPlayer> players) {
        this.core = core;
        this.themes = themes;
        this.players = players;
        this.guard = core.addonGuard();
    }

    /**
     * Registers {@code addons}, in order, then calls their ready callbacks on the next client
     * task. Only the first call registers anything: the moment has passed after it.
     */
    public void load(List<PendingAddon> addons) {
        // No client-thread check: Fabric starts mods on that thread before the game knows it is
        // its thread.
        if (done) {
            if (!addons.isEmpty()) {
                LOGGER.warn("Addons handed over after startup are ignored: {}", addons);
            }
            return;
        }
        done = true;
        var ready = new ArrayList<Ready>();
        for (PendingAddon pending : addons) {
            if (RESERVED.contains(pending.addonId())) {
                LOGGER.error("Addon id {} is reserved; addon {} not loaded", pending.addonId(), pending.addon());
                continue;
            }
            var context = new Context(pending.addonId(), ready);
            MediaPlayerAddon addon = pending.addon();
            if (guard.run(pending.addonId() + ":register", () -> addon.register(context))) {
                loaded.add(pending.addonId());
            }
            context.close();
            LOGGER.info("Addon {}: {}", pending.addonId(), context.summary());
        }
        if (ready.isEmpty()) {
            return;
        }
        core.platform().clientThread().execute(() -> {
            for (Ready callback : ready) {
                MediaPlayer player = players.apply(callback.addonId());
                var unused = guard.run(
                        callback.addonId() + ":ready", () -> callback.callback().accept(player));
            }
        });
    }

    /** The addons that registered without failing, in order. */
    public List<String> loaded() {
        return List.copyOf(loaded);
    }

    /** The addons' window buttons, for the windows that open from now on. */
    public AddonActions windowActions() {
        return new AddonActions(() -> List.copyOf(windowActions), guard);
    }

    /** The key bindings to hand to the game, in the order registered. */
    public List<KeyBindingSpec> keyBindings() {
        return List.copyOf(keyBindings.values());
    }

    /** An addon's key binding was pressed; {@code player} is what the mod's transport keys drive. */
    public void keyBindingPressed(String id, Optional<MediaHandle> player) {
        KeyBindingSpec spec = keyBindings.get(id);
        if (spec != null) {
            var unused = guard.run(id, () -> spec.onPress().accept(player));
        }
    }

    /**
     * Offers a key the mod's own shortcuts declined to the addons' shortcuts.
     *
     * @return whether one took it
     */
    public boolean shortcut(Key key, Mods mods, Optional<MediaHandle> player) {
        if (shortcuts.isEmpty() || !mods.primary() || mods.alt()) {
            return false;
        }
        for (ShortcutSpec spec : shortcuts) {
            if (spec.shift() == mods.shift() && keyOf(spec.key()) == key) {
                return guard.call(spec.id(), () -> spec.action().test(player), false);
            }
        }
        return false;
    }

    private static Key keyOf(char key) {
        return key >= '0' && key <= '9' ? Key.digit(key - '0') : Key.letter(key);
    }

    private record Ready(String addonId, Consumer<MediaPlayer> callback) {}

    /** What one addon's {@code register} is handed. */
    private final class Context implements AddonContext {

        private final String addonId;
        private final List<Ready> ready;
        private final Map<String, Integer> counts = new HashMap<>();
        private final EventSubscriptions events;
        private boolean open = true;

        Context(String addonId, List<Ready> ready) {
            this.addonId = addonId;
            this.ready = ready;
            this.events = core.mediaEvents().subscriptions(guard, addonId + ":events");
        }

        void close() {
            open = false;
        }

        String summary() {
            return counts.isEmpty() ? "nothing registered" : counts.toString();
        }

        private <T> Registry<T> registry(String what, Function<? super T, String> idOf, Consumer<? super T> sink) {
            Set<String> ids = taken.computeIfAbsent(what, unused -> new HashSet<>());
            return new AddonRegistry<T>(addonId, what, idOf, ids, () -> open, entry -> {
                sink.accept(entry);
                counts.merge(what.toLowerCase(Locale.ROOT) + "s", 1, Integer::sum);
            });
        }

        @Override
        public String addonId() {
            return addonId;
        }

        @Override
        public Registry<LinkSource> sources() {
            return registry("Source", LinkSource::id, source -> core.sources().addAddonSource(source, guard));
        }

        @Override
        public Registry<Resolver> resolvers() {
            return registry(
                    "Resolver", Resolver::id, resolver -> core.resolver().addAddonResolver(resolver, guard));
        }

        @Override
        public Registry<ThemeSpec> themes() {
            return registry("Theme", ThemeSpec::id, spec -> themes.register(Themes.from(spec)));
        }

        @Override
        public Registry<WindowAction> windowActions() {
            return registry("Window action", WindowAction::id, windowActions::add);
        }

        @Override
        public Registry<ShortcutSpec> shortcuts() {
            return registry("Shortcut", ShortcutSpec::id, shortcuts::add);
        }

        @Override
        public Registry<KeyBindingSpec> keyBindings() {
            return registry("Key binding", KeyBindingSpec::id, spec -> {
                // The name key is also the mapping's id in the game's options file.
                boolean nameTaken = keyBindings.values().stream()
                        .anyMatch(other -> other.nameKey().equals(spec.nameKey()));
                if (nameTaken || spec.nameKey().startsWith("key." + ModInfo.MOD_ID + ".")) {
                    throw new IllegalArgumentException("Key binding name already used: " + spec.nameKey());
                }
                keyBindings.put(spec.id(), spec);
            });
        }

        @Override
        public Registry<SettingSpec<?>> settings() {
            return registry("Setting", SettingSpec::id, spec -> core.settings().register(spec));
        }

        @Override
        public Registry<Interceptor> interceptors() {
            return registry(
                    "Interceptor",
                    Interceptor::id,
                    interceptor -> core.interceptors().add(interceptor, guard));
        }

        @Override
        public EventSubscriptions events() {
            return events;
        }

        @Override
        public void onReady(Consumer<MediaPlayer> callback) {
            if (!open) {
                throw new IllegalStateException("onReady is called from MediaPlayerAddon.register (" + addonId + ")");
            }
            ready.add(new Ready(addonId, callback));
        }
    }
}
