package com.lia.mediaplayer.api.ui;

import com.lia.mediaplayer.api.Ids;
import com.lia.mediaplayer.api.media.MediaHandle;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A key the player chooses in the game's controls screen, listed with the mod's own. It starts
 * unbound, as the mod's do: a key nobody asked for should not take one from another mod.
 *
 * <p>It works where the mod's own keys do: in the world, and over the mod's screens.
 *
 * @param nameKey the translation key of its name in the controls screen; also its id in the
 *     game's options file, so keep it stable
 * @param onPress told the front-most player, the audio bar before a video window, as the mod's
 *     transport keys are; client thread
 * @since 4.0.0
 */
public record KeyBindingSpec(String id, String nameKey, Consumer<Optional<MediaHandle>> onPress) {

    public KeyBindingSpec {
        Ids.require(id, "Key binding id");
        if (nameKey.isBlank()) {
            throw new IllegalArgumentException(id + ": a key binding needs a name key");
        }
    }
}
