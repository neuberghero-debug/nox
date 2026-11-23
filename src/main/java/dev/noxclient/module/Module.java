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
    protected final Minecraft mc = Minecraft.getInstance();

    private final Setting.Mode activation;
    private final Setting.Bind bind;

    protected Module(String name, String description, Category category) {
        this.name = name; this.description = description; this.category = category;
        activation = add(new Setting.Mode("Activation", "Toggle", "Toggle", "Hold"));
        bind = add(new Setting.Bind("Keybind", -1));
    }

    protected <T extends Setting<?>> T add(T s) { settings.add(s); return s; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) {
        if (v && premium && !dev.noxclient.premium.LicenseManager.premium()) {
            if (dev.noxclient.gui.NoxWindowScreen.open)
                dev.noxclient.gui.Toasts.push(name + " requires Premium — see Shop", 0xFFFFD870);
            else
                net.minecraft.client.Minecraft.getInstance().setScreenAndShow(
                    new dev.noxclient.gui.PremiumScreen());
            return;
        }
        if (v == enabled) return;
        enabled = v;
        dev.noxclient.gui.Toasts.push(
            name + (v ? " enabled" : " disabled"), v ? 0xFF66FF99 : 0xFFFF6666);
        if (v) onEnable(); else onDisable();
    }
    /** Config restore — applies state without popping the premium gate screen. */
    public void restore(boolean v) {
        if (v && premium && !dev.noxclient.premium.LicenseManager.premium()) return;
        if (v == enabled) return;
        enabled = v;
        if (v) onEnable(); else onDisable();
    }
    public void toggle() { setEnabled(!enabled); }
    /** True when enabling is blocked by the premium gate. */
    public boolean gated() { return premium && !dev.noxclient.premium.LicenseManager.premium(); }
    public int keybind() {
        Object v = ((Setting<?>) bind).get();
        return v instanceof Number n ? n.intValue() : -1;
    }
    public void setKeybind(int key) { bind.set(key); }
    /** Hold mode: module stays enabled only while the bound key is held. */
    public boolean holdMode() { return "Hold".equals(activation.get()); }

    protected void onEnable() {}
    protected void onDisable() {}
    public void onTick() {}
    /** Inbound game packets — dispatched by GamePacketMixin. Main thread. */
    public void onPacket(net.minecraft.network.protocol.Packet<?> p) {}

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
