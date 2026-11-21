package dev.noxclient.module.misc;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.premium.Hwid;
import dev.noxclient.premium.LicenseManager;
import dev.noxclient.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * FriendSync (Modul 8) — cross-client friend list + presence markers.
 *
 * Backend: nox-license-server /friends/* (HS256 session JWT issued on
 * /friends/login with the stored NOX1 license key + HWID — sync rides the
 * premium gate).
 *
 * Friend codes: "/nox friendcode" mints a 6-char one-shot code; the friend
 * redeems it with "/nox friendadd XXXXXX" → mutual link server-side.
 * Presence: every 5 s the module pings name/server/pos/dim; online mutual
 * friends on the SAME server+dimension get a green gizmo beam + name tag.
 * Synced names also feed Friends.isFriendName → green ESP/alerts.
 */
public class FriendSync extends Module {
    private final Setting.Bool sharePos = add(new Setting.Bool("Share position", true,
        "Send name/server/pos to mutual friends every 5 s"));
    private final Setting.Bool markers = add(new Setting.Bool("HUD markers", true,
        "Beam + nametag for online friends on this server"));
    private final Setting.Str api = add(new Setting.Str("Sync API",
        "http://127.0.0.1:8787"));

    private static final Gson G = new Gson();
    private static volatile String jwt;
    private static final List<JsonObject> friends = new CopyOnWriteArrayList<>();
    private static final Map<String, String> syncedNames = new ConcurrentHashMap<>();
    private int tick;

    public FriendSync() {
        super("FriendSync", "Cross-client friend sync + presence", Category.MISC);
        premium = true;
        dev.noxclient.api.AddonContext.COMMANDS.put("friendcode", a -> friendCode());
        dev.noxclient.api.AddonContext.COMMANDS.put("friendadd", a -> {
            if (a.length > 0) redeem(a[0]);
        });
        dev.noxclient.render.NoxRender.add(FriendSync::renderMarkers);
    }

    /** Synced friend names → Friends.isFriendName hook (ESP/alerts). */
    public static boolean isSynced(String name) {
        return name != null && syncedNames.containsKey(name.toLowerCase());
    }

    @Override
    protected void onEnable() {
        if (!LicenseManager.assertPremium()) { setEnabled(false); return; }
        jwt = null;
        login();
    }

    @Override
    public void onTick() {
        if (++tick % 100 == 1 && jwt != null) ping();
        if (tick % 200 == 20 && jwt != null) refresh();
    }

    /* ---------- http ---------- */

    private String base() {
        String a = api.get();
        return a.isEmpty() ? LicenseManager.api() : a;
    }

    private void login() {
        String key = LicenseManager.storedKey();
        if (key.isEmpty() || base().isEmpty()) return;
        JsonObject b = new JsonObject();
        b.addProperty("key", key);
        b.addProperty("hwid", Hwid.get());
        post("/friends/login", b, r -> {
            if (r.has("token") && r.get("ok").getAsBoolean()) {
                jwt = r.get("token").getAsString();
                chat("§a[NOX friends] synced — /nox friendcode for a code");
            }
        });
    }

    private void ping() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !sharePos.on()) return;
        JsonObject b = new JsonObject();
        b.addProperty("name", mc.player.getGameProfile().name());
        var srv = mc.getCurrentServer();
        b.addProperty("server", srv != null ? srv.ip : "");
        var p = mc.player.position();
        b.addProperty("x", p.x); b.addProperty("y", p.y); b.addProperty("z", p.z);
        b.addProperty("dim", mc.level.dimension().identifier().toString());
        post("/friends/ping", b, null);
    }

    private void refresh() {
        get("/friends", r -> {
            friends.clear(); syncedNames.clear();
            if (r.has("friends")) {
                for (var e : r.getAsJsonArray("friends")) {
                    JsonObject f = e.getAsJsonObject();
                    friends.add(f);
                    if (f.has("name")) syncedNames.put(f.get("name").getAsString().toLowerCase(), "1");
                }
            }
        });
    }

    private void friendCode() {
        if (jwt == null) { chat("§c[NOX friends] not logged in"); return; }
        post("/friends/code", new JsonObject(), r -> {
            if (r.has("code"))
                chat("§a[NOX friends] code §d" + r.get("code").getAsString()
                    + "§a — friend runs /nox friendadd <code>");
        });
    }

    private void redeem(String code) {
        if (jwt == null) { chat("§c[NOX friends] not logged in"); return; }
        JsonObject b = new JsonObject();
        b.addProperty("code", code);
        var mc = Minecraft.getInstance();
        b.addProperty("name", mc.player != null ? mc.player.getGameProfile().name() : "?");
        post("/friends/redeem", b, r -> {
            if (r.has("friend"))
                chat("§a[NOX friends] linked with §d" + r.get("friend").getAsString());
        });
    }

    private void post(String path, JsonObject body,
                      java.util.function.Consumer<JsonObject> cb) {
        Thread t = new Thread(() -> {
            try {
                var rb = HttpRequest.newBuilder(URI.create(base() + path))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(8))
                    .POST(HttpRequest.BodyPublishers.ofString(G.toJson(body)));
                if (jwt != null) rb.header("Authorization", "Bearer " + jwt);
                String s = HttpClient.newHttpClient().send(rb.build(),
                    HttpResponse.BodyHandlers.ofString()).body();
                if (cb != null) cb.accept(G.fromJson(s, JsonObject.class));
            } catch (Throwable ignored) {}
        }, "nox-friendsync");
        t.setDaemon(true); t.start();
    }

    private void get(String path, java.util.function.Consumer<JsonObject> cb) {
        Thread t = new Thread(() -> {
            try {
                var rb = HttpRequest.newBuilder(URI.create(base() + path))
                    .timeout(Duration.ofSeconds(8)).GET();
                if (jwt != null) rb.header("Authorization", "Bearer " + jwt);
                String s = HttpClient.newHttpClient().send(rb.build(),
                    HttpResponse.BodyHandlers.ofString()).body();
                if (cb != null) cb.accept(G.fromJson(s, JsonObject.class));
            } catch (Throwable ignored) {}
        }, "nox-friendsync");
        t.setDaemon(true); t.start();
    }

    /* ---------- markers ---------- */

    public static void renderMarkers() {
        var m = dev.noxclient.NoxClient.INSTANCE.modules.get("FriendSync");
        if (!(m instanceof FriendSync fs) || !fs.isEnabled() || !fs.markers.on()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        var srv = mc.getCurrentServer();
        String myServer = srv != null ? srv.ip : "";
        String myDim = mc.level.dimension().identifier().toString();
        for (JsonObject f : friends) {
            try {
                if (!f.get("online").getAsBoolean()) continue;
                if (!myServer.equals(f.get("server").getAsString())
                    || !myDim.equals(f.get("dim").getAsString())) continue;
                JsonArray pos = f.getAsJsonArray("pos");
                double x = pos.get(0).getAsDouble(), y = pos.get(1).getAsDouble(),
                       z = pos.get(2).getAsDouble();
                Vec3 v = new Vec3(x, y + 1.2, z);
                Gizmos.billboardText("★ " + f.get("name").getAsString(), v,
                    TextGizmo.Style.forColorAndCentered(0xFF7AFFE0)).setAlwaysOnTop();
                for (int i = 0; i < 10; i++)
                    Gizmos.point(new Vec3(x, y + i, z), 0xFF7AFFE0, 0.15f)
                        .setAlwaysOnTop();
            } catch (Throwable ignored) {}
        }
    }

    /** Chat/toast from the HTTP daemon thread — hop to the client thread so
     *  the chat component list isn't mutated mid-render. */
    private void chat(String s) {
        Minecraft.getInstance().execute(() -> {
            var p = Minecraft.getInstance().player;
            if (p != null) p.sendSystemMessage(Component.literal(s));
            else dev.noxclient.gui.Toasts.push(s.replaceAll("§.", ""), 0xFF7AFFE0);
        });
    }
}
