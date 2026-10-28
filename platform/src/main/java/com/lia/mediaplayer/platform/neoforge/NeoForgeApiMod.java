package com.lia.mediaplayer.platform.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/**
 * The second entry of neoforge.mods.toml: gives the API its own mod id, so an addon can
 * depend on a version range of it. Holds no logic.
 */
@Mod(value = "liasmediaplayerapi", dist = Dist.CLIENT)
public final class NeoForgeApiMod {}
