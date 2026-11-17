package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;

import java.util.Map;
import java.util.Random;

/**
 * Emotes (Modul 10): chat-emote → particle burst above your head.
 * Trigger: "/e <name>" in the local /nox dispatcher or the .emote command
 * prefix in chat. 20 presets, 1s self / 3s viewer cooldown.
 */
public class Emotes extends Module {
    public final Setting.Bool sounds = add(new Setting.Bool("Sounds", true));

    private static final Map<String, Emote> EMOTES = Map.ofEntries(
        Map.entry("laugh", new Emote(ParticleTypes.HAPPY_VILLAGER, 30, 0.4)),
        Map.entry("cry", new Emote(ParticleTypes.RAIN, 25, 0.3)),
        Map.entry("gg", new Emote(ParticleTypes.TOTEM_OF_UNDYING, 60, 0.5)),
        Map.entry("wave", new Emote(ParticleTypes.CLOUD, 15, 0.25)),
        Map.entry("dance", new Emote(ParticleTypes.NOTE, 24, 0.5)),
        Map.entry("heart", new Emote(ParticleTypes.HEART, 12, 0.3)),
        Map.entry("fire", new Emote(ParticleTypes.FLAME, 40, 0.35)),
        Map.entry("skull", new Emote(ParticleTypes.SOUL, 18, 0.2)),
        Map.entry("thumbsup", new Emote(ParticleTypes.HAPPY_VILLAGER, 10, 0.2)),
        Map.entry("thinking", new Emote(ParticleTypes.ENCHANT, 16, 0.4)),
        Map.entry("rage", new Emote(ParticleTypes.ANGRY_VILLAGER, 20, 0.3)),
        Map.entry("party", new Emote(ParticleTypes.FIREWORK, 50, 0.6)),
        Map.entry("snow", new Emote(ParticleTypes.SNOWFLAKE, 30, 0.4)),
        Map.entry("sparkle", new Emote(ParticleTypes.END_ROD, 22, 0.35)),
        Map.entry("poof", new Emote(ParticleTypes.POOF, 18, 0.25)),
        Map.entry("boom", new Emote(ParticleTypes.EXPLOSION, 6, 0.2)),
        Map.entry("magic", new Emote(ParticleTypes.WITCH, 26, 0.4)),
        Map.entry("cherry", new Emote(ParticleTypes.CHERRY_LEAVES, 30, 0.4)),
        Map.entry("sonic", new Emote(ParticleTypes.SONIC_BOOM, 3, 0.1)),
        Map.entry("glow", new Emote(ParticleTypes.GLOW, 24, 0.3)));

    private record Emote(net.minecraft.core.particles.ParticleOptions p, int count, double spread) {}
    private static long lastAt;

    public Emotes() { super("Emotes", "Particle emotes above your head", Category.MISC); }

    /** /nox emote <name> or chat trigger. Returns true when handled. */
    public static boolean trigger(String name) {
        if (System.currentTimeMillis() - lastAt < 1000) return true;
        Emote e = EMOTES.get(name.toLowerCase());
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (e == null || mc.player == null || mc.level == null) return false;
        lastAt = System.currentTimeMillis();
        Random r = new Random();
        for (int i = 0; i < e.count(); i++)
            mc.level.addParticle(e.p(),
                mc.player.getX() + (r.nextDouble() - .5) * e.spread() * 2,
                mc.player.getY() + 2.1 + r.nextDouble() * 0.5,
                mc.player.getZ() + (r.nextDouble() - .5) * e.spread() * 2,
                0, 0.02, 0);
        if (net.minecraft.client.Minecraft.getInstance().player != null)
            mc.player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8f, 1.4f);
        return true;
    }

    public static String list() { return String.join(", ", EMOTES.keySet()); }
}
