package com.lia.mediaplayer.api.settings;

import com.lia.mediaplayer.api.Ids;
import com.lia.mediaplayer.api.text.Text;
import java.util.Optional;

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

    public SettingInfo {
        Ids.require(id, "Setting id");
        Ids.require(group, "Setting group");
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
