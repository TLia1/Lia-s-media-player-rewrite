package com.lia.mediaplayer.api.settings;

import com.lia.mediaplayer.api.event.Subscription;
import java.util.function.Consumer;

/**
 * The current values of settings an addon registered. Reads are safe from any thread; {@link
 * #set} belongs to the client thread, where listeners run.
 *
 * @since 4.0.0
 */
public interface SettingValues {

    /**
     * The current value of {@code spec}.
     *
     * @throws IllegalArgumentException if it was never registered
     */
    <T> T get(SettingSpec<T> spec);

    /** Changes a setting as the settings screen would: normalized, saved, and told to its listeners. */
    <T> void set(SettingSpec<T> spec, T value);

    /** Calls {@code listener} with each new value of {@code spec}. */
    <T> Subscription onChange(SettingSpec<T> spec, Consumer<? super T> listener);
}
