package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashSet;
import java.util.Set;

/**
 * Flags "unnatural" chunks around the player: newly loaded chunks containing
 * player-made structures (chests, furnaces, claimed blocks, beacons) are logged
 * to a heatmap rendered in-world. Used for base hunting on DonutSMP.
 */
public class ChunkFinder extends Module {
    private final Setting.Num radius = add(new Setting.Num("Radius (chunks)", 8, 2, 32));
    private final Setting.Bool notify = add(new Setting.Bool("Chat notify", true));
    private final Set<ChunkPos> flagged = new HashSet<>();
    /** chunk -> last-scan ms — a done-forever set missed bases built/mined
     *  after the first pass and never rescaned re-entered chunks. */
    private final java.util.Map<ChunkPos, Long> seen = new java.util.HashMap<>();
    private static final long RESCAN_MS = 20_000;

    public ChunkFinder() { super("ChunkFinder", "Detects player-built chunks", Category.DONUTSMP); }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        ChunkPos pc = mc.player.chunkPosition();
        int r = radius.intVal();
        long now = System.currentTimeMillis();
        if (seen.size() > 4096)   // long AFK sessions — drop oldest area entries
            seen.keySet().removeIf(c -> Math.abs(c.x() - pc.x()) > r
                || Math.abs(c.z() - pc.z()) > r);
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            ChunkPos cp = new ChunkPos(pc.x() + dx, pc.z() + dz);
            Long t = seen.get(cp);
            if ((t != null && now - t < RESCAN_MS) || !mc.level.hasChunk(cp.x(), cp.z())) continue;
            seen.put(cp, now);
            LevelChunk chunk = mc.level.getChunk(cp.x(), cp.z());
            flagged.remove(cp);   // rescan can un-flag a torn-down base
            if (scan(chunk) && flagged.add(cp)) {
                if (notify.on() && mc.player != null)
                    mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§e[NOX] Suspicious chunk at " + cp.x() + ", " + cp.z()));
            }
            return; // one chunk-column per tick — full scans froze the game
        }
    }

    private boolean scan(LevelChunk c) {
        // every counted block has a block entity — the census is equivalent
        // to the old column scan but O(#blockEntities) instead of ~98k
        // getBlockState calls (a full column per tick stalled the client).
        // NOTE: no OBSIDIAN/END_PORTAL_FRAME/SPAWNER — ruined portals, lava
        // pools, strongholds and dungeons are all natural; spawner context
        // is SusChunkFinder's job.
        int hits = 0;
        for (var be : c.getBlockEntities().values()) {
            if (be instanceof net.minecraft.world.level.block.entity.BellBlockEntity)
                return false;   // village — packed with chests, permanent FP
            if (be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity
                || be instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity
                || be instanceof net.minecraft.world.level.block.entity.FurnaceBlockEntity
                || be instanceof net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity
                || be instanceof net.minecraft.world.level.block.entity.BeaconBlockEntity) {
                if (++hits >= 4) return true;
            }
        }
        return false;
    }

    public Set<ChunkPos> flagged() { return flagged; }
    @Override protected void onDisable() { flagged.clear(); seen.clear(); }
}
