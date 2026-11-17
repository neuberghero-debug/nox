package dev.noxclient.util;

import dev.noxclient.NoxClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.zip.ZipFile;

/**
 * Jar integrity guard. The build embeds assets/noxclient/sig.txt = SHA-256
 * over every dev/noxclient/**.class + fabric.mod.json + noxclient.mixins.json.
 * At runtime the same entries are re-hashed from the live jar; a mismatch
 * means the file was repacked (scammer dropper). Compromise → TamperScreen
 * once + periodic chat warning. Dev runs (classes dir) skip the check.
 */
public final class TamperGuard {
    private static final long RECHECK_MS = 60_000;
    private static volatile Boolean compromised;
    private static volatile boolean screenShown;
    private static long lastWarn;
    private static long lastRecheck;

    public static void verifyAsync() {
        Thread t = new Thread(() -> {
            try {
                String actual = selfHash();
                if (actual == null) return;                 // dev env → skip
                String expected = readSig();
                compromised = expected != null && !expected.equalsIgnoreCase(actual);
            } catch (Throwable ignored) { /* can't verify → don't lock users out */ }
        }, "nox-tamperguard");
        t.setDaemon(true);
        t.start();
    }

    public static boolean compromised() { return Boolean.TRUE.equals(compromised); }

    /**
     * Synchronous verify — run ONCE early in client init so the result is
     * known before the title screen ever shows (~100-300ms, one-time).
     * Dev runs (classes dir) return instantly via the .jar check.
     */
    public static void verifySync() {
        try {
            String force = System.getProperty("nox.tamper.force",
                System.getenv().getOrDefault("NOX_TAMPER_FORCE", ""));
            if (force.equalsIgnoreCase("true") || force.equals("1")) {
                compromised = true;                        // test hook
                return;
            }
            String actual = selfHash();
            if (actual == null) return;                    // dev env → skip
            String expected = readSig();
            compromised = expected != null && !expected.equalsIgnoreCase(actual);
        } catch (Throwable ignored) { /* can't verify → don't lock users out */ }
    }

    /**
     * Modul 11: periodic re-check (called each client tick, self-throttled to
     * RECHECK_MS). A jar swapped/patched after launch is caught within a
     * minute instead of surviving until restart.
     */
    public static void tick() {
        long now = System.currentTimeMillis();
        if (now - lastRecheck < RECHECK_MS) return;
        lastRecheck = now;
        if (compromised == null) { verifyAsync(); return; }
        verifyAsync();                                    // re-hash in bg
    }

    /** Null-safe probe for SecurityAudit — class present + callable. */
    public static boolean verified() { return compromised != null; }

    /**
     * Modul 11: hostile-injection heuristics — Java agents loaded via
     * -javaagent/attach, debugger attached (JVMTI), or a module class loaded
     * from OUTSIDE our jar (addon injected at classloader level = not covered
     * by the jar signature). Returns a human-readable reason or null.
     */
    public static String injectionDetected() {
        try {
            for (String a : java.lang.management.ManagementFactory
                    .getRuntimeMXBean().getInputArguments())
                if (a.contains("-javaagent:"))
                    return "javaagent in JVM args: " + a;
            if (java.lang.management.ManagementFactory.getRuntimeMXBean()
                    .getSystemProperties().containsKey("sun.java.command.agent.loaded"))
                return "agent attached post-launch";
        } catch (Throwable ignored) {}
        try {
            if (dev.noxclient.ncore.NativeCore.available()
                    && dev.noxclient.ncore.NativeCore.antiDebug() != 0)
                return "debugger / ptrace detected (native)";
        } catch (Throwable ignored) {}
        try {
            URL loc = TamperGuard.class.getProtectionDomain().getCodeSource().getLocation();
            for (dev.noxclient.module.Module m
                    : NoxClient.INSTANCE.modules.all()) {
                CodeSource cs = m.getClass().getProtectionDomain().getCodeSource();
                if (cs == null || cs.getLocation() == null) continue;
                String p = cs.getLocation().toString();
                if (!p.equals(loc.toString()) && !p.contains("fabric-loader")
                        && !p.startsWith("jar:")) {
                    return "module loaded outside jar: " + m.name;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** Force the hard block screen (GuiSetScreenMixin keeps it up anyway). */
    public static void maybeShowScreen(Minecraft mc) {
        if (!compromised()) return;
        if (!(mc.gui.screen() instanceof dev.noxclient.gui.TamperBlockScreen))
            mc.setScreenAndShow(new dev.noxclient.gui.TamperBlockScreen());
    }

    public static void warn() {
        long now = System.currentTimeMillis();
        if (now - lastWarn < 20_000) return;
        lastWarn = now;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null)
            mc.player.sendSystemMessage(Component.literal(
                "§4[NOX] WARNING: this copy of " + NoxClient.NAME + " was MODIFIED. "
                + "Do not enter your account — someone may be trying to steal it. "
                + "Redownload only from " + dev.noxclient.discord.DiscordLink.get()));
    }

    private static String readSig() {
        try (InputStream in = TamperGuard.class.getResourceAsStream("/assets/noxclient/sig.txt")) {
            if (in == null) return null;
            return new String(in.readAllBytes()).trim();
        } catch (Exception e) { return null; }
    }

    private static String selfHash() throws Exception {
        CodeSource cs = TamperGuard.class.getProtectionDomain().getCodeSource();
        if (cs == null) return null;
        URL loc = cs.getLocation();
        if (!loc.getPath().endsWith(".jar")) return null;   // classes dir → dev
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        // new File(loc.toURI()) handles Windows "/C:/..." paths correctly —
        // URI→URL→getPath() produced a path ZipFile rejects, making the whole
        // integrity check a silent no-op on Windows.
        try (ZipFile jar = new ZipFile(new java.io.File(loc.toURI()))) {
            var entries = new ArrayList<>(jar.stream()
                .filter(e -> (e.getName().startsWith("dev/noxclient/") && e.getName().endsWith(".class"))
                    || e.getName().equals("fabric.mod.json") || e.getName().equals("noxclient.mixins.json"))
                .map(java.util.zip.ZipEntry::getName).sorted().toList());
            entries.sort(Comparator.naturalOrder());
            for (String name : entries) {
                md.update(name.getBytes());
                try (InputStream in = jar.getInputStream(jar.getEntry(name))) {
                    byte[] buf = new byte[8192]; int r;
                    while ((r = in.read(buf)) != -1) md.update(buf, 0, r);
                }
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }
}
