package dev.noxclient.mixin;

import dev.noxclient.NoxClient;
import dev.noxclient.module.combat.Velocity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scales server knockback after vanilla applies it, per Velocity settings. */
@Mixin(ClientPacketListener.class)
public abstract class VelocityMixin {
    @Inject(method = "handleSetEntityMotion", at = @At("TAIL"))
    private void nox$scaleKb(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        var m = NoxClient.INSTANCE == null ? null : NoxClient.INSTANCE.modules.get("Velocity");
        if (!(m instanceof Velocity v) || !v.isEnabled()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        var e = mc.level.getEntity(packet.id());
        if (e != mc.player) return;
        Vec3 d = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(
            d.x * v.horizontal.get() / 100.0,
            d.y * v.vertical.get() / 100.0,
            d.z * v.horizontal.get() / 100.0);
    }
}
