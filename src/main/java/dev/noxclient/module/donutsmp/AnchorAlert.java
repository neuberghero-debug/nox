package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.gui.Toasts;
import net.minecraft.core.BlockPos;

/** Toasts when respawn anchors / beds appear near you — PvP trap intel. */
public class AnchorAlert extends Module {
    public final Setting.Num range = add(new Setting.Num("Range", 32, 8, 96));
    private final java.util.Set<BlockPos> seen = new java.util.HashSet<>();
    private long next;

    public AnchorAlert() { super("AnchorAlert", "Warns of nearby anchors/beds", Category.DONUTSMP); }

    @Override public void onTick() {
        if (mc.player == null || mc.level == null || System.currentTimeMillis() < next) return;
        next = System.currentTimeMillis() + 1000;
        int r = range.intVal();
        var pp = mc.player.blockPosition();
        var it = BlockPos.betweenClosed(pp.offset(-r, -8, -r), pp.offset(r, 8, r)).iterator();
        while (it.hasNext()) {
            BlockPos p = it.next();
            String b = mc.level.getBlockState(p).getBlock().toString();
            boolean hit = b.contains("respawn_anchor") || b.contains("_bed");
            if (hit && seen.add(p.immutable())) {
                Toasts.push((b.contains("anchor") ? "Respawn anchor" : "Bed")
                    + " at " + p.getX() + " " + p.getY() + " " + p.getZ(), 0xFFFFAA00);
                mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 0.6f);
            }
        }
    }
}
