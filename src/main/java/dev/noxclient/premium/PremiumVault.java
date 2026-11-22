package dev.noxclient.premium;

import dev.noxclient.NoxClient;
import dev.noxclient.module.Module;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypted premium module pack — the anti-crack that actually works in an
 * open-source client: premium module CLASSES are not in the free jar at all.
 * They ship as an AES-256-GCM blob (assets/noxclient/premium.nxp) and are only
 * decrypted in memory after a signed license verifies. Stripping the premium
 * gate yields nothing — without a real NOX1 key the pack is ciphertext.
 *
 * Blob: [12B GCM nonce][ciphertext+16B tag]; key = SHA-256("NOX-PREM-1" || k),
 * k = base64url secret carried inside the signed license payload ("k" field).
 */
public final class PremiumVault {
    private static final String BLOB = "/assets/noxclient/premium.nxp";
    private static final byte[] KDF_SALT = "NOX-PREM-1".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    private static volatile boolean loaded;
    private static volatile List<String> registered = List.of();

    public static boolean loaded() { return loaded; }
    public static List<String> modules() { return registered; }

    /** Premium module classes — private sources (src/premium, gitignored).
     *  In dev runs they're on the classpath; in prod they exist only inside
     *  the decrypted pack. Keep in sync with the premium source tree. */
    private static final String[] PREMIUM_CLASSES = {
        "dev.noxclient.module.combat.AntiTrap",
        "dev.noxclient.module.combat.AutoMace",
        "dev.noxclient.module.combat.AutoPot",
        "dev.noxclient.module.combat.AutoWeb",
        "dev.noxclient.module.donutsmp.AHSniper",
        "dev.noxclient.module.donutsmp.AntiESPBypass",
        "dev.noxclient.module.donutsmp.AntiVanish",
        "dev.noxclient.module.donutsmp.AutoFlip",
        "dev.noxclient.module.donutsmp.AutoTotemOrder",
        "dev.noxclient.module.donutsmp.RTPBaseFinder",
        "dev.noxclient.module.donutsmp.TunnelBaseFinder",
        "dev.noxclient.module.misc.FriendSync",
        "dev.noxclient.module.misc.PvpCoach",
        "dev.noxclient.module.movement.Blink",
        "dev.noxclient.module.movement.FlyBypass",
    };

    /** Decrypt + register all premium modules. No-op without verified premium. */
    public static synchronized void load() {
        if (loaded) return;
        if (devRun()) { loadDev(); return; }   // classes dir — pack isn't needed
        byte[] k = LicenseManager.packKey();
        if (k == null) return;
        try {
            byte[] blob = readBlob();
            if (blob == null) return;   // free self-build ships no pack
            byte[] zip = decrypt(blob, deriveKey(k));
            Map<String, byte[]> classes = new HashMap<>();
            Map<String, byte[]> resources = new HashMap<>();
            try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(zip))) {
                for (ZipEntry e; (e = z.getNextEntry()) != null; ) {
                    if (e.isDirectory()) continue;
                    byte[] data = z.readAllBytes();
                    if (e.getName().endsWith(".class")) classes.put(e.getName(), data);
                    else resources.put(e.getName(), data);
                }
            }
            PackLoader loader = new PackLoader(classes, resources,
                PremiumVault.class.getClassLoader());
            List<String> names = new ArrayList<>();
            for (String path : classes.keySet()) {
                if (!path.startsWith("dev/noxclient/module/") || path.contains("$")) continue;
                String bin = path.substring(0, path.length() - 6).replace('/', '.');
                try {
                    Class<?> c = Class.forName(bin, true, loader);
                    if (!Module.class.isAssignableFrom(c)) continue;
                    Module m = (Module) c.getDeclaredConstructor().newInstance();
                    NoxClient.INSTANCE.modules.register(m);
                    names.add(m.name);
                } catch (Throwable t) {
                    System.err.println("[NOX premium] " + bin + " failed: " + t);
                }
            }
            registered = List.copyOf(names);
            loaded = true;
            System.out.println("[NOX premium] pack unlocked — " + names.size() + " modules");
        } catch (Throwable t) {
            System.err.println("[NOX premium] pack unlock failed: " + t);
        }
    }

    /** Drop premium modules (license deactivated mid-session). */
    public static synchronized void unload() {
        for (String n : registered) NoxClient.INSTANCE.modules.remove(n);
        registered = List.of();
        loaded = false;
    }

    private static byte[] readBlob() throws Exception {
        try (InputStream in = PremiumVault.class.getResourceAsStream(BLOB)) {
            if (in == null) return null;   // free self-build — no pack shipped
            return in.readAllBytes();
        }
    }

    /** Classes-dir run (gradle runClient): premium sources compiled into main
     *  output are already on the classpath — instantiate directly. License is
     *  still checked; the pack path stays the prod-only mechanism. */
    private static boolean devRun() {
        try {
            var src = PremiumVault.class.getProtectionDomain().getCodeSource();
            return src == null || !src.getLocation().getPath().endsWith(".jar");
        } catch (Throwable t) { return false; }
    }

    private static void loadDev() {
        List<String> names = new ArrayList<>();
        for (String bin : PREMIUM_CLASSES) {
            try {
                Class<?> c = Class.forName(bin);
                if (!Module.class.isAssignableFrom(c)) continue;
                Module m = (Module) c.getDeclaredConstructor().newInstance();
                NoxClient.INSTANCE.modules.register(m);
                names.add(m.name);
            } catch (Throwable ignored) {}
        }
        registered = List.copyOf(names);
        loaded = true;
        if (!names.isEmpty())
            System.out.println("[NOX premium] dev classpath — " + names.size() + " modules");
    }

    private static SecretKeySpec deriveKey(byte[] k) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(KDF_SALT);
        md.update(k);
        return new SecretKeySpec(md.digest(), "AES");
    }

    private static byte[] decrypt(byte[] blob, SecretKeySpec key) throws Exception {
        if (blob.length < 12 + 16) throw new IllegalStateException("premium.nxp truncated");
        byte[] nonce = new byte[12];
        System.arraycopy(blob, 0, nonce, 0, 12);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        return c.doFinal(blob, 12, blob.length - 12);
    }

    /** In-memory loader over the decrypted pack. Parent-first so premium
     *  modules resolve Module/MC classes through the normal classpath. */
    private static final class PackLoader extends ClassLoader {
        private final Map<String, byte[]> classes, resources;

        PackLoader(Map<String, byte[]> classes, Map<String, byte[]> resources, ClassLoader parent) {
            super(parent);
            this.classes = classes;
            this.resources = resources;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] b = classes.get(name.replace('.', '/') + ".class");
            if (b == null) throw new ClassNotFoundException(name);
            return defineClass(name, b, 0, b.length);
        }

        @Override
        protected java.net.URL findResource(String name) {
            byte[] b = resources.get(name);
            if (b == null) return null;
            try {
                return new java.net.URL(null, "noxpack:///" + name,
                    new java.net.URLStreamHandler() {
                        @Override protected java.net.URLConnection openConnection(java.net.URL u) {
                            return new java.net.URLConnection(u) {
                                @Override public void connect() {}
                                @Override public InputStream getInputStream() {
                                    return new ByteArrayInputStream(b);
                                }
                            };
                        }
                    });
            } catch (java.net.MalformedURLException e) { return null; }
        }
    }

    private PremiumVault() {}
}
