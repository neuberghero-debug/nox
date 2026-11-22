package dev.noxclient.mixin;

import dev.noxclient.module.combat.SprintReset;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class AttackMixin {
    @Inject(method = "attack", at = @At("TAIL"))
    private void nox$onHit(Player player, Entity target, CallbackInfo ci) {
        if (player == net.minecraft.client.Minecraft.getInstance().player) {
            dev.noxclient.stats.Stats.onHit(target);
            dev.noxclient.util.PvpHooks.onHitEntity(target);
            if (player.isSprinting()) { SprintReset.onHit(); dev.noxclient.module.combat.WTap.onHit(); }
        }
    }
}
