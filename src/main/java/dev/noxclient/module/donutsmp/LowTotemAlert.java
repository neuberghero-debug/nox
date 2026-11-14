package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.gui.Toasts;
import net.minecraft.world.item.Items;

/** Toasts when your totem count drops below the comfort level. */
public class LowTotemAlert extends Module {
    public final Setting.Num minTotems = add(new Setting.Num("Min totems", 3, 1, 10));
    public final Setting.Bool sound = add(new Setting.Bool("Sound", true));
    private boolean warned;

    public LowTotemAlert() { super("LowTotemAlert", "Warns on low totem count", Category.DONUTSMP); }

    @Override public void onTick() {
        if (mc.player == null) return;
        int n = 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < 41; i++)
            if (inv.getItem(i).is(Items.TOTEM_OF_UNDYING)) n += inv.getItem(i).getCount();
        if (mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING))
            n += mc.player.getOffhandItem().getCount();
        if (n < minTotems.intVal() && !warned) {
            warned = true;
            Toasts.push("LOW TOTEMS: " + n, 0xFFFF5555);
            if (sound.on()) mc.player.playSound(
                net.minecraft.sounds.SoundEvents.ANVIL_LAND, 1f, 1.4f);
        } else if (n >= minTotems.intVal()) warned = false;
    }
}
