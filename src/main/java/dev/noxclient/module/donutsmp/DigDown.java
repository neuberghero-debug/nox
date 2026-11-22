package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import dev.noxclient.util.MoveGuard;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * Auto Dig Down — digs a shaft straight to a set Y level.
 * Vanilla destroy path: continueDestroyBlock EVERY tick on a locked target
 * (progress only accumulates while held — the old pacing gate made it look
 * like it never dug). Rotation rides MoveGuard; swing is paced for looks.
 * AC-safe: identical traffic to holding LMB.
 */
public class DigDown extends Module {
    public final Setting.Num targetY = add(new Setting.Num("Target Y", 8, -59, 200));
    public final Setting.Bool lookDown = add(new Setting.Bool("Look down", true));
    public final Setting.Bool autoOff = add(new Setting.Bool("Stop at target", true));
    private net.minecraft.core.BlockPos target;
    private long lastSwing;

    public DigDown() { super("DigDown", "Digs straight down to a set Y level", Category.DONUTSMP); }

    @Override protected void onDisable() { target = null; }

    @Override public void onTick() {
        if (mc.level == null || mc.player == null || mc.gameMode == null) return;
        if (mc.player.tickCount < 60) return;   // join grace
        int ty = targetY.intVal();
        if (mc.player.getBlockY() <= ty) { if (autoOff.on()) setEnabled(false); return; }
        if (lookDown.on()) MoveGuard.requestRot(mc.player.getYRot(), 85f);

        // lock a target until it breaks — recomputing below() mid-fall kept
        // resetting destroy progress on a different pos
        if (target == null) {
            var below = mc.player.blockPosition().below();
            var st = mc.level.getBlockState(below);
            if (st.isAir()) return;                       // mid-fall — wait for the landing
            if (st.is(Blocks.BEDROCK)) { if (autoOff.on()) setEnabled(false); return; }
            target = below.immutable();
            mc.gameMode.startDestroyBlock(target, Direction.UP);
        }

        var st = mc.level.getBlockState(target);
        if (st.isAir()) { target = null; return; }        // broke — lock the next block
        // bedrock or a block we can't reach from the new stance → drop it
        if (st.is(Blocks.BEDROCK)) { target = null; if (autoOff.on()) setEnabled(false); return; }
        mc.gameMode.continueDestroyBlock(target, Direction.UP);
        if (System.currentTimeMillis() - lastSwing > 350) {
            lastSwing = System.currentTimeMillis();
            mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND,
                net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        }
    }
}
