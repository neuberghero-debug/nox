package dev.noxclient.config;

import com.google.gson.*;
import dev.noxclient.NoxClient;
import dev.noxclient.gui.NoxWindowScreen;
import dev.noxclient.gui.theme.Theme;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.Base64;

/**
 * JSON config with per-server presets.
 *
 *   run/nox/config.json            global: theme, window geometry, client opts
 *   run/nox/presets/<server>.json  per-server module+settings profile
 *      e.g. presets/donutsmp.json auto-loads when you join play.donutsmp.net
 *
 * Share/import via "NOXCFG:<base64>" strings (clipboard).
 */
public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path root, globalFile, presetDir;
    private String lastServer = "";
    public boolean inviteShown;
    /** True once when a pre-ClickGUI config was loaded (toast + persist flag). */
    public boolean legacyGui;

    public ConfigManager() {
        root = Minecraft.getInstance().gameDirectory.toPath().resolve("nox");
        globalFile = root.resolve("config.json");
        presetDir = root.resolve("presets");
    }

    // ---------- global ----------
    public void save() {
        try {
            Files.createDirectories(presetDir);
            JsonObject g = new JsonObject();
            g.addProperty("theme", Theme.index());
            g.addProperty("winX", NoxWindowScreen.winX);
            g.addProperty("winY", NoxWindowScreen.winY);
            g.addProperty("winW", NoxWindowScreen.winW);
            g.addProperty("winH", NoxWindowScreen.winH);
            g.addProperty("tab", NoxWindowScreen.activeTab);
            g.addProperty("inviteShown", inviteShown);
            g.addProperty("guiV2", true);
            g.addProperty("opsec_stripAll", dev.noxclient.util.Opsec.stripAll);
            JsonObject pk = new JsonObject();
            for (var e : presetKeys.entrySet()) pk.addProperty(String.valueOf(e.getKey()), e.getValue());
            g.add("presetKeys", pk);
            g.addProperty("schema_version", ConfigMigrator.CURRENT);
            Files.writeString(globalFile, GSON.toJson(g));
            savePreset(currentServer()); // always persist the active profile too
        } catch (IOException ignored) {}
    }

    /** Visible starter set — a fresh install must look like NOX immediately,
     *  not vanilla. Only applied when no config exists (first launch ever). */
    private static final String[] FIRST_RUN = {
        "Interface", "CustomSky", "StatsHud", "HudWidgets",
        "Scoreboard", "Fullbright", "Cosmetics", "DiscordRPC"};

    public void load() {
        boolean firstRun = !Files.exists(globalFile);
        try {
            if (Files.exists(globalFile)) {
                JsonObject g = loadMigrated(globalFile);
                if (g == null) return;
                Theme.set(g.has("theme") ? g.get("theme").getAsInt() : 0);
                NoxWindowScreen.winX = g.has("winX") ? g.get("winX").getAsInt() : -1;
                NoxWindowScreen.winY = g.has("winY") ? g.get("winY").getAsInt() : 40;
                NoxWindowScreen.winW = g.has("winW") ? g.get("winW").getAsInt() : 300;
                NoxWindowScreen.winH = g.has("winH") ? g.get("winH").getAsInt() : 260;
                NoxWindowScreen.activeTab = g.has("tab") ? g.get("tab").getAsInt() : 0;
                inviteShown = g.has("inviteShown") && g.get("inviteShown").getAsBoolean();
                dev.noxclient.util.Opsec.stripAll = !g.has("opsec_stripAll")
                    || g.get("opsec_stripAll").getAsBoolean();
                if (g.has("presetKeys"))
                    for (var e : g.getAsJsonObject("presetKeys").entrySet()) {
                        try { presetKeys.put(Integer.parseInt(e.getKey()), e.getValue().getAsString()); }
                        catch (NumberFormatException ignored) {}
                    }
                // first load of a pre-ClickGUI config → one-time migration toast
                legacyGui = !g.has("guiV2");
            }
            loadPreset(currentServer());
        } catch (Exception ignored) {}
        if (firstRun) {
            for (String n : FIRST_RUN) {
                Module m = NoxClient.INSTANCE.modules.get(n);
                if (m != null) m.restore(true);
            }
            ensureDefaultPresets();
        }
    }

    // ---------- preset hotkeys ----------
    /** keycode → preset file stem. Polled each tick; edge-detect loads it. */
    public final java.util.Map<Integer, String> presetKeys = new java.util.LinkedHashMap<>();
    private final java.util.Map<Integer, Boolean> presetKeyDown = new java.util.HashMap<>();

    /** All preset names on disk (file stem, sorted). */
    public java.util.List<String> presets() {
        try (var s = Files.list(presetDir)) {
            return s.map(p -> p.getFileName().toString())
                .filter(n -> n.endsWith(".json") && !n.contains(".bak"))
                .map(n -> n.substring(0, n.length() - 5)).sorted().toList();
        } catch (IOException e) { return java.util.List.of(); }
    }

    /** Called from ModuleManager.tick — bound key edge-loads the preset. */
    public void tickPresetKeys() {
        if (presetKeys.isEmpty()) return;
        for (var e : presetKeys.entrySet()) {
            boolean down = com.mojang.blaze3d.platform.InputConstants.isKeyDown(e.getKey())
                || dev.noxclient.module.ModuleManager.FAKE_KEYS.contains(e.getKey());
            Boolean was = presetKeyDown.put(e.getKey(), down);
            if (down && was != Boolean.TRUE) {
                loadPreset(e.getValue());
                chat("§7[NOX] preset §f" + e.getValue() + " §7loaded");
            }
        }
    }

    /** Curated starter presets — written once on first launch so the preset
     *  list isn't empty and each profile actually does something visible. */
    private void ensureDefaultPresets() {
        writePresetIfMissing("donutsmp",
            "Interface", "CustomSky", "StatsHud", "Scoreboard", "Fullbright",
            "Cosmetics", "DiscordRPC", "StaffLive", "StaffNotifier",
            "BaseSurvey", "RegionMap", "AutoEat", "Zoom");
        writePresetIfMissing("pvp",
            "Interface", "StatsHud", "Scoreboard", "Fullbright", "TargetHUD",
            "Hitboxes", "LowTotemAlert", "DurabilityAlert", "PvpCoach", "Zoom");
        writePresetIfMissing("legit",
            "Interface", "StatsHud", "Scoreboard", "Fullbright", "Cosmetics");
        writePresetIfMissing("showcase",
            "Interface", "CustomSky", "HudWidgets", "StatsHud", "Scoreboard",
            "Cosmetics", "KillEffects", "Breadcrumbs", "Particles", "SpotifyHud",
            "LyricsSky", "DiscordRPC", "Animations", "TimeChanger", "StreamerMode");
    }

    private void writePresetIfMissing(String name, String... enabled) {
        Path p = presetDir.resolve(name + ".json");
        if (Files.exists(p)) return;
        JsonObject root = new JsonObject();
        java.util.Set<String> on = new java.util.HashSet<>(java.util.Arrays.asList(enabled));
        for (Module m : NoxClient.INSTANCE.modules.all()) {
            if (m.name.equals("TamperWarning") || m.name.equals("GhostMode")
                || m.name.equals("SelfDestruct") || m.name.equals("Panic")) continue;
            JsonObject o = new JsonObject();
            o.addProperty("enabled", on.contains(m.name));
            JsonObject s = new JsonObject();
            for (Setting<?> st : m.settings) s.addProperty(st.name, String.valueOf(st.get()));
            o.add("settings", s);
            root.add(m.name, o);
        }
        root.addProperty("schema_version", ConfigMigrator.CURRENT);
        try {
            Files.createDirectories(presetDir);
            Files.writeString(p, GSON.toJson(root));
        } catch (IOException ignored) {}
    }

    // ---------- per-server presets ----------
    /** Normalized server key: "play.donutsmp.net" → "donutsmp". */
    public String currentServer() {
        var d = Minecraft.getInstance().getCurrentServer();
        if (d == null) return "singleplayer";
        String ip = d.ip.toLowerCase().replaceAll("[^a-z0-9.]", "");
        String[] parts = ip.split("\\.");
        return parts.length >= 2 ? parts[parts.length - 2] : ip.replace(".", "_");
    }

    /** Call on join: hot-swaps the module profile when the server changes. */
    public void onServerChange() {
        String s = currentServer();
        if (s.equals(lastServer)) return;
        lastServer = s;
        loadPreset(s);
        chat("§7[NOX] loaded preset §f" + s + ".json");
    }

    public void savePreset(String server) {
        try {
            Files.createDirectories(presetDir);
            JsonObject out = serializeModules();
            out.addProperty("schema_version", ConfigMigrator.CURRENT);
            Files.writeString(presetDir.resolve(server + ".json"), GSON.toJson(out));
        } catch (IOException ignored) {}
    }

    public void loadPreset(String server) {
        try {
            Path p = presetDir.resolve(server + ".json");
            if (Files.exists(p)) {
                JsonObject o = loadMigrated(p);
                if (o != null) apply(o);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Read + migrate a persisted config file (Modul 24):
     * version-check → backup <file>.bak.v{N} → migrate chain → write back.
     * On any failure the raw bytes are restored and null returned.
     */
    private JsonObject loadMigrated(Path f) {
        try {
            String raw = Files.readString(f);
            JsonObject o = JsonParser.parseString(raw).getAsJsonObject();
            int v = ConfigMigrator.version(o);
            if (v >= ConfigMigrator.CURRENT) return o;
            Path bak = f.resolveSibling(f.getFileName() + ".bak.v" + v);
            try {
                ConfigMigrator.migrate(o);
                Files.writeString(bak, raw);                       // backup pre-migration
                Files.writeString(f, GSON.toJson(o));
                dev.noxclient.gui.Toasts.info("Config migriert v" + v + " → v" + ConfigMigrator.CURRENT);
            } catch (Exception ex) {
                Files.writeString(f, raw);                          // rollback on failure
                dev.noxclient.gui.Toasts.warn("Config-Migration fehlgeschlagen — Backup: " + bak.getFileName());
                return null;
            }
            return o;
        } catch (Exception e) { return null; }
    }

    // ---------- module (de)serialization ----------
    private JsonObject serializeModules() {
        JsonObject root = new JsonObject();
        for (Module m : NoxClient.INSTANCE.modules.all()) {
            JsonObject o = new JsonObject();
            o.addProperty("enabled", m.isEnabled());
            o.addProperty("keybind", m.keybind());
            JsonObject s = new JsonObject();
            for (Setting<?> st : m.settings) s.addProperty(st.name, String.valueOf(st.get()));
            o.add("settings", s);
            root.add(m.name, o);
        }
        return root;
    }

    public void apply(JsonObject root) {
        for (Module m : NoxClient.INSTANCE.modules.all()) {
            if (!root.has(m.name)) continue;
            JsonObject o = root.getAsJsonObject(m.name);
            if (o.has("settings"))
                for (Setting<?> st : m.settings) applySetting(st, o.getAsJsonObject("settings"));
            if (o.has("keybind")) m.setKeybind(o.get("keybind").getAsInt());
            if (o.has("enabled") && !m.name.equals("TamperWarning"))
                m.restore(o.get("enabled").getAsBoolean());
        }
    }

    @SuppressWarnings("unchecked")
    private void applySetting(Setting<?> st, JsonObject s) {
        if (!s.has(st.name)) return;
        var el = s.get(st.name);
        if (!el.isJsonPrimitive()) return;   // getAsString() on objects/arrays throws mid-preset
        String v = el.getAsString();
        try {
            if (st instanceof Setting.Bool) ((Setting<Boolean>) st).set(Boolean.parseBoolean(v));
            else if (st instanceof Setting.Num n) ((Setting<Double>) st).set(Math.min(n.max, Math.max(n.min, Double.parseDouble(v))));
            else if (st instanceof Setting.Bind) ((Setting<Integer>) st).set(Integer.parseInt(v));
            else if (st instanceof Setting.Col) ((Setting<Integer>) st).set((int) Long.parseLong(v));
            else ((Setting<String>) st).set(v);
        } catch (NumberFormatException ignored) {}
    }

    // ---------- preset browser ----------
    public java.util.List<String> listPresets() {
        try (var st = Files.list(presetDir)) {
            return st.filter(f -> f.toString().endsWith(".json"))
                .map(f -> f.getFileName().toString().replaceAll("[.]json$", "")).sorted().toList();
        } catch (IOException e) { return java.util.List.of(); }
    }
    public boolean deletePreset(String name) {
        try { return Files.deleteIfExists(presetDir.resolve(name + ".json")); }
        catch (IOException e) { return false; }
    }
    public boolean renamePreset(String from, String to) {
        try { Files.move(presetDir.resolve(from + ".json"),
            presetDir.resolve(to + ".json"), StandardCopyOption.REPLACE_EXISTING); return true; }
        catch (IOException e) { return false; }
    }
    public void savePresetAs(String name) { savePreset(name); }

    /** Diff summary between the live config and a candidate preset JSON. */
    public String previewDiff(JsonObject root) {
        JsonObject cur = serializeModules();
        int modChanges = 0, setChanges = 0;
        var top = new java.util.ArrayList<String>();
        for (Module m : NoxClient.INSTANCE.modules.all()) {
            if (!root.has(m.name)) continue;
            JsonObject in = root.getAsJsonObject(m.name), cu = cur.getAsJsonObject(m.name);
            boolean mEnabled = in.has("enabled") && in.get("enabled").getAsBoolean() != m.isEnabled();
            boolean mKey = in.has("keybind") && in.get("keybind").getAsInt() != m.keybind();
            if (mEnabled || mKey) modChanges++;
            if (in.has("settings")) {
                JsonObject sIn = in.getAsJsonObject("settings"), sCu = cu.getAsJsonObject("settings");
                for (Setting<?> st : m.settings) {
                    if (!sIn.has(st.name)) continue;
                    String nv = sIn.get(st.name).getAsString(),
                           ov = sCu.has(st.name) ? sCu.get(st.name).getAsString() : "";
                    if (!nv.equals(ov)) {
                        setChanges++;
                        if (top.size() < 5) top.add(m.name + "." + st.name + ": " + ov + " → " + nv);
                    }
                }
            }
        }
        return modChanges + " module(s), " + setChanges + " setting(s) changed"
            + (top.isEmpty() ? "" : " — " + String.join("; ", top));
    }
    /** Apply with rollback — restores the previous profile on any apply error. */
    public boolean applySafe(JsonObject root) {
        JsonObject snap = serializeModules();
        try { apply(root); return true; }
        catch (Exception e) { try { apply(snap); } catch (Exception ignored) {} return false; }
    }
    /** Parse a shared config payload (NOXCFG:/NOXCFG2:/raw JSON) without applying. */
    public JsonObject parseShared(String payload) {
        if (payload == null || payload.isBlank()) return null;
        try {
            if (payload.startsWith("NOXCFG2:")) {
                byte[] gz = Base64.getUrlDecoder().decode(payload.substring(8));
                return JsonParser.parseString(new String(inflate(gz))).getAsJsonObject();
            }
            if (payload.startsWith("NOXCFG:"))
                return JsonParser.parseString(new String(Base64.getDecoder()
                    .decode(payload.substring(7)))).getAsJsonObject();
            return JsonParser.parseString(payload).getAsJsonObject();
        } catch (Exception e) { return null; }
    }
    private static byte[] inflate(byte[] in) throws IOException {
        var z = new java.util.zip.InflaterInputStream(new java.io.ByteArrayInputStream(in));
        return z.readAllBytes();
    }
    /** Compact share code — gzip+base64url; "" when it exceeds the 2k code limit. */
    public String shareCode() {
        try {
            byte[] raw = GSON.toJson(serializeModules()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            var bos = new java.io.ByteArrayOutputStream();
            try (var z = new java.util.zip.DeflaterOutputStream(bos)) { z.write(raw); }
            String code = "NOXCFG2:" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(bos.toByteArray());
            return code.length() <= 2000 ? code : "";
        } catch (Exception e) { return ""; }
    }

    // ---------- share / import ----------
    public void share() {
        try {
            String b64 = Base64.getEncoder().encodeToString(
                GSON.toJson(serializeModules()).getBytes());
            Minecraft.getInstance().keyboardHandler.setClipboard("NOXCFG:" + b64);
            chat("§a[NOX] Config copied — paste the NOXCFG: string to share.");
        } catch (Exception e) { chat("§c[NOX] Export failed: " + e.getMessage()); }
    }

    /** Share one module only — NOXMOD:<name>:<b64>. */
    public void shareModule(Module m) {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("enabled", m.isEnabled());
            o.addProperty("keybind", m.keybind());
            JsonObject s = new JsonObject();
            for (Setting<?> st : m.settings) s.addProperty(st.name, String.valueOf(st.get()));
            o.add("settings", s);
            String b64 = Base64.getEncoder().encodeToString(GSON.toJson(o).getBytes());
            Minecraft.getInstance().keyboardHandler.setClipboard("NOXMOD:" + m.name + ":" + b64);
            chat("§a[NOX] §f" + m.name + " §aconfig copied — share the NOXMOD: string.");
        } catch (Exception e) { chat("§c[NOX] Export failed: " + e.getMessage()); }
    }

    public void importConfig(String payload) {
        try {
            if (payload == null) { chat("§c[NOX] Clipboard empty."); return; }
            if (payload.startsWith("NOXMOD:")) {
                String[] parts = payload.split(":", 3);
                if (parts.length < 3) { chat("§c[NOX] Invalid module config."); return; }
                Module m = NoxClient.INSTANCE.modules.get(parts[1]);
                if (m == null) { chat("§c[NOX] Unknown module: " + parts[1]); return; }
                JsonObject o = JsonParser.parseString(
                    new String(Base64.getDecoder().decode(parts[2]))).getAsJsonObject();
                JsonObject root = new JsonObject();
                root.add(parts[1], o);
                apply(root); save();
                chat("§a[NOX] §f" + parts[1] + " §aconfig imported.");
                return;
            }
            if (dev.noxclient.module.misc.WaypointShare.importTag(payload)) return;
            if (!payload.startsWith("NOXCFG:")) { chat("§c[NOX] Clipboard has no NOXCFG:/NOXMOD:/NOXWP: string."); return; }
            JsonObject root = JsonParser.parseString(
                new String(Base64.getDecoder().decode(payload.substring(7)))).getAsJsonObject();
            apply(root); save();
            chat("§a[NOX] Config imported.");
        } catch (Exception e) { chat("§c[NOX] Invalid config string."); }
    }

    private void chat(String s) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal(s));
    }
}
