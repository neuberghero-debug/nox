package dev.noxclient.security;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Anti-crack 30.7 — VM-level injection watch:
 *  - JVM launch args scanned for -javaagent / -agentlib / -Xdebug / jdwp
 *    (dev builds whitelist the Gradle instrumentation agent + loom agents).
 *  - Loaded native libraries scanned (Linux /proc/self/maps) for known
 *    injector/inspector signatures (frida, xposed, substrate, cheat-engine
 *    linux ports, jvmti redefinition helpers).
 *  - Instrumentation presence (if an agent attached post-launch it leaves
 *    a loaded agent lib even when the arg isn't ours).
 *
 * Deviations report to FailSafe; nothing here throws — a VM that can't be
 * inspected just doesn't produce findings.
 */
public final class VmGuard {
    private static final Set<String> ARG_BAD = Set.of(
        "-xdebug", "-agentlib:", "-xrunjdwp", "-javaagent:");
    private static final Set<String> ARG_OK_SUBSTR = Set.of(
        "gradle-instrumentation-agent", "fabric-loader", "devlaunchinjector",
        "jmxremote");
    private static final Set<String> LIB_BAD = Set.of(
        "frida", "xposed", "substrate", "libinject", "ceserver",
        "libvmtrace", "jdwpagent", "redefineclasses");

    private static volatile Boolean instrumented;
    private static volatile long nextScan;

    public static boolean instrumented() {
        Boolean b = instrumented;
        if (b != null && System.currentTimeMillis() < nextScan) return b;
        boolean r = compute();
        instrumented = r;
        nextScan = System.currentTimeMillis() + 60_000; // rescan every 60s
        return r;
    }

    private static boolean compute() {
        try {
            for (String a : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                String l = a.toLowerCase(Locale.ROOT);
                for (String bad : ARG_BAD) {
                    if (l.startsWith(bad)) {
                        boolean whitelisted = false;
                        for (String ok : ARG_OK_SUBSTR)
                            if (l.contains(ok)) { whitelisted = true; break; }
                        if (!whitelisted) return true;
                    }
                }
            }
        } catch (Throwable ignored) { }
        return badLibLoaded();
    }

    /** Linux: scan the maps file once per rescan window. */
    private static boolean badLibLoaded() {
        try {
            Path maps = Path.of("/proc/self/maps");
            if (!Files.exists(maps)) return false;
            List<String> lines = Files.readAllLines(maps);
            for (String line : lines) {
                int i = line.lastIndexOf('/');
                if (i < 0) continue;
                String lib = line.substring(i + 1).toLowerCase(Locale.ROOT);
                for (String bad : LIB_BAD)
                    if (lib.contains(bad)) return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }
}
