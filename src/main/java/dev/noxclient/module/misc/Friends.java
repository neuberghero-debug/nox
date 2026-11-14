package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Friend list — other modules read isFriend() for green ESP + quiet alerts. */
public class Friends extends Module {
    public final Setting.Str names = add(new Setting.Str("Friends (csv)", ""));
    private static final Set<String> LIST = new HashSet<>();

    public Friends() { super("Friends", "Friend list for ESP/alerts", Category.MISC); }

    public static boolean isFriend(Entity e) {
        return e instanceof Player p && LIST.contains(p.getName().getString().toLowerCase());
    }

    public static boolean isFriendName(String name) {
        return name != null && LIST.contains(name.toLowerCase());
    }

    @Override public void onTick() {
        LIST.clear();
        for (String n : names.get().split(","))
            if (!n.isBlank()) LIST.add(n.trim().toLowerCase());
    }
}
