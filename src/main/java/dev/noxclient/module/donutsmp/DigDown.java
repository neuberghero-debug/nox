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
 * Uses the vanilla destroy path (gameMode.continueDestroyBlock) paced by
 * Legitifier; looking down rides on vanilla rotation via MoveGuard —
 * no extra packets.
 */
public class DigDown extends Module {
    public final Setting.Num targetY = add(new Setting.Num("Target Y", 8, -59, 200));
    public final Setting.Num cps = add(new Setting.Num("Breaks/s", 3, 0.5, 10));
    public final Setting.Bool lookDown = add(new Setting.Bool("Look down", true));
    public final Setting.Bool autoOff = add(new Setting.Bool("Stop at target", true));
    private long lastSwing;

    public DigDown() { super("DigDown", "Digs straight down to a set Y level", Category.DONUTSMP); }

    @Override public void onTick() {
        if (mc.level == null || mc.player == null || mc.gameMode == null) return;
        if (mc.player.tickCount < 60) return;   // join grace
        int ty = targetY.intVal();
        if (mc.player.getBlockY() <= ty) { if (autoOff.on()) setEnabled(false); return; }
        var feet = mc.player.blockPosition();
        var below = feet.below();
        var st = mc.level.getBlockState(below);
        if (lookDown.on()) MoveGuard.requestRot(mc.player.getYRot(), 85f);
        if (!Legitifier.ready(this, cps.get(), 0.15)) return;
        if (st.isAir() || st.is(Blocks.BEDROCK)) {
            // bedrock floor / already at a cave — stop so we don't fall blindly
            if (st.is(Blocks.BEDROCK) && autoOff.on()) setEnabled(false);
            return;
        }
        mc.gameMode.startDestroyBlock(below, Direction.UP);
        mc.gameMode.continueDestroyBlock(below, Direction.UP);
        if (System.currentTimeMillis() - lastSwing > 350) {
            lastSwing = System.currentTimeMillis();
            mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND,
                net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        }
    }
}
