package dev.noxclient.physics;

import dev.noxclient.module.render.Animations;
import net.minecraft.world.entity.LivingEntity;

/** Tick-only state machine. Models are exclusively modified from render snapshots. */
public final class EntityAnimator {
    private final float[] weights = new float[AnimState.values().length], previous = weights.clone(), start = weights.clone();
    private final float[] yawVelocity = {0}, pitchVelocity = {0}, landingVelocity = {0};
    private AnimState state = AnimState.IDLE;
    private int blendTick = 5;
    private boolean grounded = true, initialized;
    private float yaw, pitch, oldYaw, oldPitch, landing, oldLanding;
    private double lastX, lastY, lastZ;
    public final long seed;
    public int seenTick;

    public EntityAnimator(long seed) {
        this.seed = seed;
        weights[AnimState.IDLE.ordinal()] = previous[AnimState.IDLE.ordinal()] = 1;
    }
    public void tick(LivingEntity e, Animations config) {
        double dx = e.getX() - lastX, dy = e.getY() - lastY, dz = e.getZ() - lastZ;
        boolean reset = !initialized || dx * dx + dy * dy + dz * dz > 16;
        lastX = e.getX(); lastY = e.getY(); lastZ = e.getZ();
        float targetYaw = wrap(e.yHeadRot - e.yBodyRot) * ((float) Math.PI / 180);
        targetYaw = Math.max(-1.2f, Math.min(1.2f, targetYaw));
        float targetPitch = e.getXRot() * ((float) Math.PI / 180);
        if (reset) {
            yaw = oldYaw = targetYaw; pitch = oldPitch = targetPitch;
            yawVelocity[0] = pitchVelocity[0] = landingVelocity[0] = 0;
            landing = oldLanding = 0; grounded = e.onGround(); initialized = true;
        }
        oldYaw = yaw; oldPitch = pitch; oldLanding = landing;
        float k = config.stiffness.floatVal(), c = (float) (2 * Math.sqrt(k)) * config.damping.floatVal();
        yaw = AnimMath.spring(yaw, targetYaw, yawVelocity, k, c, .05f);
        pitch = AnimMath.spring(pitch, targetPitch, pitchVelocity, k, c, .05f);
        if (!grounded && e.onGround()) landingVelocity[0] = 5;
        landing = AnimMath.spring(landing, 0, landingVelocity, 150, 22, .05f);
        boolean justLanded = !grounded && e.onGround();
        grounded = e.onGround();
        AnimState next = choose(e, justLanded);
        System.arraycopy(weights, 0, previous, 0, weights.length);
        if (next != state) {
            state = next; blendTick = 0; System.arraycopy(weights, 0, start, 0, weights.length);
        }
        float blend = AnimMath.smootherstep(Math.min(1, ++blendTick / 5f));
        for (int i = 0; i < weights.length; i++)
            weights[i] = start[i] * (1 - blend) + (i == state.ordinal() ? blend : 0);
    }
    private static AnimState choose(LivingEntity e, boolean landed) {
        if (!e.isAlive()) return AnimState.DEATH;
        if (e.hurtTime > 0) return AnimState.HURT;
        if (e.isPassenger()) return AnimState.RIDING;
        if (e.isUsingItem()) return switch (e.getUseItem().getUseAnimation()) {
            case BOW -> AnimState.BOW_DRAW;
            case CROSSBOW -> AnimState.CROSSBOW;
            case BLOCK -> AnimState.SHIELD_BLOCK;
            default -> AnimState.USE_ITEM;
        };
        if (e.isSwinging()) return AnimState.ATTACK;
        if (e.isFallFlying()) return AnimState.ELYTRA;
        if (e.isInWater()) return e.getDeltaMovement().lengthSqr() > .002 ? AnimState.SWIM : AnimState.SWIM_IDLE;
        if (e.onClimbable()) return AnimState.CLIMB;
        if (landed) return AnimState.LAND;
        if (!e.onGround()) return e.getDeltaMovement().y > 0 ? AnimState.JUMP : AnimState.FALL;
        boolean moving = e.walkAnimation.speed() > .02;
        if (e.isCrouching()) return moving ? AnimState.SNEAK_WALK : AnimState.SNEAK;
        if (!moving) return AnimState.IDLE;
        return e.isSprinting() ? AnimState.SPRINT : AnimState.WALK;
    }
    public void sample(AnimationFrame out, float delta, LivingEntity entity) {
        float t = Math.max(0, Math.min(1, delta));
        for (int i = 0; i < weights.length; i++) out.weights[i] = AnimMath.lerp(previous[i], weights[i], t);
        out.headYaw = AnimMath.lerp(oldYaw, yaw, t); out.headPitch = AnimMath.lerp(oldPitch, pitch, t);
        out.landing = AnimMath.lerp(oldLanding, landing, t);
        out.phase = (seed & 0xFFFF) * (float) (Math.PI * 2 / 65536);
        var velocity = entity.getDeltaMovement();
        double angle = Math.toRadians(entity.getYRot());
        out.strafe = (float) (velocity.x * Math.cos(angle) + velocity.z * Math.sin(angle));
        out.active = true;
    }
    private static float wrap(float d) { return (d % 360 + 540) % 360 - 180; }
}
