package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Reports stable snapshots of non-player slots; never clicks or confirms a trade. */
public class TradeChecker extends Module {
    public final Setting.Bool onlyTrades = add(new Setting.Bool("Only trade GUIs", true));
    public final Setting.Num maxItems = add(new Setting.Num("Max items shown", 12, 3, 30));
    public final Setting.Bool totals = add(new Setting.Bool("Show totals", true));
    private static final int STABLE_TICKS = 5;
    private AbstractContainerMenu lastMenu;
    private List<ItemStack> candidate;
    private List<ItemStack> lastReported;
    private int stableTicks;
    private boolean reported;
    private boolean shownNonEmpty;

    public TradeChecker() { super("TradeChecker", "Reports visible menu contents and changes before you trade", Category.DONUTSMP); }

    @Override protected void onDisable() { reset(); }

    @Override public void onTick() {
        if (mc.player == null || !(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
            reset();
            return;
        }
        String title = screen.getTitle().getString().toLowerCase(Locale.ROOT);
        if (onlyTrades.on() && !title.contains("trade") && !title.contains("offer")) {
            reset();
            return;
        }
        var menu = screen.getMenu();
        if (menu != lastMenu) {
            reset();
            lastMenu = menu;
        }
        var inventory = mc.player.getInventory();
        int index = 0;
        boolean same = candidate != null;
        for (var slot : menu.slots) {
            if (slot.container == inventory) continue;
            if (same && (index >= candidate.size() || !ItemStack.matches(slot.getItem(), candidate.get(index))))
                same = false;
            index++;
        }
        if (!same || index != candidate.size()) {
            candidate = new ArrayList<>(index);
            for (var slot : menu.slots) {
                if (slot.container != inventory) candidate.add(slot.getItem().copy());
            }
            stableTicks = 1;
            reported = false;
        } else if (!reported && ++stableTicks >= STABLE_TICKS) {
            reported = true;
            if (lastReported == null || !ItemStack.listMatches(lastReported, candidate)) {
                lastReported = candidate;
                if (report()) shownNonEmpty = true;
            }
        }
    }

    private boolean report() {
        int stacks = 0, total = 0;
        StringBuilder details = new StringBuilder();
        int limit = maxItems.intVal();
        for (var stack : candidate) {
            if (stack.isEmpty()) continue;
            total += stack.getCount();
            if (++stacks <= limit) {
                details.append(" | ").append(stack.getHoverName().getString())
                    .append(" x").append(stack.getCount());
            }
        }
        if (stacks == 0 && !shownNonEmpty) return false;
        // These are visible menu slots, not a verified separation of offers from plugin controls.
        StringBuilder message = new StringBuilder(shownNonEmpty
            ? "[NOX] Menu changed (check before accepting):" : "[NOX] Visible menu slots:");
        if (stacks == 0) message.append(" empty");
        else message.append(details);
        if (stacks > limit) message.append(" | +").append(stacks - limit).append(" more");
        if (totals.on()) message.append(" (").append(stacks).append(" stacks, ").append(total).append(" items)");
        mc.player.sendSystemMessage(Component.literal(message.toString()));
        return stacks > 0;
    }

    private void reset() {
        lastMenu = null;
        candidate = null;
        lastReported = null;
        stableTicks = 0;
        reported = false;
        shownNonEmpty = false;
    }

    @Override public String status() { return lastMenu == null ? "idle - open a trade window" : "watching menu slots"; }
}
