package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ancient debris finder (seedless, complements OreSim):
 *  · Confirmed — every ANCIENT_DEBRIS block actually present in a loaded
 *    chunk gets a green through-wall mark + a one-time coord toast.
 *  · Candidate areas — 16³ sections inside the debris band (Y 8–22) whose
 *    cave walls could hide debris: sections that contain BOTH air and
 *    netherrack/basalt/blackstone adjacent to each other. Orange marks at
 *    the section center — "possible debris in outlined areas".
 * Scans a few chunks per tick with a rescan TTL, same pattern as the
 * other finders.
 */
public class DebrisFinder extends Module {
    private final Setting.Num radius = add(new Setting.Num("Radius (chunks)", 6, 1, 16));
    private final Setting.Num perTick = add(new Setting.Num("Scan per tick", 2, 1, 8));
    private final Setting.Num maxAreas = add(new Setting.Num("Max areas", 320, 16, 1200));
    private final Setting.Bool notify = add(new Setting.Bool("Chat notify", true));
    private final Setting.Num yMin = add(new Setting.Num("Y min", 8, 1, 60));
    private final Setting.Num yMax = add(new Setting.Num("Y max", 22, 8, 120));

    /** confirmed debris pos -> color (green), read by the HUD overlay */
    public static final Map<BlockPos, Integer> HITS = new ConcurrentHashMap<>();
    /** candidate-area section-center pos -> color (orange) */
    public static final Map<BlockPos, Integer> AREAS = new ConcurrentHashMap<>();
    private final Map<ChunkPos, Long> scanned = new HashMap<>();
    private final Set<BlockPos> told = new HashSet<>();
    private static final long RESCAN_MS = 20_000;
    private long pruneAt;

    public DebrisFinder() { super("DebrisFinder", "Ancient debris areas + confirmed blocks", Category.DONUTSMP); }

    public int areas() { return AREAS.size(); }
    public int blocks() { return HITS.size(); }

    @Override public void onTick() {
        if (mc.level == null || mc.player == null
            || !mc.level.dimension().toString().contains("nether")) {
            if (!HITS.isEmpty() || !AREAS.isEmpty()) { HITS.clear(); AREAS.clear(); scanned.clear(); }
            return;
        }
        ChunkPos pc = mc.player.chunkPosition();
        int r = radius.intVal();
        long now = System.currentTimeMillis();
        if (now - pruneAt > 3000) {
            pruneAt = now;
            int lim = r + 1;
            HITS.keySet().removeIf(p -> Math.abs((p.getX() >> 4) - pc.x()) > lim
                || Math.abs((p.getZ() >> 4) - pc.z()) > lim);
            AREAS.keySet().removeIf(p -> Math.abs((p.getX() >> 4) - pc.x()) > lim
                || Math.abs((p.getZ() >> 4) - pc.z()) > lim);
            scanned.keySet().removeIf(c -> Math.abs(c.x() - pc.x()) > lim
                || Math.abs(c.z() - pc.z()) > lim);
            while (AREAS.size() > maxAreas.intVal()) {
                var it = AREAS.keySet().iterator();
                if (!it.hasNext()) break; it.next(); it.remove();
            }
        }
        int n = 0;
        for (int dx = -r; dx <= r && n < perTick.intVal(); dx++)
            for (int dz = -r; dz <= r && n < perTick.intVal(); dz++) {
                ChunkPos cp = new ChunkPos(pc.x() + dx, pc.z() + dz);
                Long t = scanned.get(cp);
                if ((t != null && now - t < RESCAN_MS) || !mc.level.hasChunk(cp.x(), cp.z())) continue;
                scanned.put(cp, now);
                n++;
                AREAS.keySet().removeIf(p -> (p.getX() >> 4) == cp.x() && (p.getZ() >> 4) == cp.z());
                scan(mc.level.getChunk(cp.x(), cp.z()));
            }
    }

    private void scan(net.minecraft.world.level.chunk.LevelChunk chunk) {
        int y0 = yMin.intVal(), y1 = yMax.intVal();
        ChunkPos cp = chunk.getPos();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        // confirmed debris: direct block census of the debris band
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++)
            for (int y = y0; y <= y1; y++) {
                p.set(cp.getMinBlockX() + x, y, cp.getMinBlockZ() + z);
                if (chunk.getBlockState(p).is(Blocks.ANCIENT_DEBRIS)) {
                    var ip = p.immutable();
                    HITS.put(ip, 0xFF55DD66);
                    if (notify.on() && mc.player != null && told.add(ip))
                        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§7[NOX] §6Ancient debris §ffound at " + ip.getX() + ", " + ip.getY() + ", " + ip.getZ()));
                }
            }
        // candidate areas: 16³ volumes in the band where cave walls meet
        // solid nether rock — debris shows on those walls
        for (int sy = y0; sy <= y1; sy += 16) {
            int ey = Math.min(sy + 16, y1 + 1);
            boolean air = false, rock = false;
            outer:
            for (int x = 0; x < 16; x += 2) for (int z = 0; z < 16; z += 2)
                for (int y = sy; y < ey; y += 2) {
                    p.set(cp.getMinBlockX() + x, y, cp.getMinBlockZ() + z);
                    var b = chunk.getBlockState(p).getBlock();
                    if (b == Blocks.AIR || b == Blocks.CAVE_AIR || b == Blocks.VOID_AIR) air = true;
                    else if (b == Blocks.NETHERRACK || b == Blocks.BASALT || b == Blocks.BLACKSTONE) rock = true;
                    if (air && rock) break outer;
                }
            if (air && rock)
                AREAS.put(new BlockPos(cp.getMiddleBlockX(), (sy + ey) / 2, cp.getMiddleBlockZ()),
                    0x66FFAA33);
        }
    }

    @Override protected void onDisable() { HITS.clear(); AREAS.clear(); scanned.clear(); told.clear(); }
    @Override public String status() { return AREAS.size() + " areas / " + HITS.size() + " blocks"; }
}
