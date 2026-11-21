package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * PvP-Coach (Modul 6) — rule-based, no AI. Watches your hits and the
 * fight outcome, then posts a coaching toast: reach avg, hit-rate,
 * combo depth, damage balance → concrete drill advice.
 * Fight window = hits within 12s of each other, ends on death/10s idle.
 */
public class PvpCoach extends Module {
    public final Setting.Num minHits = add(new Setting.Num("Min hits/fight", 4, 2, 20));
    public final Setting.Bool chatTips = add(new Setting.Bool("Chat tips", false));

    private record Hit(long at, double reach, boolean sprint) {}
    private static final Deque<Hit> fight = new ArrayDeque<>();
    private static long lastHitAt;
    private static int swings, hits;
    private static boolean inFight;
    private boolean wasSwing;

    public PvpCoach() { super("PvpCoach", "Rule-based post-fight coaching tips", Category.MISC); premium = true; }

    /** AttackMixin path — call on every local-player hit. */
    public static void onHitEntity(Entity target) {
        if (!(target instanceof Player)) return;
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        hits++;
        if (!inFight) inFight = true;
        double reach = mc.player.distanceTo(target);
        fight.addLast(new Hit(System.currentTimeMillis(), reach, mc.player.isSprinting()));
        lastHitAt = System.currentTimeMillis();
        // NOTE: Stats.onHit is owned by AttackMixin — calling it here too
        // double-counted every hit
    }

    /** Call on swing (misses count for accuracy). */
    public static void onSwing() { swings++; }

    @Override
    public void onTick() {
        if (mc.player == null) { reset(); return; }
        // swing edge → accuracy denominator (hits AND misses both swing)
        boolean sw = mc.player.isSwinging();
        if (sw && !wasSwing) onSwing();
        wasSwing = sw;
        if (!inFight) return;
        boolean dead = mc.player.isDeadOrDying();
        boolean timedOut = System.currentTimeMillis() - lastHitAt > 10_000;
        if (!dead && !timedOut) return;
        evaluate(dead);
        reset();
    }

    private void evaluate(boolean died) {
        if (fight.size() < minHits.get()) return;
        double reachSum = 0, reachMax = 0;
        int combo = 1, bestCombo = 1;
        long prev = 0;
        for (Hit h : fight) {
            reachSum += h.reach(); reachMax = Math.max(reachMax, h.reach());
            combo = (prev > 0 && h.at() - prev < 1200) ? combo + 1 : 1;
            bestCombo = Math.max(bestCombo, combo);
            prev = h.at();
        }
        double avgReach = reachSum / fight.size();
        double acc = swings > 0 ? 100.0 * hits / swings : 100;

        java.util.List<String> tips = new java.util.ArrayList<>();
        if (avgReach > 2.8) tips.add("zu weit weg (" + String.format("%.1f", avgReach) + " Blöcke) — näher ran, ~0.2 kürzer");
        if (acc < 60) tips.add("Hit-Rate " + (int) acc + "% — Kamera unruhig, sens senken");
        if (bestCombo < 3) tips.add("Combos brechen bei " + bestCombo + " — W-Tap/Sprint-Reset Timing üben");
        if (died) tips.add("Fight verloren — vor Re-Engage: Totem/Gap prüfen, Health-Advantage abwarten");
        if (tips.isEmpty()) tips.add("sauberer Fight — Reach " + String.format("%.1f", avgReach) + ", Combo " + bestCombo);

        String msg = "PvP-Coach: " + String.join(" · ", tips);
        if (chatTips.on() && mc.player != null)
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§d" + msg));
        else
            dev.noxclient.gui.Toasts.push(msg, 0xFF7A9BFF);
    }

    private static void reset() {
        fight.clear(); inFight = false; swings = 0; hits = 0;
    }

    /** D.5 — analyze a .donutreplay instead of a live fight.
     *  Derives encounters from entity proximity (tag-1 players within 6
     *  blocks of the recorded player) plus hit/kill/damage event frames. */
    public static String analyzeReplay(java.nio.file.Path file) {
        try {
            var rp = dev.noxclient.replay.ReplayPlayer.load(file);
            int encounters = 0, maxGap = 0;
            long longest = 0, fightStart = -1, lastClose = -1;
            int hits = 0, kills = 0;
            for (var ev : rp.events()) {
                switch (ev.kind()) {
                    case "hit", "damage" -> hits++;
                    case "kill" -> kills++;
                }
            }
            // scan snapshots at 1s granularity for close-range player presence
            for (long ms = 0; ms <= rp.duration(); ms += 1000) {
                var ents = rp.sample(ms);
                var me = ents.stream().filter(e -> e.tag() == 1).findFirst().orElse(null);
                if (me == null) continue;
                boolean close = ents.stream().anyMatch(e -> e.tag() == 1 && e.id() != me.id()
                    && dist2(e, me) < 36);
                if (close) {
                    if (fightStart < 0) { fightStart = ms; encounters++; }
                    lastClose = ms;
                } else if (fightStart >= 0 && ms - lastClose > 5000) {
                    longest = Math.max(longest, lastClose - fightStart);
                    fightStart = -1;
                }
            }
            if (fightStart >= 0) longest = Math.max(longest, lastClose - fightStart);
            return String.format("replay %s: %d encounter(s), longest fight %ds, %d hit events, %d kills",
                file.getFileName(), encounters, longest / 1000, hits, kills);
        } catch (Exception e) {
            return "replay analysis failed: " + e.getMessage();
        }
    }

    private static float dist2(dev.noxclient.replay.ReplayPlayer.Ent a,
                               dev.noxclient.replay.ReplayPlayer.Ent b) {
        float dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
