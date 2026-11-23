package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.BaseIntel;
import dev.noxclient.util.BaseSigs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Event Triangulator [PREMIUM] — the global-event coordinate exploit.
 *
 * Global level events (wither spawn, dragon death, end portal open) are
 * broadcast to EVERY client on the server — but the server doesn't send the
 * real position when it's outside your render distance: it sends a "proxy"
 * BlockPos offset roughly renderdistance*16 blocks from YOU in the direction
 * of the real event. The direction is real though.
 *
 * So each packet gives us a wedge: apex = where you stood, axis = direction
 * to the proxy. Two wedges from spots far enough apart intersect at the
 * event. RTP around, collect wedges, get a region. DonutSMP bases where
 * people spawn withers or open end portals = free coordinates.
 */
public class EventTriangulator extends Module {
    private final Setting.Bool chatNotify = add(new Setting.Bool("Chat notify", true));
    private final Setting.Num minApexDist = add(new Setting.Num("Min move dist", 48, 8, 256));
    private final Setting.Num halfAngleDeg = add(new Setting.Num("Wedge half-angle", 4, 1, 20));
    private final Setting.Num expireMin = add(new Setting.Num("Wedge expire min", 30, 5, 240));

    private final List<Wedge> wedges = new ArrayList<>();

    private record Wedge(double ax, double az, double dx, double dz, String what, long at) {}

    public EventTriangulator() {
        super("EventTriangulator", "Triangulates global events (wither/portal)", Category.DONUTSMP);
        premium = true;
    }

    @Override
    public void onPacket(Packet<?> packet) {
        if (!(packet instanceof ClientboundLevelEventPacket p) || mc.player == null) return;
        if (!p.isGlobalEvent()) return;
        String what = switch (p.getType()) {
            case LevelEvent.SOUND_WITHER_BOSS_SPAWN -> "wither spawn";
            case LevelEvent.SOUND_WITHER_BOSS_SHOOT -> "wither shoot";
            case LevelEvent.SOUND_DRAGON_DEATH -> "dragon death";
            case LevelEvent.SOUND_END_PORTAL_SPAWN -> "end portal spawn";
            case LevelEvent.SOUND_GHAST_WARNING -> "ghast warning";
            default -> null;
        };
        if (what == null) return;

        Vec3 me = mc.player.position();
        BlockPos proxy = p.getPos();
        Vec3 dir = new Vec3(proxy.getX() + 0.5 - me.x, 0, proxy.getZ() + 0.5 - me.z);
        if (dir.lengthSqr() < 1) return;
        dir = dir.normalize();

        // drop wedges taken from nearly the same spot — parallel rays don't cut
        double minD2 = minApexDist.get() * minApexDist.get();
        wedges.removeIf(w -> (me.x - w.ax) * (me.x - w.ax) + (me.z - w.az) * (me.z - w.az) < minD2
            && !w.what.equals(what));
        wedges.add(new Wedge(me.x, me.z, dir.x, dir.z, what, System.currentTimeMillis()));
        mark(what + " wedge #" + wedges.size());

        long cutoff = (long) (expireMin.get() * 60_000);
        wedges.removeIf(w -> System.currentTimeMillis() - w.at > cutoff);

        if (chatNotify.on()) {
            int same = (int) wedges.stream().filter(w -> w.what.equals(what)).count();
            BaseIntel.notify("wedge" + what,
                String.format("§d[NOX] §f%s wedge §7(dir %+.2f,%+.2f) — %d wedge%s%s",
                    what, dir.x, dir.z, same, same == 1 ? "" : "s",
                    same < 2 ? " — move/RTP for a second bearing" : ""));
        }
        triangulate(what);
    }

    /** Intersect the wedges for one event type → region estimate. */
    private void triangulate(String what) {
        var rays = wedges.stream().filter(w -> w.what.equals(what)).toList();
        if (rays.size() < 2) return;

        // closest approach point for each ray pair → average = estimate
        double sx = 0, sz = 0; int n = 0; double err = 0;
        for (int i = 0; i < rays.size(); i++) {
            for (int j = i + 1; j < rays.size(); j++) {
                var a = rays.get(i); var b = rays.get(j);
                // solve a + s*da = b + t*db  (2D)
                double det = a.dx * (-b.dz) - a.dz * (-b.dx);
                if (Math.abs(det) < 1e-6) continue; // parallel
                double ox = b.ax - a.ax, oz = b.az - a.az;
                double s = (ox * (-b.dz) - oz * (-b.dx)) / det;
                double t = (a.dx * oz - a.dz * ox) / det;
                if (s < 0 || t < 0) continue; // intersection behind a ray
                double px = a.ax + s * a.dx, pz = a.az + s * a.dz;
                // residual: how far the two rays actually missed each other
                double qx = b.ax + t * b.dx, qz = b.az + t * b.dz;
                err += Math.hypot(px - qx, pz - qz);
                sx += px; sz += pz; n++;
            }
        }
        if (n == 0) return;
        double ex = sx / n, ez = sz / n;
        int unc = (int) Math.max(16, err / n + 16);
        BlockPos est = BlockPos.containing(ex, mc.level.getSeaLevel(), ez);
        var h = BaseIntel.hit("tri:" + what, est, BaseSigs.Cat.TP.color,
            what + " region (±" + unc + "m)", 8);
        if (chatNotify.on() && h.count <= 2) {
            BaseIntel.notify("tri" + what,
                String.format("§d[NOX] §f%s ≈ §7%d %d §8±%dm", what, est.getX(), est.getZ(), unc));
        }
    }

    @Override protected void onDisable() { wedges.clear(); }
    @Override public String status() { return wedges.size() + " wedges"; }
}
