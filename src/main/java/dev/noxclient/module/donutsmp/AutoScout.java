package dev.noxclient.module.donutsmp;

import dev.noxclient.NoxClient;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.gui.Toasts;

import java.util.ArrayList;
import java.util.List;

/**
 * AutoScout — hands-free base hunting. Arms the whole finder stack
 * (SusChunkFinder, SoundFind, ActivityFind, EntityFind, StorageESP,
 * ArtificialLightFinder, ChunkFinder, StashLogger) and cycles /rtp on a
 * human-paced interval so each teleport lands somewhere new for the
 * finders to sweep.
 *   pauseOnHit — when a finder scores a hit near you (sus score ≥ threshold
 *     or a DEEP sound/activity mark), hopping pauses: you stay in the area
 *     while the signal is hot, and the scout resumes once it decays or you
 *     move on. The whole point of scouting is not to teleport past the base.
 * Settles after each RTP before the next one so chunks actually load.
 */
public class AutoScout extends Module {
    private final Setting.Num hopSec = add(new Setting.Num("RTP every (s)", 150, 45, 900,
        "Base delay — gaussian-jittered ±50% plus fatigue pauses"));
    private final Setting.Str rtpCmd = add(new Setting.Str("RTP command", "rtp"));
    private final Setting.Bool finders = add(new Setting.Bool("Arm finders", true,
        "Toggle the finder stack"));
    private final Setting.Bool pauseOnHit = add(new Setting.Bool("Pause on hit", true,
        "Hold the area when a sus chunk or deep mark is nearby"));
    private final Setting.Num hitScore = add(new Setting.Num("Hold score", 15, 4, 100,
        "SusChunkFinder score that pauses hopping"));
    private final Setting.Num holdRadius = add(new Setting.Num("Hold radius (ch)", 8, 1, 32,
        "How near a hit must be to hold"));

    private final List<Module> armed = new ArrayList<>();
    private long lastHop;
    private int hops;
    private boolean holding;
    private long holdToastAt;

    private static final String[] STACK = {
        "SusChunkFinder", "SoundFind", "ActivityFind", "EntityFind",
        "StorageESP", "ArtificialLightFinder", "ChunkFinder", "StashLogger"
    };

    public AutoScout() { super("AutoScout", "Automated base hunting — RTP + finder stack",
        Category.DONUTSMP); }

    @Override protected void onEnable() {
        armed.clear(); hops = 0; lastHop = 0; holding = false;
        if (finders.on()) {
            var mm = NoxClient.INSTANCE.modules;
            for (String n : STACK) {
                Module m = mm.get(n);
                if (m != null && !m.isEnabled()) { m.setEnabled(true); armed.add(m); }
            }
        }
        Toasts.push("AutoScout armed — " + armed.size() + " finders up", 0xFF66D9A0);
    }

    @Override public void onTick() {
        if (mc.player == null || mc.getConnection() == null) return;
        if (mc.player.tickCount < 60) return;

        if (pauseOnHit.on() && checkHold()) {
            if (!holding || System.currentTimeMillis() - holdToastAt > 60_000) {
                holding = true; holdToastAt = System.currentTimeMillis();
                Toasts.push("◈ AutoScout holding — hit nearby, scout the area", 0xFFFFB84D);
            }
            return;
        }
        holding = false;

        if (!dev.noxclient.util.Legitifier.ready(this, 1.0 / hopSec.get(), 0.5)) return;
        mc.getConnection().sendCommand(rtpCmd.get());
        lastHop = System.currentTimeMillis(); hops++;
        mark("rtp #" + hops);
    }

    /** True while a scored hit sits inside the hold radius. */
    private boolean checkHold() {
        var pc = mc.player.chunkPosition();
        var sus = NoxClient.INSTANCE.modules.get("SusChunkFinder");
        if (sus instanceof SusChunkFinder f) {
            double best = 0;
            int r = holdRadius.intVal();
            for (var e : f.scores().entrySet())
                if (Math.abs(e.getKey().x() - pc.x()) <= r
                    && Math.abs(e.getKey().z() - pc.z()) <= r)
                    best = Math.max(best, e.getValue());
            if (best >= hitScore.get()) return true;
        }
        // deep marks = active base below — hold even without a scored chunk
        double r2 = holdRadius.get() * 16 * holdRadius.get() * 16;
        for (MarkProbe m : probes()) if (m.deep()
            && m.dist2(mc.player) < r2) return true;
        return false;
    }

    private record MarkProbe(double x, double z, boolean deep) {
        double dist2(net.minecraft.client.player.LocalPlayer p) {
            double dx = x - p.getX(), dz = z - p.getZ();
            return dx * dx + dz * dz;
        }
    }

    private static List<MarkProbe> probes() {
        var out = new ArrayList<MarkProbe>();
        for (var m : SoundFind.MARKS.values()) out.add(new MarkProbe(m.x, m.z, m.deep));
        for (var m : ActivityFind.MARKS.values()) out.add(new MarkProbe(m.x, m.z, m.deep));
        return out;
    }

    @Override protected void onDisable() {
        for (Module m : armed) if (m.isEnabled()) m.setEnabled(false);
        armed.clear(); holding = false;
    }

    @Override public String status() {
        if (!isEnabled()) return "off";
        if (holding) return "HOLDING — hit nearby";
        return "hopping — " + hops + " rtps, "
            + (lastHop == 0 ? "first soon" : "last " + (System.currentTimeMillis() - lastHop) / 1000 + "s ago");
    }
}
