package dev.noxclient.module.render;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** Spins your head — looks chaotic to enemies (purely visual to them). */
public class AntiAim extends Module {
    public final Setting.Mode mode = add(new Setting.Mode("Mode", "Spin",
        "Spin", "Jitter", "Down", "Backwards"));
    public final Setting.Num speed = add(new Setting.Num("Speed", 45, 10, 180));
    private float ang;

    public AntiAim() { super("AntiAim", "Spinning head", Category.RENDER); risky = true; }

    @Override public void onTick() {
        if (mc.player == null) return;
        switch (mode.get()) {
            case "Spin" -> {
                ang = (ang + speed.get().floatValue() * 0.05f) % 360;
                mc.player.setYRot(ang);
            }
            case "Jitter" -> mc.player.setYRot(mc.player.getYRot()
                + (Math.random() < 0.5 ? 90 : -90));
            case "Down" -> { mc.player.setXRot(90f); }
            case "Backwards" -> mc.player.setYRot(mc.player.getYRot() + 180f);
        }
    }
}
