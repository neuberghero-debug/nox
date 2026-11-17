package dev.noxclient.util;

import dev.noxclient.gui.Toasts;
import dev.noxclient.security.WebhookSec;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Auto Crash Report (Modul 12). After a crash the next launch detects the
 * newest file in run/crash-reports/, deduplicates by content hash (persisted
 * in run/nox/reported-crashes.txt), and — only when telemetry opt-in is on —
 * offers to send it: toast prompt + "/nox crashreport" command which posts
 * the sanitized top section through WebhookSec (rate-limit / HMAC / dedup /
 * 8 KB / retry ≤3). Nothing ever leaves without explicit opt-in + command.
 */
public final class CrashReporter {
    private static Path crashDir, marker;
    private static Path pending;             // newest unreported crash file
    private static boolean checked;

    public static void init(Minecraft mc) {
        crashDir = mc.gameDirectory.toPath().resolve("crash-reports");
        marker = mc.gameDirectory.toPath().resolve("nox").resolve("reported-crashes.txt");
        dev.noxclient.api.AddonContext.COMMANDS.put("crashreport", CrashReporter::sendNow);
        dev.noxclient.api.AddonContext.COMMANDS.put("audit",
            args -> dev.noxclient.module.misc.SecurityAudit.run());
        dev.noxclient.api.AddonContext.COMMANDS.put("alt", args -> {
            if (args.length > 0) dev.noxclient.alts.AltManager.quickSwitch(args[0]);
        });
        dev.noxclient.util.StashIndex.registerCommand();   // /nox stashes [cat]
    }

    /** One-shot scan on first client tick. */
    public static void tick() {
        if (checked || crashDir == null) return;
        checked = true;
        try {
            if (!Files.isDirectory(crashDir)) return;
            Path newest;
            try (Stream<Path> s = Files.list(crashDir)) {
                newest = s.filter(p -> p.getFileName().toString().startsWith("crash-"))
                    .max(Comparator.comparing(p -> p.getFileName().toString()))
                    .orElse(null);
            }
            if (newest == null) return;
            String h = hashOf(newest);
            if (h == null || reported().contains(h)) return;
            pending = newest;
            // prompt — opt-in still gates the actual send
            Toasts.push("NOX crashed last session — /nox crashreport to share a sanitized report",
                0xFFFFD870);
        } catch (Throwable ignored) {}
    }

    /** User-confirmed send ("/nox crashreport"). */
    private static void sendNow(String[] args) {
        if (pending == null) { chat("§7[NOX] no unreported crash"); return; }
        if (!dev.noxclient.telemetry.Telemetry.enabled()) {
            chat("§e[NOX] crash reports need telemetry opt-in "
                + "(enable it in the privacy popup or next launch)");
            return;
        }
        try {
            String raw = Files.readString(pending, StandardCharsets.UTF_8);
            // top section only — the stack + system details, no world coords
            int cut = raw.indexOf("-- Affected level --");
            if (cut < 0) cut = Math.min(raw.length(), 6 * 1024);
            String body = "file=" + pending.getFileName()
                + "\nactor=" + WebhookSec.anonId()
                + "\nversion=" + dev.noxclient.NoxClient.VERSION
                + "\n\n" + raw.substring(0, Math.min(cut, raw.length()));
            WebhookSec.alert("Crash report",
                body, "crash:" + hashOf(pending));
            markReported(hashOf(pending));
            pending = null;
            chat("§a[NOX] crash report queued — thanks!");
        } catch (Throwable t) { chat("§c[NOX] report failed: " + t.getMessage()); }
    }

    private static void chat(String s) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(net.minecraft.network.chat.Component.literal(s));
        else Toasts.push(s.replaceAll("§.", ""), 0xFF8A8A9E);
    }

    private static String hashOf(Path p) {
        try {
            byte[] head = Files.readAllBytes(p);
            byte[] d = MessageDigest.getInstance("SHA-256").digest(head);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (Throwable t) { return null; }
    }

    private static Set<String> reported() {
        try {
            if (!Files.exists(marker)) return new HashSet<>();
            return new HashSet<>(Files.readAllLines(marker, StandardCharsets.UTF_8));
        } catch (IOException e) { return new HashSet<>(); }
    }

    private static void markReported(String h) {
        try {
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, h + "\n", StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ignored) {}
    }
}
