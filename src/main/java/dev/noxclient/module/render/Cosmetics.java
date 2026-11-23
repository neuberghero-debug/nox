package dev.noxclient.module.render;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.premium.LicenseManager;
import dev.noxclient.setting.Setting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * NOX cosmetics (client-side only): particle trails, NOX cape flag.
 * Premium tiers unlock the exotic trails — cosmetic, zero gameplay effect.
 */
public class Cosmetics extends Module {
    public final Setting.Mode trail = add(new Setting.Mode("Trail", "Flame",
        "Off", "Flame", "Hearts", "Portal", "Cherry", "Notes", "Crits",
        "Soul", "Glow", "Witch", "Void", "Galaxy", "Rainbow"));
    public final Setting.Num density = add(new Setting.Num("Density", 1, 1, 5));
    public final Setting.Bool jump = add(new Setting.Bool("Burst on jump", true));
    public final Setting.Mode aura = add(new Setting.Mode("Aura", "Off",
        "Off", "Halo", "Orbit", "Wings", "FlameRing", "Storm",
        "Tornado", "Fountain", "Galaxy Halo", "Helix", "BlackHole",
        "EmberCrown", "Aurora", "Petals", "Spark"));
    public final Setting.Bool noxCape = add(new Setting.Bool("NOX cape", true, "Your cape — shop styles or custom PNG"));
    public final Setting.Mode capeStyle = add(new Setting.Mode("Cape style", "NOX",
        "NOX", "Inferno", "Void", "Animated", "Custom"));
    public final Setting.Bool capePhysics = add(new Setting.Bool("Cape physics", true, "Cloth sim; off = static pre-bent cape"));
    public final Setting.Num capeWind = add(new Setting.Num("Cape wind", 0.6, 0.0, 2.0));
    public final Setting.Num capeFlex = add(new Setting.Num("Cape flex", 1.0, 0.3, 2.0));
    public final Setting.Str capeFile = add(new Setting.Str("Cape file", ""));
    public final Setting.Str capeUrl = add(new Setting.Str("Cape URL", "", "Remote PNG — async load, disk-cached"));
    public final Setting.Num capeDistance = add(new Setting.Num("Cape distance", 32, 8, 64, "Max render/sim distance for capes"));
    public final Setting.Mode pet = add(new Setting.Mode("Pet", "Off",
        "Off", "Dragon", "Parrot", "Blob", "Wisp"));
    public final Setting.Mode petSide = add(new Setting.Mode("Pet side", "Right",
        "Right", "Left"));
    public final Setting.Num petScale = add(new Setting.Num("Pet size", 0.9, 0.4, 2.0));
    public final Setting.Mode hat = add(new Setting.Mode("Hat", "Off",
        "Off", "Crown", "Horns", "Halo", "Beanie"));
    public final Setting.Num hatScale = add(new Setting.Num("Hat size", 1.0, 0.5, 2.0));
    public final Setting.Num hatOffY = add(new Setting.Num("Hat height", -8.5, -14, 0));
    public final Setting.Mode meshAura = add(new Setting.Mode("3D aura", "Off",
        "Off", "OrbitRing", "Sigil", "Shards"));
    public final Setting.Num auraScale = add(new Setting.Num("Aura size", 1.0, 0.4, 2.5));
    public final Setting.Num auraSpin = add(new Setting.Num("Aura spin", 0.8, 0.0, 4.0));
    public final Setting.Num auraAlpha = add(new Setting.Num("Aura alpha", 0.55, 0.1, 1.0));

    public static volatile Cosmetics INSTANCE;
    private static final java.util.Set<String> PREMIUM =
        java.util.Set.of("Soul", "Glow", "Witch", "Void", "Galaxy", "Rainbow",
            "Halo", "Orbit", "Wings", "FlameRing", "Storm", "Tornado",
            "Fountain", "Galaxy Halo", "Helix", "BlackHole",
            "EmberCrown", "Aurora", "Petals", "Spark");
    private boolean wasAir;

    public Cosmetics() {
        super("Cosmetics", "Trails & capes (client-side)", Category.RENDER);
        INSTANCE = this;
        var M = dev.noxclient.cosmetics.CosmeticsMarket.class;
        dev.noxclient.api.AddonContext.COMMANDS.put("cosmetic", a -> {
            if (a.length == 0) { M.getClass(); dev.noxclient.cosmetics.CosmeticsMarket.browse(""); return; }
            switch (a[0].toLowerCase()) {
                case "browse" -> dev.noxclient.cosmetics.CosmeticsMarket.browse(a.length > 1 ? a[1] : "");
                case "get" -> { if (a.length > 1) dev.noxclient.cosmetics.CosmeticsMarket.get(a[1]); }
                case "export" -> { if (a.length > 1) dev.noxclient.cosmetics.CosmeticsMarket.export(a[1]); }
                case "publish" -> { if (a.length > 1) dev.noxclient.cosmetics.CosmeticsMarket.publish(a[1]); }
                case "import" -> { if (a.length > 1) dev.noxclient.cosmetics.CosmeticsMarket.importFile(a[1]); }
                default -> dev.noxclient.cosmetics.CosmeticsMarket.browse("");
            }
        });
    }

    /** FailSafe level-2 hook — force all premium visual slots off. */
    public static void degrade() {
        Cosmetics c = INSTANCE;
        if (c == null) return;
        c.trail.set("Off"); c.aura.set("Off"); c.meshAura.set("Off");
        c.pet.set("Off"); c.hat.set("Off");
    }

    /** CapeLayer mixin hook — "Custom" (self-upload) needs the one-time cape
     *  entitlement or premium; bundled styles need premium. */
    public static boolean capeActive() {
        if (INSTANCE == null || !INSTANCE.isEnabled() || !INSTANCE.noxCape.on()) return false;
        return "Custom".equals(INSTANCE.capeStyle.get())
            ? LicenseManager.capeUnlocked()
            : LicenseManager.assertPremium();
    }

    /** Physics cloth cape: replaces the vanilla rigid cape for the local player. */
    public static boolean physCapeActive() {
        return capeActive() && INSTANCE.capePhysics.on();
    }

    /** Shared cape texture resolution — vanilla-layer swap and the cloth
     *  renderer both pick the same Identifier. Null = fall back to NOX. */
    public static net.minecraft.resources.Identifier capeTexId() {
        if (INSTANCE == null) return null;
        return switch (INSTANCE.capeStyle.get()) {
            case "Inferno" -> net.minecraft.resources.Identifier
                .fromNamespaceAndPath("noxclient", "textures/cape/nox_cape_inferno.png");
            case "Void" -> net.minecraft.resources.Identifier
                .fromNamespaceAndPath("noxclient", "textures/cape/nox_cape_void.png");
            case "Animated" -> dev.noxclient.gui.AnimatedCape.id();
            case "Custom" -> dev.noxclient.gui.CustomCape.id();
            default -> net.minecraft.resources.Identifier
                .fromNamespaceAndPath("noxclient", "textures/cape/nox_cape.png");
        };
    }

    private boolean locked() {
        return (PREMIUM.contains(trail.get()) || PREMIUM.contains(aura.get()))
            && !LicenseManager.assertPremium();
    }

    // ---------- particle helpers ----------
    private static final java.util.Map<Integer, net.minecraft.core.particles.DustParticleOptions>
        DUST = new java.util.HashMap<>();
    /** RGB dust — the whole point: real colors instead of the vanilla set. */
    private static net.minecraft.core.particles.DustParticleOptions dust(int rgb, float s) {
        return DUST.computeIfAbsent(((rgb & 0xFFFFFF) << 8) | (int) (s * 10),
            k -> new net.minecraft.core.particles.DustParticleOptions(rgb & 0xFFFFFF, s));
    }
    private void p(net.minecraft.core.particles.ParticleOptions t, double x, double y,
                   double z, double dx, double dy, double dz) {
        mc.level.addParticle(t, true, false, x, y, z, dx, dy, dz);
    }
    private static int hsv(float h, float s, float v) {
        h = ((h % 1f) + 1f) % 1f;
        int i = (int) (h * 6); float f = h * 6 - i, q = v * (1 - s * f), t = v * (1 - s * (1 - f));
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = v * (1 - s); }
            case 1 -> { r = q; g = v; b = v * (1 - s); }
            case 2 -> { r = v * (1 - s); g = v; b = t; }
            case 3 -> { r = v * (1 - s); g = q; b = v; }
            case 4 -> { r = t; g = v * (1 - s); b = v; }
            default -> { r = v; g = v * (1 - s); b = q; }
        }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }
    private static double rnd(double r) { return (Math.random() - 0.5) * r; }
    private double lastX, lastY, lastZ;

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || locked()) return;
        // aura patterns emit ~40 particles — every other tick halves density so
        // dust doesn't pile up into a screen-filling blob swarm
        if ((mc.player.tickCount & 1) == 0) drawAura();
        if (trail.get().equals("Off")) { lastX = mc.player.getX(); lastY = mc.player.getY(); lastZ = mc.player.getZ(); return; }
        boolean moving = mc.player.getDeltaMovement().horizontalDistanceSqr() >= 0.001;
        boolean jumped = wasAir && mc.player.onGround();
        wasAir = !mc.player.onGround();
        int n = density.intVal();
        if (jumped && jump.on()) n += 8;
        else if (!moving) { lastX = mc.player.getX(); lastY = mc.player.getY(); lastZ = mc.player.getZ(); return; }
        // interpolate along the movement segment so sprints leave a dense line
        double dx = mc.player.getX() - lastX, dy = mc.player.getY() - lastY,
               dz = mc.player.getZ() - lastZ;
        int steps = Math.max(1, Math.min(6, (int) (Math.hypot(dx, dz) / 0.08)));
        for (int i = 0; i < n; i++) {
            double f = Math.random();
            double x = mc.player.getX() - dx * f + rnd(0.22);
            double y = mc.player.getY() - dy * f + 0.12 + Math.random() * 0.35;
            double z = mc.player.getZ() - dz * f + rnd(0.22);
            for (int s = 0; s < steps; s++) emitTrail(x - dx * s / steps, y, z - dz * s / steps);
        }
        lastX = mc.player.getX(); lastY = mc.player.getY(); lastZ = mc.player.getZ();
    }

    /** One trail emission — layered: core particle + colored dust + accents. */
    private void emitTrail(double x, double y, double z) {
        double t = System.currentTimeMillis() / 1000.0;
        switch (trail.get()) {
            case "Hearts" -> {
                p(ParticleTypes.HEART, x, y + 0.1, z, 0, 0.04, 0);
                p(dust(0xFF5E8A, 0.5f), x + rnd(0.3), y, z + rnd(0.3), 0, 0.02, 0);
            }
            case "Portal" -> {
                p(ParticleTypes.PORTAL, x, y + 0.15, z, rnd(0.1), 0.1, rnd(0.1));
                p(dust(0x9B4DFF, 0.6f), x, y, z, 0, 0.06, 0);
            }
            case "Cherry" -> {
                p(ParticleTypes.CHERRY_LEAVES, x + rnd(0.4), y + 0.6, z + rnd(0.4), 0, -0.05, 0);
                p(dust(0xFFB7C5, 0.5f), x, y, z, 0, 0.01, 0);
            }
            case "Notes" -> {
                p(ParticleTypes.NOTE, x, y + 0.25, z, 0, 0.08, 0);
                p(dust(0xF5F5F5, 0.45f), x + rnd(0.35), y, z + rnd(0.35), 0, 0.03, 0);
            }
            case "Crits" -> {
                p(ParticleTypes.CRIT, x + rnd(0.3), y + 0.15, z + rnd(0.3), rnd(0.15), 0.05, rnd(0.15));
                p(dust(0xFFCF5E, 0.5f), x, y, z, 0, 0.03, 0);
            }
            case "Soul" -> {
                p(ParticleTypes.SOUL, x, y + 0.1, z, 0, 0.06, 0);
                p(dust(0x7BE3E3, 0.55f), x + rnd(0.3), y, z + rnd(0.3), 0, 0.04, 0);
            }
            case "Glow" -> {
                p(ParticleTypes.GLOW, x, y + 0.1, z, 0, 0.02, 0);
                p(dust(0xFFFFFF, 0.55f), x + rnd(0.25), y, z + rnd(0.25), 0, 0.03, 0);
            }
            case "Witch" -> {
                p(ParticleTypes.WITCH, x, y + 0.15, z, 0, 0.05, 0);
                p(dust(0xB05CFF, 0.55f), x + rnd(0.3), y, z + rnd(0.3), 0, 0.04, 0);
            }
            case "Void" -> {   // violet dust + portal sparks drifting up
                p(dust(0x8A3FE8, 0.6f), x + rnd(0.35), y + 0.1, z + rnd(0.35), 0, 0.07, 0);
                p(dust(0x6A1FCC, 0.6f), x, y, z, 0, 0.08, 0);
                if (Math.random() < 0.15)
                    p(ParticleTypes.PORTAL, x, y + 0.2, z, 0, 0.15, 0);
            }
            case "Galaxy" -> {
                p(ParticleTypes.END_ROD, x, y + 0.15, z, 0, 0.04, 0);
                p(dust(hsv((float) (t * 0.08), 0.55f, 1f), 0.5f), x + rnd(0.4), y, z + rnd(0.4), 0, 0.05, 0);
                if (Math.random() < 0.1)
                    p(ParticleTypes.SOUL_FIRE_FLAME, x, y + 0.3, z, 0, 0.05, 0);
            }
            case "Rainbow" ->
                p(dust(hsv((float) (t * 0.22), 0.9f, 1f), 0.7f), x, y + 0.1, z, 0, 0.05, 0);
            default -> {   // Flame — layered fire + smoke + ember dust
                p(ParticleTypes.FLAME, x, y + 0.1, z, 0, 0.05, 0);
                p(dust(0xFF7A2B, 0.55f), x + rnd(0.3), y, z + rnd(0.3), 0, 0.04, 0);
                if (Math.random() < 0.12)
                    p(ParticleTypes.LARGE_SMOKE, x, y + 0.3, z, 0, 0.03, 0);
            }
        }
    }

    // ---------- aura primitives ----------
    private void ringDust(double px, double py, double pz, double r, int n,
                          int rgb, float scale, double phase, double wobble) {
        for (int i = 0; i < n; i++) {
            double a = i / (double) n * Math.PI * 2 + phase;
            double rr = r + (wobble != 0 ? Math.sin(a * 3 + phase * 2) * wobble : 0);
            p(dust(rgb, scale), px + Math.cos(a) * rr, py, pz + Math.sin(a) * rr, 0, 0, 0);
        }
    }

    private void drawAura() {
        String au = aura.get();
        if (au.equals("Off")) return;
        double t = System.currentTimeMillis() / 1000.0;
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        switch (au) {
            case "Halo" -> {
                ringDust(px, py + 2.12 + Math.sin(t * 2) * 0.05, pz, 0.5, 18, 0xF5F5F5, 0.5f, t * 0.7, 0);
                ringDust(px, py + 2.12 + Math.sin(t * 2) * 0.05, pz,
                    0.32 + Math.sin(t * 1.6) * 0.06, 12, 0x9A9A9A, 0.35f, -t * 1.1, 0);
            }
            case "Orbit" -> {
                for (int i = 0; i < 3; i++) {
                    double a = t * 1.8 + i * Math.PI * 2 / 3;
                    double y = py + 1.0 + Math.sin(a) * 0.45;
                    double r = 0.85;
                    int col = new int[]{0x9B4DFF, 0xF5F5F5, 0x5AC8FF}[i];
                    p(dust(col, 0.8f), px + Math.cos(a) * r, y, pz + Math.sin(a) * r, 0, 0, 0);
                    p(dust(col, 0.35f), px + Math.cos(a) * r * 0.94, y,
                        pz + Math.sin(a) * r * 0.94, 0, 0, 0);
                }
            }
            case "Wings" -> {
                float yaw = (float) Math.toRadians(mc.player.getYRot());
                double bx = -Math.sin(yaw), bz = Math.cos(yaw);
                double flap = Math.sin(t * 3.2) * 0.06;
                for (int row = 0; row < 3; row++)
                    for (int i = 1; i <= 6; i++) {
                        double spread = i * (0.10 + row * 0.025);
                        double h = 1.95 - i * 0.20 - row * 0.28 + flap * i / 4;
                        for (int side : new int[]{-1, 1}) {
                            double sx = px + bx * (0.25 + row * 0.12) - bz * side * spread;
                            double sz = pz + bz * (0.25 + row * 0.12) + bx * side * spread;
                            p(row == 0 ? dust(0xF5F5F5, 0.6f)
                                    : row == 1 ? dust(0xB8C4E0, 0.5f)
                                    : dust(0x5AC8FF, 0.4f),
                                sx, py + h, sz, 0, 0, 0);
                        }
                    }
            }
            case "FlameRing" -> {
                for (int i = 0; i < 14; i++) {
                    double a = i / 14.0 * Math.PI * 2 - t * 1.6;
                    p(ParticleTypes.FLAME, px + Math.cos(a) * 0.7, py + 0.12, pz + Math.sin(a) * 0.7,
                        0, 0.03, 0);
                }
                for (int i = 0; i < 10; i++) {
                    double a = i / 10.0 * Math.PI * 2 + t * 0.9;
                    double h = (i / 10.0) * 1.6;
                    p(dust(0xFF7A2B, 0.5f), px + Math.cos(a) * (0.7 - h * 0.25),
                        py + 0.15 + h, pz + Math.sin(a) * (0.7 - h * 0.25), 0, 0, 0);
                }
            }
            case "Tornado" -> {
                for (int i = 0; i < 20; i++) {
                    double h = i / 20.0 * 2.3;
                    double a = i * 0.9 + t * 4.5;
                    double r = 0.18 + h * 0.32;
                    p(i % 3 == 0 ? ParticleTypes.CLOUD : dust(0x8A8F9A, 0.45f),
                        px + Math.cos(a) * r, py + h, pz + Math.sin(a) * r, 0, 0, 0);
                }
            }
            case "Fountain" -> {
                for (int i = 0; i < 5; i++)
                    p(ParticleTypes.END_ROD, px + rnd(0.18), py + 2.05, pz + rnd(0.18),
                        rnd(0.18), 0.28, rnd(0.18));
                for (int i = 0; i < 4; i++)
                    p(dust(0xF5F5F5, 0.4f), px + rnd(0.3), py + 2.2 + Math.random() * 0.5,
                        pz + rnd(0.3), rnd(0.25), 0.1, rnd(0.25));
            }
            case "Galaxy Halo" -> {
                for (int i = 0; i < 16; i++) {
                    double a = i / 16.0 * Math.PI * 2 + t * 1.1;
                    double wob = Math.sin(t * 3 + i * 1.3) * 0.12;
                    p(dust(i % 4 == 0 ? 0xF5F5F5 : 0x9B4DFF, 0.5f),
                        px + Math.cos(a) * (0.62 + wob), py + 2.18 + wob * 0.5,
                        pz + Math.sin(a) * (0.62 + wob), 0, 0, 0);
                }
                double sa = t * 2.6;
                p(ParticleTypes.END_ROD, px + Math.cos(sa) * 0.62, py + 2.18,
                    pz + Math.sin(sa) * 0.62, 0, 0, 0);
            }
            case "Helix" -> {
                for (int s = 0; s < 2; s++)
                    for (int i = 0; i < 10; i++) {
                        double h = i / 10.0 * 2.15;
                        double a = h * 3 + t * 2.4 + s * Math.PI;
                        p(dust(s == 0 ? 0xF5F5F5 : 0x9B4DFF, 0.5f),
                            px + Math.cos(a) * 0.55, py + h, pz + Math.sin(a) * 0.55, 0, 0, 0);
                    }
            }
            case "BlackHole" -> {
                for (int i = 0; i < 12; i++) {
                    double h = i / 12.0 * 1.7;
                    double a = i * 0.75 - t * 4.5;
                    double r = 0.8 - h * 0.34;
                    p(dust(i % 3 == 0 ? 0xC77DFF : 0x5A2E9E, 0.45f),
                        px + Math.cos(a) * r, py + 0.25 + h, pz + Math.sin(a) * r, 0, 0, 0);
                }
                ringDust(px, py + 1.05 + Math.sin(t * 1.5) * 0.04, pz,
                    0.55, 12, 0xF5F5F5, 0.5f, t * 3, 0);
                ringDust(px, py + 1.05, pz, 0.38, 8, 0x9B4DFF, 0.45f, -t * 2.2, 0.03);
            }
            case "EmberCrown" -> {
                for (int i = 0; i < 10; i++) {
                    double a = i / 10.0 * Math.PI * 2;
                    p(ParticleTypes.LAVA, px + Math.cos(a) * 0.42,
                        py + 2.28 + Math.sin(t * 3 + i) * 0.07, pz + Math.sin(a) * 0.42, 0, 0, 0);
                }
                ringDust(px, py + 2.28, pz, 0.42, 10, 0xFF7A2B, 0.4f, t, 0.02);
            }
            case "Aurora" -> {
                for (int c = 0; c < 3; c++) {
                    double base = c * Math.PI * 2 / 3 + t * 0.3;
                    for (int i = 0; i < 5; i++) {
                        double a = base + Math.sin(t * 0.8 + i) * 0.2;
                        double r = 0.55 + Math.sin(t + c) * 0.15;
                        p(dust(hsv((float) (0.55 + c * 0.12 + t * 0.03), 0.8f, 1f), 0.55f),
                            px + Math.cos(a) * r, py + 0.3 + i * 0.45, pz + Math.sin(a) * r,
                            0, 0.02, 0);
                    }
                }
            }
            case "Petals" -> {
                for (int i = 0; i < 3; i++)
                    p(ParticleTypes.CHERRY_LEAVES, px + rnd(1.7), py + 2.5 + Math.random() * 0.4,
                        pz + rnd(1.7), 0, -0.08, 0);
                for (int i = 0; i < 3; i++)
                    p(dust(0xFFB7C5, 0.45f), px + rnd(1.5), py + 2.2 + Math.random() * 0.6,
                        pz + rnd(1.5), 0, -0.05, 0);
            }
            case "Spark" -> {
                for (int i = 0; i < 2; i++)
                    p(ParticleTypes.ELECTRIC_SPARK, px + rnd(0.9), py + Math.random() * 2.1,
                        pz + rnd(0.9), rnd(0.15), 0.06, rnd(0.15));
                if (Math.random() < 0.3)
                    p(dust(0xEAF6FF, 0.65f), px + rnd(0.7), py + 0.5 + Math.random() * 1.4,
                        pz + rnd(0.7), 0, 0, 0);
            }
            case "Storm" -> {
                for (int i = 0; i < 4; i++) {
                    double a = Math.random() * Math.PI * 2;
                    p(ParticleTypes.SOUL, px + Math.cos(a) * (0.3 + Math.random() * 0.5),
                        py + 2.4 + Math.random() * 0.4, pz + Math.sin(a) * (0.3 + Math.random() * 0.5),
                        0, -0.06, 0);
                }
                for (int i = 0; i < 3; i++)
                    p(dust(0x5AC8FF, 0.4f), px + rnd(1.0), py + 2.6, pz + rnd(1.0), 0, -0.15, 0);
                if (Math.random() < 0.08)
                    p(ParticleTypes.ELECTRIC_SPARK, px + rnd(0.6), py + 2.3, pz + rnd(0.6), 0, -0.1, 0);
            }
        }
    }

    @Override public String status() {
        var sb = new StringBuilder();
        if (!trail.get().equals("Off")) sb.append("trail:").append(trail.get()).append(' ');
        if (!aura.get().equals("Off")) sb.append("aura:").append(aura.get()).append(' ');
        if (noxCape.on()) sb.append("cape:").append(capeStyle.get());
        return sb.length() == 0 ? "all cosmetics off" : sb.toString().trim();
    }

}
