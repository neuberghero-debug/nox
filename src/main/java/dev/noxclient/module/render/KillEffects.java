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

    private void p(net.minecraft.core.particles.ParticleOptions t, double x, double y,
                   double z, double dx, double dy, double dz) {
        mc.level.addParticle(t, true, false, x, y, z, dx, dy, dz);
    }
    private static net.minecraft.core.particles.DustParticleOptions dust(int rgb, float s) {
        return new net.minecraft.core.particles.DustParticleOptions(rgb, s);
    }

    private void spawn(double x, double y, double z) {
        switch (effect.get()) {
            case "Souls" -> {
                // soul column spiralling up + cyan dust ring + soul flames
                for (int i = 0; i < 24; i++) {
                    double a = i / 24.0 * Math.PI * 2;
                    p(ParticleTypes.SOUL, x + Math.cos(a) * 0.5, y + 0.2,
                        z + Math.sin(a) * 0.5, Math.cos(a) * 0.06, 0.18 + Math.random() * 0.15, Math.sin(a) * 0.06);
                }
                for (int i = 0; i < 12; i++)
                    p(ParticleTypes.SOUL_FIRE_FLAME, x + rnd(0.8), y + Math.random() * 0.6,
                        z + rnd(0.8), 0, 0.12, 0);
                for (int i = 0; i < 16; i++) {
                    double a = i / 16.0 * Math.PI * 2;
                    p(dust(0x7BE3E3, 0.7f), x + Math.cos(a) * 0.7, y + 0.4, z + Math.sin(a) * 0.7,
                        Math.cos(a) * 0.12, 0.05, Math.sin(a) * 0.12);
                }
            }
            case "Nova" -> {
                // expanding white ring + vertical end-rod pillar + sparkle fallout
                for (int i = 0; i < 28; i++) {
                    double a = i / 28.0 * Math.PI * 2;
                    p(dust(0xF5F5F5, 0.9f), x + Math.cos(a) * 0.3, y + 0.9, z + Math.sin(a) * 0.3,
                        Math.cos(a) * 0.32, 0.02, Math.sin(a) * 0.32);
                }
                for (int i = 0; i < 12; i++)
                    p(ParticleTypes.END_ROD, x + rnd(0.2), y + 0.1 + i * 0.22, z + rnd(0.2),
                        0, 0.1, 0);
                for (int i = 0; i < 10; i++)
                    p(ParticleTypes.FIREWORK, x + rnd(1.4), y + 0.4 + Math.random(), z + rnd(1.4),
                        rnd(0.2), 0.1, rnd(0.2));
            }
            case "Blood" -> {
                // red dust burst + damage ticks + slow drip
                for (int i = 0; i < 26; i++) {
                    double a = Math.random() * Math.PI * 2;
                    double r = Math.random() * 0.6;
                    p(dust(0xB01212, 0.8f), x + Math.cos(a) * r, y + Math.random() * 0.8,
                        z + Math.sin(a) * r, Math.cos(a) * 0.15, 0.05, Math.sin(a) * 0.15);
                }
                for (int i = 0; i < 10; i++)
                    p(ParticleTypes.DAMAGE_INDICATOR, x + rnd(0.8), y + 0.3 + Math.random() * 0.7,
                        z + rnd(0.8), rnd(0.1), 0.08, rnd(0.1));
                for (int i = 0; i < 6; i++)
                    p(dust(0x6E0A0A, 0.55f), x + rnd(0.5), y + 0.05, z + rnd(0.5), 0, -0.02, 0);
            }
            default -> {   // Firework — white flash + multi-ring burst
                for (int i = 0; i < 20; i++) {
                    double a = i / 20.0 * Math.PI * 2;
                    p(dust(0xFFF2CC, 0.85f), x, y + 1.0, z,
                        Math.cos(a) * 0.28, 0.12, Math.sin(a) * 0.28);
                }
                for (int i = 0; i < 14; i++) {
                    double a = Math.random() * Math.PI * 2;
                    p(ParticleTypes.FIREWORK, x + Math.cos(a) * 0.3, y + 0.5 + Math.random(),
                        z + Math.sin(a) * 0.3, Math.cos(a) * 0.2, 0.15, Math.sin(a) * 0.2);
                }
                for (int i = 0; i < 8; i++)
                    p(ParticleTypes.END_ROD, x + rnd(0.3), y + i * 0.3, z + rnd(0.3), 0, 0.12, 0);
            }
        }
    }

    private static double rnd(double r) { return (Math.random() - 0.5) * r; }
}
