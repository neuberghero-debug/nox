package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Keeps mining the block you look at — no click-holding. */
public class AutoMine extends Module {
    public final Setting.Bool onlyOre = add(new Setting.Bool("Only ores", false));

    public AutoMine() { super("AutoMine", "Mines the block you aim at", Category.DONUTSMP); }

    @Override public void onTick() {
        if (mc.player == null || mc.gameMode == null || mc.hitResult == null) return;
        if (mc.hitResult.getType() != HitResult.Type.BLOCK) return;
        var bhr = (BlockHitResult) mc.hitResult;
        if (onlyOre.on() && !mc.level.getBlockState(bhr.getBlockPos())
                .toString().toLowerCase().contains("_ore")) return;
        mc.gameMode.continueDestroyBlock(bhr.getBlockPos(), bhr.getDirection());
        mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
    }
}
