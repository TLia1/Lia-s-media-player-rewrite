package com.lia.mediaplayer.api.settings;

import com.lia.mediaplayer.api.text.Text;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What every setting has, whatever its type.
 *
 * @param id namespaced, {@code "owner:name"}: {@code "liasmediaplayer:video.max_height"}
 * @param group namespaced too; the settings screen lists one group per entry on its left
 * @param label the setting's name on its row
 * @param description the longer explanation shown when the row is hovered
 * @param warning a caution shown in red under the description, for a setting that can hurt
 *     performance
 */
public record SettingInfo(String id, String group, Text label, Optional<Text> description, Optional<Text> warning) {

    private static final Pattern NAMESPACED = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public SettingInfo {
        if (!NAMESPACED.matcher(id).matches()) {
            throw new IllegalArgumentException("Setting id must be namespaced, like owner:name — was " + id);
        }
        if (!NAMESPACED.matcher(group).matches()) {
            throw new IllegalArgumentException("Setting group must be namespaced, like owner:name — was " + group);
        }
    }

    /** A setting with a label and nothing else. */
    public static SettingInfo of(String id, String group, Text label) {
        return new SettingInfo(id, group, label, Optional.empty(), Optional.empty());
    }

    public SettingInfo withDescription(Text newDescription) {
        return new SettingInfo(id, group, label, Optional.of(newDescription), warning);
    }

    public SettingInfo withWarning(Text newWarning) {
        return new SettingInfo(id, group, label, description, Optional.of(newWarning));
    }
}
