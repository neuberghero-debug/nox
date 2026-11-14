package dev.noxclient.module.combat;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import net.minecraft.world.entity.LivingEntity;

/**
 * Triggerbot — attacks ONLY when your real crosshair is already on an enemy.
 * Zero rotation packets, zero reach anomalies: indistinguishable from clicking.
 */
public class Triggerbot extends Module {
    private final Setting.Num cps = add(new Setting.Num("CPS", 11, 1, 20));
    private final Setting.Bool playersOnly = add(new Setting.Bool("Players only", true));
    private final Setting.Bool weaponOnly = add(new Setting.Bool("Weapon only", false));
    private final Setting.Num minCharge = add(new Setting.Num("Min charge", 0.95, 0.5, 1.0));

    public Triggerbot() { super("Triggerbot", "Clicks when crosshair is on target", Category.COMBAT); }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;
        var e = mc.crosshairPickEntity;
        if (!(e instanceof LivingEntity le) || !le.isAlive()) return;
        if (playersOnly.on() && !(e instanceof net.minecraft.world.entity.player.Player)) return;
        if (weaponOnly.on() && !mc.player.getMainHandItem().toString().contains("sword")
            && !mc.player.getMainHandItem().toString().contains("axe")
            && !mc.player.getMainHandItem().toString().contains("mace")) return;
        if (mc.player.getAttackStrengthScale(0) < minCharge.get()) return;
        if (!Legitifier.ready(this, cps.get(), 0.35)) return;
        mc.gameMode.attack(mc.player, e);
        mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND,
            net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
    }
}
