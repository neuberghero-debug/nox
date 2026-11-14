package dev.noxclient.module.render;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client-side kill effects: particles + sound when a nearby player dies. */
public class KillEffects extends Module {
    public final Setting.Mode effect = add(new Setting.Mode("Effect", "Firework",
        "Firework", "Souls", "Nova", "Blood"));
    public final Setting.Bool sound = add(new Setting.Bool("Sound", true));
    private final Map<UUID, Float> hp = new HashMap<>();

    public KillEffects() { super("KillEffects", "Particles on nearby kills", Category.RENDER); }

    @Override public void onTick() {
        if (mc.player == null || mc.level == null) return;
        var now = new HashMap<UUID, Float>();
        for (var e : mc.level.entitiesForRendering())
            if (e instanceof Player p && p != mc.player && p.distanceToSqr(mc.player) < 2500)
                now.put(p.getUUID(), p.getHealth());
        for (var en : hp.entrySet()) {
            Float h = now.get(en.getKey());
            if (h != null && h > 0) continue; // alive
            if (en.getValue() <= 0) continue; // was already dead
            var e = find(en.getKey());
            double x, y2, z;
            if (e != null) { x = e.getX(); y2 = e.getY(); z = e.getZ(); }
            else continue;
            spawn(x, y2 + 1, z);
            if (sound.on()) mc.player.playSound(
                net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_BLAST, 1f, 0.8f);
        }
        hp.clear(); hp.putAll(now);
    }

    private net.minecraft.world.entity.Entity find(UUID id) {
        for (var e : mc.level.entitiesForRendering())
            if (e.getUUID().equals(id)) return e;
        return null;
    }

    private void spawn(double x, double y, double z) {
        var type = switch (effect.get()) {
            case "Souls" -> ParticleTypes.SOUL;
            case "Nova" -> ParticleTypes.END_ROD;
            case "Blood" -> ParticleTypes.DAMAGE_INDICATOR;
            default -> ParticleTypes.FIREWORK;
        };
        for (int i = 0; i < 30; i++)
            mc.level.addParticle(type, true, false,
                x + (Math.random() - 0.5), y + Math.random(),
                z + (Math.random() - 0.5), 0, 0.08, 0);
    }
}
