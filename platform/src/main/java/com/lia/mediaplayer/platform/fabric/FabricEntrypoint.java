package com.lia.mediaplayer.platform.fabric;

import com.lia.mediaplayer.platform.mc.McPlatform;
import com.lia.mediaplayer.platform.mc.ModBootstrap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

/** Fabric's way in: builds the mod and forwards Fabric API events to it. No decisions here. */
public final class FabricEntrypoint implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        var loader = FabricLoader.getInstance();
        String minecraftVersion = loader.getModContainer("minecraft")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        var mod = ModBootstrap.start(new McPlatform("Fabric", minecraftVersion, loader.getGameDir()));

        ClientTickEvents.END_CLIENT_TICK.register(client -> mod.events().tick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> mod.events().worldLeft());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> mod.close());
    }
}
