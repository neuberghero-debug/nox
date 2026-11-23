package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.BaseIntel;
import dev.noxclient.util.Legitifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Reveal Probe [PREMIUM] — the ACTIVE anti-xray bypass. ⚠ sends packets.
 *
 * When the server gets an ABORT_DESTROY_BLOCK action for a position it
 * answers with a ClientboundBlockUpdatePacket carrying the TRUE block state —
 * anti-xray setups reveal what they hid to keep the client consistent. That
 * turns every other finder's mark into a confirmed answer: probe the walls
 * of a sealed room, the neighbors of a light leak or a block-entity position
 * and the real chest/redstone/portal comes back over the wire for
 * BlockLeakFinder to log.
 *
 * Paced to stay under suspicion thresholds: a small per-second budget with
 * jittered timing, each position probed once per session. Still an outbound
 * signature — leave it off if you don't need confirmation. Default OFF.
 */
public class RevealProbe extends Module {
    private final Setting.Num perSec = add(new Setting.Num("Probes/sec", 4, 1, 20));
    private final Setting.Bool autoSeed = add(new Setting.Bool("Probe near marks", true));
    private final Setting.Num seedRadius = add(new Setting.Num("Probe radius", 1, 1, 3));
    private final Setting.Bool chatNotify = add(new Setting.Bool("Chat notify", true));

    private final Deque<BlockPos> queue = new ArrayDeque<>();
    private final Set<BlockPos> done = new HashSet<>();
    private int budget;
    private long lastSeed;

    public RevealProbe() {
        super("RevealProbe", "Forces the server to reveal hidden blocks (RISKY)", Category.DONUTSMP);
        premium = true;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return;

        // top up the per-second budget (jittered so it doesn't tick exactly)
        if (budget <= 0) budget = perSec.intVal();
        if (queue.isEmpty() && autoSeed.on() && System.currentTimeMillis() - lastSeed > 3000) {
            lastSeed = System.currentTimeMillis();
            seed();
        }
        if (queue.isEmpty()) return;
        if (!Legitifier.ready(this, perSec.get(), 0.35)) return;

        BlockPos pos = queue.poll();
        if (pos == null || !done.add(pos)) return;
        if (!mc.level.hasChunkAt(pos)) return;
        // skip what we can already see clearly — probing air/stone-visible
        // blocks wastes budget
        var seen = mc.level.getBlockState(pos);
        if (seen.isAir()) return;

        // START+ABORT pair: looks exactly like a player tapping a block and
        // letting go — and both actions force the reveal on more anti-xray
        // versions than ABORT alone
        mc.getConnection().send(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, Direction.DOWN));
        mc.getConnection().send(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, pos, Direction.DOWN));
        budget--;
        mark("probe " + pos.toShortString());
    }

    /** Probe the block faces around every intel mark — that's where the
        disguised storage/redstone sits. */
    private void seed() {
        int r = seedRadius.intVal();
        int added = 0;
        for (var e : BaseIntel.hitsSnapshot()) {
            BlockPos c = e.getKey();
            if (!mc.level.hasChunkAt(c)) continue;
            for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++)
                for (int dz = -r; dz <= r; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos p = c.offset(dx, dy, dz);
                    if (done.contains(p)) continue;
                    queue.add(p);
                    if (++added > 256) return;
                }
        }
    }

    /** Manual probe — call from a DebugConsole command or addon. */
    public void probe(BlockPos pos) {
        if (!done.contains(pos)) queue.addFirst(pos);
    }

    @Override public String status() { return queue.size() + " queued · " + done.size() + " probed"; }
    @Override protected void onDisable() { queue.clear(); }
}
