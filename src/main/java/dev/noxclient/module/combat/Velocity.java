package dev.noxclient.module.combat;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/**
 * Velocity — scales incoming knockback. The packet mixin reads these settings;
 * 0% = full cancel, 100% = vanilla.
 */
public class Velocity extends Module {
    public final Setting.Num horizontal = add(new Setting.Num("Horizontal %", 0, 0, 100));
    public final Setting.Num vertical = add(new Setting.Num("Vertical %", 0, 0, 100));
    public final Setting.Num chance = add(new Setting.Num("Chance %", 100, 0, 100));

    public Velocity() { super("Velocity", "Reduces knockback", Category.COMBAT); }
}
