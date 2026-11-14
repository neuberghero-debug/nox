package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Set;

/**
 * Anti-Vanish [PREMIUM] — flags players that render (entity packets arrive)
 * but are missing from the tab list = vanished staff watching you.
 */
public class AntiVanish extends Module {
    private final Setting.Bool alarm = add(new Setting.Bool("Alarm", true));
    private final Set<Integer> warned = new HashSet<>();

    public AntiVanish() { super("AntiVanish", "Detects vanished players", Category.DONUTSMP); premium = true; }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null || mc.getConnection() == null) return;
        Set<java.util.UUID> listed = new HashSet<>();
        for (var info : mc.getConnection().getListedOnlinePlayers())
            listed.add(info.getProfile().id());

        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof Player p && e != mc.player && !listed.contains(p.getUUID())
                && warned.add(e.getId())) {
                mc.player.sendSystemMessage(Component.literal(
                    "§c[NOX] VANISHED player nearby: " + p.getName().getString()));
                if (alarm.on()) mc.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL, 0.7f));
            }
        }
    }
}
