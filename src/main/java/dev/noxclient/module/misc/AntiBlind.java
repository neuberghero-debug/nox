package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.world.effect.MobEffects;

/** Removes blindness + darkness + nausea effects client-side. */
public class AntiBlind extends Module {
    public final Setting.Bool darkness = add(new Setting.Bool("Darkness too", true));
    public final Setting.Bool nausea = add(new Setting.Bool("Nausea too", false));

    public AntiBlind() { super("AntiBlind", "Clears blindness effects", Category.MISC); }

    @Override public void onTick() {
        if (mc.player == null) return;
        mc.player.removeEffect(MobEffects.BLINDNESS);
        if (darkness.on()) mc.player.removeEffect(MobEffects.DARKNESS);
        if (nausea.on()) mc.player.removeEffect(MobEffects.NAUSEA);
    }
}
