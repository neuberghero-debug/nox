package dev.noxclient.module.combat;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * AutoMace [PREMIUM] — DonutSMP mace meta: fires a wind charge at your feet to
 * boost upward, then swaps to mace and smashes the nearest enemy on the way
 * down. Paced through Legitifier.
 */
public class AutoMace extends Module {
    private final Setting.Num cps = add(new Setting.Num("Combo CPS", 4, 1, 10));
    private int phase;

    public AutoMace() { super("AutoMace", "Wind-charge mace combo", Category.COMBAT); premium = true; }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return;
        Player target = nearest();
        if (target == null || !Legitifier.ready(this, cps.get(), 0.4)) return;

        switch (phase) {
            case 0 -> { // wind charge boost
                int w = find(Items.WIND_CHARGE);
                if (w < 0) { phase = 1; return; }
                mc.player.getInventory().setSelectedSlot(w);
                float oldPitch = mc.player.getXRot();
                mc.player.setXRot(90f);
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                mc.player.setXRot(oldPitch);   // don't go into the smash blind
                phase = 1;
            }
            case 1 -> { // wait for apex, swap to mace
                if (mc.player.getDeltaMovement().y < 0.15) {
                    int m = find(Items.MACE);
                    if (m >= 0) mc.player.getInventory().setSelectedSlot(m);
                    phase = 2;
                }
            }
            case 2 -> { // smash on the way down
                if (mc.player.getDeltaMovement().y < 0
                    && mc.player.distanceToSqr(target) < 16
                    && mc.player.getAttackStrengthScale(0) >= 1.0f) {
                    mc.gameMode.attack(mc.player, target);
                    mc.player.swing(InteractionHand.MAIN_HAND,
                        net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
                    phase = 0;
                }
            }
        }
    }

    private int find(net.minecraft.world.item.Item item) {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getItem(i).is(item)) return i;
        return -1;
    }

    private Player nearest() {
        Player best = null; double bd = 64;
        for (Entity e : mc.level.entitiesForRendering())
            if (e instanceof Player p && e != mc.player && p.isAlive()) {
                double d = mc.player.distanceToSqr(p);
                if (d < bd) { bd = d; best = p; }
            }
        return best;
    }
}
