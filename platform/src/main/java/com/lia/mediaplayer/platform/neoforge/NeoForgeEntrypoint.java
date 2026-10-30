package com.lia.mediaplayer.platform.neoforge;

import com.lia.mediaplayer.api.LiasMediaPlayerApi;
import com.lia.mediaplayer.core.ModInfo;
import com.lia.mediaplayer.platform.mc.McPlatform;
import com.lia.mediaplayer.platform.mc.ModBootstrap;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

/**
 * NeoForge's way in: builds the mod and forwards NeoForge events to it. No decisions here:
 * "the mod took it" becomes {@code setCanceled(true)}, and that is all.
 *
 * <p>Addons hand themselves to {@link LiasMediaPlayerApi#registerAddon} from their own {@code
 * @Mod} constructors. NeoForge constructs every mod before it asks for key mappings, so that
 * is when they are registered, whichever mod was constructed first.
 */
@Mod(value = ModInfo.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeEntrypoint {

    public NeoForgeEntrypoint(IEventBus modBus, ModContainer container) {
        String minecraftVersion = ModList.get()
                .getModContainerById("minecraft")
                .map(mod -> mod.getModInfo().getVersion().toString())
                .orElse("unknown");
        var mod = ModBootstrap.start(new McPlatform("NeoForge", minecraftVersion, FMLPaths.GAMEDIR.get()));

        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            mod.loadAddons(LiasMediaPlayerApi.takePending());
            mod.keyMappings().forEach(event::register);
        });
        container.registerExtensionPoint(
                IConfigScreenFactory.class, (IConfigScreenFactory) (owner, parent) -> mod.settingsScreen(parent));

        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ClientTickEvent.Post.class, event -> mod.tick());
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> mod.worldLeft());
        bus.addListener(GameShuttingDownEvent.class, event -> mod.close());
        bus.addListener(RenderGuiEvent.Post.class, event -> mod.renderHud(event.getGuiGraphics()));

        bus.addListener(ScreenEvent.Init.Post.class, event -> mod.screenOpened(event.getScreen(), event::addListener));
        bus.addListener(ScreenEvent.Render.Post.class, event -> mod.renderScreen(
                event.getScreen(), event.getGuiGraphics(), event.getMouseX(), event.getMouseY()));
        bus.addListener(ScreenEvent.MouseButtonPressed.Pre.class, event -> {
            if (mod.mousePressed(event.getScreen(), event.getMouseX(), event.getMouseY(), event.getButton())) {
                event.setCanceled(true);
            }
        });
        bus.addListener(ScreenEvent.MouseButtonReleased.Pre.class, event -> {
            if (mod.mouseReleased(event.getScreen(), event.getMouseX(), event.getMouseY(), event.getButton())) {
                event.setCanceled(true);
            }
        });
        bus.addListener(ScreenEvent.MouseScrolled.Pre.class, event -> {
            if (mod.mouseScrolled(event.getScreen(), event.getMouseX(), event.getMouseY(), event.getScrollDeltaY())) {
                event.setCanceled(true);
            }
        });
        bus.addListener(ScreenEvent.KeyPressed.Pre.class, event -> {
            if (mod.keyPressed(event.getScreen(), event.getKeyCode(), event.getScanCode(), event.getModifiers())) {
                event.setCanceled(true);
            }
        });
        bus.addListener(ScreenEvent.CharacterTyped.Pre.class, event -> {
            if (mod.charTyped(event.getScreen(), event.getCodePoint())) {
                event.setCanceled(true);
            }
        });

        bus.addListener(RegisterClientCommandsEvent.class, event -> event.getDispatcher()
                .register(mod.<CommandSourceStack>showCommand((context, message) ->
                        context.getSource().sendFailure(message))));
    }
}
