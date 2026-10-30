package com.lia.mediaplayer.api;

import com.lia.mediaplayer.api.event.EventSubscriptions;
import com.lia.mediaplayer.api.policy.Interceptor;
import com.lia.mediaplayer.api.settings.SettingSpec;
import com.lia.mediaplayer.api.source.LinkSource;
import com.lia.mediaplayer.api.source.Resolver;
import com.lia.mediaplayer.api.ui.KeyBindingSpec;
import com.lia.mediaplayer.api.ui.ShortcutSpec;
import com.lia.mediaplayer.api.ui.ThemeSpec;
import com.lia.mediaplayer.api.ui.WindowAction;
import java.util.function.Consumer;

/**
 * What {@link MediaPlayerAddon#register} is handed: one registry per kind of extension, all
 * namespaced by the addon's id. The mod implements it; addons only call it.
 *
 * <p>New registries are added in minor versions; {@link MediaPlayer#supports} says which ones
 * the installed mod has.
 *
 * @since 4.0.0
 */
public interface AddonContext {

    /**
     * The addon's id: the mod id on Fabric, the id given to {@link
     * LiasMediaPlayerApi#registerAddon} on NeoForge. The owner of every id it registers.
     */
    String addonId();

    /** Link sources, asked before the built-in ones of the same priority. */
    Registry<LinkSource> sources();

    /** Resolvers, asked before every built-in one, in the order registered. */
    Registry<Resolver> resolvers();

    /** Palettes offered in the theme setting. */
    Registry<ThemeSpec> themes();

    /** Buttons in the corner row of the mod's windows. */
    Registry<WindowAction> windowActions();

    /** Fixed Ctrl+key shortcuts over the chat. */
    Registry<ShortcutSpec> shortcuts();

    /** Keys the player binds in the controls screen. */
    Registry<KeyBindingSpec> keyBindings();

    /** Settings, shown in the mod's settings screen and stored in its file under their ids. */
    Registry<SettingSpec<?>> settings();

    /** A say over which links become labels and which may play. */
    Registry<Interceptor> interceptors();

    /** Playback events. Subscriptions made here last for the game session. */
    EventSubscriptions events();

    /** Called once on the client thread when the mod is up and every addon is registered. */
    void onReady(Consumer<MediaPlayer> callback);
}
