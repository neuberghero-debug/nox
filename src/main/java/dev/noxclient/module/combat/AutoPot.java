package dev.noxclient.module.combat;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

/** Throws a splash health potion from the hotbar when health drops. */
public class AutoPot extends Module {
    public final Setting.Num hp = add(new Setting.Num("Health", 12, 4, 20));
    public final Setting.Num cps = add(new Setting.Num("CPS", 2, 1, 8));
    public final Setting.Bool back = add(new Setting.Bool("Swap back", true));
    private int prev = -1;

    public AutoPot() { super("AutoPot", "Splash-heals when low HP", Category.COMBAT); premium = true; }

    @Override public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        if (prev >= 0 && back.on() && mc.player.getHealth() > hp.get()) {
            mc.player.getInventory().setSelectedSlot(prev); prev = -1; return;
        }
        if (mc.player.getHealth() > hp.get() || !Legitifier.ready(this, cps.get(), 0.3)) return;
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            var s = inv.getItem(i);
            if (s.is(Items.SPLASH_POTION) || s.is(Items.LINGERING_POTION)) {
                prev = inv.getSelectedSlot();
                inv.setSelectedSlot(i);
                mc.player.setXRot(85f);
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                return;
            }
        }
    }
}
