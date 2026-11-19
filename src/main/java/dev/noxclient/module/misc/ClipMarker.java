package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.gui.Toasts;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * ClipMarker — timestamps worth clipping later. Auto-marks your death and
 * any registered trigger (Twitch `!clip`, `/nox clip`), writes
 * wall-clock + position + context to nox/clips.txt and pings the webhook
 * so you can scrub the VOD straight to the moment.
 */
public class ClipMarker extends Module {
    private static final File FILE = new File(
        net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().toFile(),
        "nox/clips.txt");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Setting.Bool markDeath = add(new Setting.Bool("Mark on death", true));
    private boolean wasDead;

    public ClipMarker() { super("ClipMarker", "Timestamps deaths + !clip for VOD scrubbing",
        Category.MISC); }

    @Override protected void onEnable() {
        dev.noxclient.api.AddonContext.COMMANDS.put("clip",
            a -> stamp("manual"));
    }

    @Override protected void onDisable() {
        dev.noxclient.api.AddonContext.COMMANDS.remove("clip");
    }

    @Override public void onTick() {
        if (mc.player == null) return;
        boolean dead = mc.player.isDeadOrDying();
        if (markDeath.on() && dead && !wasDead)
            stamp("death at " + fmtPos());
        wasDead = dead;
    }

    /** Shared entry — ChatTriggers `!clip` and `/nox clip` land here. */
    public static void stamp(String why) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        String pos = mc.player != null ? fmtPos() : "?";
        String line = LocalTime.now().format(TS) + "  " + why + "  " + pos;
        try {
            FILE.getParentFile().mkdirs();
            try (var w = new PrintWriter(new FileWriter(FILE, true))) { w.println(line); }
        } catch (Throwable ignored) {}
        Toasts.push("CLIP — " + line, 0xFFFE2C55);
    }

    private static String fmtPos() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return "?";
        var p = mc.player.blockPosition();
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    @Override public String status() {
        return isEnabled() ? "recording to nox/clips.txt" : "off";
    }
}
