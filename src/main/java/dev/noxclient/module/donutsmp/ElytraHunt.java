package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.BaseIntel;
import dev.noxclient.util.Legitifier;
import dev.noxclient.util.MoveGuard;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;

/**
 * ElytraHunt — auto-flies the elytra toward BaseFinder's hottest chunks.
 * Deploys the elytra when falling (vanilla START_FALL_FLYING), feeds
 * rockets like AutoRocket, and steers with silent rotation through
 * MoveGuard — every motion rides on the vanilla move packet.
 * With "Auto dig" it hands off to DigDown when hovering a hot entry.
 */
public class ElytraHunt extends Module {
    public final Setting.Num minConf = add(new Setting.Num("Min confidence %", 30, 5, 95));
    public final Setting.Num arriveDist = add(new Setting.Num("Arrive distance", 48, 16, 200));
    public final Setting.Num cruisePitch = add(new Setting.Num("Cruise pitch", -8, -30, 10));
    public final Setting.Bool rockets = add(new Setting.Bool("Auto rockets", true));
    public final Setting.Num rocketCps = add(new Setting.Num("Rockets/s", 1.2, 0.3, 4));
    public final Setting.Bool autoDig = add(new Setting.Bool("Auto dig at target", false,
        "Arm DigDown when hovering a ≥70% entry"));
    private long lastJump;

    public ElytraHunt() { super("ElytraHunt", "Flies and digs to search for bases", Category.DONUTSMP); }

    @Override public void onTick() {
        if (mc.level == null || mc.player == null || mc.gameMode == null) return;
        if (mc.player.tickCount < 60) return;
        boolean wearing = mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);

        // deploy when falling — vanilla path, rate-limited like ElytraGlide
        if (wearing && !mc.player.isFallFlying() && mc.player.fallDistance > 1.5
            && !mc.player.onGround() && System.currentTimeMillis() - lastJump > 600) {
            lastJump = System.currentTimeMillis();
            mc.getConnection().send(new ServerboundPlayerCommandPacket(
                mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
        }

        // target = hottest BaseIntel entry
        BaseIntel.Entry best = null;
        for (var e : BaseIntel.entries().values())
            if (e.pct >= minConf.intVal() && (best == null || e.pct > best.pct)) best = e;

        if (mc.player.isFallFlying()) {
            float yaw = mc.player.getYRot(), pitch = cruisePitch.get().floatValue();
            if (best != null) {
                double dx = best.cp.getMiddleBlockX() - mc.player.getX();
                double dz = best.cp.getMiddleBlockZ() - mc.player.getZ();
                double dist = Math.hypot(dx, dz);
                yaw = (float) (Math.atan2(-dx, dz) * 180 / Math.PI);
                if (dist < arriveDist.intVal()) {
                    // hover the spot — slow climb keeps us above it
                    pitch = -20f;
                    if (autoDig.on() && best.pct >= 70 && dist < 24) {
                        var dd = dev.noxclient.NoxClient.INSTANCE.modules.get("DigDown");
                        if (dd != null && !dd.isEnabled()) dd.restore(true);
                    }
                }
            }
            MoveGuard.requestRot(yaw, pitch);
            if (rockets.on()) fireRocket();
        }
    }

    private void fireRocket() {
        var inv = mc.player.getInventory();
        int slot = -1;
        for (int i = 0; i < 9; i++)
            if (inv.getItem(i).is(Items.FIREWORK_ROCKET)) { slot = i; break; }
        if (slot < 0) return;
        inv.setSelectedSlot(slot);
        if (Legitifier.ready(this, rocketCps.get(), 0.1)) {
            mc.getConnection().send(new net.minecraft.network.protocol.game
                .ServerboundSetCarriedItemPacket(slot));
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        }
    }

    @Override public String status() {
        BaseIntel.Entry best = null;
        for (var e : BaseIntel.entries().values())
            if (e.pct >= minConf.intVal() && (best == null || e.pct > best.pct)) best = e;
        return best == null ? "no target" : best.type + " " + best.pct + "%";
    }
}
