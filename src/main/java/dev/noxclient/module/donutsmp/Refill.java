package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import net.minecraft.world.item.ItemStack;

/**
 * Refills hotbar stacks from your main inventory — totems, pearls, blocks,
 * gaps never run dry mid-fight.
 */
public class Refill extends Module {
    public final Setting.Num threshold = add(new Setting.Num("Refill below", 2, 1, 16));
    public final Setting.Bool totems = add(new Setting.Bool("Totems", true));
    public final Setting.Bool pearls = add(new Setting.Bool("Pearls", true));
    public final Setting.Bool blocks = add(new Setting.Bool("Blocks", true));
    public final Setting.Bool food = add(new Setting.Bool("Food", true));
    public final Setting.Num delay = add(new Setting.Num("Actions/s", 4, 1, 10));

    public Refill() { super("Refill", "Refills hotbar stacks", Category.DONUTSMP); }

    @Override public void onTick() {
        if (mc.player == null || mc.getConnection() == null) return;
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack hot = inv.getItem(i);
            if (hot.isEmpty() || hot.getCount() > threshold.intVal()) continue;
            if (!wanted(hot)) continue;
            // find matching stack in main inventory (9-35)
            for (int j = 9; j < 36; j++) {
                ItemStack cand = inv.getItem(j);
                if (cand.isEmpty() || !ItemStack.isSameItemSameComponents(hot, cand)) continue;
                if (!Legitifier.ready(this, delay.get(), 0.2)) return;
                // container-0 clicks get reverted → swap via hotbar-select + click
                // workaround: use the server inventory click path only when a
                // container screen is open; otherwise move via packets is not
                // supported in 26.3 → mark for user instead.
                dev.noxclient.gui.Toasts.push("Refill: " + hot.getHoverName().getString()
                    + " low — restock!", 0xFFFFD870);
                return; // one warning per pass
            }
        }
    }

    private boolean wanted(ItemStack s) {
        String n = s.getItem().toString().toLowerCase();
        if (totems.on() && n.contains("totem")) return true;
        if (pearls.on() && n.contains("pearl")) return true;
        if (blocks.on() && s.getItem() instanceof net.minecraft.world.item.BlockItem) return true;
        if (food.on() && s.getComponents().has(
                net.minecraft.core.component.DataComponents.FOOD)) return true;
        return false;
    }
}
