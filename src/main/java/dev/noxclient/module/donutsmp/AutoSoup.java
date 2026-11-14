package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

/**
 * AutoSoup — DonutSMP soup-PvP staple. Eats a stew bowl from the hotbar the
 * moment health drops below the threshold, then swaps back to the held slot.
 * Bowls only in the hotbar (that's where they are during a fight anyway).
 */
public class AutoSoup extends Module {
    public final Setting.Num hp = add(new Setting.Num("Eat below HP", 14, 4, 20));
    public final Setting.Bool swapBack = add(new Setting.Bool("Swap back", true));
    public final Setting.Bool onlyCombat = add(new Setting.Bool("Only in combat", false));

    private float lastHealth = -1;
    private long lastHurt;

    public AutoSoup() { super("AutoSoup", "Eats stew when HP drops", Category.DONUTSMP); }

    private static boolean isSoup(net.minecraft.world.item.ItemStack s) {
        return s.is(Items.MUSHROOM_STEW) || s.is(Items.BEETROOT_SOUP)
            || s.is(Items.RABBIT_STEW) || s.is(Items.SUSPICIOUS_STEW);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;
        float h = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (lastHealth > 0 && h < lastHealth) lastHurt = System.currentTimeMillis();
        lastHealth = h;
        if (h >= hp.get()) return;
        if (onlyCombat.on() && System.currentTimeMillis() - lastHurt > 8000) return;
        if (!Legitifier.ready(this, 2, 0.3)) return;
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (!isSoup(inv.getItem(i))) continue;
            int prev = inv.getSelectedSlot();
            inv.setSelectedSlot(i);
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            if (swapBack.on()) inv.setSelectedSlot(prev);
            return;
        }
    }
    @Override public String status() {
        if (mc.player == null) return "no player";
        int n = 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var st = inv.getItem(i);
            if (st.is(net.minecraft.world.item.Items.MUSHROOM_STEW)
                || st.is(net.minecraft.world.item.Items.BEETROOT_SOUP)) n += st.getCount();
        }
        return n == 0 ? "idle — no soup in inventory" : n + " soups left";
    }

}
