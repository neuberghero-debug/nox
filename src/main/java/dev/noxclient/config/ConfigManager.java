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
            Files.writeString(globalFile, GSON.toJson(g));
            savePreset(currentServer()); // always persist the active profile too
        } catch (IOException ignored) {}
    }

    public void load() {
        try {
            if (Files.exists(globalFile)) {
                JsonObject g = JsonParser.parseString(Files.readString(globalFile)).getAsJsonObject();
                Theme.set(g.has("theme") ? g.get("theme").getAsInt() : 0);
                NoxWindowScreen.winX = g.has("winX") ? g.get("winX").getAsInt() : -1;
                NoxWindowScreen.winY = g.has("winY") ? g.get("winY").getAsInt() : 40;
                NoxWindowScreen.winW = g.has("winW") ? g.get("winW").getAsInt() : 300;
                NoxWindowScreen.winH = g.has("winH") ? g.get("winH").getAsInt() : 260;
                NoxWindowScreen.activeTab = g.has("tab") ? g.get("tab").getAsInt() : 0;
                inviteShown = g.has("inviteShown") && g.get("inviteShown").getAsBoolean();
            }
            loadPreset(currentServer());
        } catch (Exception ignored) {}
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
            Files.writeString(presetDir.resolve(server + ".json"), GSON.toJson(serializeModules()));
        } catch (IOException ignored) {}
    }

    public void loadPreset(String server) {
        try {
            Path p = presetDir.resolve(server + ".json");
            if (Files.exists(p))
                apply(JsonParser.parseString(Files.readString(p)).getAsJsonObject());
        } catch (Exception ignored) {}
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
        String v = s.get(st.name).getAsString();
        try {
            if (st instanceof Setting.Bool) ((Setting<Boolean>) st).set(Boolean.parseBoolean(v));
            else if (st instanceof Setting.Num n) ((Setting<Double>) st).set(Math.min(n.max, Math.max(n.min, Double.parseDouble(v))));
            else if (st instanceof Setting.Bind) ((Setting<Integer>) st).set(Integer.parseInt(v));
            else if (st instanceof Setting.Col) ((Setting<Integer>) st).set((int) Long.parseLong(v));
            else ((Setting<String>) st).set(v);
        } catch (NumberFormatException ignored) {}
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
