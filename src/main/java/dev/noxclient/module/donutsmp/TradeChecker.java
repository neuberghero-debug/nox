package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import dev.noxclient.setting.Setting;
import net.minecraft.world.item.ItemStack;

/** Dumps trade-window contents to chat once per open — anti-scam check. */
public class TradeChecker extends Module {
    public final Setting.Bool onlyTrades = add(new Setting.Bool("Only trade GUIs", false));
    public final Setting.Num maxItems = add(new Setting.Num("Max items shown", 12, 3, 30));
    public final Setting.Bool totals = add(new Setting.Bool("Show totals", true));
    private Object lastMenu;

    public TradeChecker() { super("TradeChecker", "Lists items in trade GUIs", Category.DONUTSMP); }

    @Override public void onTick() {
        if (mc.player == null || !(mc.gui.screen() instanceof AbstractContainerScreen<?> sc)) {
            lastMenu = null; return;
        }
        var menu = sc.getMenu();
        if (menu == lastMenu) return;
        String title = sc.getTitle() == null ? "" : sc.getTitle().getString().toLowerCase();
        if (onlyTrades.on() && !title.contains("trade") && !title.contains("offer")) return;
        lastMenu = menu;
        StringBuilder sb = new StringBuilder("§e[NOX] Trade contains:");
        int n = 0, total = 0;
        for (int i = 0; i < Math.min(menu.slots.size(), 54); i++) {
            ItemStack st = menu.getSlot(i).getItem();
            if (st.isEmpty()) continue;
            total += st.getCount();
            sb.append(" | ").append(st.getHoverName().getString())
              .append(" x").append(st.getCount());
            if (++n > maxItems.intVal()) { sb.append(" …"); break; }
        }
        if (n == 0) return;
        if (totals.on()) sb.append("  §7(").append(n).append(" stacks, ")
            .append(total).append(" items)");
        mc.player.sendSystemMessage(Component.literal(sb.toString()));
    }
    @Override public String status() {
        return mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu
            ? "scanning trade window" : "idle — open a trade window";
    }

}
