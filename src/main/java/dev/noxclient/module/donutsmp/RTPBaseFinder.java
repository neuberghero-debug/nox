package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

/**
 * RTP Base Finder [PREMIUM] — sends /rtp (or /wild), waits for the new area to
 * load, scores nearby chunks via the shared sus-chunk scanner, logs hits to the
 * stash log, then teleports again. Fully automatic base-hunt loop.
 */
public class RTPBaseFinder extends Module {
    private final Setting.Str command = add(new Setting.Str("RTP command", "/rtp"));
    private final Setting.Num waitSec = add(new Setting.Num("Scan seconds", 12, 5, 60));
    private final Setting.Num scoreMin = add(new Setting.Num("Min score", 6, 1, 50));
    private long nextAt;
    private boolean awaitingScan;

    public RTPBaseFinder() { super("RTPBaseFinder", "RTP + chunk scan loop", Category.DONUTSMP); premium = true; }

    @Override
    public void onTick() {
        if (mc.player == null || mc.getConnection() == null) return;
        long now = System.currentTimeMillis();
        if (now < nextAt) return;

        if (!awaitingScan) {
            mc.getConnection().sendCommand(command.get().replaceFirst("^/", ""));
            awaitingScan = true;
            nextAt = now + (long) (waitSec.get() * 1000);
            chat("§7[NOX] RTP sent — scanning in " + waitSec.intVal() + "s");
            return;
        }
        awaitingScan = false;
        nextAt = now + 3000;
        scanArea();
    }

    private void scanArea() {
        var sus = dev.noxclient.NoxClient.INSTANCE.modules.get("SusChunkFinder");
        if (sus instanceof SusChunkFinder f) {
            ChunkPos pc = mc.player.chunkPosition();
            var scores = f.scores();
            scores.forEach((cp, score) -> {
                if (score >= scoreMin.get() && Math.abs(cp.x() - pc.x()) < 20 && Math.abs(cp.z() - pc.z()) < 20) {
                    chat("§e[NOX] Sus chunk " + cp.x() + "," + cp.z() + " score=" + score.intValue());
                    dev.noxclient.util.StashLogger.log("rtp-scan",
                        new net.minecraft.core.BlockPos(cp.getMiddleBlockX(), 64, cp.getMiddleBlockZ()), "score=" + score);
                }
            });
        }
    }

    private void chat(String s) {
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(s));
    }
}
