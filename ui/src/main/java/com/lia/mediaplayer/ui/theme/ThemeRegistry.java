package com.lia.mediaplayer.ui.theme;

import com.lia.mediaplayer.api.settings.ChoiceSetting;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The themes that can be chosen: the built-in ones, then those addons register. A theme id
 * that names nothing — an addon removed since — falls back to {@link Themes#DARK}, and the
 * setting keeps the id in case the addon comes back. Client thread only.
 */
public final class ThemeRegistry {

    private final Map<String, Theme> themes = new LinkedHashMap<>();

    public ThemeRegistry() {
        Themes.all().forEach(this::register);
    }

    /**
     * Adds a theme that can be chosen.
     *
     * @throws IllegalArgumentException if a theme of that id is already registered
     */
    public void register(Theme theme) {
        if (themes.putIfAbsent(theme.id(), theme) != null) {
            throw new IllegalArgumentException("Theme already registered: " + theme.id());
        }
    }

    /** The theme of {@code id}, or the dark one. */
    public Theme byId(String id) {
        return themes.getOrDefault(id, Themes.DARK);
    }

    public List<Theme> all() {
        return List.copyOf(themes.values());
    }

    /** The themes as the choices of the theme setting. */
    public List<ChoiceSetting.Choice> choices() {
        var choices = new ArrayList<ChoiceSetting.Choice>();
        for (Theme theme : themes.values()) {
            choices.add(new ChoiceSetting.Choice(theme.id(), theme.name()));
        }
        return choices;
    }
}
