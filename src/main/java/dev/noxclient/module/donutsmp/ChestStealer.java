package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;


/** Grabs items out of any open chest menu with legit pacing. */
public class ChestStealer extends Module {
    public final Setting.Num cps = add(new Setting.Num("CPS", 5, 1, 15));
    public final Setting.Bool close = add(new Setting.Bool("Close when done", false));
    public final Setting.Str filter = add(new Setting.Str("Filter (empty=all)", ""));

    public ChestStealer() { super("ChestStealer", "Loots open chests fast", Category.DONUTSMP); }

    @Override public void onTick() {
        if (mc.player == null || !(mc.gui.screen() instanceof AbstractContainerScreen<?> sc)) return;
        if (!Legitifier.ready(this, cps.get(), 0.25)) return;
        var menu = sc.getMenu();
        int invStart = menu.slots.size() - 36;
        String f = filter.get().toLowerCase();
        for (int i = 0; i < invStart; i++) {
            var st = menu.getSlot(i).getItem();
            if (st.isEmpty()) continue;
            if (!f.isEmpty() && !st.getHoverName().getString().toLowerCase().contains(f)
                && !st.getItem().toString().toLowerCase().contains(f)) continue;
            mc.gameMode.handleContainerInput(menu.containerId, i, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, mc.player);
            return;
        }
        if (close.on()) mc.player.closeContainer();
    }
}
