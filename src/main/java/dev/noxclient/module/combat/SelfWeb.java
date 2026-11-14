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

/** Drops a cobweb in your own feet — slows chasers behind you. */
public class SelfWeb extends Module {
    public final Setting.Bool autoOff = add(new Setting.Bool("Disable when done", true));
    public final Setting.Bool onlyAir = add(new Setting.Bool("Only if feet empty", true));

    public SelfWeb() { super("SelfWeb", "Web in your feet", Category.COMBAT); }

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
            if (inv.getItem(i).is(Items.COBWEB)) { slot = i; break; }
        if (slot < 0) { if (autoOff.on()) setEnabled(false); return; }
        var support = feet.below();
        if (!mc.level.getBlockState(support).isSolid()) return;
        if (!Legitifier.ready(this, 8, 0.2)) return;
        inv.setSelectedSlot(slot);
        var hit = new BlockHitResult(Vec3.atCenterOf(support), Direction.UP, support, false);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
    }
}
