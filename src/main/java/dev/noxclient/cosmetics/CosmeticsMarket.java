package dev.noxclient.cosmetics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.noxclient.premium.Hwid;
import dev.noxclient.premium.LicenseManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.File;
import java.time.Duration;

/**
 * Modul 23 — cosmetics marketplace client: browse approved listings,
 * download+install .cosmetic packages, publish own creations.
 * Auth rides the FriendSync JWT path (license key + hwid → session token).
 */
public final class CosmeticsMarket {
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8)).build();
    private static volatile String jwt;

    private static void chat(String s) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(
            net.minecraft.network.chat.Component.literal(s));
        else dev.noxclient.gui.Toasts.push(s.replaceAll("§.", ""), 0xFF7AFFE0);
    }

    /** Ensure a session token exists (same /friends/login endpoint). */
    private static void ensureLogin(Runnable then) {
        if (jwt != null) { then.run(); return; }
        String key = LicenseManager.storedKey();
        String base = base();
        if (key.isEmpty() || base.isEmpty()) {
            chat("§c[NOX cosmetics] no license/api"); return;
        }
        JsonObject b = new JsonObject();
        b.addProperty("key", key);
        b.addProperty("hwid", Hwid.get());
        req("POST", "/friends/login", b, r -> {
            if (r != null && r.has("ok") && r.get("ok").getAsBoolean()) {
                jwt = r.get("token").getAsString();
                then.run();
            } else chat("§c[NOX cosmetics] login failed");
        });
    }

    private static String base() {
        String a = LicenseManager.api();
        return a.isEmpty()
            ? System.getProperty("nox.license.api",
                System.getenv().getOrDefault("NOX_LICENSE_API",
                    "http://127.0.0.1:8787"))
            : a;
    }

    /** /nox cosmetic browse [slot] — list approved items. */
    public static void browse(String slot) {
        String q = slot == null || slot.isEmpty() ? "" : "?slot=" + slot;
        req("GET", "/cosmetics" + q, null, r -> {
            if (r == null || !r.has("items")) {
                chat("§c[NOX cosmetics] browse failed"); return;
            }
            var items = r.getAsJsonArray("items");
            if (items.isEmpty()) { chat("§7[NOX cosmetics] nothing listed"); return; }
            var sb = new StringBuilder("§6[NOX cosmetics] ");
            int n = Math.min(items.size(), 8);
            for (int i = 0; i < n; i++) {
                var it = items.get(i).getAsJsonObject();
                sb.append("§e").append(it.get("name").getAsString())
                  .append("§8(").append(it.get("slot").getAsString()).append(")")
                  .append("§7#").append(it.get("id").getAsString());
                if (i < n - 1) sb.append("  ");
            }
            chat(sb.toString());
            chat("§7/nox cosmetic get <id> to install");
        });
    }

    /** /nox cosmetic get <id> — download + install + apply. */
    public static void get(String id) {
        ensureLogin(() -> req("GET", "/cosmetics/" + id, null, r -> {
            if (r == null || !r.has("cosmetic")) {
                chat("§c[NOX cosmetics] unavailable"); return;
            }
            try {
                CosmeticDef d = CosmeticDef.parse(r.get("cosmetic").getAsString());
                String slug = d.installAssets();
                if (slug == null) { chat("§c[NOX cosmetics] empty package"); return; }
                d.apply();
                chat("§a[NOX cosmetics] installed §d" + d.name
                    + "§a by " + d.author);
            } catch (Exception e) {
                chat("§c[NOX cosmetics] bad package");
            }
        }));
    }

    /** /nox cosmetic export <slot> — write current slot to nox/cosmetics/*.cosmetic. */
    public static void export(String slot) {
        try {
            Path p = CosmeticDef.export(slot);
            chat("§a[NOX cosmetics] exported → §d" + p.getFileName());
        } catch (Exception e) {
            chat("§c[NOX cosmetics] export: " + e.getMessage());
        }
    }

    /** /nox cosmetic publish <file.cosmetic> — upload for moderation. */
    public static void publish(String file) {
        ensureLogin(() -> {
            try {
                Path p = Path.of(file);
                if (!p.isAbsolute()) {
                    p = new File(Minecraft.getInstance().gameDirectory,
                        "nox/cosmetics/" + file).toPath();
                }
                String raw = Files.readString(p);
                CosmeticDef.parse(raw); // sanity
                JsonObject b = new JsonObject();
                b.addProperty("cosmetic", raw);
                req("POST", "/cosmetics/publish", b, r -> {
                    if (r != null && r.has("ok") && r.get("ok").getAsBoolean())
                        chat("§a[NOX cosmetics] submitted §d#" + r.get("id").getAsString()
                            + "§a — pending moderation");
                    else chat("§c[NOX cosmetics] publish failed");
                });
            } catch (Exception e) {
                chat("§c[NOX cosmetics] can't read " + file);
            }
        });
    }

    /** /nox cosmetic import <file> — local install without marketplace. */
    public static void importFile(String file) {
        try {
            Path p = Path.of(file);
            if (!p.isAbsolute()) {
                p = new File(Minecraft.getInstance().gameDirectory,
                    "nox/cosmetics/" + file).toPath();
            }
            CosmeticDef d = CosmeticDef.parse(Files.readString(p));
            if (d.installAssets() == null) { chat("§c[NOX cosmetics] empty package"); return; }
            d.apply();
            chat("§a[NOX cosmetics] installed §d" + d.name);
        } catch (Exception e) {
            chat("§c[NOX cosmetics] " + e.getMessage());
        }
    }

    private static void req(String method, String path, JsonObject body,
                            java.util.function.Consumer<JsonObject> cb) {
        Thread t = new Thread(() -> {
            try {
                var b = HttpRequest.newBuilder(URI.create(base() + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("content-type", "application/json");
                if (jwt != null) b.header("authorization", "Bearer " + jwt);
                HttpRequest rq = "GET".equals(method) ? b.GET().build()
                    : b.POST(HttpRequest.BodyPublishers.ofString(
                        body == null ? "{}" : body.toString())).build();
                var resp = HTTP.send(rq, HttpResponse.BodyHandlers.ofString());
                JsonObject r = JsonParser.parseString(resp.body()).getAsJsonObject();
                var mc = Minecraft.getInstance();
                mc.execute(() -> cb.accept(r));
            } catch (Exception e) {
                var mc = Minecraft.getInstance();
                mc.execute(() -> cb.accept(null));
            }
        }, "nox-cosmetics");
        t.setDaemon(true);
        t.start();
    }
}
