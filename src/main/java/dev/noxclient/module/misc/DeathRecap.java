package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;

/** Logs death coords + killer + damage source to chat so you can run back. */
public class DeathRecap extends Module {
    public final Setting.Bool coords = add(new Setting.Bool("Coordinates", true));
    public final Setting.Bool killer = add(new Setting.Bool("Killer info", true));
    public final Setting.Bool waypoint = add(new Setting.Bool("Auto waypoint", true));
    private boolean wasDead;

    public DeathRecap() { super("DeathRecap", "Recalls where you died", Category.MISC); }

    @Override public void onTick() {
        if (mc.player == null) return;
        boolean dead = !mc.player.isAlive();
        if (dead && !wasDead) {
            var p = mc.player.blockPosition();
            var sb = new StringBuilder("§c[NOX] ☠ Died");
            if (coords.on())
                sb.append(" at §f").append(p.getX()).append(' ').append(p.getY()).append(' ').append(p.getZ());
            DamageSource src = mc.player.getLastDamageSource();
            if (killer.on() && src != null) {
                if (src.getEntity() != null)
                    sb.append(" §7— killed by §c").append(src.getEntity().getName().getString());
                else
                    sb.append(" §7— §8").append(src.getLocalizedDeathMessage(mc.player).getString()
                        .replace(mc.player.getName().getString(), "").trim());
            }
            mc.player.sendSystemMessage(Component.literal(sb.toString()));
            if (waypoint.on()) {
                try {
                    var wp = dev.noxclient.NoxClient.INSTANCE.modules.get("DeathWaypoint");
                    if (wp != null) wp.restore(true);   // DeathWaypoint plants its own mark
                } catch (Throwable ignored) {}
            }
        }
        wasDead = dead;
    }
}
