package dev.noxclient.mixin;

import dev.noxclient.NoxClient;
import dev.noxclient.module.render.SelfTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects the SelfTag module's custom nametag into the local player's
 * AvatarRenderState after vanilla extraction — vanilla never sets a nametag
 * for the camera entity, so we write both nameTag and its anchor ourselves.
 */
@Mixin(AvatarRenderer.class)
public abstract class SelfTagMixin {
    private static volatile boolean warned;

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void nox$selfTag(Avatar entity, AvatarRenderState state, float dt, CallbackInfo ci) {
        try {
            if (NoxClient.INSTANCE == null) return;
            if (!(NoxClient.INSTANCE.modules.get("SelfTag") instanceof SelfTag st)
                || !st.isEnabled()) return;
            Minecraft mc = Minecraft.getInstance();
            if (entity != mc.player) return;
            Component c = st.component();
            if (c == null) return;
            state.nameTag = c;
            // recompute every frame — attachments can go stale after
            // teleports/dimension changes when only set once (P1 fix)
            if (entity instanceof Entity e)
                state.nameTagAttachment = e.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, 1.0f);
        } catch (Throwable t) {
            if (!warned) { warned = true; System.out.println("[NOX SelfTag] " + t); }
        }
    }
}
