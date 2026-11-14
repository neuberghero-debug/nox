package dev.noxclient.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.noxclient.module.render.Cosmetics;
import dev.noxclient.physics.AnimMath;
import dev.noxclient.physics.ClothSim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Vector3f;

/**
 * Real cloth cape: a Verlet grid ({@link ClothSim}, 8x12 near / 6x8 far LOD)
 * simulated in body-local space and emitted as textured triangles through
 * the deferred pipeline. The vanilla CapeLayer is hidden for the local
 * player while this is on (CapeMixin checks physCapeActive()).
 *
 * Sim drives render, never the other way round — dt comes from frame time,
 * clamped and sub-stepped at a fixed 1/120s so behaviour is identical at
 * 40fps and 400fps.
 */
public class NoxCapeLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    private static final int COLS = 8, ROWS = 12;
    private static final float W_PX = 10f, H_PX = 16f;      // cape face, model px
    private static final float SUB = 1f / 120f;             // fixed sim step
    // cape texture face regions on a 64x32 cape.png
    private static final float FU0 = 1f / 64f, FU1 = 11f / 64f;
    private static final float FV0 = 1f / 32f, FV1 = 17f / 32f;
    private static final float BU0 = 12f / 64f, BU1 = 22f / 64f;

    private final ClothSim near = new ClothSim(COLS, ROWS, W_PX, H_PX);
    private final ClothSim far  = new ClothSim(6, 8, W_PX, H_PX);
    private ClothSim cloth = near;
    private double accum;
    private long lastNanos;
    private float lastBodyRot = Float.NaN;
    private double lastPX = Double.NaN, lastPY, lastPZ;
    private boolean first = true;

    public NoxCapeLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    private static boolean isLocal(AvatarRenderState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        PlayerSkin skin = state.skin, own = mc.player.getSkin();
        return skin != null && own != null
            && skin.body().texturePath().equals(own.body().texturePath());
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light,
            AvatarRenderState state, float limbAngle, float limbDistance) {
        if (!Cosmetics.physCapeActive() || state.isInvisible || !isLocal(state)) return;
        Identifier tex = Cosmetics.capeTexId();
        if (tex == null) return;

        step(state);

        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        pose.scale(0.0625f, -0.0625f, 0.0625f);   // -> px units, y up-positive
        final int l = light;
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(tex),
            (p, vc) -> emit(p, vc, l));
        pose.popPose();
    }

    // ---- simulation -------------------------------------------------------

    private void step(AvatarRenderState state) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0 : Math.min((now - lastNanos) / 1e9f, 0.1f);
        lastNanos = now;

        // LOD: far capes use the coarse grid + fewer constraint passes.
        float dist2 = (float) state.distanceToCameraSq;
        cloth = dist2 > 20 * 20 ? far : near;
        Cosmetics c = Cosmetics.INSTANCE;
        int flex = c != null ? (int) (1 + c.capeFlex.floatVal() * 3) : 4;
        cloth.iterations = dist2 > 12 * 12 ? Math.max(2, flex - 2) : flex + 1;
        cloth.drag = 0.988f - (c != null ? c.capeFlex.floatVal() : 0.5f) * 0.008f;

        float windAmt = c != null ? c.capeWind.floatVal() : 0.5f;
        float t = now / 1e9f;
        long seed = state.id * 31L;
        float bodyRot = state.bodyRot;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && first) {
            cloth.reset(0, 7.5f, 2.2f);
            lastPX = mc.player.getX(); lastPY = mc.player.getY(); lastPZ = mc.player.getZ();
            lastBodyRot = bodyRot;
            first = false;
        }

        if (dt > 0) {
            accum = Math.min(accum + dt, SUB * 8);
            while (accum >= SUB) {
                accum -= SUB;
                // wind: slow noise-driven gusts, rotated into the body frame
                float wa = AnimMath.noise2(seed, t * 0.35f, 0f) * 34f * windAmt;
                float wy = AnimMath.noise2(seed, 0f, t * 0.3f) * 9f * windAmt;
                float yaw = (float) Math.toRadians(-bodyRot);
                float cs = (float) Math.cos(yaw), sn = (float) Math.sin(yaw);
                float wx = wa * cs, wz = wa * sn;
                // motion drag: opposing apparent airflow + fall draft.
                // player speed is ~90 px/s at sprint → keep accel in the
                // same ballpark as gravity (~140 px/s^2), not orders above
                if (mc.player != null) {
                    var v = mc.player.getDeltaMovement();
                    float lvx = (float) (v.x * cs - v.z * sn) * -26f;
                    float lvz = (float) (v.x * sn + v.z * cs) * -26f;
                    wx += lvx; wz += lvz;
                    wy += (float) -v.y * 12f;
                }
                // clamp total accel so stacked gusts can't explode the cloth
                float a2 = wx * wx + wy * wy + wz * wz;
                if (a2 > 320f * 320f) {
                    float s = 320f / (float) Math.sqrt(a2);
                    wx *= s; wy *= s; wz *= s;
                }
                cloth.setWind(wx, wy, wz);
                cloth.step(SUB);
            }
        }

        // yaw-delta swing: carry ~88% of the turn, let the rest lag behind
        if (!Float.isNaN(lastBodyRot)) {
            float d = (float) Math.toRadians(bodyRot - lastBodyRot);
            if (d > Math.PI) d -= (float) (2 * Math.PI);
            if (d < -Math.PI) d += (float) (2 * Math.PI);
            cloth.swing(-d * 0.88f);
        }
        lastBodyRot = bodyRot;

        // teleport / respawn: if the player jumped a long way, re-drape
        if (mc.player != null) {
            double dx = mc.player.getX() - lastPX, dy = mc.player.getY() - lastPY,
                   dz = mc.player.getZ() - lastPZ;
            if (dx * dx + dy * dy + dz * dz > 16) cloth.reset(0, 7.5f, 2.2f);
            lastPX = mc.player.getX(); lastPY = mc.player.getY(); lastPZ = mc.player.getZ();
        }
    }

    // ---- geometry ---------------------------------------------------------

    private void emit(PoseStack.Pose p, com.mojang.blaze3d.vertex.VertexConsumer vc, int light) {
        Vector3f tmp = new Vector3f();
        for (int r = 0; r < cloth.rows - 1; r++) {
            for (int c = 0; c < cloth.cols - 1; c++) {
                float u0 = AnimMath.lerp(FU0, FU1, c / (float) (cloth.cols - 1));
                float u1 = AnimMath.lerp(FU0, FU1, (c + 1) / (float) (cloth.cols - 1));
                float v0 = AnimMath.lerp(FV0, FV1, r / (float) (cloth.rows - 1));
                float v1 = AnimMath.lerp(FV0, FV1, (r + 1) / (float) (cloth.rows - 1));
                quad(p, vc, light, r, c, u0, v0, u1, v1, true, tmp);
                // back face: mirrored UV region, reversed winding
                float bu0 = AnimMath.lerp(BU1, BU0, c / (float) (cloth.cols - 1));
                float bu1 = AnimMath.lerp(BU1, BU0, (c + 1) / (float) (cloth.cols - 1));
                quad(p, vc, light, r, c, bu0, v0, bu1, v1, false, tmp);
            }
        }
    }

    private void quad(PoseStack.Pose p, com.mojang.blaze3d.vertex.VertexConsumer vc,
            int light, int r, int c, float u0, float v0, float u1, float v1,
            boolean front, Vector3f tmp) {
        float x00 = cloth.x(r, c),     y00 = cloth.y(r, c),     z00 = cloth.z(r, c);
        float x10 = cloth.x(r, c + 1), y10 = cloth.y(r, c + 1), z10 = cloth.z(r, c + 1);
        float x11 = cloth.x(r + 1, c + 1), y11 = cloth.y(r + 1, c + 1), z11 = cloth.z(r + 1, c + 1);
        float x01 = cloth.x(r + 1, c), y01 = cloth.y(r + 1, c), z01 = cloth.z(r + 1, c);
        // face normal from the first triangle
        float e1x = x10 - x00, e1y = y10 - y00, e1z = z10 - z00;
        float e2x = x01 - x00, e2y = y01 - y00, e2z = z01 - z00;
        float nx = e1y * e2z - e1z * e2y, ny = e1z * e2x - e1x * e2z, nz = e1x * e2y - e1y * e2x;
        float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1e-6f) { nx = 0; ny = 0; nz = 1; } else { nx /= nl; ny /= nl; nz /= nl; }
        if (!front) { nx = -nx; ny = -ny; nz = -nz; }
        Vector3f n = new Vector3f(nx, ny, -nz);   // z negated with positions

        if (front) {
            vert(p, vc, light, tmp, n, x00, y00, z00, u0, v0);
            vert(p, vc, light, tmp, n, x01, y01, z01, u0, v1);
            vert(p, vc, light, tmp, n, x11, y11, z11, u1, v1);
            vert(p, vc, light, tmp, n, x00, y00, z00, u0, v0);
            vert(p, vc, light, tmp, n, x11, y11, z11, u1, v1);
            vert(p, vc, light, tmp, n, x10, y10, z10, u1, v0);
        } else {
            vert(p, vc, light, tmp, n, x00, y00, z00, u0, v0);
            vert(p, vc, light, tmp, n, x11, y11, z11, u1, v1);
            vert(p, vc, light, tmp, n, x01, y01, z01, u0, v1);
            vert(p, vc, light, tmp, n, x00, y00, z00, u0, v0);
            vert(p, vc, light, tmp, n, x10, y10, z10, u1, v0);
            vert(p, vc, light, tmp, n, x11, y11, z11, u1, v1);
        }
    }

    private void vert(PoseStack.Pose p, com.mojang.blaze3d.vertex.VertexConsumer vc,
            int light, Vector3f tmp, Vector3f n,
            float x, float y, float z, float u, float v) {
        // sim space is back-positive (+z = behind); model space agrees (+z = back)
        tmp.set(x, y, z + 0.8f); // +0.8 clears the torso back plane
        vc.addVertex(p, tmp)
            .setColor(1f, 1f, 1f, 1f)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(p, n);
    }
}
