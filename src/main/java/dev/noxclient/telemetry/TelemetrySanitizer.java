package dev.noxclient.telemetry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Scrub pass on every outgoing telemetry event (Modul 20.4).
 * Denylist-first: any key or value that smells like PII/coords/servers
 * gets dropped or replaced — never logged, never sent.
 */
public final class TelemetrySanitizer {
    private static final Set<String> BAD_KEYS = Set.of(
        "ip", "server", "host", "address", "player", "username", "uuid",
        "x", "y", "z", "coord", "pos", "position", "seed",
        "chat", "message", "text", "path", "file", "dir", "user",
        "token", "key", "secret", "password", "hwid", "mac", "email");
    private static final Pattern IP = Pattern.compile(
        "\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b|\\b[0-9a-fA-F:]{7,}:[0-9a-fA-F:]*\\b");
    private static final Pattern HOME = Pattern.compile(
        "(/home/|/Users/|[A-Za-z]:\\\\Users\\\\)[^\\s\"']*");
    private static final Pattern COORDS = Pattern.compile("-?\\d{4,}");

    public static JsonObject scrub(JsonObject in) {
        JsonObject out = new JsonObject();
        for (Map.Entry<String, JsonElement> e : in.entrySet()) {
            String k = e.getKey().toLowerCase();
            if (badKey(k)) continue;
            JsonElement v = clean(e.getValue());
            if (v != null) out.add(e.getKey(), v);
        }
        return out;
    }

    private static boolean badKey(String k) {
        for (String bad : BAD_KEYS) if (k.contains(bad)) return true;
        return false;
    }

    private static JsonElement clean(JsonElement v) {
        try {
            if (v.isJsonObject()) return scrub(v.getAsJsonObject());
            if (v.isJsonArray()) {
                JsonArray a = new JsonArray();
                for (JsonElement el : v.getAsJsonArray()) {
                    JsonElement c = clean(el);
                    if (c != null) a.add(c);
                }
                return a;
            }
            if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) {
                String s = v.getAsString();
                if (IP.matcher(s).find()) return null;               // drop whole string
                s = HOME.matcher(s).replaceAll("<home>");
                if (COORDS.matcher(s).find()) return null;           // likely coords/seed
                if (s.length() > 120) s = s.substring(0, 120);
                return new JsonPrimitive(s);
            }
            return v;   // numbers/booleans pass (they're metrics)
        } catch (Throwable t) { return null; }
    }
}
