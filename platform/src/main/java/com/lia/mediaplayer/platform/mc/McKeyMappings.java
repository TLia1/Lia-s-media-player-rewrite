package com.lia.mediaplayer.platform.mc;

import com.lia.mediaplayer.api.ui.KeyBindingSpec;
import com.lia.mediaplayer.ui.ClientEvents;
import com.lia.mediaplayer.ui.Hotkey;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;

/**
 * The game's key mappings for the mod's {@link Hotkey}s and the addons' key bindings, all in
 * the mod's category. Each loader registers {@link #all()} at its registration moment, after
 * the addons are in; the platform polls them once a tick.
 */
final class McKeyMappings {

    private final Map<Hotkey, KeyMapping> mappings = new EnumMap<>(Hotkey.class);
    private final Map<String, KeyMapping> addonMappings = new LinkedHashMap<>();

    McKeyMappings() {
        for (Hotkey hotkey : Hotkey.values()) {
            mappings.put(hotkey, unbound(hotkey.translationKey()));
        }
    }

    /** Adds a mapping, unbound, for each of the addons' key bindings. */
    void addAddonBindings(List<KeyBindingSpec> bindings) {
        for (KeyBindingSpec binding : bindings) {
            addonMappings.put(binding.id(), unbound(binding.nameKey()));
        }
    }

    List<KeyMapping> all() {
        var all = new ArrayList<KeyMapping>(mappings.values());
        all.addAll(addonMappings.values());
        return List.copyOf(all);
    }

    private static KeyMapping unbound(String name) {
        return new KeyMapping(
                name, InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), Hotkey.categoryKey());
    }

    /** Reports the presses since the last poll. The game counts presses only with no screen open. */
    void poll(ClientEvents events) {
        mappings.forEach((hotkey, mapping) -> {
            while (mapping.consumeClick()) {
                events.hotkeyPressed(hotkey, McInput.currentMods());
            }
        });
        addonMappings.forEach((id, mapping) -> {
            while (mapping.consumeClick()) {
                events.keyBindingPressed(id, McInput.currentMods());
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
        for (var entry : addonMappings.entrySet()) {
            if (!entry.getValue().isUnbound() && entry.getValue().matches(keyCode, scanCode)) {
                events.keyBindingPressed(entry.getKey(), McInput.currentMods());
                return true;
            }
        }
        return false;
    }
}
