package com.lia.mediaplayer.api.settings;

import com.lia.mediaplayer.api.text.Text;
import java.util.List;
import java.util.function.Supplier;

/**
 * One of a list of ids that can grow while the game runs — a theme, which an addon may
 * register after the setting was read. An id that names nothing (an addon removed since) is
 * kept as it is, so it comes back if the addon does; whoever reads the setting falls back on
 * its own.
 *
 * @param choices the ids offered now, with their names, in the order the button cycles them
 */
public record ChoiceSetting(SettingInfo info, String defaultValue, Supplier<List<Choice>> choices)
        implements SettingSpec<String> {

    /** One offered value. */
    public record Choice(String id, Text name) {}

    @Override
    public String normalize(String value) {
        String trimmed = value.strip();
        return trimmed.isEmpty() ? defaultValue : trimmed;
    }
}
