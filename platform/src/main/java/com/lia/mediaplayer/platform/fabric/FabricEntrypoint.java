package com.lia.mediaplayer.platform.fabric;

import com.lia.mediaplayer.api.LiasMediaPlayerApi;
import com.lia.mediaplayer.api.LiasMediaPlayerApi.PendingAddon;
import com.lia.mediaplayer.api.MediaPlayerAddon;
import com.lia.mediaplayer.core.ModInfo;
import com.lia.mediaplayer.platform.mc.McPlatform;
import com.lia.mediaplayer.platform.mc.ModBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
//? if <1.21.6 {
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
//?} else {
/*import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.ResourceLocation;
*///?}
//? if <26.1 {
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
//?} else
/*import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;*/
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric's way in: builds the mod and forwards Fabric API events to it. No decisions here:
 * "the mod took it" becomes {@code false} from an {@code allow*} callback, and that is all.
 *
 * <p>Fabric's screen events are per screen, so the screen hooks are subscribed from {@code
 * AFTER_INIT} for each screen as it is built.
 *
 * <p>Fabric API follows the game's renames: from 1.21.11 mouse and key callbacks take the
 * game's input records, and from 26.1 a screen's widgets are {@code getWidgets}, rendering is
 * extraction, and key bindings are key mappings. The HUD is drawn as an element after the
 * chat's from 1.21.6, where Fabric's element registry replaced the HUD callback.
 *
 * <p>Addons are the {@code "liasmediaplayer"} entrypoints of other mods, registered here before
 * the key mappings are handed to Fabric, whatever order Fabric starts the mods in.
 */
public final class FabricEntrypoint implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(FabricEntrypoint.class);

    /** The running mod, for the mod menu's settings button; the only static besides constants. */
    private static @Nullable ModBootstrap running;

    /** The running mod, once Fabric has started it. */
    static Optional<ModBootstrap> running() {
        return Optional.ofNullable(running);
    }

    @Override
    public void onInitializeClient() {
        var loader = FabricLoader.getInstance();
        String minecraftVersion = loader.getModContainer("minecraft")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        var mod = ModBootstrap.start(new McPlatform("Fabric", minecraftVersion, loader.getGameDir()));
        running = mod;

        ClientTickEvents.END_CLIENT_TICK.register(client -> mod.tick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> mod.worldLeft());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> mod.close());

        //? if <1.21.6 {
        // Deprecated from 1.21.4 in favour of HudElementRegistry, and the only way on 1.21.1.
        HudRenderCallback.EVENT.register((graphics, tickCounter) -> mod.renderHud(graphics));
        //?} else {
        /*HudElementRegistry.attachElementAfter(
                VanillaHudElements.CHAT,
                ResourceLocation.fromNamespaceAndPath(ModInfo.MOD_ID, "media_windows"),
                (graphics, deltaTracker) -> mod.renderHud(graphics));
        *///?}

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            //? if <26.1 {
            mod.screenOpened(screen, widget -> Screens.getButtons(screen).add(widget));
            ScreenEvents.afterRender(screen)
                    .register((s, graphics, mouseX, mouseY, delta) -> mod.renderScreen(s, graphics, mouseX, mouseY));
            //?} else {
            /*mod.screenOpened(screen, widget -> Screens.getWidgets(screen).add(widget));
            ScreenEvents.afterExtract(screen)
                    .register((s, graphics, mouseX, mouseY, delta) -> mod.renderScreen(s, graphics, mouseX, mouseY));
            *///?}
            //? if <1.21.11 {
            ScreenMouseEvents.allowMouseClick(screen)
                    .register((s, mouseX, mouseY, button) -> !mod.mousePressed(s, mouseX, mouseY, button));
            ScreenMouseEvents.allowMouseRelease(screen)
                    .register((s, mouseX, mouseY, button) -> !mod.mouseReleased(s, mouseX, mouseY, button));
            //?} else {
            /*ScreenMouseEvents.allowMouseClick(screen)
                    .register((s, event) -> !mod.mousePressed(s, event.x(), event.y(), event.button()));
            ScreenMouseEvents.allowMouseRelease(screen)
                    .register((s, event) -> !mod.mouseReleased(s, event.x(), event.y(), event.button()));
            *///?}
            ScreenMouseEvents.allowMouseScroll(screen)
                    .register((s, mouseX, mouseY, horizontal, vertical) ->
                            !mod.mouseScrolled(s, mouseX, mouseY, vertical));
            //? if <1.21.11 {
            ScreenKeyboardEvents.allowKeyPress(screen)
                    .register((s, key, scancode, modifiers) -> !mod.keyPressed(s, key, scancode, modifiers));
            //?} else {
            /*ScreenKeyboardEvents.allowKeyPress(screen)
                    .register((s, event) -> !mod.keyPressed(s, event.key(), secondCode(event), event.modifiers()));
            *///?}
        });

        mod.loadAddons(addons(loader));
        //? if <26.1 {
        mod.keyMappings().forEach(KeyBindingHelper::registerKeyBinding);
        //?} else
        /*mod.keyMappings().forEach(KeyMappingHelper::registerKeyMapping);*/
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registries) -> dispatcher.register(
                mod.<FabricClientCommandSource>showCommand((context, message) ->
                        context.getSource().sendError(message))));
    }

    //? if >=1.21.11 {
    /*/^* The key event's second code: the scancode on GLFW, SDL's keycode from 26.3. ^/
    private static int secondCode(net.minecraft.client.input.KeyEvent event) {
        //? if <26.3 {
        return event.scancode();
        //?} else
        /^return event.keycode();^/
    }
    *///?}

    /** The addons: every {@code "liasmediaplayer"} entrypoint, then any handed to the API's door. */
    private static List<PendingAddon> addons(FabricLoader loader) {
        var addons = new ArrayList<PendingAddon>();
        try {
            for (var container : loader.getEntrypointContainers(ModInfo.MOD_ID, MediaPlayerAddon.class)) {
                String addonId = container.getProvider().getMetadata().getId();
                try {
                    addons.add(new PendingAddon(addonId, container.getEntrypoint()));
                } catch (RuntimeException e) {
                    LOGGER.error("Addon {} could not be created", addonId, e);
                }
            }
        } catch (RuntimeException e) {
            LOGGER.error("The addons' entrypoints could not be read", e);
        }
        addons.addAll(LiasMediaPlayerApi.takePending());
        return addons;
    }
}
