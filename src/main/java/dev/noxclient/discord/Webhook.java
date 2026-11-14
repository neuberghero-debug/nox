package dev.noxclient.discord;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;

/**
 * Report-bug / suggest buttons → community Discord webhook. The bot token is
 * NEVER shipped in the jar; the community bot (NOXBot, separate repo) reads
 * DONUT_BOT_TOKEN and relays webhook posts into channels.
 */
public final class Webhook {
    // Public webhook URL for the #reports channel — replace with yours.
    private static final String REPORT_WEBHOOK = System.getenv().getOrDefault(
        "NOX_REPORT_WEBHOOK", "https://discord.com/api/webhooks/REPLACE_ME");

    public static void reportBug() { prompt("bug report"); }
    public static void suggest()  { prompt("suggestion"); }

    private static void prompt(String kind) {
        Minecraft mc = Minecraft.getInstance();
        mc.keyboardHandler.setClipboard("[NOX " + kind + "] version=" + dev.noxclient.NoxClient.VERSION + " ");
        chat("§e[NOX] " + kind + " template copied — posting it in the Discord now.");
        dev.noxclient.gui.Toasts.push(kind + " template copied — paste it in Discord",
            0xFF8B5CF6);
        try {
            if (java.awt.Desktop.isDesktopSupported())
                java.awt.Desktop.getDesktop().browse(
                    new java.net.URI(DiscordLink.channelUrl(DiscordLink.CH_GENERAL)));
        } catch (Exception ignored) {}
        // Direct-send path once the report screen exists:
        // post(REPORT_WEBHOOK, kind, clipboardText);
    }

    public static void post(String webhook, String title, String body) {
        Thread t = new Thread(() -> {
            try {
                JsonObject o = new JsonObject();
                o.addProperty("username", "NOX " + title);
                o.addProperty("content", "```\n" + body + "\n```");
                HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(webhook))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(o.toString(), StandardCharsets.UTF_8))
                    .build(), HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {}
        }, "nox-webhook");
        t.setDaemon(true); t.start();
    }

    private static void chat(String s) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal(s));
    }
}
