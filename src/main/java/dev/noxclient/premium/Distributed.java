package dev.noxclient.premium;

import dev.noxclient.ncore.NativeCore;

/**
 * Distributed premium checks (Modul 30.2). Instead of one big gate, many
 * small independent predicates are sprinkled through premium paths — a
 * cracker patching assertPremium() alone still hits these.
 *
 * Each check is tiny on purpose; together they form the consensus.
 * premiumAllowed() requires ALL of them.
 */
public final class Distributed {
    private Distributed() {}

    /** native-or-fallback check value must match the compiled constant. */
    public static boolean c1() { return NativeCore.check(1) != -1; }
    /** public key reassembles to 32 bytes. */
    public static boolean c2() {
        try { return NativeCore.publicKeyRaw().length == 32 || !NativeCore.available(); }
        catch (Throwable t) { return false; }
    }
    /** hwid present and non-trivial. */
    public static boolean c3() { String h = NativeCore.hwid(); return h != null && h.length() >= 8; }
    /** clock not rolled back. */
    public static boolean c4() { return !LicenseManager.clockRolledBack(); }
    /** grace window sane. */
    public static boolean c5() { return !LicenseManager.graceExpired(); }
    /** tamper sig subsystem present. */
    public static boolean c6() { return !dev.noxclient.util.TamperGuard.compromised(); }
    /** update signer class intact. */
    public static boolean c7() {
        try { Class.forName("dev.noxclient.update.UpdateManager"); return true; }
        catch (Throwable t) { return false; }
    }
    /** native anti-debug didn't trip (or native absent → skip). */
    public static boolean c8() { int f = NativeCore.antiDebug(); return f <= 0 || (f & 1) == 0; }

    /** Consensus — every distributed check must pass. */
    public static boolean premiumAllowed() {
        return LicenseManager.premium()
            && c1() && c2() && c3() && c4() && c5() && c6() && c7() && c8();
    }

    /** Laxer variant for cosmetic-only gates (still stops single-point patches). */
    public static boolean cosmeticsAllowed() {
        return LicenseManager.capeUnlocked() && c4() && c6();
    }
}
