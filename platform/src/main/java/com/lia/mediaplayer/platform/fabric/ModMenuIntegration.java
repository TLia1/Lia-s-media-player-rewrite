package com.lia.mediaplayer.platform.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * The settings button of Mod Menu's mod list, when Mod Menu is installed. Mod Menu is an
 * optional dependency: without it this class is never loaded.
 */
public final class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> FabricEntrypoint.running()
                .map(mod -> mod.settingsScreen(parent))
                .orElse(null);
    }
}
