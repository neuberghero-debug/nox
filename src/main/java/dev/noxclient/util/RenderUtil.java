package dev.noxclient.util;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Helpers for world-space overlay rendering (boxes, lines, quads). */
public final class RenderUtil {
    public static Vec3 camPos() {
        return Minecraft.getInstance().gameRenderer.mainCamera().position();
    }

    /** Translate a world AABB into camera-relative space for PoseStack drawing. */
    public static AABB relative(AABB box) {
        Vec3 c = camPos();
        return box.move(-c.x, -c.y, -c.z);
    }
}
