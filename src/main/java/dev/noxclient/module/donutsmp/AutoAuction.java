package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;

/** Lists hotbar items on /ah for a fixed price, one per cycle. */
public class AutoAuction extends Module {
    public final Setting.Num price = add(new Setting.Num("Price", 10000, 1, 100_000_000));
    public final Setting.Num interval = add(new Setting.Num("Interval s", 8, 2, 120));
    public final Setting.Str itemId = add(new Setting.Str("Item id contains", ""));

    public AutoAuction() { super("AutoAuction", "Auto-lists items on /ah", Category.DONUTSMP); }

    @Override public void onTick() {
        if (mc.player == null || mc.getConnection() == null) return;
        if (!Legitifier.ready(this, 1.0 / interval.get(), 0.4)) return;
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            var st = inv.getItem(i);
            if (st.isEmpty()) continue;
            if (!itemId.get().isEmpty()
                && !st.getItem().toString().toLowerCase().contains(itemId.get().toLowerCase()))
                continue;
            inv.setSelectedSlot(i);
            // carried-item packet first — setSelectedSlot alone only writes the
            // local field, so /ah sell would list the previously held item
            mc.getConnection().send(
                new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(i));
            mc.getConnection().sendCommand("ah sell " + (int) (double) price.get());
            return;
        }
    }
}
