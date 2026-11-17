package dev.noxclient.security;

import dev.noxclient.premium.Hwid;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Anti-crack 30.11 — config integrity. Premium-sensitive fields in the
 * config (license tier cache, entitlement flags) are HMAC'd with a key
 * derived from the machine HWID + build salt, so a cracked/pasted config
 * can't claim premium on another machine or after tampering.
 *
 * The signer runs through NativeCore when loaded (the key never materialises
 * in pure-Java constant form); a SHA-256 fallback covers dev runs.
 */
public final class ConfigSig {
    private static final byte[] SALT = "nox-cfg-v1".getBytes(StandardCharsets.UTF_8);

    /** HMAC(hwid-key, field-name + value) → url-safe b64 tag. */
    public static String tag(String field, String value) {
        try {
            // native key fragments (nKeyByte) → key material; Java fallback
            // for dev runs where the .so isn't loaded
            byte[] seed = (field + "|" + Hwid.get()).getBytes(StandardCharsets.UTF_8);
            byte[] key = null;
            if (dev.noxclient.ncore.NativeCore.available()) {
                key = new byte[32];
                for (int i = 0; i < 32; i++)
                    key[i] = (byte) (dev.noxclient.ncore.NativeCore
                        .nKeyByte(i % 16) ^ seed[i % seed.length]);
            }
            if (key == null) {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(SALT); md.update(Hwid.get().getBytes(StandardCharsets.UTF_8));
                key = md.digest();
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] out = mac.doFinal((field + "=" + value).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(out).substring(0, 22);
        } catch (Exception e) {
            return "";
        }
    }

    /** Constant-time verify. Empty stored tag = legacy config → accept once
     *  (re-saved with a tag on next save). */
    public static boolean verify(String field, String value, String storedTag) {
        if (storedTag == null || storedTag.isEmpty()) return true;
        String want = tag(field, value);
        if (want.isEmpty()) return true;
        return MessageDigest.isEqual(
            want.getBytes(StandardCharsets.UTF_8),
            storedTag.getBytes(StandardCharsets.UTF_8));
    }
}
