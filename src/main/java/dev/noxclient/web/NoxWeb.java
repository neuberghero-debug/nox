package dev.noxclient.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.noxclient.NoxClient;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Embedded web UI + REST API — boots with the client while the WebUI module
 * is enabled (module enabled-state persists via config). Binds loopback
 * only: this is a local control surface, not a service to expose.
 *
 *   GET  /                 NOX-styled dashboard (modules, state, marks)
 *   GET  /api/state        player/world/session info
 *   GET  /api/modules      all modules with enabled/status/category
 *   GET  /api/settings?module=X   settings of one module
 *   GET  /api/toggle?name=X       toggle a module (POST also works)
 *   GET  /api/set?module=X&setting=Y&value=Z
 *   GET  /api/marks        finder marks + sus scores + esp pins
 *   GET  /api/stashes      raw stashes.json
 */
public final class NoxWeb {
    private static HttpServer server;
    private static int boundPort;

    private NoxWeb() {}

    public static synchronized boolean running() { return server != null; }
    public static int port() { return boundPort; }

    public static synchronized String start(int port) {
        if (server != null) {
            if (boundPort == port) return "already running on :" + port;
            stop0();
        }
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        } catch (IOException e) {
            server = null;
            return "bind failed: " + e.getMessage();
        }
        boundPort = port;
        var pool = Executors.newFixedThreadPool(2, r -> {
            var t = new Thread(r, "nox-web"); t.setDaemon(true); return t; });
        server.setExecutor(pool);
        server.createContext("/", NoxWeb::route);
        server.start();
        return "http://127.0.0.1:" + port;
    }

    public static synchronized void stop() {
        if (server != null) stop0();
    }

    private static void stop0() {
        try { server.stop(0); } catch (Throwable ignored) {}
        server = null;
    }

    // ---------- routing ----------

    private static void route(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            Map<String, String> q = query(ex.getRequestURI().getRawQuery());
            switch (path) {
                case "/" -> html(ex, DASH);
                case "/api/state" -> json(ex, state().toString());
                case "/api/modules" -> json(ex, modules().toString());
                case "/api/settings" -> json(ex, settings(q.get("module")).toString());
                case "/api/toggle" -> json(ex, toggle(q.get("name")).toString());
                case "/api/set" -> json(ex, set(q.get("module"), q.get("setting"), q.get("value")).toString());
                case "/api/marks" -> json(ex, marks().toString());
                case "/api/stashes" -> json(ex, stashes());
                case "/api/chat" -> json(ex, chat(q.get("msg")).toString());
                default -> { if (path.startsWith("/api/")) err(ex, 404, "unknown endpoint");
                             else html(ex, DASH); }
            }
        } catch (Throwable t) {
            try { err(ex, 500, String.valueOf(t)); } catch (Throwable ignored) {}
        } finally {
            ex.close();
        }
    }

    // ---------- endpoints ----------

    private static JsonObject state() {
        var mc = Minecraft.getInstance();
        JsonObject o = new JsonObject();
        o.addProperty("client", "NOX");
        o.addProperty("webapi", boundPort);
        if (mc.player != null) {
            o.addProperty("player", mc.player.getName().getString());
            o.addProperty("x", (int) mc.player.getX());
            o.addProperty("y", (int) mc.player.getY());
            o.addProperty("z", (int) mc.player.getZ());
            o.addProperty("health", mc.player.getHealth());
        }
        if (mc.level != null) o.addProperty("dim", mc.level.dimension().identifier().toString());
        var srv = mc.getCurrentServer();
        o.addProperty("server", srv == null ? "singleplayer" : srv.ip);
        o.addProperty("fps", mc.getFps());
        var mm = NoxClient.INSTANCE.modules;
        int on = 0; for (var m : mm.all()) if (m.isEnabled()) on++;
        o.addProperty("modulesOn", on);
        o.addProperty("modulesTotal", mm.all().size());
        return o;
    }

    private static JsonArray modules() {
        JsonArray arr = new JsonArray();
        for (Module m : NoxClient.INSTANCE.modules.all()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", m.name);
            o.addProperty("category", m.category.label);
            o.addProperty("enabled", m.isEnabled());
            o.addProperty("status", m.status());
            o.addProperty("desc", m.description);
            arr.add(o);
        }
        return arr;
    }

    private static JsonArray settings(String module) {
        JsonArray arr = new JsonArray();
        Module m = NoxClient.INSTANCE.modules.get(module);
        if (m == null) { err(arr); return arr; }
        for (Setting<?> s : m.settings) {
            JsonObject o = new JsonObject();
            o.addProperty("name", s.name);
            o.addProperty("value", String.valueOf(s.get()));
            o.addProperty("type", s instanceof Setting.Bool ? "bool"
                : s instanceof Setting.Num ? "num"
                : s instanceof Setting.Mode ? "mode" : "str");
            arr.add(o);
        }
        return arr;
    }

    /** Toggle runs on the client thread — onEnable handlers touch world state. */
    private static JsonObject toggle(String name) {
        JsonObject o = new JsonObject();
        Module m = name == null ? null : NoxClient.INSTANCE.modules.get(name);
        if (m == null) { o.addProperty("ok", false); o.addProperty("error", "no such module"); return o; }
        var mc = Minecraft.getInstance();
        try {
            CompletableFuture<Boolean> f = new CompletableFuture<>();
            mc.execute(() -> { m.toggle(); f.complete(m.isEnabled()); });
            o.addProperty("enabled", f.get(2, TimeUnit.SECONDS));
        } catch (Throwable t) {
            o.addProperty("ok", false); o.addProperty("error", String.valueOf(t));
            return o;
        }
        o.addProperty("ok", true); o.addProperty("name", m.name);
        return o;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static JsonObject set(String module, String setting, String value) {
        JsonObject o = new JsonObject();
        Module m = module == null ? null : NoxClient.INSTANCE.modules.get(module);
        Setting s = null;
        if (m != null && setting != null)
            for (Setting<?> x : m.settings) if (x.name.equalsIgnoreCase(setting)) { s = x; break; }
        if (s == null || value == null) {
            o.addProperty("ok", false); o.addProperty("error", "no such module/setting"); return o;
        }
        final Setting<?> fs = s;
        var mc = Minecraft.getInstance();
        try {
            CompletableFuture<Void> f = new CompletableFuture<>();
            mc.execute(() -> {
                try {
                    if (fs instanceof Setting.Bool) ((Setting<Object>) fs).set("true".equalsIgnoreCase(value) || "1".equals(value));
                    else if (fs instanceof Setting.Num) ((Setting<Object>) fs).set(Double.parseDouble(value));
                    else ((Setting<Object>) fs).set(value);
                } finally { f.complete(null); }
            });
            f.get(2, TimeUnit.SECONDS);
        } catch (Throwable t) {
            o.addProperty("ok", false); o.addProperty("error", String.valueOf(t)); return o;
        }
        o.addProperty("ok", true); o.addProperty("value", String.valueOf(s.get()));
        return o;
    }

    private static JsonObject marks() {
        JsonObject o = new JsonObject();
        JsonArray act = new JsonArray();
        for (var e : dev.noxclient.module.donutsmp.ActivityFind.MARKS.values()) {
            JsonObject m = new JsonObject();
            m.addProperty("label", e.label); m.addProperty("x", (int) e.x);
            m.addProperty("y", (int) e.y); m.addProperty("z", (int) e.z);
            m.addProperty("count", e.count); m.addProperty("deep", e.deep);
            act.add(m);
        }
        o.add("activity", act);
        JsonArray snd = new JsonArray();
        for (var e : dev.noxclient.module.donutsmp.SoundFind.MARKS.values()) {
            JsonObject m = new JsonObject();
            m.addProperty("label", e.label); m.addProperty("x", (int) e.x);
            m.addProperty("y", (int) e.y); m.addProperty("z", (int) e.z);
            m.addProperty("count", e.count); m.addProperty("deep", e.deep);
            snd.add(m);
        }
        o.add("sound", snd);
        JsonObject sus = new JsonObject();
        var sf = NoxClient.INSTANCE.modules.get("SusChunkFinder");
        if (sf instanceof dev.noxclient.module.donutsmp.SusChunkFinder f)
            for (var e : f.scores().entrySet())
                sus.addProperty(e.getKey().x() + "," + e.getKey().z(), e.getValue());
        o.add("susChunks", sus);
        return o;
    }

    private static String stashes() {
        try {
            var f = Minecraft.getInstance().gameDirectory.toPath().resolve("nox/stashes.json");
            return Files.exists(f) ? Files.readString(f) : "[]";
        } catch (Throwable t) { return "[]"; }
    }

    private static JsonObject chat(String msg) {
        JsonObject o = new JsonObject();
        var mc = Minecraft.getInstance();
        if (msg == null || msg.isBlank() || mc.getConnection() == null) {
            o.addProperty("ok", false); return o;
        }
        try {
            CompletableFuture<Void> f = new CompletableFuture<>();
            mc.execute(() -> {
                try {
                    if (msg.startsWith("/")) mc.getConnection().sendCommand(msg.substring(1));
                    else mc.getConnection().sendChat(msg);
                } finally { f.complete(null); }
            });
            f.get(2, TimeUnit.SECONDS);
            o.addProperty("ok", true);
        } catch (Throwable t) { o.addProperty("ok", false); o.addProperty("error", String.valueOf(t)); }
        return o;
    }

    // ---------- http plumbing ----------

    private static Map<String, String> query(String raw) {
        Map<String, String> out = new HashMap<>();
        if (raw == null) return out;
        for (String kv : raw.split("&")) {
            int i = kv.indexOf('=');
            if (i < 0) continue;
            try {
                out.put(URLDecoder.decode(kv.substring(0, i), StandardCharsets.UTF_8),
                        URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8));
            } catch (Throwable ignored) {}
        }
        return out;
    }

    private static void json(HttpExchange ex, String body) throws IOException {
        respond(ex, 200, "application/json; charset=utf-8", body);
    }
    private static void html(HttpExchange ex, String body) throws IOException {
        respond(ex, 200, "text/html; charset=utf-8", body);
    }
    private static void err(HttpExchange ex, int code, String msg) throws IOException {
        respond(ex, code, "application/json", "{\"error\":\"" + msg.replace("\"", "'") + "\"}");
    }
    private static void respond(HttpExchange ex, int code, String type, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    private static void err(JsonArray arr) {}

    // ---------- embedded dashboard ----------

    private static final String DASH = """
<!doctype html><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1">
<title>NOX</title><style>
:root{--bg:#0D0F14;--card:#161B26;--line:#ffffff22;--acc:#7A2BE2;--txt:#E8E8F0;--dim:#8A8A9E;--ok:#66D9A0}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--txt);font:14px/1.45 system-ui,sans-serif;padding:18px;max-width:1100px;margin:0 auto}
h1{font-size:20px;letter-spacing:.12em;font-weight:800}h1 b{color:var(--acc)}
.bar{display:flex;gap:14px;align-items:baseline;color:var(--dim);flex-wrap:wrap;margin:4px 0 16px}
.bar b{color:var(--txt)}
input{background:#0D0F14;border:1px solid var(--line);color:var(--txt);border-radius:8px;padding:7px 10px;width:260px;outline:0}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(240px,1fr));gap:8px}
.m{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:9px 12px;display:flex;gap:8px;align-items:center;cursor:pointer}
.m:hover{border-color:#ffffff44}.m.on{border-left:3px solid var(--acc)}
.m .n{font-weight:650;flex:0 0 auto}.m .c{color:var(--dim);font-size:11px}.m .st{margin-left:auto;color:var(--dim);font-size:11px;max-width:45%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.dot{width:8px;height:8px;border-radius:50%;background:#444;flex:0 0 auto}.on .dot{background:var(--ok);box-shadow:0 0 8px var(--ok)}
h2{font-size:13px;color:var(--dim);margin:20px 0 8px;letter-spacing:.08em}
table{font-size:12px;border-collapse:collapse}td{padding:2px 10px 2px 0;color:var(--dim)}td:first-child{color:var(--txt)}
</style>
<h1><b>N O X</b> web console</h1>
<div class=bar id=state>loading…</div>
<input id=q placeholder="search modules…" oninput="render()">
<h2>modules <span id=mc></span></h2><div class=grid id=mods></div>
<h2>finder marks</h2><div id=marks>—</div>
<script>
let mods=[];
async function j(u,o){try{const r=await fetch(u,o);return await r.json()}catch(e){return{}}}
async function refresh(){
  const s=await j('/api/state');
  state.innerHTML=s.player?`<b>${s.player}</b> ${s.x},${s.y},${s.z} · ${s.dim} · <b>${s.server}</b> · ${s.fps}fps · modules <b>${s.modulesOn}/${s.modulesTotal}</b>`:'client not in game';
  mods=(await j('/api/modules'))||[];render();
  const mk=await j('/api/marks');let h='';
  for(const k of['activity','sound'])for(const m of(mk[k]||[]))h+=`<tr><td>${m.label}</td><td>${m.x},${m.y},${m.z}</td><td>×${m.count}${m.deep?' DEEP':''}</td></tr>`;
  const sc=mk.susChunks||{};for(const c in sc)h+=`<tr><td>sus chunk</td><td>${c}</td><td>score ${sc[c]}</td></tr>`;
  marks.innerHTML=h?`<table>${h}</table>`:'—';
}
function render(){
  const f=q.value.toLowerCase();let on=0;
  mods.innerHTML=mods.filter(m=>!f||m.name.toLowerCase().includes(f)).map(m=>{
    if(m.enabled)on++;
    return`<div class="m${m.enabled?' on':''}" onclick="tgl('${m.name}',this)"><span class=dot></span><span class=n>${m.name}</span><span class=c>${m.category}</span><span class=st>${m.status||''}</span></div>`}).join('');
  mc.textContent=`· ${mods.filter(m=>m.enabled).length} on`;
}
async function tgl(n,el){await j('/api/toggle?name='+encodeURIComponent(n));refresh()}
refresh();setInterval(refresh,5000);
</script>
""";
}
