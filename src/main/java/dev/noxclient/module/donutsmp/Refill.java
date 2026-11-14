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
    private long lastWarn;

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
                // container-0 inputs are only accepted while the player has an
                // inventory/container screen open (server reverts them
                // otherwise) — so refill is a restock-assist, and outside a
                // screen we warn once per stack instead of spamming.
                if (mc.gui.screen() == null) {
                    if (System.currentTimeMillis() - lastWarn > 8000) {
                        lastWarn = System.currentTimeMillis();
                        dev.noxclient.gui.Toasts.push("Refill: " + hot.getHoverName().getString()
                            + " low — restock!", 0xFFFFD870);
                    }
                    return;
                }
                if (!Legitifier.ready(this, delay.get(), 0.2)) return;
                // SWAP = the vanilla "hover slot, press hotkey N" path — swaps
                // inventory slot j with hotbar slot i in ONE container-0 input.
                if (mc.gameMode != null)
                    mc.gameMode.handleContainerInput(0, j, i,
                        net.minecraft.world.inventory.ContainerInput.SWAP, mc.player);
                return; // one move per pass
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
