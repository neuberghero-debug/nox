package dev.noxclient.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.noxclient.module.render.Cosmetics;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * Shoulder pet / hat / mesh-aura attachments on the player. GLB models from
 * NoxModels, positioned via the humanoid ModelParts so they ride animations
 * (head-hats nod, shoulder-pets bounce with the torso). All animation is
 * procedural: idle bob, wing flap driven by player speed, orbit for auras.
 */
public class CosmeticLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    public CosmeticLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light,
            AvatarRenderState state, float limbAngle, float limbDistance) {
        Cosmetics c = Cosmetics.INSTANCE;
        if (c == null || !c.isEnabled() || state.isInvisible) return;

        float age = state.ageInTicks;
        float speed = Math.min(limbDistance, 1f);

        // ---- shoulder pet -------------------------------------------------
        GlbModel pet = NoxModels.get(c.pet.get());
        if (pet != null) {
            pose.pushPose();
            getParentModel().body.translateAndRotate(pose);
            boolean left = c.petSide.get().equals("Left");
            float side = left ? 6.4f : -6.4f;
            float bob = Mth.sin(age * 0.15f) * (0.6f + speed * 1.2f);
            pose.translate(side * 0.0625f, (-8.5f + bob) * 0.0625f, 0);
            float ps = c.petScale.floatVal();
            pose.scale(ps, -ps, ps);
            pose.rotateDegrees(com.mojang.math.Axis.YP,
                Mth.sin(age * 0.05f) * 14f + (left ? 180f : 0f));
            // slight hop+tilt while sprinting — sells "alive"
            pose.rotateDegrees(com.mojang.math.Axis.XP,
                Mth.sin(age * 0.3f) * speed * 9f);
            final int l = light;
            collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(NoxModels.whiteTex()),
                (p, vc) -> pet.render(p, vc, l, 1, 1, 1, 1));
            pose.popPose();
        }

        // ---- head wear ----------------------------------------------------
        GlbModel hat = NoxModels.get(c.hat.get());
        if (hat != null) {
            pose.pushPose();
            getParentModel().head.translateAndRotate(pose);
            float s = c.hatScale.floatVal();
            pose.translate(0, c.hatOffY.floatVal() * 0.0625f, 0);
            pose.scale(s, -s, s);
            final int l = light;
            collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(NoxModels.whiteTex()),
                (p, vc) -> hat.render(p, vc, l, 1, 1, 1, 1));
            pose.popPose();
        }

        // ---- mesh aura ----------------------------------------------------
        GlbModel aura = NoxModels.get(c.meshAura.get());
        if (aura != null) {
            pose.pushPose();
            getParentModel().body.translateAndRotate(pose);
            float spin = age * (c.auraSpin.floatVal() * 57.2958f); // rad/s -> deg
            float pulse = 1f + Mth.sin(age * 0.12f) * 0.08f;
            pose.rotateDegrees(com.mojang.math.Axis.YP, spin);
            pose.translate(0, -6f * 0.0625f + Mth.sin(age * 0.1f) * 0.02f, 0);
            float s = c.auraScale.floatVal() * pulse;
            pose.scale(s, -s, s);
            final int l = 0xF000F0;                     // always fullbright
            collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(NoxModels.whiteTex()),
                (p, vc) -> aura.render(p, vc, l, 1, 1, 1, c.auraAlpha.floatVal()));
            pose.popPose();
        }
    }
}
