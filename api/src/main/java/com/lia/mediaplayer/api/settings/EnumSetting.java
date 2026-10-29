package com.lia.mediaplayer.api.settings;

import com.lia.mediaplayer.api.text.Text;
import java.util.List;
import java.util.function.Function;

/**
 * One constant of an enum, cycled through with a button. Stored by the constant's name, so
 * renaming a constant forgets what was chosen.
 *
 * @param names what each constant is called on the button
 */
public record EnumSetting<E extends Enum<E>>(SettingInfo info, E defaultValue, Function<E, Text> names)
        implements SettingSpec<E> {

    public Class<E> type() {
        return defaultValue.getDeclaringClass();
    }

    public List<E> values() {
        return List.of(type().getEnumConstants());
    }

    @Override
    public E normalize(E value) {
        return value;
    }
}
