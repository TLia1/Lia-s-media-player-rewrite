package com.lia.mediaplayer.platform.mc;

import com.lia.mediaplayer.ui.ClientEvents;
import com.lia.mediaplayer.ui.Hotkey;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;

/**
 * The game's key mappings for the mod's {@link Hotkey}s. Each loader registers {@link #all()}
 * at its registration moment; the platform polls them once a tick.
 */
final class McKeyMappings {

    private final Map<Hotkey, KeyMapping> mappings = new EnumMap<>(Hotkey.class);

    McKeyMappings() {
        for (Hotkey hotkey : Hotkey.values()) {
            mappings.put(
                    hotkey,
                    new KeyMapping(
                            hotkey.translationKey(),
                            InputConstants.Type.KEYSYM,
                            InputConstants.UNKNOWN.getValue(),
                            Hotkey.categoryKey()));
        }
    }

    List<KeyMapping> all() {
        return List.copyOf(mappings.values());
    }

    /** Reports the presses since the last poll. The game counts presses only with no screen open. */
    void poll(ClientEvents events) {
        mappings.forEach((hotkey, mapping) -> {
            while (mapping.consumeClick()) {
                events.hotkeyPressed(hotkey, McInput.currentMods());
            }
        });
    }

    /** Reports a press over a screen, where the game does not count it, if it is a mapped key. */
    boolean pressedOverScreen(int keyCode, int scanCode, ClientEvents events) {
        for (var entry : mappings.entrySet()) {
            if (!entry.getValue().isUnbound() && entry.getValue().matches(keyCode, scanCode)) {
                events.hotkeyPressed(entry.getKey(), McInput.currentMods());
                return true;
            }
        }
        return false;
    }
}
