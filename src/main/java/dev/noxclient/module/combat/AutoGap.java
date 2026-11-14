package dev.noxclient.module.combat;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

/** Eats a golden apple from the hotbar when health drops. */
public class AutoGap extends Module {
    public final Setting.Num hp = add(new Setting.Num("Health", 14, 4, 20));
    public final Setting.Num cps = add(new Setting.Num("CPS", 2, 1, 8));
    public final Setting.Bool enchanted = add(new Setting.Bool("Enchanted gaps too", false));
    private int prev = -1;

    public AutoGap() { super("AutoGap", "Eats gaps when low HP", Category.COMBAT); }

    @Override public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        if (prev >= 0) {
            if (mc.player.isUsingItem()) return;
            mc.player.getInventory().setSelectedSlot(prev); prev = -1;
        }
        if (mc.player.getHealth() > hp.get()) return;
        if (!Legitifier.ready(this, cps.get(), 0.3)) return;
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            var st = inv.getItem(i);
            boolean ok = st.is(Items.GOLDEN_APPLE)
                || (enchanted.on() && st.is(Items.ENCHANTED_GOLDEN_APPLE));
            if (!ok) continue;
            prev = inv.getSelectedSlot();
            inv.setSelectedSlot(i);
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            return;
        }
    }
}
