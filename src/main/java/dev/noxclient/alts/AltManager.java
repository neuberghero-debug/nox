package dev.noxclient.alts;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.network.chat.Component;
import sun.misc.Unsafe;

import java.io.*;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Account manager — token login, offline alts, per-connection proxy.
 * Identity swap replaces Minecraft.user via Unsafe (field is private final);
 * applies to the NEXT server join (handshake + session-server auth read the
 * User object). Chat-signing keys stay on the main account.
 */
public final class AltManager {
    public static class Alt {
        public String name, uuid, token;
        public boolean offline;
        public String group = "";                    // Modul 13: account groups
        public long lastUsed;
        Alt(String n, String u, String t, boolean off) { name=n; uuid=u; token=t; offline=off; }
    }

    private static final List<Alt> alts = new ArrayList<>();
    private static Alt active;
    private static String status = "";
    public static final AltProxy proxy = new AltProxy();
    private static Unsafe UNSAFE;

    public static List<Alt> alts() { return alts; }
    public static Alt active() { return active; }
    public static String status() { return status; }

    /** Distinct group tags across stored accounts (Modul 13). */
    public static Set<String> groups() {
        Set<String> g = new TreeSet<>();
        for (Alt a : alts) if (!a.group.isEmpty()) g.add(a.group);
        return g;
    }

    /** Most recently used alt, for the quick-switch row. */
    public static Alt lastUsed() {
        Alt best = null;
        for (Alt a : alts) if (best == null || a.lastUsed > best.lastUsed) best = a;
        return best;
    }

    /* ---------- login ---------- */

    /** Async: resolve token → profile via minecraftservices, then swap. */
    public static void loginToken(String token) {
        token = token.trim();
        if (token.isEmpty()) { status = "empty token"; return; }
        status = "resolving token…";
        String tok = token;
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(
                    "https://api.minecraftservices.com/minecraft/profile").openConnection();
                c.setRequestProperty("Authorization", "Bearer " + tok);
                c.setConnectTimeout(8000); c.setReadTimeout(8000);
                if (c.getResponseCode() != 200) { status = "token rejected (" + c.getResponseCode() + ")"; return; }
                String body = new String(c.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                JsonObject j = new Gson().fromJson(body, JsonObject.class);
                String raw = j.get("id").getAsString();
                UUID uuid = UUID.fromString(raw.replaceFirst(
                    "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
                String name = j.get("name").getAsString();
                Alt a = new Alt(name, uuid.toString(), tok, false);
                Minecraft.getInstance().execute(() -> apply(a, true));
            } catch (Exception e) { status = "resolve failed: " + e.getMessage(); }
        }, "nox-alt").start();
    }

    /** Offline-mode alt (cracked/test servers). */
    public static void loginOffline(String name) {
        name = name.trim();
        if (name.length() < 3) { status = "name too short"; return; }
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        apply(new Alt(name, uuid.toString(), "", true), true);
    }

    public static void remove(Alt a) {
        alts.remove(a);
        if (a == active) { active = null; }
        save();
    }

    /** Quick-switch: "/nox alt <name>" — swap to a stored account by name. */
    public static void quickSwitch(String name) {
        Alt best = null;
        for (Alt a : alts)
            if (a.name.equalsIgnoreCase(name)) { best = a; break; }
        if (best == null) { status = "no alt '" + name + "'"; return; }
        apply(best, true);
    }

    /** Swap identity; if connected, drop to the multiplayer screen. */
    public static void apply(Alt a, boolean save) {
        try {
            User u = new User(a.name, UUID.fromString(a.uuid), a.offline ? "0" : a.token,
                Optional.empty(), Optional.empty());
            a.lastUsed = System.currentTimeMillis();
            unsafe().putObject(Minecraft.getInstance(), userOffset(), u);
            if (!alts.contains(a)) alts.add(a);
            active = a;
            status = "now " + a.name + (a.offline ? " (offline)" : "");
            if (save) save();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendSystemMessage(
                    Component.literal("§7[NOX] §faccount → §d" + a.name + "§8 — rejoin to play"));
                mc.disconnectWithProgressScreen();
            }
        } catch (Throwable e) { status = "swap failed: " + e.getMessage(); }
    }

    /** Netty handler for the next connection, or null. */
    public static io.netty.channel.ChannelHandler proxyHandler(boolean socketChannel) {
        return socketChannel ? proxy.handler() : null;
    }

    /* ---------- persistence ---------- */

    private static File file() {
        return new File(new File(Minecraft.getInstance().gameDirectory, "nox"), "alts.json");
    }

    public static void load() {
        try {
            File f = file();
            if (!f.exists()) return;
            JsonObject j = new Gson().fromJson(new FileReader(f), JsonObject.class);
            for (var e : j.getAsJsonArray("accounts")) {
                JsonObject o = e.getAsJsonObject();
                Alt a = new Alt(o.get("name").getAsString(), o.get("uuid").getAsString(),
                    "", o.get("offline").getAsBoolean());
                if (o.has("enc"))
                    a.token = unwrap(o.get("enc").getAsString());
                else if (o.has("token"))
                    a.token = o.get("token").getAsString();   // legacy plaintext → migrated on save
                if (o.has("group")) a.group = o.get("group").getAsString();
                if (o.has("lastUsed")) a.lastUsed = o.get("lastUsed").getAsLong();
                alts.add(a);
            }
            if (j.has("proxy")) {
                JsonObject p = j.getAsJsonObject("proxy");
                proxy.type = AltProxy.Type.valueOf(p.get("type").getAsString());
                proxy.host = p.get("host").getAsString();
                proxy.port = p.get("port").getAsInt();
                proxy.user = p.has("user") ? p.get("user").getAsString() : "";
                proxy.pass = p.has("pass") ? p.get("pass").getAsString() : "";
            }
        } catch (Throwable ignored) {}
    }

    public static void save() {
        try {
            JsonObject j = new JsonObject();
            JsonArray arr = new JsonArray();
            for (Alt a : alts) {
                JsonObject o = new JsonObject();
                o.addProperty("name", a.name); o.addProperty("uuid", a.uuid);
                o.addProperty("enc", wrap(a.token));          // keystore — never plaintext
                o.addProperty("offline", a.offline);
                if (!a.group.isEmpty()) o.addProperty("group", a.group);
                if (a.lastUsed > 0) o.addProperty("lastUsed", a.lastUsed);
                arr.add(o);
            }
            j.add("accounts", arr);
            JsonObject p = new JsonObject();
            p.addProperty("type", proxy.type.name()); p.addProperty("host", proxy.host);
            p.addProperty("port", proxy.port); p.addProperty("user", proxy.user);
            p.addProperty("pass", proxy.pass);
            j.add("proxy", p);
            File f = file(); f.getParentFile().mkdirs();
            try (FileWriter w = new FileWriter(f)) { w.write(new Gson().toJson(j)); }
        } catch (Throwable ignored) {}
    }

    /* ---------- keystore (machine-bound token wrap) ---------- */

    private static String wrap(String token) {
        if (token == null || token.isEmpty()) return "";
        byte[] w = dev.noxclient.ncore.NativeCore.wrap(
            token.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(w);
    }

    private static String unwrap(String enc) {
        if (enc == null || enc.isEmpty()) return "";
        try {
            byte[] w = dev.noxclient.ncore.NativeCore.wrap(
                Base64.getDecoder().decode(enc));
            return new String(w, StandardCharsets.UTF_8);
        } catch (Throwable t) { return ""; }
    }

    /* ---------- user field swap ---------- */

    private static Unsafe unsafe() throws Exception {
        if (UNSAFE == null) {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);
        }
        return UNSAFE;
    }

    private static long userOffset() throws Exception {
        Field f = Minecraft.class.getDeclaredField("user");
        return unsafe().objectFieldOffset(f);
    }
}
