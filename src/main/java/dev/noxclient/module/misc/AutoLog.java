package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** Disconnects when health drops under the threshold — hardcore log-out. */
public class AutoLog extends Module {
    public final Setting.Num hp = add(new Setting.Num("Logout HP", 6, 2, 19));
    public final Setting.Bool onPlayerNear = add(new Setting.Bool("Also on player <12m", false));
    public final Setting.Bool totemsBypass = add(new Setting.Bool("Ignore if totem in offhand", true));
    private long cooldown;

    public AutoLog() { super("AutoLog", "Auto-disconnect at low HP", Category.MISC); }

    @Override public void onTick() {
        if (mc.player == null || mc.getConnection() == null) return;
        if (System.currentTimeMillis() - cooldown < 20000) return;
        if (totemsBypass.on()
            && mc.player.getOffhandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING))
            return;
        boolean low = mc.player.getHealth() <= hp.get();
        if (!low && onPlayerNear.on()) {
            for (var e : mc.level.entitiesForRendering())
                if (e instanceof net.minecraft.world.entity.player.Player p
                    && p != mc.player && !dev.noxclient.module.misc.Friends.isFriend(p)
                    && p.distanceToSqr(mc.player) < 144) { low = true; break; }
        }
        if (!low) return;
        cooldown = System.currentTimeMillis();
        mc.disconnectWithProgressScreen();
    }
}
