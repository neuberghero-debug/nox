package dev.noxclient.chat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.noxclient.gui.Toasts;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.ChaCha20ParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.NamedParameterSpec;
import java.security.KeyFactory;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-to-client encrypted chat (Modul 21).
 *
 * X25519 key agreement (JDK XDH) + ChaCha20-Poly1305 AEAD. Private key is
 * persisted wrapped by NativeCore (machine-bound keystore, same as alt
 * tokens). Identity = base64url(pubkey) fingerprint — no accounts, no
 * phone numbers.
 *
 * Transport: dumb WebSocket relay (tools/nox_relay.py) that only sees
 * routing metadata {"type":"msg","to":fp} — ciphertext end-to-end.
 * Default relay is off; NOX_CHAT_RELAY env or /nox chatrelay <ws://…>
 * to set one. Messages arrive as purple chat lines + toast.
 */
public final class NoxChat {
    private static final Gson G = new Gson();
    private static KeyPair kp;
    private static String relay;
    private static WebSocket ws;
    private static final Map<String, SecretKeySpec> peers = new ConcurrentHashMap<>();
    private static Path keyFile;

    public static void init(Path noxDir) {
        keyFile = noxDir.resolve("chat.key");
        relay = System.getenv().getOrDefault("NOX_CHAT_RELAY", "");
        loadKey();
        dev.noxclient.api.AddonContext.COMMANDS.put("chat", NoxChat::cmd);
        dev.noxclient.api.AddonContext.COMMANDS.put("chatkey",
            a -> chat("§7[NOX chat] your fingerprint: §d" + fingerprint()));
        dev.noxclient.api.AddonContext.COMMANDS.put("chatrelay", a -> {
            if (a.length > 0) { relay = a[0]; disconnect(); chat("§a[NOX chat] relay set: " + relay); }
        });
    }

    public static String fingerprint() {
        if (kp == null) return "no-key";
        byte[] pub = kp.getPublic().getEncoded();
        // X25519 SubjectPublicKeyInfo: last 32 bytes are the raw key
        byte[] raw = new byte[32];
        System.arraycopy(pub, pub.length - 32, raw, 0, 32);
        return b64(raw);
    }

    /* ---------- commands ---------- */

    private static void cmd(String[] a) {
        // args arrive AFTER the command name: /nox chat <peer-fp> <msg…>
        if (a.length < 2) {
            chat("§7[NOX chat] /nox chat <peer-fingerprint> <msg…>");
            return;
        }
        String peer = a[0];
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < a.length; i++) sb.append(a[i]).append(' ');
        send(peer, sb.toString().trim());
    }

    /** Encrypt + route a message to a peer fingerprint. */
    public static void send(String peerFp, String text) {
        SecretKeySpec key = peers.computeIfAbsent(peerFp, k -> {
            try { return derive(k); } catch (Throwable t) { return null; }
        });
        if (key == null) { chat("§c[NOX chat] bad peer fingerprint"); return; }
        connect();
        if (ws == null) return;   // no relay / still connecting → silent retry on next send
        try {
            byte[] nonce = new byte[12];
            SecureRandom.getInstanceStrong().nextBytes(nonce);
            Cipher c = Cipher.getInstance("ChaCha20-Poly1305");
            c.init(Cipher.ENCRYPT_MODE, key, new ChaCha20ParameterSpec(nonce, 128));
            byte[] ct = c.doFinal(text.getBytes(StandardCharsets.UTF_8));
            JsonObject m = new JsonObject();
            m.addProperty("type", "msg");
            m.addProperty("from", fingerprint());
            m.addProperty("to", peerFp);
            m.addProperty("n", b64(nonce));
            m.addProperty("ct", b64(ct));
            ws.sendText(m.toString(), true);
            chat("§8[NOX→" + shortFp(peerFp) + "] §f" + text);
        } catch (Throwable t) { chat("§c[NOX chat] encrypt failed: " + t.getMessage()); }
    }

    private static void onPacket(String raw) {
        try {
            JsonObject m = G.fromJson(raw, JsonObject.class);
            if (!"msg".equals(m.get("type").getAsString())) return;
            if (!fingerprint().equals(m.get("to").getAsString())) return;  // not for us
            String from = m.get("from").getAsString();
            SecretKeySpec key = peers.computeIfAbsent(from, k -> {
                try { return derive(k); } catch (Throwable t) { return null; }
            });
            if (key == null) return;
            byte[] nonce = unb64(m.get("n").getAsString());
            byte[] ct = unb64(m.get("ct").getAsString());
            Cipher c = Cipher.getInstance("ChaCha20-Poly1305");
            c.init(Cipher.DECRYPT_MODE, key, new ChaCha20ParameterSpec(nonce, 128));
            String text = new String(c.doFinal(ct), StandardCharsets.UTF_8);
            chat("§d[NOX←" + shortFp(from) + "] §f" + text);
            Toasts.push("NOX chat ← " + shortFp(from), 0xFF7A2BE2);
        } catch (Throwable ignored) {}
    }

    /* ---------- crypto ---------- */

    private static SecretKeySpec derive(String peerFp) throws Exception {
        byte[] raw = unb64(peerFp);
        if (raw.length != 32) return null;
        // reconstruct peer X25519 pubkey: 12-byte prefix + raw key
        byte[] prefix = kp.getPublic().getEncoded();
        byte[] enc = new byte[prefix.length];
        System.arraycopy(prefix, 0, enc, 0, enc.length);
        System.arraycopy(raw, 0, enc, enc.length - 32, 32);
        PublicKey peer = KeyFactory.getInstance("X25519")
            .generatePublic(new java.security.spec.X509EncodedKeySpec(enc));
        KeyAgreement ka = KeyAgreement.getInstance("X25519");
        ka.init(kp.getPrivate());
        ka.doPhase(peer, true);
        byte[] shared = ka.generateSecret();
        // ECDH shared secret is identical on both sides → domain-tagged KDF
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(shared);
        md.update("nox-chat-v1".getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(md.digest(), "ChaCha20");
    }

    private static void loadKey() {
        try {
            if (Files.exists(keyFile)) {
                // format: "b64(pkcs8):b64(x509)" then machine-wrapped
                String s = new String(dev.noxclient.ncore.NativeCore.wrap(
                    Files.readAllBytes(keyFile)), StandardCharsets.UTF_8);
                String[] parts = s.split(":");
                KeyFactory kf = KeyFactory.getInstance("X25519");
                kp = new KeyPair(
                    kf.generatePublic(new java.security.spec.X509EncodedKeySpec(
                        Base64.getDecoder().decode(parts[1]))),
                    kf.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(
                        Base64.getDecoder().decode(parts[0]))));
                return;
            }
            KeyPairGenerator g = KeyPairGenerator.getInstance("X25519");
            g.initialize(new NamedParameterSpec("X25519"));
            kp = g.generateKeyPair();
            String blob = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded())
                + ":" + Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
            Files.createDirectories(keyFile.getParent());
            Files.write(keyFile, dev.noxclient.ncore.NativeCore.wrap(
                blob.getBytes(StandardCharsets.UTF_8)));
        } catch (Throwable t) {
            try {
                KeyPairGenerator g = KeyPairGenerator.getInstance("X25519");
                kp = g.generateKeyPair();
            } catch (Throwable t2) { kp = null; }
        }
    }

    /* ---------- transport ---------- */

    private static void connect() {
        if (relay.isEmpty()) { chat("§c[NOX chat] no relay — /nox chatrelay ws://host:port"); return; }
        if (ws != null) return;
        HttpClient.newHttpClient().newWebSocketBuilder()
            .buildAsync(URI.create(relay), new WebSocket.Listener() {
                private final StringBuilder buf = new StringBuilder();
                @Override public void onOpen(WebSocket w) {
                    ws = w; w.request(1);
                    JsonObject h = new JsonObject();
                    h.addProperty("type", "hello");
                    h.addProperty("fp", fingerprint());
                    w.sendText(h.toString(), true);
                }
                @Override public CompletionStage<?> onText(WebSocket w, CharSequence d, boolean last) {
                    buf.append(d);
                    if (last) { onPacket(buf.toString()); buf.setLength(0); }
                    w.request(1); return null;
                }
                @Override public void onError(WebSocket w, Throwable t) { ws = null; }
                @Override public CompletionStage<?> onClose(WebSocket w, int c, String r) {
                    ws = null; return null;
                }
            });
    }

    private static void disconnect() {
        if (ws != null) { try { ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye"); } catch (Throwable ignored) {} ws = null; }
    }

    private static void chat(String s) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal(s));
        else System.out.println(s);
    }

    private static String b64(byte[] b) { return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    private static byte[] unb64(String s) { return Base64.getUrlDecoder().decode(s); }
    private static String shortFp(String fp) { return fp.length() > 10 ? fp.substring(0, 10) + "…" : fp; }
}
