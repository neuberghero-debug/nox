package dev.noxclient.premium;

import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Enumeration;

/**
 * Stable machine fingerprint: sha256(os.arch | machine-id | primary MAC).
 * No usernames, no paths — components are hashed individually then combined.
 * Linux: /etc/machine-id or /var/lib/dbus/machine-id.
 * Windows: HKLM MachineGuid via reg query.
 * macOS: IOPlatformUUID via ioreg.
 */
public final class Hwid {
    private static volatile String cached;

    public static String get() {
        String c = cached;
        if (c != null) return c;
        synchronized (Hwid.class) {
            if (cached == null) cached = compute();
            return cached;
        }
    }

    private static String compute() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            mix(md, System.getProperty("os.name", "?") + "|" + System.getProperty("os.arch", "?"));
            mix(md, machineId());
            mix(md, primaryMac());
            return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(md.digest()).substring(0, 24);
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static void mix(MessageDigest md, String s) {
        md.update(s.getBytes(StandardCharsets.UTF_8));
        md.update((byte) 0);
    }

    private static String machineId() {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("linux")) {
                for (String p : new String[]{"/etc/machine-id", "/var/lib/dbus/machine-id"}) {
                    Path f = Path.of(p);
                    if (Files.exists(f)) {
                        String s = Files.readString(f).trim();
                        if (s.length() >= 8) return s;
                    }
                }
            } else if (os.contains("win")) {
                Process pr = new ProcessBuilder("reg", "query",
                    "HKLM\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid")
                    .redirectErrorStream(true).start();
                String out = new String(pr.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                int i = out.indexOf("REG_SZ");
                if (i >= 0) return out.substring(i + 6).trim();
            } else if (os.contains("mac")) {
                Process pr = new ProcessBuilder("ioreg", "-rd1", "-c", "IOPlatformExpertDevice")
                    .redirectErrorStream(true).start();
                String out = new String(pr.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                int i = out.indexOf("IOPlatformUUID");
                if (i >= 0) {
                    int q1 = out.indexOf('"', out.indexOf('=', i));
                    int q2 = out.indexOf('"', q1 + 1);
                    if (q1 > 0 && q2 > q1) return out.substring(q1 + 1, q2);
                }
            }
        } catch (Exception ignored) {}
        return "no-machine-id";
    }

    private static String primaryMac() {
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            String best = null;
            while (ifs != null && ifs.hasMoreElements()) {
                NetworkInterface nif = ifs.nextElement();
                if (nif.isLoopback() || nif.isVirtual() || !nif.isUp()) continue;
                byte[] mac = nif.getHardwareAddress();
                if (mac == null || mac.length != 6) continue;
                var sb = new StringBuilder();
                for (byte b : mac) sb.append(String.format("%02x", b));
                String s = sb.toString();
                if (best == null || nif.getName().startsWith("e") || nif.getName().startsWith("w"))
                    best = s;
            }
            return best != null ? best : "no-mac";
        } catch (Exception e) {
            return "no-mac";
        }
    }
}
