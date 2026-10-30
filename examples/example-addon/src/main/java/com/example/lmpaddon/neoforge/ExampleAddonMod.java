package com.example.lmpaddon.neoforge;

import com.example.lmpaddon.ExampleAddon;
import com.lia.mediaplayer.api.LiasMediaPlayerApi;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/**
 * NeoForge's way in: the one class of the addon that names the loader. Fabric never loads it.
 *
 * <p>The mod registers every addon handed over here once all mods are constructed, so it does
 * not matter whether this constructor runs before Lia's Media Player's or after.
 */
@Mod(value = ExampleAddon.ID, dist = Dist.CLIENT)
public final class ExampleAddonMod {

    public ExampleAddonMod() {
        LiasMediaPlayerApi.registerAddon(ExampleAddon.ID, new ExampleAddon());
    }
}
