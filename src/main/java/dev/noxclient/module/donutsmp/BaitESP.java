package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.world.entity.EntityTypes;

/**
 * BaitESP — ported from WaterClient AntiTrap. Staff on DSMP plant armor stands,
 * minecarts and hopper minecarts as detection baits (and hopper minecarts
 * under a floor mean a working collection system = base below). Marking
 * them with a glow outline makes baits and hidden storage instantly visible.
 */
public class BaitESP extends Module {
    private final Setting.Bool stands = add(new Setting.Bool("Armor stands", true));
    private final Setting.Bool carts = add(new Setting.Bool("Minecarts", true));
    private final Setting.Bool chestCarts = add(new Setting.Bool("Chest minecarts", true));
    private final Setting.Bool hopperCarts = add(new Setting.Bool("Hopper minecarts", true));

    public BaitESP() { super("BaitESP", "Glowing outlines on bait/trap entities",
        Category.DONUTSMP); }

    @Override public void onTick() {
        if (mc.level == null || mc.player == null) return;
        for (var e : mc.level.entitiesForRendering()) {
            var t = e.getType();
            boolean hit = (stands.on() && t == EntityTypes.ARMOR_STAND)
                || (carts.on() && t == EntityTypes.MINECART)
                || (chestCarts.on() && t == EntityTypes.CHEST_MINECART)
                || (hopperCarts.on() && t == EntityTypes.HOPPER_MINECART);
            if (hit && !e.hasGlowingTag()) e.setGlowingTag(true);
        }
    }
}
