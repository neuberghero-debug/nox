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
    private static volatile Boolean compromised;
    private static volatile boolean screenShown;
    private static long lastWarn;

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

    /** Opens the warning screen once when a compromised jar is detected. */
    public static void maybeShowScreen(Minecraft mc) {
        if (!compromised() || screenShown) return;
        screenShown = true;
        mc.setScreenAndShow(new dev.noxclient.gui.TamperScreen());
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
