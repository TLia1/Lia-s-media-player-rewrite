package com.lia.mediaplayer.api.settings;

/** A line of text, at most {@code maxLength} characters. */
public record StringSetting(SettingInfo info, String defaultValue, int maxLength) implements SettingSpec<String> {

    public StringSetting {
        if (maxLength < 1 || defaultValue.length() > maxLength) {
            throw new IllegalArgumentException(info.id() + ": bad maximum length " + maxLength);
        }
    }

    @Override
    public String normalize(String value) {
        String oneLine = value.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() > maxLength ? oneLine.substring(0, maxLength) : oneLine;
    }
}
