package dev.noxclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.noxclient.module.render.Animations;
import dev.noxclient.physics.AnimMath;
import dev.noxclient.physics.AnimationSystem;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonAnimationMixin {
    /** Replace only vanilla's swing helper; equipment/map transforms remain owned by vanilla. */
    @Inject(method = "applyItemArmAttackTransform", at = @At("HEAD"), cancellable = true)
    private void nox$swing(PoseStack pose, HumanoidArm arm, float progress, CallbackInfo ci) {
        Animations cfg = Animations.active();
        if (cfg == null || AnimationSystem.deferPlayer() || cfg.itemSwing.amount() == 0) return;
        float amount = cfg.itemSwing.amount(), t = Math.max(0, Math.min(1, progress));
        int sign = arm == HumanoidArm.RIGHT ? 1 : -1;
        float originalA = (float) Math.sin(t * t * Math.PI);
        float originalB = (float) Math.sin(Math.sqrt(t) * Math.PI);
        float eased = AnimMath.cubicBezier(t, .25f, .05f, .2f, 1);
        float follow = (float) Math.sin(eased * Math.PI);
        float windup = t < .2 ? (float) Math.sin(t / .2 * Math.PI) * .2f : 0;
        float a = AnimMath.lerp(originalA, follow, amount), b = AnimMath.lerp(originalB, follow - windup, amount);
        pose.translate(sign * follow * .035f * amount, -follow * .025f * amount, -follow * .08f * amount);
        pose.rotateDegrees(Axis.YP, sign * (45 - a * 20));
        pose.rotateDegrees(Axis.ZP, sign * b * -20);
        pose.rotateDegrees(Axis.XP, b * -80);
        pose.rotateDegrees(Axis.YP, sign * -45);
        ci.cancel();
    }

    /** Inside vanilla's per-hand push/pop: no pose state escapes into the other hand. */
    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER, ordinal = 0))
    private void nox$hand(PlayerRenderState player, FirstPersonHandsAndItemsRenderState hands,
        float partialTick, float pitch, InteractionHand hand, float swing, ItemStack item, float equipped,
        PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        Animations cfg = Animations.active();
        var state = player.avatarRenderState;
        if (cfg == null || state == null || hands.isScoping || AnimationSystem.deferPlayer()) return;
        boolean right = (hand == InteractionHand.MAIN_HAND) == (state.mainArm == HumanoidArm.RIGHT);
        int sign = right ? 1 : -1;
        if (!state.isUsingItem) {
            float bob = cfg.handBob.amount() * Math.min(1, state.walkAnimationSpeed);
            float phase = state.walkAnimationPos * .6662f;
            pose.translate(Math.sin(phase) * bob * .012, -Math.abs(Math.cos(phase)) * bob * .018, 0);
            pose.rotateDegrees(Axis.ZP, (float) Math.sin(phase) * bob * .8f);
            if (item.isEmpty()) {
                float arc = (float) Math.sin(AnimMath.easeInOutQuad(swing) * Math.PI) * cfg.itemSwing.amount();
                pose.translate(sign * arc * .035, arc * .015, -arc * .1);
                pose.rotateDegrees(Axis.ZP, sign * arc * -7);
            }
            return;
        }
        if (state.useItemHand != hand) return;
        float amount = cfg.itemUse.amount(), use = state.ticksUsingItem;
        float pull = Math.min(1, use / 20f);
        switch (item.getUseAnimation()) {
            case BOW -> pose.translate(0, 0, -.025 * pull * amount);
            case CROSSBOW -> {
                float charge = Math.min(1, use / Math.max(1, state.maxCrossbowChargeDuration));
                pose.rotateDegrees(Axis.ZP, sign * charge * -1.4f * amount);
            }
            case BLOCK -> pose.rotateDegrees(Axis.ZP, sign * -3 * amount);
            case EAT, DRINK -> pose.translate(0, Math.sin(use * Math.PI / 4) * .012 * amount, 0);
            case TRIDENT, SPEAR -> pose.translate(0, .02 * pull * amount, .035 * pull * amount);
            case SPYGLASS -> { /* Vanilla owns the scoped camera and hand pose. */ }
            default -> pose.rotateDegrees(Axis.ZP, (float) Math.sin(use * .25) * .5f * amount);
        }
    }
}
