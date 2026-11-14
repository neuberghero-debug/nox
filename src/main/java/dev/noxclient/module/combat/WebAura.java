package dev.noxclient.module.combat;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Drops a cobweb at the nearest enemy's feet. */
public class WebAura extends Module {
    public final Setting.Num range = add(new Setting.Num("Range", 4, 2, 6));
    public final Setting.Num cps = add(new Setting.Num("CPS", 6, 1, 15));
    private int slot = -1;

    public WebAura() { super("WebAura", "Cobweb at enemy feet", Category.COMBAT); }

    @Override public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        var inv = mc.player.getInventory();
        if (slot < 0 || !inv.getItem(slot).is(Items.COBWEB)) {
            slot = -1;
            for (int i = 0; i < 9; i++)
                if (inv.getItem(i).is(Items.COBWEB)) { slot = i; break; }
            if (slot < 0) return;
        }
        for (var e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Player p) || p == mc.player) continue;
            if (dev.noxclient.module.misc.Friends.isFriend(p)) continue;
            if (p.distanceToSqr(mc.player) > range.get() * range.get()) continue;
            var feet = p.blockPosition();
            var st = mc.level.getBlockState(feet);
            if (!st.isAir() && !st.canBeReplaced()) continue;
            var support = feet.below();
            if (!mc.level.getBlockState(support).isSolid()) continue;
            if (!Legitifier.ready(this, cps.get(), 0.3)) return;
            inv.setSelectedSlot(slot);
            var hit = new BlockHitResult(Vec3.atCenterOf(support), Direction.UP, support, false);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            return;
        }
    }
}
