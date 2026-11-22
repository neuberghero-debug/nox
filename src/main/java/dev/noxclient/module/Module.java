package dev.noxclient.module;

import dev.noxclient.setting.Setting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    public final String name, description;
    public final Category category;
    public final List<Setting<?>> settings = new ArrayList<>();
    private boolean enabled;
    public boolean premium; // set in ctor for paid modules
    public boolean risky;   // ban-bait on DonutSMP's AC — needs a confirm press
    /** Modules confirmed this session — the warn-and-arm ask happens once. */
    private static final java.util.Set<String> RISK_OK = new java.util.HashSet<>();
    private long riskAsk;
    protected final Minecraft mc = Minecraft.getInstance();

    private final Setting.Mode activation;
    private final Setting.Bind bind;
    private final Setting.Bool hideInList;
    private final Setting.Bool notifyToggle;

    protected Module(String name, String description, Category category) {
        this.name = name; this.description = description; this.category = category;
        activation = add(new Setting.Mode("Activation", "Toggle", "Toggle", "Hold"));
        bind = add(new Setting.Bind("Keybind", -1));
        hideInList = add(new Setting.Bool("Hide in list", false,
            "Don't show this module in the HUD arraylist"));
        notifyToggle = add(new Setting.Bool("Toggle notify", true,
            "Toast notification when this module toggles"));
    }

    protected <T extends Setting<?>> T add(T s) { settings.add(s); return s; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) {
        if (v && premium && !dev.noxclient.premium.Distributed.premiumAllowed()) {
            if (dev.noxclient.gui.NoxWindowScreen.open)
                dev.noxclient.gui.Toasts.push(name + " requires Premium — see Shop", 0xFFFFD870);
            else
                net.minecraft.client.Minecraft.getInstance().setScreenAndShow(
                    new dev.noxclient.gui.PremiumScreen());
            return;
        }
        if (v && !enabled && risky && !RISK_OK.contains(name)) {
            long now = System.currentTimeMillis();
            if (now - riskAsk < 6000) {
                RISK_OK.add(name);
            } else {
                riskAsk = now;
                dev.noxclient.gui.Toasts.push(
                    name + " can flag or kick on GrimAC — toggle again to arm", 0xFFFF6666);
                if (mc.player != null)
                    mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§c[NOX] " + name + " is ban-bait on DonutSMP — toggle again within 6s to confirm"));
                return;
            }
        }
        if (v == enabled) return;
        enabled = v;
        dev.noxclient.gui.UiSounds.toggle(v);
        if (notifyToggle.on())
            dev.noxclient.gui.Toasts.push(
                name + (v ? " enabled" : " disabled"), v ? 0xFF66FF99 : 0xFFFF6666);
        if (v) onEnable(); else onDisable();
    }
    /** Config restore — applies state without popping the premium gate screen. */
    public void restore(boolean v) {
        if (v && premium && !dev.noxclient.premium.LicenseManager.assertPremium()) return;
        if (v == enabled) return;
        enabled = v;
        // a module that dies in onEnable/onDisable must not kill client init —
        // log + leave it off instead of aborting config restore
        try { if (v) onEnable(); else onDisable(); }
        catch (Throwable t) {
            enabled = false;
            System.err.println("[NOX] " + name + " failed to " + (v ? "enable" : "disable")
                + " during config restore: " + t);
        }
    }
    public void toggle() { setEnabled(!enabled); }
    /** True when enabling is blocked by the premium gate. */
    public boolean gated() { return premium && !dev.noxclient.premium.LicenseManager.assertPremium(); }
    public int keybind() {
        Object v = ((Setting<?>) bind).get();
        return v instanceof Number n ? n.intValue() : -1;
    }
    public void setKeybind(int key) { bind.set(key); }
    /** Hold mode: module stays enabled only while the bound key is held. */
    public boolean holdMode() { return "Hold".equals(activation.get()); }
    /** Hidden from the HUD arraylist (still fully functional). */
    public boolean hideList() { return hideInList.on(); }

    protected void onEnable() {}
    protected void onDisable() {}
    public void onTick() {}

    /** Diagnostics — filled by ModuleManager + mark(). */
    public volatile String lastError, lastAction;
    public volatile long lastErrorAt, lastActionAt;
    /** Call when the module actually does something visible (acts, not idles). */
    protected void mark(String action) { lastAction = action; lastActionAt = System.currentTimeMillis(); }

    /**
     * Live one-liner for `why`/selftest — what the module is doing RIGHT NOW,
     * or why it's idle (e.g. "no target in range"). Modules override.
     */
    public String status() { return enabled ? "active" : "off"; }
}
