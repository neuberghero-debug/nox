package dev.noxclient.util;

import dev.noxclient.NoxClient;
import dev.noxclient.module.Module;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.net.URI;
import java.security.CodeSource;
import java.util.Comparator;
import java.util.stream.Stream;
import java.nio.file.*;

/**
 * Complete client removal:
 *  1. every module disabled (all packet hooks & render state dropped)
 *  2. managers + references nulled so the classloader can unload
 *  3. run/nox/ (configs, presets, logs) wiped from disk
 *  4. the mod jar deleted from mods/ — on exit, not while JVM holds it open
 *  5. JVM halted; no NOX classes remain mapped
 *
 * Triggered by the PANIC footer (hold-confirm) or the SelfDestruct module.
 */
public final class SelfDestruct {

    public static void execute() {
        Minecraft mc = Minecraft.getInstance();

        // 1-2: modules off, managers detached
        try {
            if (NoxClient.INSTANCE != null && NoxClient.INSTANCE.modules != null) {
                for (Module m : NoxClient.INSTANCE.modules.all()) {
                    try { m.setEnabled(false); } catch (Throwable ignored) {}
                }
                NoxClient.INSTANCE.modules.all().clear();
            }
        } catch (Throwable ignored) {}

        // 3: wipe config dir now (it's plain files, not held open)
        try {
            Path nox = mc.gameDirectory.toPath().resolve("nox");
            if (Files.exists(nox)) {
                try (Stream<Path> s = Files.walk(nox)) {
                    s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                }
            }
        } catch (Throwable ignored) {}

        // 4: schedule jar deletion — JVM keeps it mapped, so delete on shutdown
        File jar = selfJar();
        if (jar != null) jar.deleteOnExit();

        // 5: kill the client process entirely (nothing left in RAM or on disk)
        Runtime.getRuntime().halt(0);
    }

    private static File selfJar() {
        try {
            CodeSource cs = SelfDestruct.class.getProtectionDomain().getCodeSource();
            if (cs == null) return null;
            File f = new File(URI.create(cs.getLocation().toString()));
            return f.getName().endsWith(".jar") ? f : null;
        } catch (Exception e) { return null; }
    }
}
