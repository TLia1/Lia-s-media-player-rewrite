package com.lia.mediaplayer.core.addon;

import com.lia.mediaplayer.api.event.Subscription;
import com.lia.mediaplayer.api.settings.SettingSpec;
import com.lia.mediaplayer.api.settings.SettingValues;
import com.lia.mediaplayer.core.lifecycle.ClientThread;
import com.lia.mediaplayer.core.settings.SettingsRegistry;
import java.util.function.Consumer;

/** The API's {@link SettingValues} over the settings registry. */
public final class SettingValuesView implements SettingValues {

    private final SettingsRegistry settings;
    private final ClientThread clientThread;

    public SettingValuesView(SettingsRegistry settings, ClientThread clientThread) {
        this.settings = settings;
        this.clientThread = clientThread;
    }

    @Override
    public <T> T get(SettingSpec<T> spec) {
        return settings.get(spec);
    }

    @Override
    public <T> void set(SettingSpec<T> spec, T value) {
        if (!clientThread.isCurrent()) {
            throw new WrongThreadException("Settings are set on the client thread, not "
                    + Thread.currentThread().getName());
        }
        settings.set(spec, value);
    }

    @Override
    public <T> Subscription onChange(SettingSpec<T> spec, Consumer<? super T> listener) {
        var subscription = settings.onChange(spec, listener);
        return subscription::close;
    }
}
