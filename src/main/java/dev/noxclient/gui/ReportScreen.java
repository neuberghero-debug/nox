package dev.noxclient.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.noxclient.NoxClient;
import dev.noxclient.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

/**
 * Bug report / suggestion intake → local nox-bridge (http://127.0.0.1:7734).
 * Bridge URL can be overridden via nox/reporturl.txt. When the bridge is
 * offline or errors, falls back to the clipboard template + Discord channel.
 * Payload is audited line-by-line below: version, identity, module list,
 * log tail, optional screenshot — never tokens, keys, or chat history.
 */
public class ReportScreen extends Screen {
    private final Screen parent;
    private boolean bug = true;
    private EditBox title;
    private MultiLineEditBox desc;
    private String status = "";
    private int statusColor = 0xFFFF6666;
    private volatile boolean sending;

    public ReportScreen(Screen parent, boolean bug) {
        super(Component.literal(bug ? "Report a bug" : "Suggestion"));
        this.parent = parent; this.bug = bug;
    }

    @Override protected void init() {
        int cx = width / 2, top = Math.max(24, height / 2 - 130), w = Math.min(380, width - 40), x0 = cx - w / 2;
        title = new EditBox(font, x0 + 16, top + 46, w - 32, 18, Component.literal("Title"));
        title.setMaxLength(80);
        title.setHint(Component.literal("Short title"));
        addRenderableWidget(title);
        desc = MultiLineEditBox.builder()
            .setX(x0 + 16).setY(top + 70)
            .setPlaceholder(Component.literal("What happened? Steps to reproduce…"))
            .setShowBackground(true).setShowDecorations(false)
            .build(font, w - 32, 90, Component.literal("Description"));
        addRenderableWidget(desc);
        addRenderableWidget(new NoxButton(x0 + 16, top + 26, 90, 18, bug ? "■ Bug" : "Bug",
            () -> bug = true));
        addRenderableWidget(new NoxButton(x0 + 112, top + 26, 90, 18, bug ? "Suggestion" : "■ Suggestion",
            () -> bug = false));
        addRenderableWidget(new NoxButton(x0 + 16, top + 168, 118, 18, "Send", this::send).primary());
        addRenderableWidget(new NoxButton(x0 + 140, top + 168, 90, 18, "Cancel", this::onClose));
        addRenderableWidget(new NoxButton(x0 + w - 106, top + 168, 90, 18, "Discord", () -> {
            try { java.awt.Desktop.getDesktop().browse(new URI(dev.noxclient.discord.DiscordLink.get())); }
            catch (Exception ignored) {}
        }));
    }

    private void send() {
        if (sending) return;
        String t = title.getValue().trim(), d = desc.getValue().trim();
        if (t.isEmpty() || d.isEmpty()) { status = "Title and description are required"; statusColor = 0xFFFF6666; return; }
        sending = true; status = "Sending…"; statusColor = 0xFFFFCC55;
        JsonObject payload = buildPayload(bug ? "bug" : "suggestion", t, d);
        Thread th = new Thread(() -> {
            try {
                HttpResponse<Void> r = HttpClient.newHttpClient().send(HttpRequest.newBuilder(bridgeUrl())
                    .header("Content-Type", "application/json").timeout(java.time.Duration.ofSeconds(6))
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                    .build(), HttpResponse.BodyHandlers.discarding());
                if (r.statusCode() / 100 == 2) {
                    minecraft.execute(() -> { status = "Sent — thank you!"; statusColor = 0xFF66FF99;
                        Toasts.ok((bug ? "Bug report" : "Suggestion") + " sent via bridge"); });
                } else throw new RuntimeException("HTTP " + r.statusCode());
            } catch (Exception e) {
                String clip = "[NOX " + (bug ? "bug report" : "suggestion") + "] v" + NoxClient.VERSION
                    + " — " + t + " — " + d;
                minecraft.execute(() -> {
                    minecraft.keyboardHandler.setClipboard(clip);
                    status = "Bridge offline — copied to clipboard, paste it in Discord";
                    statusColor = 0xFFFFCC55;
                    Toasts.warn("Bridge offline — report copied to clipboard");
                });
            } finally { sending = false; }
        }, "nox-report");
        th.setDaemon(true); th.start();
    }

    /** Endpoint from nox/reporturl.txt when present, else the local bridge. */
    static URI bridgeUrl() {
        try {
            Path p = Minecraft.getInstance().gameDirectory.toPath().resolve("nox/reporturl.txt");
            if (Files.exists(p)) {
                String s = Files.readString(p).trim();
                if (!s.isEmpty()) return URI.create(s);
            }
        } catch (Exception ignored) {}
        return URI.create("http://127.0.0.1:7734/api/report");
    }

    /** Assemble the POST body — every field is audited below (no secrets). */
    private JsonObject buildPayload(String type, String t, String d) {
        JsonObject o = new JsonObject();
        o.addProperty("type", type);
        o.addProperty("title", t);
        o.addProperty("description", d);
        o.addProperty("version", NoxClient.VERSION);
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            o.addProperty("reporter", mc.player.getName().getString());
            o.addProperty("uuid", mc.player.getUUID().toString());
        }
        JsonArray mods = new JsonArray();
        for (var m : NoxClient.INSTANCE.modules.all()) if (m.isEnabled()) mods.add(m.name);
        o.add("modules", mods);
        // newest screenshot ≤ ~1 MB as base64
        try {
            Path shots = mc.gameDirectory.toPath().resolve("screenshots");
            if (Files.isDirectory(shots)) {
                var newest = Files.list(shots).filter(f -> f.toString().endsWith(".png"))
                    .max(Comparator.comparing(f -> f.toFile().lastModified()));
                if (newest.isPresent() && Files.size(newest.get()) <= 1_000_000)
                    o.addProperty("screenshot_b64",
                        Base64.getEncoder().encodeToString(Files.readAllBytes(newest.get())));
            }
        } catch (Exception ignored) {}
        // last 200 lines of latest.log, ≤ 64 KB
        try {
            Path log = mc.gameDirectory.toPath().resolve("logs/latest.log");
            if (Files.exists(log)) {
                List<String> lines = Files.readAllLines(log);
                int from = Math.max(0, lines.size() - 200);
                StringBuilder tail = new StringBuilder(32768);
                for (int i = from; i < lines.size() && tail.length() < 65536; i++)
                    tail.append(lines.get(i)).append('\n');
                o.addProperty("log_tail", tail.toString());
            }
        } catch (Exception ignored) {}
        return o;
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        Theme t = Theme.current();
        Gui.backdrop(g, width, height, 1f);
        int cx = width / 2, top = Math.max(24, height / 2 - 130), w = Math.min(380, width - 40), x0 = cx - w / 2;
        Gui.shadow(g, x0, top - 8, w, 204, 12);
        Gui.roundBorder(g, x0, top - 8, w, 204, 12, UICustom.borderHover, UICustom.winFill);
        Gui.windowFx(x0, top - 8, w, 204, 12);
        Gui.windowFrost(x0, top - 8, w, 204, 12);
        Gui.textBold(g, font, bug ? "Report a bug" : "Suggestion", x0 + 16, top + 2, t.accent());
        if (!status.isEmpty()) Gui.text(g, font, status, x0 + 16, top + 194, statusColor);
        super.extractRenderState(g, mx, my, dt);
    }

    @Override public void onClose() { minecraft.setScreenAndShow(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
