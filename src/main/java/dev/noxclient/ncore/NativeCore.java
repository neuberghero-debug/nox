package dev.noxclient.ncore;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JNI bridge into the Rust native core (native/lib.rs → libnoxcore).
 *
 * Design: every sensitive primitive has a Java fallback — a cracker who
 * strips the lib gets the same behaviour, but MUST then patch Java checks
 * which are themselves distributed (Modul 30.2). The lib is extracted from
 * the jar once per run into a private temp dir with a salted name.
 */
public final class NativeCore {
    private static volatile boolean loaded;
    private static volatile boolean tried;

    public static boolean available() {
        if (!tried) load();
        return loaded;
    }

    private static synchronized void load() {
        if (tried) return;
        tried = true;
        try {
            String lib = libName();
            String res = "/natives/" + osDir() + "/" + lib;
            try (InputStream in = NativeCore.class.getResourceAsStream(res)) {
                if (in == null) return;
                Path dir = Files.createTempDirectory("noxn");
                dir.toFile().deleteOnExit();
                Path out = dir.resolve(lib);
                Files.copy(in, out);
                out.toFile().deleteOnExit();
                System.load(out.toString());
                loaded = true;
            }
        } catch (Throwable ignored) {}
    }

    private static String osDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "");
        String a = arch.contains("aarch64") || arch.contains("arm") ? "arm64" : "amd64";
        if (os.contains("win")) return "windows-" + a;
        if (os.contains("mac")) return "macos-" + a;
        return "linux-" + a;
    }

    private static String libName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return "noxcore.dll";
        if (os.contains("mac")) return "libnoxcore.dylib";
        return "libnoxcore.so";
    }

    /* ---- native entry points (see native/src/lib.rs) ---- */
    public static native String nHwid();
    public static native boolean nVerify(byte[] payload, byte[] sig, byte[] pubRaw32);
    public static native int nAntiDebug();
    public static native byte nKeyByte(int index);
    public static native byte nMaskByte(int index);
    public static native long nCheck(int index);
    public static native byte[] nWrap(byte[] data, String hwid);

    /** Reassemble the embedded public key from native fragments. */
    public static byte[] publicKeyRaw() {
        byte[] k = new byte[32];
        for (int i = 0; i < 32; i++)
            k[i] = (byte) (nKeyByte(i) ^ nMaskByte(i));
        return k;
    }

    /** hwid via native when available, else the Java Hwid fallback. */
    public static String hwid() {
        try {
            if (available()) {
                String h = nHwid();
                if (h != null && !h.isEmpty()) return h;
            }
        } catch (Throwable ignored) {}
        return dev.noxclient.premium.Hwid.get();
    }

    /** Ed25519 verify via native when available. */
    public static boolean verify(byte[] payload, byte[] sig, byte[] pubRaw32) {
        try {
            return available() && nVerify(payload, sig, pubRaw32);
        } catch (Throwable t) { return false; }
    }

    /** anti-debug bitmask: 1=tracer, 2=ptrace-blocked, 4=timing. -1 = no native. */
    public static int antiDebug() {
        try {
            return available() ? nAntiDebug() : -1;
        } catch (Throwable t) { return -1; }
    }

    /**
     * Symmetric secret wrap keyed to this machine (Modul 13 keystore).
     * Native: XOR with sha256(hwid + build salt). Java fallback (native
     * unavailable): XOR with sha256(hwid + domain tag) — still machine-bound,
     * just a different derivation domain.
     */
    public static byte[] wrap(byte[] data) {
        try {
            if (available()) {
                byte[] w = nWrap(data, hwid());
                if (w != null) return w;
            }
        } catch (Throwable ignored) {}
        try {
            byte[] k = java.security.MessageDigest.getInstance("SHA-256")
                .digest((hwid() + "|nox-ks").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] out = new byte[data.length];
            for (int i = 0; i < data.length; i++) out[i] = (byte) (data[i] ^ k[i % 32]);
            return out;
        } catch (Throwable t) { return data; }
    }

    /** distributed check value — 30.2 gates compare against this. */
    public static long check(int index) {
        try {
            return available() ? nCheck(index) : 0x4E4F58L + index; // 'NOX' fallback
        } catch (Throwable t) { return -1; }
    }
}
