package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Swaps to the fastest hotbar tool for the block you're mining, swaps back after. */
public class AutoTool extends Module {
    private final Setting.Bool swapBack = add(new Setting.Bool("Swap back", true));
    private int prevSlot = -1;

    public AutoTool() { super("AutoTool", "Best tool for the block", Category.DONUTSMP); }

    @Override
    public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        boolean mining = mc.options.keyAttack.isDown()
            && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK;
        if (!mining) {
            if (prevSlot >= 0 && swapBack.on()) {
                mc.player.getInventory().setSelectedSlot(prevSlot);
                prevSlot = -1;
            }
            return;
        }
        BlockHitResult hit = (BlockHitResult) mc.hitResult;
        BlockState state = mc.level.getBlockState(hit.getBlockPos());
        int best = bestSlot(state);
        if (best >= 0 && best != mc.player.getInventory().getSelectedSlot()) {
            if (prevSlot < 0) prevSlot = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(best);
        }
    }

    private int bestSlot(BlockState state) {
        int best = -1; float speed = 1f;
        for (int i = 0; i < 9; i++) {
            var st = mc.player.getInventory().getItem(i);
            float s = st.getDestroySpeed(state);
            if (s > speed) { speed = s; best = i; }
        }
        return best;
    }
}
