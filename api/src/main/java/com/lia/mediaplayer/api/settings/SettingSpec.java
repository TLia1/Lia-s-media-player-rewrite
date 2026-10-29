package com.lia.mediaplayer.api.settings;

import com.lia.mediaplayer.api.text.Text;
import java.util.Optional;

/**
 * One setting: its description and the values it may take. A closed set of types, so the
 * settings screen knows how to edit each one and the file knows how to store it.
 *
 * <p>Specs are values. The current value of a setting lives in the mod's settings registry,
 * which hands every value through {@link #normalize} first: a value read from a hand-edited
 * file or set by an addon is clamped or replaced, never trusted as it is.
 *
 * @param <T> the value's type
 */
public sealed interface SettingSpec<T>
        permits BoolSetting, IntSetting, EnumSetting, ChoiceSetting, StringSetting, StringListSetting {

    SettingInfo info();

    T defaultValue();

    /** {@code value} made acceptable: clamped, trimmed, or the default when nothing can be made of it. */
    T normalize(T value);

    default String id() {
        return info().id();
    }

    default String group() {
        return info().group();
    }

    default Text label() {
        return info().label();
    }

    default Optional<Text> description() {
        return info().description();
    }

    default Optional<Text> warning() {
        return info().warning();
    }
}
