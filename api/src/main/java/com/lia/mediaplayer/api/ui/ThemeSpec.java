package com.lia.mediaplayer.api.ui;

import com.lia.mediaplayer.api.Ids;
import com.lia.mediaplayer.api.text.Text;
import java.util.EnumMap;
import java.util.Map;

/**
 * A palette an addon offers in the theme setting: the roles it changes, over the mod's dark
 * theme. Partial by design, so a role added in a later version still has a colour.
 *
 * <pre>{@code
 * ThemeSpec.of("myaddon:sunset", Text.translatable("theme.myaddon.sunset"))
 *         .with(ThemeRole.WINDOW_BG, 0xD0221018)
 *         .with(ThemeRole.FILL, 0xFFFF8A50)
 * }</pre>
 *
 * @param id what the player's settings store; keep it stable
 * @param colors {@code 0xAARRGGBB}; alpha matters, the window and panel backgrounds are
 *     translucent in the dark theme
 * @since 4.0.0
 */
public record ThemeSpec(String id, Text name, Map<ThemeRole, Integer> colors) {

    public ThemeSpec {
        Ids.require(id, "Theme id");
        colors = Map.copyOf(colors);
    }

    /** A theme that changes nothing yet. */
    public static ThemeSpec of(String id, Text name) {
        return new ThemeSpec(id, name, Map.of());
    }

    /** This theme with {@code role} drawn in {@code argb}. */
    public ThemeSpec with(ThemeRole role, int argb) {
        var changed = new EnumMap<ThemeRole, Integer>(ThemeRole.class);
        changed.putAll(colors);
        changed.put(role, argb);
        return new ThemeSpec(id, name, changed);
    }
}
