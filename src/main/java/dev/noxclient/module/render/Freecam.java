package dev.noxclient.module.render;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.player.PlayerModelPart;

/** Camera detaches from your body — scout without moving. */
public class Freecam extends Module {
    public final Setting.Num speed = add(new Setting.Num("Speed", 1.0, 0.2, 5.0));
    private RemotePlayer cam;

    public Freecam() { super("Freecam", "Detached camera", Category.RENDER); }

    @Override public void onEnable() {
        if (mc.player == null || mc.level == null) { setEnabled(false); return; }
        cam = new RemotePlayer(mc.level, mc.player.getGameProfile());
        cam.setPos(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        cam.setYRot(mc.player.getYRot());
        cam.setXRot(mc.player.getXRot());
        mc.setCameraEntity(cam);
    }

    @Override public void onTick() {
        if (cam == null || mc.player == null) return;
        cam.setYRot(mc.player.getYRot());
        cam.setXRot(mc.player.getXRot());
        var mv = mc.player.input.getMoveVector();
        double s = speed.get() * 0.5;
        float yaw = (float) Math.toRadians(cam.getYRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = fz, rz = -fx;
        var d = cam.getDeltaMovement();
        cam.setDeltaMovement(
            (fx * mv.y + rx * mv.x) * s,
            mc.options.keyJump.isDown() ? s : mc.options.keyShift.isDown() ? -s : 0,
            (fz * mv.y + rz * mv.x) * s);
        cam.setPos(cam.getX() + cam.getDeltaMovement().x,
            cam.getY() + cam.getDeltaMovement().y,
            cam.getZ() + cam.getDeltaMovement().z);
    }

    @Override public void onDisable() {
        if (mc.player != null) mc.setCameraEntity(mc.player);
        cam = null;
    }
    @Override public String status() {
        return cam == null ? "no camera" : String.format("flying at %.1f %.1f %.1f",
            cam.getX(), cam.getY(), cam.getZ());
    }

}
