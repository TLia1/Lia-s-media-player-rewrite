package com.lia.mediaplayer.platform.mc.mixin;

import com.lia.mediaplayer.platform.mc.McChat;
import com.mojang.authlib.GameProfile;
import java.time.Instant;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tells the chat mixin who sent the player message being shown, which the chat itself is not
 * told, so the player's blocked-senders list can apply. Read-only: it changes nothing.
 *
 * <p>The loaders' chat events carry the sender, but fire when a message is received, not when
 * it is shown — with the chat delay option the two are seconds apart and other messages come
 * between. This is the call that shows it, delayed or not.
 */
@Mixin(ChatListener.class)
abstract class ChatListenerMixin {

    @Inject(method = "showMessageToPlayer", at = @At("HEAD"))
    private void liasmediaplayer$senderShowing(
            ChatType.Bound type,
            PlayerChatMessage message,
            Component decorated,
            GameProfile sender,
            boolean onlyShowSecureChat,
            Instant timestamp,
            CallbackInfoReturnable<Boolean> callback) {
        // authlib 7 (1.21.11; unbisected) made the profile a record.
        //? if <1.21.11 {
        McChat.senderShowing(sender.getName());
        //?} else
        /*McChat.senderShowing(sender.name());*/
    }

    @Inject(method = "showMessageToPlayer", at = @At("RETURN"))
    private void liasmediaplayer$senderShown(
            ChatType.Bound type,
            PlayerChatMessage message,
            Component decorated,
            GameProfile sender,
            boolean onlyShowSecureChat,
            Instant timestamp,
            CallbackInfoReturnable<Boolean> callback) {
        McChat.senderShowing(null);
    }
}
