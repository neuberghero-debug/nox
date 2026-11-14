package dev.noxclient.module.combat;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Places obsidian inside your own feet block — burrow protection. */
public class Burrow extends Module {
    public final Setting.Bool autoOff = add(new Setting.Bool("Disable when done", true));
    public final Setting.Bool jumpOut = add(new Setting.Bool("Jump assist", true));

    public Burrow() { super("Burrow", "Obsidian in your feet", Category.COMBAT); }

    @Override public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        var feet = mc.player.blockPosition();
        var st = mc.level.getBlockState(feet);
        if (!st.isAir() && !st.canBeReplaced()) {
            if (autoOff.on()) setEnabled(false);
            return;
        }
        var inv = mc.player.getInventory();
        int slot = -1;
        for (int i = 0; i < 9; i++)
            if (inv.getItem(i).is(Items.OBSIDIAN)) { slot = i; break; }
        if (slot < 0) { if (autoOff.on()) setEnabled(false); return; }
        // need a solid support face: feet.below() works if solid
        var support = feet.below();
        if (!mc.level.getBlockState(support).isSolid()) return;
        if (!Legitifier.ready(this, 10, 0.1)) return;
        inv.setSelectedSlot(slot);
        if (jumpOut.on() && mc.player.onGround())
            mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, 0.42,
                mc.player.getDeltaMovement().z);
        var hit = new BlockHitResult(Vec3.atCenterOf(support), Direction.UP, support, false);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
    }
}
