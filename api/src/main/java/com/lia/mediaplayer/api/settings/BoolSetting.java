package com.lia.mediaplayer.api.settings;

/** On or off. */
public record BoolSetting(SettingInfo info, Boolean defaultValue) implements SettingSpec<Boolean> {

    @Override
    public Boolean normalize(Boolean value) {
        return value;
    }
}
