package com.lia.mediaplayer.ui;

import com.lia.mediaplayer.core.ModInfo;
import com.lia.mediaplayer.core.text.Keys;

/**
 * The key mappings the mod registers with the game, which lists them in its controls screen
 * where the player picks the keys. All start unbound, as in v2: a media key nobody asked for
 * should not take a key away from another mod.
 *
 * <p>They work in the world, with no screen open, and over the mod's own screens; the
 * transport ones act on the front-most player, the audio bar before a video window.
 */
public enum Hotkey {
    PLAY_PAUSE("playpause"),
    NEXT("next"),
    PREVIOUS("previous"),
    VOLUME_UP("volume_up"),
    VOLUME_DOWN("volume_down"),
    MUTE("mute"),
    /** Hides every window, or shows them again; what they play goes on. */
    TOGGLE_WINDOWS("toggle_windows"),
    CLOSE_ALL("close_all"),
    /** Opens the media windows without the chat. */
    MEDIA_CONTROLS("controls"),
    PLAYLISTS("playlists"),
    SETTINGS("config"),
    /** Plays the link on the clipboard, as a click on it in chat would (Alt, Shift included). */
    PLAY_CLIPBOARD("play_clipboard");

    private final String translationKey;

    Hotkey(String name) {
        this.translationKey = "key." + ModInfo.MOD_ID + "." + name;
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
