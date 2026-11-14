package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Says gg when a nearby player dies — DonutSMP PvP etiquette. */
public class AutoGG extends Module {
    public final Setting.Str msg = add(new Setting.Str("Message", "gg"));
    public final Setting.Num range = add(new Setting.Num("Range", 32, 8, 96));
    public final Setting.Bool whisper = add(new Setting.Bool("Only on kills by me", false));
    private final Map<UUID, Float> hp = new HashMap<>();

    public AutoGG() { super("AutoGG", "Auto gg on nearby kills", Category.MISC); }

    @Override public void onTick() {
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return;
        var seen = new HashMap<UUID, Float>();
        double r2 = range.get() * range.get();
        for (var e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Player p) || p == mc.player) continue;
            if (p.distanceToSqr(mc.player) < r2) seen.put(p.getUUID(), p.getHealth());
        }
        for (var en : hp.entrySet()) {
            if (!seen.containsKey(en.getKey()) || seen.get(en.getKey()) <= 0) {
                if (en.getValue() > 0 && !msg.get().isEmpty())
                    mc.getConnection().sendChat(msg.get());
            }
        }
        hp.clear(); hp.putAll(seen);
    }
}
