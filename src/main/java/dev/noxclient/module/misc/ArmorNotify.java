package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.gui.Toasts;

/** Toast when an armor piece breaks or leaves your body. */
public class ArmorNotify extends Module {
    private final String[] last = new String[4];

    public ArmorNotify() { super("ArmorNotify", "Alerts when armor breaks", Category.MISC); }

    @Override public void onTick() {
        if (mc.player == null) return;
        String[] names = {"boots", "leggings", "chestplate", "helmet"};
        var inv = mc.player.getInventory();
        for (int i = 0; i < 4; i++) {
            var st = inv.getItem(36 + i);
            String now = st.isEmpty() ? "" : st.getHoverName().getString();
            if (last[i] != null && !last[i].isEmpty() && now.isEmpty())
                Toasts.push(names[i] + " broke/unequipped!", 0xFFFF5555);
            last[i] = now;
        }
    }
}
