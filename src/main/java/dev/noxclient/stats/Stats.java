package dev.noxclient.stats;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client stats tracker (Modul 26.1). Local only — persisted by StatsStore,
 * surfaced by StatsScreen and the HUD stat widgets.
 *
 * Tracked: playtime, distance travelled, kills (entities you last-hit that
 * die), deaths, damage dealt/taken, blocks broken/placed, module usage
 * minutes. Money/shard deltas come from the existing SessionStats module.
 */
public final class Stats {
    private static long playTicks;
    private static double distance;
    private static int kills, deaths, blocksBroken, blocksPlaced;
    private static double dmgDealt, dmgTaken;
    private static double lastHealth = -1;
    private static double lx, ly, lz;
    private static boolean posInit;

    /** entityId → last time we hit it (ms). Kill credit = dies within 8s. */
    private static final Map<Integer, Long> lastHit = new ConcurrentHashMap<>();
    private static final Map<String, Long> moduleTicks = new ConcurrentHashMap<>();

    /** Rolling 60-sample ring (~30s at 2Hz) for live sparklines (fps). */
    private static final float[] ring = new float[60];
    private static int ringPos;

    public static void init() { StatsStore.load(); }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { posInit = false; return; }
        playTicks++;

        // distance
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        if (posInit && !mc.player.isDeadOrDying()) {
            double d = Math.sqrt((x - lx) * (x - lx) + (z - lz) * (z - lz));
            if (d < 8) distance += d;   // ignore teleports/lag spikes
        }
        lx = x; ly = y; lz = z; posInit = true;

        // damage taken
        double hp = mc.player.getHealth();
        if (lastHealth > 0 && hp < lastHealth && !mc.player.isCreative())
            dmgTaken += lastHealth - hp;
        lastHealth = hp;

        // live ring: fps sample every 10 ticks
        if (playTicks % 10 == 0)
            ring[ringPos++ % ring.length] = (float) mc.getFps();

        // module usage (every 20 ticks → +1 minute sample each)
        if (playTicks % 20 == 0) {
            for (var m : dev.noxclient.NoxClient.INSTANCE.modules.all())
                if (m.isEnabled()) moduleTicks.merge(m.name, 1L, Long::sum);
            if (playTicks % 600 == 0) StatsStore.saveAsync();
        }

        // credit kills: entity we hit in the last 8s just died
        if (playTicks % 5 == 0 && !lastHit.isEmpty()) {
            long now = System.currentTimeMillis();
            lastHit.entrySet().removeIf(e -> {
                if (now - e.getValue() > 8000) return true;
                Entity ent = mc.level.getEntity(e.getKey());
                if (ent != null && ent.isRemoved()) { kills++; StatsStore.bump("kills"); return true; }
                return false;
            });
        }
        if (mc.player.isDeadOrDying() && lastHealth > 0) { deaths++; StatsStore.bump("deaths"); lastHealth = 0; }
    }

    /** AttackMixin → called when the local player lands a hit. */
    public static void onHit(Entity target) {
        lastHit.put(target.getId(), System.currentTimeMillis());
        if (target instanceof Player) StatsStore.bump("player_hits");
    }

    public static void onBlockBroken() { blocksBroken++; StatsStore.bump("blocks_broken"); }
    public static void onBlockPlaced() { blocksPlaced++; StatsStore.bump("blocks_placed"); }
    public static void onDamageDealt(double amount) { dmgDealt += amount; }

    /* ---------- read model for HUD/screen ---------- */
    public static Map<String, String> sessionView() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Playtime", fmtTime(playTicks / 20));
        m.put("Distance", distance >= 1000 ? String.format("%.1f km", distance / 1000) : (int) distance + " m");
        m.put("Kills", String.valueOf(kills + StatsStore.lifetime("kills")));
        m.put("Deaths", String.valueOf(deaths + StatsStore.lifetime("deaths")));
        long k = kills + StatsStore.lifetime("kills"), d = deaths + StatsStore.lifetime("deaths");
        m.put("K/D", d == 0 ? String.valueOf(k) : String.format("%.2f", (double) k / d));
        m.put("Dmg dealt", String.format("%.0f", dmgDealt));
        m.put("Dmg taken", String.format("%.0f", dmgTaken));
        m.put("Blocks ±", blocksBroken + " / " + blocksPlaced);
        return m;
    }

    /** Top-N module usage in minutes (for pie chart). */
    public static Map<String, Double> topModules(int n) {
        var list = moduleTicks.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(n).toList();
        Map<String, Double> out = new LinkedHashMap<>();
        for (var e : list) out.put(e.getKey(), e.getValue() / 60.0);
        return out;
    }

    /** fps ring, oldest→newest (for sparkline drawing in StatsScreen/HUD). */
    public static float[] fpsRing() {
        float[] out = new float[ring.length];
        for (int i = 0; i < ring.length; i++) out[i] = ring[(ringPos + i) % ring.length];
        return out;
    }

    private static String fmtTime(long s) {
        return s >= 3600 ? s / 3600 + "h" + s / 60 % 60 + "m" : s / 60 + "m" + s % 60 + "s";
    }
}
