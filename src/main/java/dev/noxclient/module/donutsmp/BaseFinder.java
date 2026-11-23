package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.BaseIntel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

/**
 * Base Finder — the orchestrator. Enabling it shows live ranked leads in
 * chat, disabling clears the shared intel store. All the individual bypasses
 * (SoundBaseFinder, ChunkBELeak, BlockLeakFinder, EntityLeakFinder,
 * RoomFinder, EventTriangulator, ArtificialLightFinder) feed the same store,
 * so one toggle tells you what the whole suite has heard.
 *
 * onTick prints the top-5 ranked chunks every interval while you hunt.
 */
public class BaseFinder extends Module {
    private final Setting.Num interval = add(new Setting.Num("Report s", 30, 5, 300));
    private final Setting.Num topN = add(new Setting.Num("Top chunks", 5, 1, 15));
    private final Setting.Num minScore = add(new Setting.Num("Min score", 4, 1, 100));
    private final Setting.Bool autoClear = add(new Setting.Bool("Clear on disable", true));
    private long last;

    public BaseFinder() {
        super("BaseFinder", "Ranked base leads from all intel sources", Category.DONUTSMP);
    }

    @Override
    public void onTick() {
        if (mc.player == null || System.currentTimeMillis() - last < interval.get() * 1000) return;
        last = System.currentTimeMillis();
        ChunkPos pc = mc.player.chunkPosition();
        var top = BaseIntel.topChunks(topN.intVal(), pc);
        top.removeIf(s -> {
            int i = s.indexOf("score=");
            return i < 0 || Integer.parseInt(s.substring(i + 6, s.indexOf(' ', i))) < minScore.intVal();
        });
        if (top.isEmpty()) return;
        mc.player.sendSystemMessage(Component.literal("§6[NOX] §ftop leads:"));
        for (String s : top)
            mc.player.sendSystemMessage(Component.literal("  §7" + s));
        mark("reported " + top.size() + " leads");
    }

    @Override
    protected void onDisable() {
        if (autoClear.on()) BaseIntel.clear();
    }

    @Override public String status() { return BaseIntel.size() + " intel marks"; }
}
