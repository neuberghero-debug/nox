package dev.noxclient.module.movement;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** Holds sneak — for bridging or hiding nametags. */
public class Sneak extends Module {
    public final Setting.Bool legit = add(new Setting.Bool("Legit (no sprint reset)", true));

    public Sneak() { super("Sneak", "Holds shift for you", Category.MOVEMENT); }

    @Override public void onTick() {
        mc.options.keyShift.setDown(true);
        if (legit.on() && mc.player != null) mc.player.setSprinting(false);
    }
    @Override public void onDisable() { mc.options.keyShift.setDown(false); }
}
