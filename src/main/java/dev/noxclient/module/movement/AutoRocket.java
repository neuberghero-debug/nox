package dev.noxclient.module.movement;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

/** Auto-fires elytra rockets while gliding. */
public class AutoRocket extends Module {
    public final Setting.Num cps = add(new Setting.Num("Rockets/s", 1.5, 0.5, 5));
    public final Setting.Bool onlyElytra = add(new Setting.Bool("Only while gliding", true));
    public final Setting.Num minSpeed = add(new Setting.Num("Min speed", 0.8, 0, 3));
    private int slot = -1;

    public AutoRocket() { super("AutoRocket", "Elytra boost rockets", Category.MOVEMENT); }

    @Override public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        if (onlyElytra.on() && !mc.player.isFallFlying()) { slot = -1; return; }
        if (mc.player.getDeltaMovement().length() < minSpeed.get()) return;
        var inv = mc.player.getInventory();
        if (slot < 0 || !inv.getItem(slot).is(Items.FIREWORK_ROCKET)) {
            slot = -1;
            for (int i = 0; i < 9; i++)
                if (inv.getItem(i).is(Items.FIREWORK_ROCKET)) { slot = i; break; }
            if (slot < 0) return;
        }
        inv.setSelectedSlot(slot);
        if (Legitifier.ready(this, cps.get(), 0.1))
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
    }
}
