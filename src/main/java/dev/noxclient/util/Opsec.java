package dev.noxclient.util;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Baseline opsec — always on, not a setting:
 *  · brand replies "vanilla" to every minecraft:brand query
 *  · every custom payload outside the minecraft: namespace is stripped
 *    (fabric handshake + mod channels never leave the wire)
 * Extra hardening (also strip minecraft:-namespaced extras) is optional and
 * lives in the Multiplexer screen — persisted via ConfigManager.
 */
public final class Opsec {
    /** Aggressive mode: also drop minecraft:-namespaced non-brand payloads. */
    public static volatile boolean stripAll = true;

    /** Brand payload type → always answer vanilla. */
    public static boolean isBrand(CustomPacketPayload p) {
        return p instanceof net.minecraft.network.protocol.common.custom.BrandPayload;
    }

    /** True when this outbound payload must not reach the server. */
    public static boolean strip(CustomPacketPayload p) {
        if (isBrand(p)) return false;              // brand is spoofed, not stripped
        var id = p.type() != null ? p.type().id() : null;
        if (id == null) return true;               // untyped → unknown → drop
        if (!"minecraft".equals(id.getNamespace())) return true;   // mod/fabric channel
        if (stripAll) {
            // vanilla clients only ever send brand + a handful of login/game
            // payloads — whitelist the safe set, drop the rest
            String path = id.getPath();
            return !(path.equals("brand") || path.equals("register") || path.equals("unregister")
                || path.startsWith("debug/") || path.startsWith("login/"));
        }
        return false;
    }
}
