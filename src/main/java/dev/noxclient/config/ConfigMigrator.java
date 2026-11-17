package dev.noxclient.config;

import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Config schema migration chain (Modul 24).
 *
 * Every persisted object (global config.json, per-server presets, shared
 * configs) carries "schema_version". Missing = legacy v0.
 *
 *   v0 → v1: adds schema_version marker (fields already compatible)
 *   v1 → v2: module renames — deprecated/replaced modules map onto their
 *            successors so old presets keep working
 *   v2 → v3: setting renames per module (future-proof bucket)
 *
 * migrate() runs the chain in order; backup + rollback live in
 * ConfigManager.loadMigrated() — a failed migration restores the raw file.
 */
public final class ConfigMigrator {
    public static final int CURRENT = 3;

    /** module name renames applied at v2 (old → new). */
    private static final Map<String, String> MOD_RENAMES = new LinkedHashMap<>();
    static {
        // deprecated module ids replaced by hardened successors (Modules 11–13)
        MOD_RENAMES.put("TamperWarner",   "TamperWarning");
        MOD_RENAMES.put("CrashReporter",  "CrashReport");
        MOD_RENAMES.put("MultiAccount",   "AccountManager");
        MOD_RENAMES.put("AltManager",     "AccountManager");
        MOD_RENAMES.put("PacketLogger",   "PacketLog");
        MOD_RENAMES.put("ClickGUI",       "NoxGUI");
    }

    /** per-module setting renames applied at v3: module → (old → new). */
    private static final Map<String, Map<String, String>> SETTING_RENAMES = new LinkedHashMap<>();
    static {
        SETTING_RENAMES.put("OreSim", Map.of("air-check-mode", "airCheck"));
        SETTING_RENAMES.put("Tracers", Map.of("show-items", "items"));
    }

    /** Run the full chain. Returns the migrated object (same instance, mutated). */
    public static JsonObject migrate(JsonObject cfg) {
        int v = version(cfg);
        if (v >= CURRENT) return cfg;
        if (v < 1) { v1(cfg); v = 1; }
        if (v < 2) { v2(cfg); v = 2; }
        if (v < 3) { v3(cfg); v = 3; }
        cfg.addProperty("schema_version", CURRENT);
        return cfg;
    }

    public static int version(JsonObject cfg) {
        try {
            return cfg.has("schema_version") ? cfg.get("schema_version").getAsInt() : 0;
        } catch (Exception e) { return 0; }
    }

    /* ---------- steps ---------- */

    private static void v1(JsonObject cfg) {
        // marker only — existing fields already match v1 semantics
    }

    private static void v2(JsonObject cfg) {
        for (var e : MOD_RENAMES.entrySet()) {
            if (!cfg.has(e.getKey())) continue;
            // keep whichever is newer: if the successor already exists, drop the old one
            if (!cfg.has(e.getValue()))
                cfg.add(e.getValue(), cfg.remove(e.getKey()));
            else
                cfg.remove(e.getKey());
        }
    }

    private static void v3(JsonObject cfg) {
        for (var e : SETTING_RENAMES.entrySet()) {
            if (!cfg.has(e.getKey()) || !cfg.get(e.getKey()).isJsonObject()) continue;
            JsonObject mod = cfg.getAsJsonObject(e.getKey());
            if (!mod.has("settings") || !mod.get("settings").isJsonObject()) continue;
            JsonObject s = mod.getAsJsonObject("settings");
            for (var r : e.getValue().entrySet()) {
                if (!s.has(r.getKey())) continue;
                if (!s.has(r.getValue()))
                    s.add(r.getValue(), s.remove(r.getKey()));
                else
                    s.remove(r.getKey());
            }
        }
    }
}
