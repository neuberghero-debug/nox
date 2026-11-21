package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Farm FPS Optimizer — Prestige-style lag reduction around farms:
 *  · hides dropped items, XP orbs, falling blocks, primed TNT (render cull)
 *  · hides explosion particles
 *  · mutes sounds that repeat too often ("sound spam" threshold)
 *  · skips piston animations — pushed blocks teleport to their end position
 *  · hides redstone dust / dust + components (levers, buttons, lamps stay)
 * All hooks live in dedicated mixins; this module answers their queries.
 */
public class FarmOptimizer extends Module {
    public final Setting.Bool items = add(new Setting.Bool("Items", true, "Hides dropped items"));
    public final Setting.Bool xp = add(new Setting.Bool("XP Orbs", true, "Hides experience orbs"));
    public final Setting.Bool falling = add(new Setting.Bool("Falling Blocks", true,
        "Hides falling sand, gravel and concrete powder"));
    public final Setting.Bool tnt = add(new Setting.Bool("TNT", true, "Hides activated TNT"));
    public final Setting.Bool explosions = add(new Setting.Bool("Explosions", true,
        "Hides explosion particles"));
    public final Setting.Bool sounds = add(new Setting.Bool("Sounds", true,
        "Mutes sounds that repeat too often. They play again after they stop repeating"));
    public final Setting.Num soundSpam = add(new Setting.Num("Sound Spam", 5, 1, 20,
        "How many repeats trigger muting. Lower = mute sooner"));
    public final Setting.Bool pistons = add(new Setting.Bool("Pistons", true,
        "Skips piston animations and moves pushed blocks straight to their new position"));
    public final Setting.Mode redstone = add(Setting.Mode.of("Redstone", "Off",
        "Hides redstone dust or dust + components. Levers, buttons and lamps stay visible",
        "Off", "Dust", "All"));

    private static final Map<String, ArrayDeque<Long>> SOUND_LOG = new HashMap<>();
    private static long lastPurge;

    public FarmOptimizer() { super("FarmOptimizer", "Reduces lag around farms", Category.DONUTSMP); }

    private static FarmOptimizer get() {
        var m = dev.noxclient.NoxClient.INSTANCE.modules.get("FarmOptimizer");
        return m instanceof FarmOptimizer f && f.isEnabled() ? f : null;
    }

    /** Entity render cull — called from EntityRenderDispatcher.shouldRender. */
    public static boolean cullEntity(Entity e) {
        var f = get(); if (f == null) return false;
        var t = e.getType();
        return (f.items.on() && t == EntityTypes.ITEM)
            || (f.xp.on() && t == EntityTypes.EXPERIENCE_ORB)
            || (f.falling.on() && t == EntityTypes.FALLING_BLOCK)
            || (f.tnt.on() && t == EntityTypes.TNT);
    }

    /** Particle cull — only explosion families per the Prestige feature. */
    public static boolean cullParticle(net.minecraft.core.particles.ParticleOptions po) {
        var f = get(); if (f == null || !f.explosions.on()) return false;
        var t = po.getType();
        return t == net.minecraft.core.particles.ParticleTypes.EXPLOSION
            || t == net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER
            || t == net.minecraft.core.particles.ParticleTypes.POOF;
    }

    /** Sound spam muting — >N repeats of the same sound id inside 1s → mute. */
    public static boolean muteSound(SoundInstance si) {
        var f = get(); if (f == null || !f.sounds.on()) return false;
        var id = si.getIdentifier();
        if (id == null) return false;
        String key = id.toString();
        long now = System.currentTimeMillis();
        if (now - lastPurge > 5000) { lastPurge = now; SOUND_LOG.values().removeIf(ArrayDeque::isEmpty); }
        var dq = SOUND_LOG.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!dq.isEmpty() && now - dq.peekFirst() > 1000) dq.pollFirst();
        if (dq.size() >= f.soundSpam.intVal()) return true;
        dq.addLast(now);
        return false;
    }

    public static boolean skipPistonAnim() {
        var f = get(); return f != null && f.pistons.on();
    }

    /** Redstone render-hide mode: 0 off, 1 dust, 2 dust+components. */
    public static int hideRedstone() {
        var f = get(); if (f == null) return 0;
        return "Dust".equals(f.redstone.get()) ? 1 : "All".equals(f.redstone.get()) ? 2 : 0;
    }

    /** Rebuild render sections so a mode change takes effect immediately. */
    private static void refreshSections() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.levelRenderer != null && mc.level != null && mc.gameRenderer != null)
            mc.execute(() -> mc.levelRenderer.invalidateCompiledGeometry(
                mc.level, mc.options, mc.gameRenderer.mainCamera(), mc.getBlockColors()));
    }

    private int lastRedstone = -1;
    @Override public void onTick() {
        int r = hideRedstone();
        if (r != lastRedstone) { lastRedstone = r; refreshSections(); }
    }

    @Override protected void onEnable() { lastRedstone = -1; }
    @Override protected void onDisable() { lastRedstone = -1; refreshSections(); }
}
