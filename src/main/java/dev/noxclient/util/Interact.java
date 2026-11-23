package dev.noxclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Grim-safe interaction helper.
 *
 * Grim validates block placements/uses against the rotation the server last
 * saw — a module that clicks BlockHitResult(center, UP, pos) without ever
 * rotating gets flagged by the Post/RotPlace checks. aim() rewrites the
 * rotation of the next outgoing move packet (zero extra packets via
 * MoveGuard), so the server-side ray matches our click. Reach guards keep
 * every interaction inside vanilla limits.
 */
public final class Interact {
    public static final double ATTACK_REACH = 3.05;
    public static final double PLACE_REACH = 4.5;

    /** Rotate the next pos packet to face a world point. Call BEFORE useItemOn. */
    public static void aim(Vec3 point) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 d = point.subtract(eye);
        double yaw = Math.toDegrees(Math.atan2(-d.x, d.z));
        double pitch = Math.toDegrees(-Math.atan2(d.y, Math.hypot(d.x, d.z)));
        MoveGuard.requestRot((float) yaw, (float) pitch);
    }

    /** Aim at a block's center. */
    public static void aim(BlockPos pos) { aim(Vec3.atCenterOf(pos)); }

    /** Aim at a block face (clicks the face center, not the block center —
        that's what a real crosshair would hit). */
    public static BlockHitResult placeOn(BlockPos support, Direction face) {
        Vec3 target = Vec3.atCenterOf(support)
            .add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        aim(target);
        return new BlockHitResult(target, face, support, false);
    }

    /** True when the support block is inside vanilla interaction reach. */
    public static boolean inReach(BlockPos pos) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos))
            <= PLACE_REACH * PLACE_REACH;
    }

    /** True when the entity is inside vanilla attack reach. */
    public static boolean inAttackReach(Entity e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return mc.player.distanceToSqr(e) <= ATTACK_REACH * ATTACK_REACH;
    }

    private Interact() {}
}
