package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.network.chat.Component;

/** Auto-disconnect when HP drops under the threshold (combat log out). */
public class CombatLog extends Module {
    private final Setting.Num hp = add(new Setting.Num("Log below HP", 6, 1, 19));

    public CombatLog() { super("CombatLog", "Logs out at low HP", Category.MISC); }

    @Override
    public void onTick() {
        if (mc.player == null || mc.getConnection() == null) return;
        if (mc.player.getHealth() > hp.get()) return;
        setEnabled(false);
        mc.disconnectWithSavingScreen();
    }
}
