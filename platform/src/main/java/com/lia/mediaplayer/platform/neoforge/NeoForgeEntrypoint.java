package com.lia.mediaplayer.platform.neoforge;

import com.lia.mediaplayer.core.ModInfo;
import com.lia.mediaplayer.platform.mc.McPlatform;
import com.lia.mediaplayer.platform.mc.ModBootstrap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

/** NeoForge's way in: builds the mod and forwards NeoForge events to it. No decisions here. */
@Mod(value = ModInfo.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeEntrypoint {

    public NeoForgeEntrypoint() {
        String minecraftVersion = ModList.get()
                .getModContainerById("minecraft")
                .map(mod -> mod.getModInfo().getVersion().toString())
                .orElse("unknown");
        var mod = ModBootstrap.start(new McPlatform("NeoForge", minecraftVersion, FMLPaths.GAMEDIR.get()));

        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> mod.events().tick());
        NeoForge.EVENT_BUS.addListener(
                ClientPlayerNetworkEvent.LoggingOut.class, event -> mod.events().worldLeft());
        NeoForge.EVENT_BUS.addListener(GameShuttingDownEvent.class, event -> mod.close());
    }
}
