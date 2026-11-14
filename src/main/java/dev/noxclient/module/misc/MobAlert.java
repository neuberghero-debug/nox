package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.gui.Toasts;
import net.minecraft.world.entity.monster.Creeper;

/** Toasts when a creeper sneaks up behind you. */
public class MobAlert extends Module {
    public final Setting.Num range = add(new Setting.Num("Range", 8, 3, 20));
    private final java.util.Set<java.util.UUID> seen = new java.util.HashSet<>();

    public MobAlert() { super("MobAlert", "Warns of sneaking mobs", Category.MISC); }

    @Override public void onTick() {
        if (mc.player == null || mc.level == null) { seen.clear(); return; }
        double r2 = range.get() * range.get();
        for (var e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Creeper c)) continue;
            if (c.distanceToSqr(mc.player) < r2 && seen.add(c.getUUID())) {
                Toasts.push("Creeper! " + (int) Math.sqrt(c.distanceToSqr(mc.player)) + "m", 0xFF66FF66);
                mc.player.playSound(net.minecraft.sounds.SoundEvents.CREEPER_PRIMED, 0.7f, 1f);
            }
        }
        // ids of creepers that left range stay blocked for this session — fine
    }
}
