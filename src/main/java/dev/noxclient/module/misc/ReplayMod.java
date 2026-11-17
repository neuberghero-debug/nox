package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.replay.ReplayRecorder;
import dev.noxclient.setting.Setting;

import java.nio.file.Path;

/**
 * Replay module (Modul 22 wrapper): toggles the ring-buffer recorder,
 * saves .donutreplay on demand. F9-style save via settings button or
 * auto-save on death when enabled.
 */
public class ReplayMod extends Module {
    public final Setting.Bool record = add(new Setting.Bool("Record", true));
    public final Setting.Bool autoSaveDeath = add(new Setting.Bool("Autosave on death", true));
    public final Setting.Bool autoSaveKill = add(new Setting.Bool("Autosave on kill", false));

    public ReplayMod() {
        super("Replay", "Records .donutreplay (entities/events) 10min buffer", Category.MISC);
        dev.noxclient.api.AddonContext.COMMANDS.put("replay", a -> {
            if (a.length == 0) { openPlayback(null); return; }
            switch (a[0].toLowerCase()) {
                case "save" -> saveNow();
                case "open" -> openPlayback(a.length > 1
                    ? java.nio.file.Path.of(a[1]) : null);
                case "export" -> {
                    try {
                        Path f = newest();
                        if (f == null) { dev.noxclient.gui.Toasts.push("No replays", 0xFFFF6666); return; }
                        Path mp4 = f.resolveSibling(f.getFileName().toString().replace(".donutreplay", ".mp4"));
                        int rc = dev.noxclient.replay.ReplayExporter.exportMp4(f, mp4, 30);
                        dev.noxclient.gui.Toasts.push(rc == 0 ? "Exported " + mp4.getFileName()
                            : "ffmpeg failed (" + rc + ")", rc == 0 ? 0xFF66D9A0 : 0xFFFF6666);
                        // Discord upload when DiscordNotifier has a webhook set (Modul 2)
                        var dn = dev.noxclient.NoxClient.INSTANCE.modules.get("DiscordNotifier");
                        if (rc == 0 && dn instanceof DiscordNotifier d && !d.webhook.get().isEmpty()) {
                            dev.noxclient.gui.Toasts.push("Uploading to Discord…", 0xFF8B5CF6);
                            dev.noxclient.discord.Webhook.postFile(d.webhook.get(), mp4,
                                "NOX replay clip", ok -> net.minecraft.client.Minecraft.getInstance()
                                    .execute(() -> dev.noxclient.gui.Toasts.push(
                                        ok ? "Clip posted to Discord" : "Discord upload failed",
                                        ok ? 0xFF66D9A0 : 0xFFFF6666)));
                        }
                    } catch (Exception e) { dev.noxclient.gui.Toasts.push("export: " + e.getMessage(), 0xFFFF6666); }
                }
                default -> openPlayback(null);
            }
        });
    }

    @Override
    protected void onEnable() { if (record.on()) { ReplayRecorder.start(); dev.noxclient.replay.HighlightMarks.start(); } }
    @Override
    protected void onDisable() { ReplayRecorder.stop(); }

    private boolean wasDead;
    private final java.util.Set<Integer> alivePlayers = new java.util.HashSet<>();

    @Override
    public void onTick() {
        ReplayRecorder.tick();
        if (mc.player == null || mc.level == null) return;
        boolean dead = mc.player.isDeadOrDying();
        if (dead && !wasDead) {
            dev.noxclient.replay.HighlightMarks.mark("death", "you died");
            if (autoSaveDeath.on()) {
                Path f = ReplayRecorder.save();
                if (f != null) {
                    dev.noxclient.replay.HighlightMarks.save(f);
                    dev.noxclient.gui.Toasts.push("Replay saved: " + f.getFileName(), 0xFF66D9A0);
                }
            }
        }
        wasDead = dead;

        // kill markers: players that were alive last tick and are now gone/dead
        java.util.Set<Integer> now = new java.util.HashSet<>();
        for (var e : mc.level.entitiesForRendering())
            if (e instanceof net.minecraft.world.entity.player.Player p && p != mc.player
                && p.isAlive() && p.distanceTo(mc.player) < 32) now.add(p.getId());
        for (int id : alivePlayers)
            if (!now.contains(id))
                dev.noxclient.replay.HighlightMarks.mark("kill", "nearby player died/despawned");
        alivePlayers.clear(); alivePlayers.addAll(now);
    }

    /** Manual save (bind or /nox replay save). */
    public static void saveNow() {
        Path f = ReplayRecorder.save();
        if (f != null) dev.noxclient.replay.HighlightMarks.save(f);
        dev.noxclient.gui.Toasts.push(f != null ? "Replay: " + f.getFileName() : "Replay save failed",
            f != null ? 0xFF66D9A0 : 0xFFFF6666);
    }

    /** Open the playback screen on a file (null = newest in nox/replays). */
    public static void openPlayback(Path file) {
        try {
            Path f = file != null ? file : newest();
            if (f == null) { dev.noxclient.gui.Toasts.push("No replays recorded yet", 0xFFFF6666); return; }
            var mc = net.minecraft.client.Minecraft.getInstance();
            mc.setScreenAndShow(new dev.noxclient.gui.ReplayScreen(f));
        } catch (Exception e) {
            dev.noxclient.gui.Toasts.push("Replay open failed: " + e.getMessage(), 0xFFFF6666);
        }
    }

    private static Path newest() throws java.io.IOException {
        Path dir = net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve("nox/replays");
        try (var s = java.nio.file.Files.list(dir)) {
            return s.filter(p -> p.toString().endsWith(".donutreplay"))
                .max(java.util.Comparator.comparingLong(p -> p.toFile().lastModified()))
                .orElse(null);
        }
    }
}
