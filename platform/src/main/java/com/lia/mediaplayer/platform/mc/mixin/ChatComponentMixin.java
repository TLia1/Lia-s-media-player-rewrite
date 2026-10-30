package com.lia.mediaplayer.platform.mc.mixin;

import com.lia.mediaplayer.platform.mc.McChat;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Rewrites media links into labels as a message enters the chat.
 *
 * <p>Replaces v2's two loader paths: on Fabric, cancelling player chat in {@code ALLOW_CHAT}
 * and re-injecting it, re-deriving its trust tag by hand, beside a separate {@code MODIFY_GAME}
 * path for system chat; on NeoForge, a chat event per kind. No loader offers one event for
 * every message at the point it is shown, and this is that point: player, system and disguised
 * chat all arrive here, and only what is displayed changes.
 */
@Mixin(ChatComponent.class)
abstract class ChatComponentMixin {

    // 26.1 split the public entry by origin (player, server, client) over one private method,
    // which is still the single point every message passes.
    //? if <26.1 {
    @ModifyVariable(
            method =
                    "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"),
            argsOnly = true)
    //?} else {
    /*@ModifyVariable(
            method =
                    "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"),
            argsOnly = true)
    *///?}
    private Component liasmediaplayer$labelLinks(Component message) {
        return McChat.displayed(message);
    }
}
