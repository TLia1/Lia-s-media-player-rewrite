package com.lia.mediaplayer.ui.theme;

import com.lia.mediaplayer.api.text.Text;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * A palette: one colour for every {@link Role}. Immutable; a different look is a different
 * theme, handed to widgets while they draw, never a static field rewritten.
 *
 * <p>A theme is usually written as another one plus the roles it changes ({@link
 * #with(String, Text, Map)}), so a role added later still has a colour in every theme.
 */
public final class Theme {

    private final String id;
    private final Text name;
    // An EnumMap is an array indexed by the role underneath: a lookup per draw call is cheap.
    private final EnumMap<Role, Integer> colors;

    private Theme(String id, Text name, EnumMap<Role, Integer> colors) {
        this.id = id;
        this.name = name;
        this.colors = colors;
    }

    /**
     * A theme that sets every role.
     *
     * @throws IllegalArgumentException if a role has no colour
     */
    public static Theme of(String id, Text name, Map<Role, Integer> colors) {
        var values = new EnumMap<Role, Integer>(Role.class);
        for (Role role : Role.values()) {
            Integer color = colors.get(role);
            if (color == null) {
                throw new IllegalArgumentException("Theme " + id + " sets no colour for " + role);
            }
            values.put(role, color);
        }
        return new Theme(id, name, values);
    }

    /** This theme with {@code overrides} applied, under a new id and name. */
    public Theme with(String newId, Text newName, Map<Role, Integer> overrides) {
        var values = new EnumMap<>(colors);
        values.putAll(overrides);
        return new Theme(newId, newName, values);
    }

    public String id() {
        return id;
    }

    public Text name() {
        return name;
    }

    public int color(Role role) {
        return Objects.requireNonNull(colors.get(role));
    }

    /** Every role and its colour. */
    public Map<Role, Integer> colors() {
        return new EnumMap<>(colors);
    }

    @Override
    public String toString() {
        return "Theme[" + id + "]";
    }
}
