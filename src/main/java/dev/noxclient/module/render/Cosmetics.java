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

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || locked()) return;
        drawAura();
        if (trail.get().equals("Off")) return;
        boolean moving = mc.player.getDeltaMovement().horizontalDistanceSqr() >= 0.001;
        boolean jumped = wasAir && mc.player.onGround();
        wasAir = !mc.player.onGround();
        int n = density.intVal();
        if (jumped && jump.on()) n += 8;
        else if (!moving) return;
        for (int i = 0; i < n; i++) {
            var type = type();
            double ox = (Math.random() - 0.5) * 0.3, oz = (Math.random() - 0.5) * 0.3;
            mc.level.addParticle(type, true, false,
                mc.player.getX() + ox, mc.player.getY() + 0.2 + Math.random() * 0.4,
                mc.player.getZ() + oz, 0, 0.02, 0);
        }
    }

    private void drawAura() {
        String a = aura.get();
        if (a.equals("Off")) return;
        double t = System.currentTimeMillis() / 1000.0;
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        switch (a) {
            case "Halo" -> {
                for (int i = 0; i < 12; i++) {
                    double ang = i / 12.0 * Math.PI * 2 + t * 0.8;
                    mc.level.addParticle(ParticleTypes.END_ROD, true, false,
                        px + Math.cos(ang) * 0.45, py + 2.15 + Math.sin(t * 2) * 0.05,
                        pz + Math.sin(ang) * 0.45, 0, 0, 0);
                }
            }
            case "Orbit" -> {
                for (int ring = 0; ring < 2; ring++) {
                    double ang = t * 2.4 + ring * Math.PI;
                    double y = py + 0.4 + ring * 0.9 + Math.sin(t * 3) * 0.15;
                    mc.level.addParticle(ring == 0 ? ParticleTypes.SOUL_FIRE_FLAME
                            : ParticleTypes.END_ROD, true, false,
                        px + Math.cos(ang) * 0.8, y, pz + Math.sin(ang) * 0.8, 0, 0.01, 0);
                }
            }
            case "Wings" -> {
                float yaw = (float) Math.toRadians(mc.player.getYRot());
                double bx = -Math.sin(yaw), bz = Math.cos(yaw);
                for (int i = 1; i <= 6; i++) {
                    double spread = i * 0.09, h = 1.9 - i * 0.22;
                    for (int side : new int[]{-1, 1}) {
                        double sx = px + bx * 0.25 - bz * side * spread;
                        double sz = pz + bz * 0.25 + bx * side * spread;
                        mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, true, false,
                            sx, py + h + Math.sin(t * 4 + i) * 0.04, sz, 0, 0, 0);
                    }
                }
            }
            case "FlameRing" -> {
                for (int i = 0; i < 16; i++) {
                    double ang = i / 16.0 * Math.PI * 2 - t * 1.5;
                    mc.level.addParticle(ParticleTypes.FLAME, true, false,
                        px + Math.cos(ang) * 0.7, py + 0.1 + i * 0.08,
                        pz + Math.sin(ang) * 0.7, 0, 0.03, 0);
                }
            }
            case "Tornado" -> {
                for (int i = 0; i < 24; i++) {
                    double h = i / 24.0 * 2.2;
                    double ang = i * 0.9 + t * 4;
                    double r = 0.25 + h * 0.3;
                    mc.level.addParticle(ParticleTypes.CLOUD, true, false,
                        px + Math.cos(ang) * r, py + h, pz + Math.sin(ang) * r, 0, 0, 0);
                }
            }
            case "Fountain" -> {
                for (int i = 0; i < 6; i++)
                    mc.level.addParticle(ParticleTypes.END_ROD, true, false,
                        px + (Math.random() - 0.5) * 0.2, py + 2.1,
                        pz + (Math.random() - 0.5) * 0.2,
                        (Math.random() - 0.5) * 0.15, 0.25,
                        (Math.random() - 0.5) * 0.15);
            }
            case "Galaxy Halo" -> {
                for (int i = 0; i < 20; i++) {
                    double ang = i / 20.0 * Math.PI * 2 + t * 1.2;
                    double wob = Math.sin(t * 3 + i) * 0.12;
                    mc.level.addParticle(i % 3 == 0 ? ParticleTypes.SOUL_FIRE_FLAME
                            : ParticleTypes.END_ROD, true, false,
                        px + Math.cos(ang) * (0.6 + wob), py + 2.2 + wob * 0.5,
                        pz + Math.sin(ang) * (0.6 + wob), 0, 0, 0);
                }
            }
            case "Helix" -> {
                for (int s = 0; s < 2; s++)
                    for (int i = 0; i < 12; i++) {
                        double h = i / 12.0 * 2.1;
                        double ang = h * 3 + t * 2.2 + s * Math.PI;
                        mc.level.addParticle(s == 0 ? ParticleTypes.END_ROD
                                : ParticleTypes.SOUL_FIRE_FLAME, true, false,
                            px + Math.cos(ang) * 0.55, py + h,
                            pz + Math.sin(ang) * 0.55, 0, 0, 0);
                    }
            }
            case "BlackHole" -> {
                for (int i = 0; i < 18; i++) {
                    double h = i / 18.0 * 1.9;
                    double ang = i * 0.7 - t * 5;
                    double r = 0.9 - h * 0.35;
                    mc.level.addParticle(i % 4 == 0 ? ParticleTypes.END_ROD
                            : ParticleTypes.ASH, true, false,
                        px + Math.cos(ang) * r, py + 0.3 + h,
                        pz + Math.sin(ang) * r, 0, 0, 0);
                }
                for (int i = 0; i < 8; i++) {   // accretion ring at head height
                    double ang = i / 8.0 * Math.PI * 2 + t * 3;
                    mc.level.addParticle(ParticleTypes.FLAME, true, false,
                        px + Math.cos(ang) * 0.5, py + 1.1,
                        pz + Math.sin(ang) * 0.5, 0, 0, 0);
                }
            }
            case "EmberCrown" -> {
                for (int i = 0; i < 10; i++) {
                    double ang = i / 10.0 * Math.PI * 2;
                    mc.level.addParticle(ParticleTypes.LAVA, true, false,
                        px + Math.cos(ang) * 0.4, py + 2.25 + Math.sin(t * 3 + i) * 0.06,
                        pz + Math.sin(ang) * 0.4, 0, 0, 0);
                }
            }
            case "Aurora" -> {
                for (int i = 0; i < 3; i++) {
                    double ang = Math.random() * Math.PI * 2;
                    double r = 0.5 + Math.random() * 0.6;
                    var pt = RAINBOW[(int) (t * 2 + i) % RAINBOW.length];
                    mc.level.addParticle(pt, true, false,
                        px + Math.cos(ang) * r,
                        py + 0.2 + Math.random() * 2.2,
                        pz + Math.sin(ang) * r, 0, 0.02, 0);
                }
            }
            case "Petals" -> {
                for (int i = 0; i < 3; i++)
                    mc.level.addParticle(ParticleTypes.CHERRY_LEAVES, true, false,
                        px + (Math.random() - 0.5) * 1.6,
                        py + 2.4 + Math.random() * 0.4,
                        pz + (Math.random() - 0.5) * 1.6,
                        0, -0.08, 0);
            }
            case "Spark" -> {
                for (int i = 0; i < 2; i++)
                    mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, true, false,
                        px + (Math.random() - 0.5) * 0.8,
                        py + Math.random() * 2.1,
                        pz + (Math.random() - 0.5) * 0.8,
                        (Math.random() - 0.5) * 0.1, 0.05,
                        (Math.random() - 0.5) * 0.1);
            }
            case "Storm" -> {
                for (int i = 0; i < 4; i++) {
                    double ang = Math.random() * Math.PI * 2;
                    mc.level.addParticle(ParticleTypes.SOUL, true, false,
                        px + Math.cos(ang) * (0.3 + Math.random() * 0.5),
                        py + 2.4 + Math.random() * 0.4,
                        pz + Math.sin(ang) * (0.3 + Math.random() * 0.5), 0, -0.05, 0);
                }
            }
        }
    }

    private SimpleParticleType type() {
        return switch (trail.get()) {
            case "Hearts" -> ParticleTypes.HEART;
            case "Portal" -> ParticleTypes.PORTAL;
            case "Cherry" -> ParticleTypes.CHERRY_LEAVES;
            case "Notes" -> ParticleTypes.NOTE;
            case "Crits" -> ParticleTypes.CRIT;
            case "Soul" -> ParticleTypes.SOUL;
            case "Glow" -> ParticleTypes.GLOW;
            case "Witch" -> ParticleTypes.WITCH;
            case "Void" -> ParticleTypes.ASH;
            case "Galaxy" -> ParticleTypes.END_ROD;
            case "Rainbow" -> RAINBOW[(int) (System.currentTimeMillis() / 300) % RAINBOW.length];
            default -> ParticleTypes.FLAME;
        };
    }

    private static final SimpleParticleType[] RAINBOW = {
        ParticleTypes.FLAME, ParticleTypes.HEART, ParticleTypes.HAPPY_VILLAGER,
        ParticleTypes.NOTE, ParticleTypes.END_ROD, ParticleTypes.SOUL_FIRE_FLAME
    };
    @Override public String status() {
        var sb = new StringBuilder();
        if (!trail.get().equals("Off")) sb.append("trail:").append(trail.get()).append(' ');
        if (!aura.get().equals("Off")) sb.append("aura:").append(aura.get()).append(' ');
        if (noxCape.on()) sb.append("cape:").append(capeStyle.get());
        return sb.length() == 0 ? "all cosmetics off" : sb.toString().trim();
    }

}
