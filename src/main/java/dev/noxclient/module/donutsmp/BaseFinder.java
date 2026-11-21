package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.BaseIntel;
import net.minecraft.world.level.ChunkPos;

import java.util.HashSet;
import java.util.Set;

/**
 * BaseFinder — marks chunks with signs of player activity and lists the
 * hottest ones in a HUD panel (type, direction, distance, Y, section
 * coverage, confidence). Detection lives in util/BaseIntel so BaseESP,
 * ElytraHunt and AutoScout share the same evidence.
 */
public class BaseFinder extends Module {
    public final Setting.Num radius = add(new Setting.Num("Radius (chunks)", 12, 4, 48));
    public final Setting.Num perTick = add(new Setting.Num("Scan per tick", 2, 1, 8));
    public final Setting.Num minConf = add(new Setting.Num("Min confidence %", 15, 1, 95));
    public final Setting.Num maxRows = add(new Setting.Num("Panel rows", 5, 1, 10));
    public final Setting.Bool notify = add(new Setting.Bool("Chat notify", true,
        "Message when a new ≥70% chunk appears"));
    public final Setting.Bool showHud = add(new Setting.Bool("HUD panel", true));

    private final Set<ChunkPos> told = new HashSet<>();

    public BaseFinder() { super("BaseFinder", "Marks chunks with signs of player activity", Category.DONUTSMP); }

    @Override public void onTick() {
        if (mc.level == null || mc.player == null) { told.clear(); return; }
        BaseIntel.tick(mc, radius.intVal(), perTick.intVal());
        if (!notify.on()) return;
        for (var e : BaseIntel.entries().values())
            if (e.pct >= 70 && told.add(e.cp))
                mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§7[NOX] §d" + e.type + " §f" + e.cp.getMiddleBlockX() + " " + e.cp.getMiddleBlockZ()
                    + " §7conf §e" + e.pct + "%"));
        if (told.size() > 512) {   // long sessions — keep the closest half
            ChunkPos pc = mc.player.chunkPosition();
            told.removeIf(c -> Math.abs(c.x() - pc.x()) > radius.intVal() + 8
                || Math.abs(c.z() - pc.z()) > radius.intVal() + 8);
        }
    }

    @Override protected void onDisable() { BaseIntel.clear(); told.clear(); }

    @Override public String status() {
        return BaseIntel.entries().size() + " flagged";
    }
}
