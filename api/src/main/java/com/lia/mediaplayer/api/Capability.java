package com.lia.mediaplayer.api;

/**
 * A feature the API names, which the installed mod may or may not have yet: ask {@link
 * MediaPlayer#supports} before relying on one. Constants are never removed, so a name an addon
 * compiled against always resolves; a feature that lands sets its constant on.
 *
 * @since 4.0.0
 */
public enum Capability {
    /** {@link AddonContext#sources()}. */
    LINK_SOURCES,
    /** {@link AddonContext#resolvers()}. */
    RESOLVERS,
    /** {@link AddonContext#themes()}. */
    THEMES,
    /** {@link AddonContext#windowActions()}. */
    WINDOW_ACTIONS,
    /** {@link AddonContext#shortcuts()}. */
    SHORTCUTS,
    /** {@link AddonContext#keyBindings()}. */
    KEY_BINDINGS,
    /** {@link AddonContext#settings()}. */
    SETTINGS,
    /** {@link AddonContext#interceptors()}. */
    INTERCEPTORS,
    /** {@link AddonContext#events()}. */
    PLAYBACK_EVENTS,
    /** {@link MediaPlayer#play} and {@link com.lia.mediaplayer.api.media.MediaHandle}. */
    HANDLES,
    /** {@link MediaPlayer#library()}, read only. */
    LIBRARY,
    /** Decoders for picture formats the mod does not read. Not yet. */
    IMAGE_DECODERS,
    /** Buttons into an addon's own screen from the playlist and history screens. Not yet. */
    SCREEN_ENTRIES,
    /** Media drawn into a texture the addon draws itself. Not yet. */
    SURFACES,
    /** Sound with no window, the mixer channels. Not yet. */
    HEADLESS_AUDIO,
    /** Sound placed in the world. Not yet. */
    POSITIONAL_AUDIO,
    /** Watch-together hooks. Not yet. */
    SYNC,
    /** Counters and the failure log. Not yet. */
    DIAGNOSTICS
}
