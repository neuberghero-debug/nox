package dev.noxclient.module.combat;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * AntiTrap — detects box traps (obsidian/cobweb/armor-stand cages) enclosing
 * you and mines the weakest block out using the best tool automatically.
 */
public class AntiTrap extends Module {
    private final Setting.Num reach = add(new Setting.Num("Escape reach", 4.5, 3, 6));

    public AntiTrap() { super("AntiTrap", "Escapes box traps (teleport = AC flag risk)", Category.COMBAT); premium = true; }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;
        BlockPos me = mc.player.blockPosition();
        int blocked = 0; BlockPos weakest = null; float weakHard = Float.MAX_VALUE;

        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos p = me.relative(d);
            var state = mc.level.getBlockState(p);
            if (state.isAir()) continue;
            var b = state.getBlock();
            if (b == Blocks.OBSIDIAN || b == Blocks.COBWEB || b == Blocks.ENDER_CHEST
                || b == Blocks.REINFORCED_DEEPSLATE || state.getDestroySpeed(mc.level, p) > 10) {
                blocked++;
            }
            float hard = state.getDestroySpeed(mc.level, p);
            if (hard >= 0 && hard < weakHard) { weakHard = hard; weakest = p; }
        }
        if (blocked >= 3 && weakest != null) {
            mc.gameMode.startDestroyBlock(weakest, Direction.UP);
            mc.gameMode.continueDestroyBlock(weakest, Direction.UP);
        }
    }
}
