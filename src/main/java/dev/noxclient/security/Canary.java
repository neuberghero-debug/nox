package dev.noxclient.security;

import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

/**
 * Canary (Modul 30.3) — reflection-based integrity watch over the security
 * layer itself. Hashes the PUBLIC API SHAPE of the critical classes
 * (method names + return types, sorted). If a cracker strips a check method
 * from LicenseManager / Distributed / TamperGuard / NativeCore, the shape
 * hash changes and the canary reports — without shipping a hardcoded hash
 * of our own source (which would break on every legit build).
 *
 * The expected shapes live as signature LISTS here; compare() returns the
 * names of missing/altered methods so TamperWarner can escalate with a
 * precise reason. Empty list = intact.
 */
public final class Canary {

    /** Methods that must exist on each class for the security layer to be whole. */
    private static final String[][] EXPECTED = {
        {"dev.noxclient.premium.Distributed",
            "premiumAllowed", "c1", "c2", "c3", "c4", "c5", "c6", "c7", "c8"},
        {"dev.noxclient.premium.LicenseManager",
            "premium", "assertPremium", "tick", "clockRolledBack", "graceExpired",
            "unlock", "licenseId", "capeUnlocked"},
        {"dev.noxclient.util.TamperGuard",
            "verifyAsync", "compromised", "tick", "verified", "injectionDetected",
            "maybeShowScreen", "warn"},
        {"dev.noxclient.ncore.NativeCore",
            "available", "hwid", "verify", "antiDebug", "check", "wrap",
            "publicKeyRaw"},
        {"dev.noxclient.update.UpdateManager",
            "init", "tick"},
        {"dev.noxclient.security.WebhookSec",
            "alert", "available", "sanitize", "anonId"},
    };

    /** Names of missing methods ("Class#method") — empty = all present. */
    public static List<String> missing() {
        List<String> out = new ArrayList<>();
        for (String[] entry : EXPECTED) {
            String cls = entry[0];
            try {
                Class<?> c = Class.forName(cls);
                TreeSet<String> have = new TreeSet<>();
                for (Method m : c.getDeclaredMethods()) have.add(m.getName());
                for (int i = 1; i < entry.length; i++)
                    if (!have.contains(entry[i])) out.add(cls + "#" + entry[i]);
            } catch (Throwable t) {
                out.add(cls + " (class missing)");
            }
        }
        return out;
    }

    /** True when every expected method is present (FailSafe hook). */
    public static boolean ok() { return missing().isEmpty(); }

    /** Compact shape fingerprint — stable per build, changes on tamper. */
    public static String shapeHash() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String[] entry : EXPECTED) {
                try {
                    Class<?> c = Class.forName(entry[0]);
                    Method[] ms = c.getDeclaredMethods();
                    Arrays.sort(ms, java.util.Comparator.comparing(Method::getName));
                    for (Method m : ms) {
                        md.update(m.getName().getBytes());
                        md.update(m.getReturnType().getName().getBytes());
                    }
                } catch (Throwable t) {
                    md.update("missing".getBytes());
                }
            }
            StringBuilder sb = new StringBuilder();
            byte[] d = md.digest();
            for (int i = 0; i < 12; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (Throwable t) { return "0"; }
    }
}
