package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Sleeps through the night when a bed is in reach. */
public class AutoSleep extends Module {
    public final Setting.Bool onlyNight = add(new Setting.Bool("Only at night", true));
    private long used;

    public AutoSleep() { super("AutoSleep", "Clicks beds at night", Category.MISC); }

    @Override public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        if (onlyNight.on() && mc.level.getSkyDarken() < 4) return;
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) return;
        var bhr = (BlockHitResult) mc.hitResult;
        if (!mc.level.getBlockState(bhr.getBlockPos()).toString().contains("_bed")) return;
        if (System.currentTimeMillis() - used < 5000) return;
        if (!Legitifier.ready(this, 2, 0.4)) return;
        used = System.currentTimeMillis();
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bhr);
    }
}
