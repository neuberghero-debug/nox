package dev.noxclient.module.combat;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** Selects the strongest melee weapon in your hotbar on target hover. */
public class AutoSword extends Module {
    public final Setting.Bool onlyOnTarget = add(new Setting.Bool("Only over enemy", true));
    private int prev = -1;

    public AutoSword() { super("AutoSword", "Picks best hotbar weapon", Category.COMBAT); }

    @Override public void onTick() {
        if (mc.player == null) return;
        boolean target = mc.crosshairPickEntity instanceof net.minecraft.world.entity.LivingEntity;
        if (onlyOnTarget.on() && !target) { restore(); return; }
        var inv = mc.player.getInventory();
        int best = -1; double bestD = 0;
        for (int i = 0; i < 9; i++) {
            var st = inv.getItem(i);
            double d = damage(st);
            if (d > bestD) { bestD = d; best = i; }
        }
        if (best >= 0 && best != inv.getSelectedSlot() && bestD > 4) {
            if (prev < 0) prev = inv.getSelectedSlot();
            inv.setSelectedSlot(best);
        }
    }

    private void restore() {
        if (prev >= 0 && mc.player != null) { mc.player.getInventory().setSelectedSlot(prev); prev = -1; }
    }

    private static double damage(net.minecraft.world.item.ItemStack st) {
        String n = st.getItem().toString();
        if (n.contains("mace")) return 12;
        if (n.contains("netherite_sword") || n.contains("netherite_axe")) return 10;
        if (n.contains("diamond_sword") || n.contains("diamond_axe")) return 9;
        if (n.contains("iron_sword") || n.contains("iron_axe")) return 7;
        if (n.contains("stone_sword")) return 5;
        return 0;
    }

    @Override public void onDisable() { restore(); }
}
