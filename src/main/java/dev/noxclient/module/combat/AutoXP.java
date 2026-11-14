package dev.noxclient.module.combat;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

/** Throws XP bottles from the hotbar — mending on demand. */
public class AutoXP extends Module {
    public final Setting.Num cps = add(new Setting.Num("CPS", 6, 1, 15));
    public final Setting.Bool lookDown = add(new Setting.Bool("Look down", true));
    private int prev = -1; private float prevPitch = 55;

    public AutoXP() { super("AutoXP", "Throws XP bottles", Category.COMBAT); }

    @Override public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        if (prev < 0) {
            var inv = mc.player.getInventory();
            for (int i = 0; i < 9; i++)
                if (inv.getItem(i).is(Items.EXPERIENCE_BOTTLE)) { prev = i; break; }
            if (prev < 0) { setEnabled(false); return; }
            prevPitch = mc.player.getXRot();
        }
        var inv = mc.player.getInventory();
        if (!inv.getItem(prev).is(Items.EXPERIENCE_BOTTLE)) { setEnabled(false); return; }
        inv.setSelectedSlot(prev);
        if (lookDown.on()) mc.player.setXRot(88f);
        if (Legitifier.ready(this, cps.get(), 0.2))
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
    }

    @Override public void onDisable() {
        if (mc.player != null) mc.player.setXRot(prevPitch);
        prev = -1;
    }
}
