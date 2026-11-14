package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** Prevents AFK kicks: micro-rotations + occasional jump on a random timer. */
public class AntiAFK extends Module {
    private final Setting.Num interval = add(new Setting.Num("Interval (s)", 20, 5, 120));
    private final Setting.Mode action = add(new Setting.Mode("Action", "Rotate", "Rotate", "Jump", "Sneak", "Random"));
    private final Setting.Bool onlyServer = add(new Setting.Bool("Only on servers", true));
    private long next;
    private long sneakUntil;

    public AntiAFK() { super("AntiAFK", "Prevents AFK kicks", Category.MISC); }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        // sneak release on the tick thread — the old raw-Thread raced the client
        // and leaked a thread per sneak
        if (sneakUntil != 0 && now >= sneakUntil) {
            mc.options.keyShift.setDown(false); sneakUntil = 0;
        }
        if (onlyServer.on() && mc.getCurrentServer() == null) return;
        if (now < next) return;
        next = now + (long) (interval.get() * 1000 * (0.7 + Math.random() * 0.6));
        String a = action.get().equals("Random")
            ? new String[]{"Rotate","Jump","Sneak"}[(int) (Math.random() * 3)] : action.get();
        switch (a) {
            case "Jump" -> mc.player.jumpFromGround();
            case "Sneak" -> {
                mc.options.keyShift.setDown(true); sneakUntil = now + 400;
            }
            default -> mc.player.setYRot(mc.player.getYRot() + (float) (Math.random() * 30 - 15));
        }
    }

    @Override protected void onDisable() {
        sneakUntil = 0;
        if (mc.options != null) mc.options.keyShift.setDown(false);
    }
}
