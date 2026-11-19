package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.gui.Toasts;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * AutoFlip — closes the snipe loop: when AHSniper buys an underpriced item,
 * this picks the same item from your inventory, holds it, and relists it via
 * your list command at the price-book minimum minus a small undercut.
 * Only relists when the book minimum still beats what we paid by Margin % —
 * if the book has no entry or the margin vanished, the item stays put.
 */
public class AutoFlip extends Module {
    private final Setting.Str listCmd = add(new Setting.Str("List command", "ah sell",
        "Run as '/<cmd> <price>' with the item in hand"));
    private final Setting.Num undercut = add(new Setting.Num("Undercut %", 3, 0, 30,
        "List this far under the book minimum for a fast sale"));
    private final Setting.Num margin = add(new Setting.Num("Margin %", 10, 0, 90,
        "Skip the relist unless book min beats paid by this much"));
    private final Setting.Num delay = add(new Setting.Num("Relist delay (s)", 6, 1, 60,
        "Grace period — buys land in the inventory a few ticks late"));
    private final Setting.Bool chatLog = add(new Setting.Bool("Chat log", true));

    private long lastFlip;

    public AutoFlip() { super("AutoFlip", "Relists sniped AH items at market price",
        Category.DONUTSMP); premium = true; }

    @Override public void onTick() {
        if (mc.player == null || mc.getConnection() == null) return;
        if (AHSniper.FLIP.isEmpty()) return;
        if (!dev.noxclient.util.Legitifier.ready(this, 1.0 / delay.get(), 0.5)) return;

        String[] s = AHSniper.FLIP.peek();
        if (s == null) return;
        double paid = Double.parseDouble(s[1]);
        Double ref = AutoStonks.min(s[0]);
        if (ref == null || ref < paid * (1 + margin.get() / 100)) {
            AHSniper.FLIP.poll();
            if (chatLog.on())
                mc.player.sendSystemMessage(Component.literal(
                    "§7[NOX] AutoFlip skip " + s[0] + (ref == null
                        ? " — no book entry" : " — margin gone")));
            return;
        }
        var inv = mc.player.getInventory();
        int slot = findStack(inv, s[0]);
        if (slot < 0) return;                             // still landing — retry next pace
        if (slot > 8) {
            AHSniper.FLIP.poll();
            if (chatLog.on())
                mc.player.sendSystemMessage(Component.literal(
                    "§7[NOX] AutoFlip: " + s[0] + " not in hotbar — move it down to relist"));
            return;
        }
        AHSniper.FLIP.poll();
        inv.setSelectedSlot(slot);
        long price = Math.max(1, (long) (ref * (1 - undercut.get() / 100)));
        mc.getConnection().sendCommand(listCmd.get() + " " + price);
        lastFlip = System.currentTimeMillis();
        Toasts.push("AutoFlip: " + s[0] + " → $" + price
            + " (paid $" + (long) paid + ")", 0xFF66D9A0);
    }

    private int findStack(net.minecraft.world.entity.player.Inventory inv, String name) {
        for (int i = 0; i < Math.min(inv.getContainerSize(), 36); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && st.getHoverName().getString().equals(name)) return i;
        }
        return -1;
    }

    @Override public String status() {
        return isEnabled() ? "watching snipe queue"
            + (lastFlip > 0 ? " — last relist " + (System.currentTimeMillis() - lastFlip) / 1000 + "s ago" : "")
            : "off";
    }
}
