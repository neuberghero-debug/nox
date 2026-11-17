package dev.noxclient.mixin;

import dev.noxclient.module.donutsmp.AutoTPAccept;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feeds inbound chat lines to modules that react to them (AutoTPAccept…). */
@Mixin(ChatComponent.class)
public abstract class ChatMixin {
    @Inject(method = "addPlayerMessage", at = @At("HEAD"))
    private void nox$playerMsg(Component msg, MessageSignature sig,
                               GuiMessageTag tag, CallbackInfo ci) {
        try { AutoTPAccept.onChat(msg.getString()); } catch (Throwable ignored) {}
        // Auction bids are ONLY fed from system messages — a signed player
        // message ("I sent you $5") must never count as a real /pay.
    }

    @Inject(method = "addServerSystemMessage", at = @At("HEAD"))
    private void nox$systemMsg(Component msg, CallbackInfo ci) {
        try { AutoTPAccept.onChat(msg.getString()); } catch (Throwable ignored) {}
        try { feed(msg.getString()); } catch (Throwable ignored) {}
    }

    private static void feed(String raw) {
        if (dev.noxclient.NoxClient.INSTANCE == null) return;
        if (dev.noxclient.NoxClient.INSTANCE.modules.get("Auction")
                instanceof dev.noxclient.module.donutsmp.Auction a
            && a.isEnabled() && a.running()) a.onChat(raw);
    }
}
