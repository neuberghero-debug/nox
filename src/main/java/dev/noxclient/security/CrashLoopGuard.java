package dev.noxclient.security;

import net.minecraft.client.Minecraft;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Anti-crack 30.10 — crash-loop guard. On startup, count crash reports from
 * the last 15 minutes. ≥3 means the client is crash-looping (commonly caused
 * by a tampered jar or hostile injected agent): enter safe mode — auto-
 * disable every non-essential module for this session and tell the user.
 */
public final class CrashLoopGuard {
    private static final int LIMIT = 3;
    private static final long WINDOW_MS = 15 * 60_000;
    private static volatile boolean safeMode;

    public static boolean safeMode() { return safeMode; }

    /** Called once during NoxClient init, BEFORE modules enable. */
    public static void init(Minecraft mc) {
        try {
            Path dir = mc.gameDirectory.toPath().resolve("crash-reports");
            if (!Files.isDirectory(dir)) return;
            long cutoff = System.currentTimeMillis() - WINDOW_MS;
            int recent = 0;
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "crash-*.txt")) {
                for (Path p : ds)
                    if (Files.getLastModifiedTime(p).toMillis() > cutoff) recent++;
            }
            if (recent >= LIMIT) {
                safeMode = true;
                System.err.println("[NOX] crash-loop detected (" + recent
                    + " crashes in 15min) — safe mode");
            }
        } catch (Throwable ignored) { }
    }

    /** Called after module registration — strips everything non-essential. */
    public static void enforce(java.util.Collection<dev.noxclient.module.Module> modules) {
        if (!safeMode) return;
        for (var m : modules) {
            if (m.isEnabled() && essential(m)) continue;
            if (m.isEnabled()) m.setEnabled(false);
        }
    }

    private static boolean essential(dev.noxclient.module.Module m) {
        return m instanceof dev.noxclient.module.misc.TamperWarner;
    }
}
