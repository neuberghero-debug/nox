package dev.noxclient.gui;

import com.google.gson.JsonObject;
import dev.noxclient.NoxClient;
import dev.noxclient.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Preset browser — lists nox/presets/*.json with apply/rename/delete, export
 * (compact NOXCFG2: share code when ≤2k chars, else clipboard JSON), and a
 * validated import: clipboard → parse → diff preview → confirm → apply with
 * snapshot-rollback on any apply error.
 */
public class PresetScreen extends Screen {
    private final Screen parent;
    private List<String> presets = new ArrayList<>();
    private EditBox nameBox;
    private int selected = -1, scroll;
    private String status = "";
    private int statusColor = 0xFF66D9A0;
    private JsonObject pendingImport;
    private String pendingSummary = "";
    private int rowsY, rowH = 22;

    public PresetScreen(Screen parent) { super(Component.literal("NOX Presets")); this.parent = parent; }

    @Override protected void init() {
        presets = new ArrayList<>(NoxClient.INSTANCE.config.listPresets());
        int cx = width / 2, w = Math.min(380, width - 40), x0 = cx - w / 2, top = Math.max(24, height / 2 - 150);
        rowsY = top + 40;
        nameBox = new EditBox(font, x0 + 16, top + 246, 150, 18, Component.literal("Preset name"));
        nameBox.setMaxLength(40);
        nameBox.setHint(Component.literal("new preset name"));
        addRenderableWidget(nameBox);
        addRenderableWidget(new NoxButton(x0 + 172, top + 246, 92, 18, "Save as", () -> {
            String n = nameBox.getValue().trim();
            if (n.isEmpty() || selected < 0 && presets.contains(n)) { }
            if (!n.isEmpty()) {
                NoxClient.INSTANCE.config.savePresetAs(n);
                presets = new ArrayList<>(NoxClient.INSTANCE.config.listPresets());
                ok("Preset saved as " + n);
            }
        }));
        addRenderableWidget(new NoxButton(x0 + 268, top + 246, 96, 18, "Rename sel", () -> {
            String n = nameBox.getValue().trim();
            if (selected >= 0 && selected < presets.size() && !n.isEmpty()
                    && NoxClient.INSTANCE.config.renamePreset(presets.get(selected), n)) {
                presets = new ArrayList<>(NoxClient.INSTANCE.config.listPresets());
                ok("Renamed to " + n);
            }
        }));
        addRenderableWidget(new NoxButton(x0 + 16, top + 272, 86, 18, "Export", this::exportSel));
        addRenderableWidget(new NoxButton(x0 + 108, top + 272, 86, 18, "Import", this::importClip));
        addRenderableWidget(new NoxButton(x0 + 200, top + 272, 76, 18, "Delete", this::deleteSel));
        addRenderableWidget(new NoxButton(x0 + 282, top + 272, 82, 18, "Close", this::onClose));
    }

    private void ok(String s) { status = s; statusColor = 0xFF66D9A0; }
    private void bad(String s) { status = s; statusColor = 0xFFFF6666; }

    private void exportSel() {
        var cfg = NoxClient.INSTANCE.config;
        String code = cfg.shareCode();
        if (!code.isEmpty()) {
            minecraft.keyboardHandler.setClipboard(code);
            ok("Share code copied (" + code.length() + " chars)");
        } else {
            cfg.share();
            ok("Config too big for a code — full JSON copied instead");
        }
    }
    private void importClip() {
        String clip = minecraft.keyboardHandler.getClipboard();
        JsonObject root = NoxClient.INSTANCE.config.parseShared(clip);
        if (root == null) { bad("Clipboard has no valid NOXCFG/NOXCFG2/JSON config"); return; }
        pendingImport = root;
        pendingSummary = NoxClient.INSTANCE.config.previewDiff(root);
        status = "";
    }
    private void confirmImport() {
        if (pendingImport == null) return;
        if (NoxClient.INSTANCE.config.applySafe(pendingImport)) {
            NoxClient.INSTANCE.config.save();
            ok("Import applied — " + pendingSummary);
        } else bad("Import failed — config rolled back, nothing changed");
        pendingImport = null; pendingSummary = "";
    }
    private void deleteSel() {
        if (selected >= 0 && selected < presets.size()
                && NoxClient.INSTANCE.config.deletePreset(presets.get(selected))) {
            ok("Deleted " + presets.get(selected));
            presets = new ArrayList<>(NoxClient.INSTANCE.config.listPresets());
            selected = -1;
        }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        Theme t = Theme.current();
        Gui.backdrop(g, width, height, 1f);
        int cx = width / 2, w = Math.min(380, width - 40), x0 = cx - w / 2, top = Math.max(24, height / 2 - 150);
        Gui.shadow(g, x0, top - 10, w, 318, 12);
        Gui.roundBorder(g, x0, top - 10, w, 318, 12, UICustom.borderHover, UICustom.winFill);
        Gui.windowFx(x0, top - 10, w, 318, 12);
        Gui.windowFrost(x0, top - 10, w, 318, 12);
        Gui.textBold(g, font, "Config presets", x0 + 16, top + 2, t.accent());
        Gui.text(g, font, "nox/presets/*.json — click a row to select", x0 + 16, top + 18, UICustom.textDim);
        // preset rows
        Gui.clipOn(x0 + 12, rowsY, x0 + w - 12, rowsY + 196);
        int y = rowsY - scroll;
        for (int i = 0; i < presets.size(); i++) {
            boolean sel = i == selected;
            boolean hov = ModuleCard.inside(mx, my, x0 + 12, y, w - 24, rowH - 2);
            Gui.round(g, x0 + 12, y, w - 24, rowH - 2, 5,
                sel ? t.accent() & 0x44FFFFFF : hov ? 0x14FFFFFF : 0xFF161B26);
            Gui.text(g, font, presets.get(i), x0 + 20, y + 6, sel || hov ? t.text() : UICustom.textDim);
            Gui.textCenter(g, font, "apply", x0 + w - 36, y + 6, UICustom.textMute);
            y += rowH;
        }
        if (presets.isEmpty())
            Gui.textCenter(g, font, "No presets yet — play once, then Save as", cx, rowsY + 40, UICustom.textDim);
        Gui.clipOff();
        // import preview panel
        if (pendingImport != null) {
            int py = top + 300 - 96;
            Gui.shadow(g, x0 + 8, py, w - 16, 88, 8);
            Gui.roundBorder(g, x0 + 8, py, w - 16, 88, 8, t.accent(), 0xF6141821);
            Gui.textBold(g, font, "Apply this config?", x0 + 20, py + 8, t.accent());
            Gui.text(g, font, clip(pendingSummary, w - 40), x0 + 20, py + 24, UICustom.textDim);
            Gui.text(g, font, "Invalid entries are skipped; any error rolls back fully.", x0 + 20, py + 36, UICustom.textMute);
            button(g, "Apply", x0 + 20, py + 56, 80, mx, my);
            button(g, "Cancel", x0 + 108, py + 56, 80, mx, my);
        }
        if (!status.isEmpty()) Gui.text(g, font, clip(status, w - 32), x0 + 16, top + 296, statusColor);
        super.extractRenderState(g, mx, my, dt);
    }
    private static void button(GuiGraphicsExtractor g, String text, int x, int y, int w, int mx, int my) {
        Gui.round(g, x, y, w, 22, 5, ModuleCard.inside(mx, my, x, y, w, 22) ? 0xFF303747 : 0xFF222936);
        Gui.textCenter(g, Minecraft.getInstance().font, text, x + w / 2, y + 7, Theme.current().text());
    }
    private static String clip(String s, int w) {
        var font = Minecraft.getInstance().font;
        return Gui.width(font, s) <= w ? s : s.substring(0, Math.max(0, s.length() - 4)) + "…";
    }

    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean dbl) {
        int cx = width / 2, w = Math.min(380, width - 40), x0 = cx - w / 2, top = Math.max(24, height / 2 - 150);
        if (pendingImport != null) {
            int py = top + 300 - 96;
            if (ModuleCard.inside(e.x(), e.y(), x0 + 20, py + 56, 80, 22)) { confirmImport(); return true; }
            if (ModuleCard.inside(e.x(), e.y(), x0 + 108, py + 56, 80, 22)) { pendingImport = null; pendingSummary = ""; return true; }
        }
        int y = rowsY - scroll;
        for (int i = 0; i < presets.size(); i++) {
            if (ModuleCard.inside(e.x(), e.y(), x0 + 12, y, w - 24, rowH - 2)) {
                if (e.x() > x0 + w - 60) {
                    NoxClient.INSTANCE.config.loadPreset(presets.get(i));
                    ok("Applied preset " + presets.get(i));
                } else selected = i;
                return true;
            }
            y += rowH;
        }
        return super.mouseClicked(e, dbl);
    }
    @Override public boolean mouseScrolled(double mx, double my, double h, double v) {
        scroll = Math.max(0, Math.min(Math.max(0, presets.size() * rowH - 196), scroll - (int) v * 22));
        return true;
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        if (e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(e);
    }
    @Override public void onClose() { minecraft.setScreenAndShow(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
