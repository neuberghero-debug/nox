package dev.noxclient.update;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * External swap helper — spawned as its own JVM right before the game exits,
 * so the running jar's file lock (Windows) is released by the time we move it.
 *
 *   java -cp <newJar> dev.noxclient.update.Apply <pid> <oldJar> <stagedJar> <bakJar>
 *
 * Waits for <pid> to die (max 60s), then: oldJar → bakJar, stagedJar → oldJar's
 * name. Writes <bakJar>.result with "ok"/"fail:<err>" for the next boot.
 */
public final class Apply {
    public static void main(String[] a) throws Exception {
        Path oldJar = Path.of(a[1]);
        Path staged = Path.of(a[2]);
        Path bak = Path.of(a[3]);
        Path result = bak.resolveSibling(bak.getFileName() + ".result");
        try {
            waitForExit(Long.parseLong(a[0]));
            Files.move(oldJar, bak, StandardCopyOption.REPLACE_EXISTING);
            Files.move(staged, oldJar, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(result, "ok");
        } catch (Throwable t) {
            Files.writeString(result, "fail:" + t);
        }
    }

    private static void waitForExit(long pid) throws InterruptedException {
        var ph = ProcessHandle.of(pid);
        if (ph.isEmpty()) { Thread.sleep(1500); return; }
        long deadline = System.currentTimeMillis() + 60_000;
        while (ph.get().isAlive() && System.currentTimeMillis() < deadline)
            Thread.sleep(250);
    }
}
