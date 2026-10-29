package com.lia.mediaplayer.api.settings;

import java.util.Optional;

/**
 * A whole number between two bounds, edited with a slider.
 *
 * @param format a translation key with one {@code %s} for the number ("%s MB"), or empty to
 *     show the number alone
 */
public record IntSetting(SettingInfo info, Integer defaultValue, int min, int max, Optional<String> format)
        implements SettingSpec<Integer> {

    public IntSetting {
        if (min > max || defaultValue < min || defaultValue > max) {
            throw new IllegalArgumentException(
                    info.id() + ": default " + defaultValue + " is outside " + min + ".." + max);
        }
    }

    public static IntSetting of(SettingInfo info, int defaultValue, int min, int max) {
        return new IntSetting(info, defaultValue, min, max, Optional.empty());
    }

    public IntSetting withFormat(String translationKey) {
        return new IntSetting(info, defaultValue, min, max, Optional.of(translationKey));
    }

    @Override
    public Integer normalize(Integer value) {
        return Math.max(min, Math.min(max, value));
    }
}
