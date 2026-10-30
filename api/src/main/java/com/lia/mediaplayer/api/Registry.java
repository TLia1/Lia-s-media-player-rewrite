package com.lia.mediaplayer.api;

/**
 * Where an addon adds one kind of thing: sources, themes, buttons. Open only while {@link
 * MediaPlayerAddon#register} runs.
 *
 * <p>Every entry has a namespaced id whose owner is the addon's own id ({@link
 * AddonContext#addonId()}): {@code "myaddon:share"} for an addon {@code myaddon}.
 *
 * @param <T> what is registered
 * @since 4.0.0
 */
public interface Registry<T> {

    /**
     * Adds {@code entry}.
     *
     * @return {@code entry}, to keep it in a field in one line
     * @throws IllegalArgumentException if its id is not in the addon's namespace, or is taken
     * @throws IllegalStateException after {@link MediaPlayerAddon#register} has returned
     */
    T register(T entry);
}
