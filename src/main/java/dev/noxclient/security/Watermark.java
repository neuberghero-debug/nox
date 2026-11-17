package dev.noxclient.security;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Build watermark (Modul 30.4). The Gradle noxSig step also writes
 * assets/noxclient/watermark.txt — a per-build fingerprint
 * (timestamp + random salt, NOT secret). If a leaked/cracked jar circulates,
 * its watermark identifies which build it came from.
 *
 * Dev runs without the resource get "dev" — intentional.
 */
public final class Watermark {
    private static volatile String cached;

    public static String get() {
        if (cached == null) {
            try (InputStream in = Watermark.class
                    .getResourceAsStream("/assets/noxclient/watermark.txt")) {
                cached = in != null
                    ? new String(in.readAllBytes(), StandardCharsets.UTF_8).trim()
                    : "dev";
            } catch (Throwable t) { cached = "dev"; }
        }
        return cached;
    }
}
