package com.lia.mediaplayer.api.settings;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * A list of words — host names, player names — edited as one comma-separated line. Entries
 * are trimmed; empty ones and repeats are dropped.
 */
public record StringListSetting(SettingInfo info, List<String> defaultValue) implements SettingSpec<List<String>> {

    public StringListSetting {
        defaultValue = clean(defaultValue);
    }

    @Override
    public List<String> normalize(List<String> value) {
        return clean(value);
    }

    /** {@code line} split at its commas. */
    public static List<String> parse(String line) {
        return clean(List.of(line.split(",", -1)));
    }

    /** The list as the one line the settings screen edits. */
    public static String join(List<String> entries) {
        return String.join(", ", entries);
    }

    private static List<String> clean(List<String> entries) {
        var kept = new LinkedHashSet<String>();
        for (String entry : entries) {
            String trimmed = entry.strip();
            if (!trimmed.isEmpty()) {
                kept.add(trimmed);
            }
        }
        return List.copyOf(kept);
    }
}
