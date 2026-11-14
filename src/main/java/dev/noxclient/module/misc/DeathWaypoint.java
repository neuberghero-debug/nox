package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.module.donutsmp.AntiESPBypass;
import net.minecraft.core.BlockPos;

/** Drops a bright ESP marker where you died — runs back to your loot. */
public class DeathWaypoint extends Module {
    public final Setting.Col color = add(new Setting.Col("Marker color", 0xFFFF3030));
    public final Setting.Num clearDist = add(new Setting.Num("Clear within (m)", 5, 2, 20));
    public final Setting.Bool stack = add(new Setting.Bool("Beam column", true));
    private boolean wasDead;
    private BlockPos lastDeath;
    private final java.util.List<BlockPos> beam = new java.util.ArrayList<>();

    public DeathWaypoint() { super("DeathWaypoint", "Marks your death spot", Category.MISC); }

    @Override public void onTick() {
        if (mc.player == null) return;
        boolean dead = !mc.player.isAlive();
        if (dead && !wasDead) {
            lastDeath = mc.player.blockPosition().immutable();
            AntiESPBypass.MARKS.put(lastDeath, color.get() | 0xFF000000);
            if (stack.on()) {
                beam.clear();
                for (int dy = 1; dy < 60; dy += 4) {
                    var bp = lastDeath.above(dy);
                    beam.add(bp); AntiESPBypass.MARKS.put(bp, (color.get() & 0xFFFFFF) | 0x66000000);
                }
            }
        }
        if (!dead && lastDeath != null
            && mc.player.blockPosition().distSqr(lastDeath) < clearDist.get() * clearDist.get()) {
            AntiESPBypass.MARKS.remove(lastDeath);
            for (var bp : beam) AntiESPBypass.MARKS.remove(bp);
            beam.clear(); lastDeath = null;
        }
        wasDead = dead;
    }

    @Override public void onDisable() {
        if (lastDeath != null) AntiESPBypass.MARKS.remove(lastDeath);
        for (var bp : beam) AntiESPBypass.MARKS.remove(bp);
        beam.clear();
    }
}
