package com.lia.mediaplayer.ui;

import com.lia.mediaplayer.core.text.Keys;

/**
 * The key mappings the mod registers with the game, which lists them in its controls screen
 * where the player picks the keys. All start unbound, as in v2: a media key nobody asked for
 * should not take a key away from another mod. The rest of v2's mappings join with M4.
 */
public enum Hotkey {
    /** Opens the media windows without the chat. */
    MEDIA_CONTROLS(Keys.KEY_CONTROLS);

    private final String translationKey;

    Hotkey(String translationKey) {
        this.translationKey = translationKey;
    }

    /** The mapping's name in the controls screen, and its id in the game's options file. */
    public String translationKey() {
        return translationKey;
    }

    /** The category every mapping of the mod is listed under. */
    public static String categoryKey() {
        return Keys.KEY_CATEGORY;
    }
}
