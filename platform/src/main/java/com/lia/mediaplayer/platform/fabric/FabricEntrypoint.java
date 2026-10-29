package com.lia.mediaplayer.platform.fabric;

import com.lia.mediaplayer.platform.mc.McPlatform;
import com.lia.mediaplayer.platform.mc.ModBootstrap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric's way in: builds the mod and forwards Fabric API events to it. No decisions here:
 * "the mod took it" becomes {@code false} from an {@code allow*} callback, and that is all.
 *
 * <p>Fabric's screen events are per screen, so the screen hooks are subscribed from {@code
 * AFTER_INIT} for each screen as it is built.
 */
public final class FabricEntrypoint implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        var loader = FabricLoader.getInstance();
        String minecraftVersion = loader.getModContainer("minecraft")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        var mod = ModBootstrap.start(new McPlatform("Fabric", minecraftVersion, loader.getGameDir()));

        ClientTickEvents.END_CLIENT_TICK.register(client -> mod.tick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> mod.worldLeft());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> mod.close());

        // Deprecated from 1.21.4 in favour of HudElementRegistry, and the only way on 1.21.1.
        HudRenderCallback.EVENT.register((graphics, tickCounter) -> mod.renderHud(graphics));

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            mod.screenOpened(screen);
            ScreenEvents.afterRender(screen)
                    .register((s, graphics, mouseX, mouseY, delta) -> mod.renderScreen(s, graphics, mouseX, mouseY));
            ScreenMouseEvents.allowMouseClick(screen)
                    .register((s, mouseX, mouseY, button) -> !mod.mousePressed(s, mouseX, mouseY, button));
            ScreenMouseEvents.allowMouseRelease(screen)
                    .register((s, mouseX, mouseY, button) -> !mod.mouseReleased(s, mouseX, mouseY, button));
            ScreenMouseEvents.allowMouseScroll(screen)
                    .register((s, mouseX, mouseY, horizontal, vertical) ->
                            !mod.mouseScrolled(s, mouseX, mouseY, vertical));
            ScreenKeyboardEvents.allowKeyPress(screen)
                    .register((s, key, scancode, modifiers) -> !mod.keyPressed(s, key, scancode, modifiers));
        });

        mod.keyMappings().forEach(KeyBindingHelper::registerKeyBinding);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registries) -> dispatcher.register(
                mod.<FabricClientCommandSource>showCommand((context, message) ->
                        context.getSource().sendError(message))));
    }
}
