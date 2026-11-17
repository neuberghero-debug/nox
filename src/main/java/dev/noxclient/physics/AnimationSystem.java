package dev.noxclient.physics;

import dev.noxclient.module.render.Animations;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Main-thread tick registry plus allocation-free pose evaluation. */
public final class AnimationSystem {
    private static final java.util.Map<Integer, EntityAnimator> ANIMATORS = new java.util.HashMap<>();
    private static java.lang.ref.WeakReference<Object> world = new java.lang.ref.WeakReference<>(null);
    private static int tick;
    private static final boolean DEFER_PLAYER = FabricLoader.getInstance().isModLoaded("player-animator")
        || FabricLoader.getInstance().isModLoaded("playeranimator") || FabricLoader.getInstance().isModLoaded("emotecraft");
    private static boolean compatLogged;
    private static long lastTickNs;
    private static final String[] SPIDER_LEGS = {"right_hind_leg", "right_middle_hind_leg", "right_middle_front_leg",
        "right_front_leg", "left_hind_leg", "left_middle_hind_leg", "left_middle_front_leg", "left_front_leg"};
    private AnimationSystem() {}
    public static boolean deferPlayer() { return DEFER_PLAYER; }
    /** Monotonic UI clock in seconds — drives all screen animation phases. */
    public static float seconds() { return System.nanoTime() * 1e-9f; }
    /** Frame-rate independent exponential approach; snaps when reducedMotion. */
    public static float approach(float cur, float tgt, float rate, float dt) {
        if (dev.noxclient.gui.UICustom.reducedMotion) return tgt;
        float f = 1f - (float) Math.exp(-rate * Math.min(dt, 0.1));
        return cur + (tgt - cur) * f;
    }
    public static void clear() { ANIMATORS.clear(); world.clear(); }
    public static String diagnostics() { return ANIMATORS.size() + " animators; tick " + lastTickNs / 1000 + " us" + (DEFER_PLAYER ? "; player layer deferred" : ""); }
    public static void tick(Minecraft mc) {
        Animations config = Animations.active();
        if (config == null || mc.level == null || mc.player == null) { clear(); return; }
        if (mc.isPaused()) return;
        if (world.get() != mc.level) { clear(); world = new java.lang.ref.WeakReference<>(mc.level); }
        if (DEFER_PLAYER && !compatLogged) {
            org.slf4j.LoggerFactory.getLogger("NOX").info("Player animation layer deferred to PlayerAnimator/Emotecraft");
            compatLogged = true;
        }
        long begin = System.nanoTime();
        tick++;
        double range = config.radius.get() * config.radius.get();
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || entity.distanceToSqr(mc.player) > range) continue;
            if (!supported(living) || (DEFER_PLAYER && living instanceof Player)) continue;
            EntityAnimator animator = ANIMATORS.get(entity.getId());
            long seed = entity.getUUID().getMostSignificantBits() ^ entity.getUUID().getLeastSignificantBits();
            if (animator == null || animator.seed != seed) {
                if (ANIMATORS.size() >= 256) continue;
                animator = new EntityAnimator(seed); ANIMATORS.put(entity.getId(), animator);
            }
            animator.seenTick = tick;
            animator.tick(living, config);
        }
        ANIMATORS.values().removeIf(a -> a.seenTick != tick);
        lastTickNs = System.nanoTime() - begin;
    }
    private static boolean supported(LivingEntity e) {
        var t = e.getType();
        return e instanceof Player || t == EntityTypes.ZOMBIE || t == EntityTypes.SKELETON
            || t == EntityTypes.CREEPER || t == EntityTypes.ENDERMAN || t == EntityTypes.SPIDER
            || t == EntityTypes.CAVE_SPIDER || t == EntityTypes.WOLF;
    }
    public static void extract(LivingEntity entity, LivingEntityRenderState state, float delta) {
        AnimationFrame out = ((AnimationFrame.Access) state).nox$animation();
        out.active = false;
        EntityAnimator a = ANIMATORS.get(entity.getId());
        if (Animations.active() != null && a != null) a.sample(out, delta, entity);
    }
    private static final AnimationFrame LOCAL = new AnimationFrame();
    public static AnimationFrame local(float delta) {
        var player = Minecraft.getInstance().player;
        LOCAL.active = false;
        if (player != null) {
            EntityAnimator a = ANIMATORS.get(player.getId());
            if (a != null) a.sample(LOCAL, delta, player);
        }
        return LOCAL;
    }
    private static AnimationFrame frame(LivingEntityRenderState s) { return ((AnimationFrame.Access) s).nox$animation(); }
    public static void humanoid(HumanoidModel<?> model, HumanoidRenderState s) {
        Animations cfg = Animations.active(); AnimationFrame f = frame(s);
        if (cfg == null || !f.active || s.deathTime > 0) return;
        boolean player = s instanceof AvatarRenderState;
        float multiplier = player ? 1 : cfg.creatures.amount();
        if (player && DEFER_PLAYER) return;
        float head = cfg.head.amount() * multiplier;
        model.head.yRot = AnimMath.lerp(model.head.yRot, f.headYaw, head);
        model.head.xRot = AnimMath.lerp(model.head.xRot, f.headPitch, head);
        float idle = cfg.idle.amount() * multiplier * f.weight(AnimState.IDLE);
        float breathe = (float) Math.sin(s.ageInTicks * .09 + f.phase);
        model.body.y += breathe * .15f * idle;
        model.head.zRot += (float) Math.sin(s.ageInTicks * .033 + f.phase) * .012f * idle;
        float body = cfg.body.amount() * multiplier;
        float sprint = f.weight(AnimState.SPRINT);
        model.body.xRot += body * (.18f * sprint + .08f * f.weight(AnimState.SNEAK)
            - .09f * f.weight(AnimState.HURT) + f.landing * .24f);
        model.body.y += f.landing * body * .8f;
        if (!s.isPassenger && !s.isUsingItem && !s.isVisuallySwimming && !s.isFallFlying) {
            float stride = cfg.legs.amount() * multiplier;
            float phase = s.walkAnimationPos * .6662f, speed = Math.min(1, s.walkAnimationSpeed);
            model.leftLeg.xRot *= 1 + stride * sprint * .12f;
            model.rightLeg.xRot *= 1 + stride * sprint * .12f;
            model.leftLeg.y -= Math.max(0, (float) Math.sin(phase)) * speed * stride * .35f;
            model.rightLeg.y -= Math.max(0, (float) Math.sin(phase + Math.PI)) * speed * stride * .35f;
            float arms = cfg.arms.amount() * multiplier;
            model.leftArm.zRot -= .025f * breathe * idle + .09f * speed * arms;
            model.rightArm.zRot += .025f * breathe * idle + .09f * speed * arms;
            float swing = Math.max(0, Math.min(1, s.swingAnimation));
            float arc = swing < .25f ? -AnimMath.easeInOutQuad(swing * 4) * .12f
                : (float) Math.sin((swing - .25f) / .75f * Math.PI) * .25f;
            model.getArm(s.mainArm).xRot -= arc * arms;
        }
        if (s.isFallFlying) {
            model.leftArm.zRot -= .06f * body; model.rightArm.zRot += .06f * body;
        }
        if (model instanceof PlayerModel p) {
            copy(p.head, p.hat); copy(p.body, p.jacket); copy(p.leftArm, p.leftSleeve);
            copy(p.rightArm, p.rightSleeve); copy(p.leftLeg, p.leftPants); copy(p.rightLeg, p.rightPants);
        }
    }
    private static void copy(ModelPart from, ModelPart to) {
        to.x = from.x; to.y = from.y; to.z = from.z;
        to.xRot = from.xRot; to.yRot = from.yRot; to.zRot = from.zRot;
    }
    public static void creature(Model<?> model, LivingEntityRenderState s) {
        Animations cfg = Animations.active(); AnimationFrame f = frame(s);
        if (cfg == null || !f.active || s.deathTime > 0) return;
        float amount = cfg.creatures.amount();
        ModelPart root = model.root();
        float phase = s.walkAnimationPos * .6662f, speed = Math.min(1, s.walkAnimationSpeed);
        if (s instanceof CreeperRenderState c) {
            ModelPart head = part(root, "head");
            if (head != null) head.zRot += Math.sin(s.ageInTicks * (c.swelling > 0 ? 2.5 : .15) + f.phase)
                * (.012f + c.swelling * .07f) * amount;
        } else if (s instanceof WolfRenderState w) {
            ModelPart tail = part(root, "tail"), head = part(root, "head");
            if (tail != null) tail.yRot += Math.sin(s.ageInTicks * .12 + f.phase) * .18f * amount;
            if (head != null) head.zRot += f.headYaw * .035f * amount;
            if (!w.isSitting) {
                ModelPart body = part(root, "body");
                if (body != null) body.y -= Math.abs(Math.sin(phase * 2)) * speed * amount * .4f;
            }
        } else {
            for (int i = 0; i < SPIDER_LEGS.length; i++) {
                ModelPart leg = part(root, SPIDER_LEGS[i]);
                if (leg != null) leg.zRot += Math.sin(phase + (i % 4) * Math.PI / 2)
                    * speed * .09f * amount * (i < 4 ? 1 : -1);
            }
        }
    }
    private static ModelPart part(ModelPart root, String name) { return root.hasChild(name) ? root.getChild(name) : null; }
}
