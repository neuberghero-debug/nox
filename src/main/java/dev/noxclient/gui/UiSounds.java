package dev.noxclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * UI sound effects (Modul 16.9) — tiny generated .ogg cues at 30% volume.
 *   toggle on = blip · off = thud · click · success = 2-tone · error = low
 */
public final class UiSounds {
    private UiSounds() {}

    private static final Identifier ON  = Identifier.fromNamespaceAndPath("noxclient", "ui_toggle_on");
    private static final Identifier OFF = Identifier.fromNamespaceAndPath("noxclient", "ui_toggle_off");
    private static final Identifier CLICK = Identifier.fromNamespaceAndPath("noxclient", "ui_click");
    private static final Identifier OK  = Identifier.fromNamespaceAndPath("noxclient", "ui_success");
    private static final Identifier ERR = Identifier.fromNamespaceAndPath("noxclient", "ui_error");

    public static boolean enabled = true;   // master switch (Interface setting feeds this)
    public static float volume = 0.3f;

    private static void play(Identifier id, float pitch) {
        if (!enabled) return;
        try {
            var sm = Minecraft.getInstance().getSoundManager();
            sm.play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(id), pitch, volume));
        } catch (Throwable ignored) {}
    }

    public static void toggle(boolean on) { play(on ? ON : OFF, 1f); }
    public static void click()  { play(CLICK, 1f); }
    public static void ok()     { play(OK, 1f); }
    public static void error()  { play(ERR, 1f); }
}
