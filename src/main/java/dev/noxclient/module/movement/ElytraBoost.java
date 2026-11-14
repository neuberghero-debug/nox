package dev.noxclient.module.movement;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

/** Auto-fires a rocket from your hotbar while gliding. */
public class ElytraBoost extends Module {
    public final Setting.Num cps = add(new Setting.Num("CPS", 1.5, 0.5, 5));
    private int prev = -1;

    public ElytraBoost() { super("ElytraBoost", "Auto-rockets while gliding", Category.MOVEMENT); }

    @Override public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        if (!mc.player.isFallFlying()) { prev = -1; return; }
        var inv = mc.player.getInventory();
        if (prev >= 0 && inv.getSelectedSlot() != prev
            && inv.getItem(inv.getSelectedSlot()).is(Items.FIREWORK_ROCKET)) return; // using
        for (int i = 0; i < 9; i++) {
            if (!inv.getItem(i).is(Items.FIREWORK_ROCKET)) continue;
            if (!Legitifier.ready(this, cps.get(), 0.3)) return;
            prev = inv.getSelectedSlot();
            inv.setSelectedSlot(i);
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            return;
        }
    }
}
