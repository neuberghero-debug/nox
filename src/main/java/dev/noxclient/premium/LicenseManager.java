package dev.noxclient.premium;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Premium gate — Ed25519-signed license tokens, verified fully offline.
 *
 * Token format: NOX1.<b64url(payload)>.<b64url(signature)>
 * Payload JSON: {"tier":"premium","lid":"a1b2c3","exp":1780000000000,"sub":"discordId"}
 *
 * Keys are issued by the NOX Discord bot (/issue) after crypto payment.
 * The private key never ships with the client.
 */
public final class LicenseManager {
    /** NOX license public key (raw 32B Ed25519, embedded as X.509 DER). */
    private static final byte[] PUB_DER;
    static {
        byte[] raw = Base64.getDecoder().decode("wAoUOK5sP+q9eqjoXY7V4C//P+v4hel4AMNvP2H8/qE=");
        byte[] prefix = {0x30, 0x2A, 0x30, 0x05, 0x06, 0x03, 0x2B, 0x65, 0x70, 0x03, 0x21, 0x00};
        PUB_DER = new byte[prefix.length + raw.length];
        System.arraycopy(prefix, 0, PUB_DER, 0, prefix.length);
        System.arraycopy(raw, 0, PUB_DER, prefix.length, raw.length);
    }

    public enum Result { OK, BAD_FORMAT, BAD_SIGNATURE, EXPIRED }

    private static volatile boolean premium;
    private static volatile boolean capeUnlocked;
    private static volatile String licenseId = "";
    private static volatile long expires = 0;

    public static boolean premium() { return premium; }
    /** One-time custom-cape upload entitlement (tier "cape", no expiry). */
    public static boolean capeUnlocked() { return premium || capeUnlocked; }
    public static String licenseId() { return licenseId; }
    public static long expires() { return expires; }

    public static void init() {
        try {
            Path p = keyFile();
            if (Files.exists(p) && check(Files.readString(p).trim()) == Result.OK)
                premium = true;
        } catch (Exception ignored) {}
        try {
            Path c = capeFile();
            if (Files.exists(c) && checkTier(Files.readString(c).trim(), "cape") == Result.OK)
                capeUnlocked = true;
        } catch (Exception ignored) {}
    }

    /** One-time cape unlock — "cape" tier tokens never expire. */
    public static Result unlockCape(String key) {
        Result r = checkTier(key, "cape");
        if (r != Result.OK) return r;
        try {
            Path c = capeFile();
            Files.createDirectories(c.getParent());
            Files.writeString(c, key.trim());
        } catch (Exception ignored) {}
        capeUnlocked = true;
        return Result.OK;
    }

    /** Activate a key. On success persists it and flips the premium gate. */
    public static Result unlock(String key) {
        Result r = check(key);
        if (r != Result.OK) return r;
        try {
            Path p = keyFile();
            Files.createDirectories(p.getParent());
            Files.writeString(p, key.trim());
        } catch (Exception ignored) {}
        premium = true;
        return Result.OK;
    }

    private static Result check(String key) {
        try {
            String[] parts = key.trim().split("\\.");
            if (parts.length != 3 || !parts[0].equals("NOX1")) return Result.BAD_FORMAT;
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            byte[] sig = Base64.getUrlDecoder().decode(parts[2]);
            if (!verify(payload, sig)) return Result.BAD_SIGNATURE;
            JsonObject o = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8))
                .getAsJsonObject();
            if (!"premium".equals(o.get("tier").getAsString())) return Result.BAD_FORMAT;
            long exp = o.get("exp").getAsLong();
            if (System.currentTimeMillis() > exp) return Result.EXPIRED;
            licenseId = o.has("lid") ? o.get("lid").getAsString() : "";
            expires = exp;
            return Result.OK;
        } catch (Exception e) { return Result.BAD_FORMAT; }
    }

    /** Signature + tier check without touching premium state. exp<=0 = never. */
    private static Result checkTier(String key, String tier) {
        try {
            String[] parts = key.trim().split("\\.");
            if (parts.length != 3 || !parts[0].equals("NOX1")) return Result.BAD_FORMAT;
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            byte[] sig = Base64.getUrlDecoder().decode(parts[2]);
            if (!verify(payload, sig)) return Result.BAD_SIGNATURE;
            JsonObject o = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8))
                .getAsJsonObject();
            if (!tier.equals(o.get("tier").getAsString())) return Result.BAD_FORMAT;
            long exp = o.has("exp") ? o.get("exp").getAsLong() : 0;
            if (exp > 0 && System.currentTimeMillis() > exp) return Result.EXPIRED;
            return Result.OK;
        } catch (Exception e) { return Result.BAD_FORMAT; }
    }

    private static boolean verify(byte[] payload, byte[] sig) throws Exception {
        PublicKey pk = KeyFactory.getInstance("Ed25519")
            .generatePublic(new X509EncodedKeySpec(PUB_DER));
        Signature s = Signature.getInstance("Ed25519");
        s.initVerify(pk);
        s.update(payload);
        return s.verify(sig);
    }

    private static Path keyFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("nox/license.key");
    }

    private static Path capeFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("nox/cape.key");
    }
}
