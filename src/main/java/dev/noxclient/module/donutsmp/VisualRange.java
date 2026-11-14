package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.misc.Friends;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.gui.Toasts;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Toasts when a player enters or leaves render distance — early warning. */
public class VisualRange extends Module {
    public final Setting.Bool toast = add(new Setting.Bool("Toast", true));
    public final Setting.Bool sound = add(new Setting.Bool("Sound", true));
    public final Setting.Bool friendsQuiet = add(new Setting.Bool("Ignore friends", false));
    private final Set<UUID> near = new HashSet<>();

    public VisualRange() { super("VisualRange", "Alerts on players in range", Category.DONUTSMP); }

    @Override public void onTick() {
        if (mc.player == null || mc.level == null) { near.clear(); return; }
        var now = new HashSet<UUID>();
        for (var e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Player p) || p == mc.player) continue;
            now.add(p.getUUID());
            if (!near.contains(p.getUUID())
                && !(friendsQuiet.on() && Friends.isFriend(p))) {
                String d = String.format("%.0f", p.distanceTo(mc.player));
                if (toast.on()) Toasts.push("⚠ " + p.getName().getString() + " (" + d + "m)", 0xFFFF6666);
                if (sound.on()) mc.player.playSound(
                    net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 0.6f);
            }
        }
        near.clear(); near.addAll(now);
    }
}
