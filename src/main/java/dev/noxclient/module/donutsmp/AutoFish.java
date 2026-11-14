package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import net.minecraft.world.InteractionHand;

/** Auto fishing: reels in on the bite splash, recasts after a pause. */
public class AutoFish extends Module {
    public final Setting.Num recastS = add(new Setting.Num("Recast delay s", 0.8, 0.2, 3));
    private long biteAt = -1, lastReel;

    public AutoFish() { super("AutoFish", "Auto-reel + recast fishing", Category.DONUTSMP); }

    @Override public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        var hook = mc.player.fishing;
        if (hook == null) {
            // no bobber out → recast after delay
            if (biteAt >= 0 && System.currentTimeMillis() - biteAt > recastS.get() * 1000
                && mc.player.getMainHandItem().toString().contains("fishing_rod")) {
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                biteAt = -1;
            }
            return;
        }
        // bite = sudden downward yank while floating
        var dm = hook.getDeltaMovement();
        if (dm.y < -0.08 && Math.abs(dm.x) < 0.05 && Math.abs(dm.z) < 0.05
            && System.currentTimeMillis() - lastReel > 800) {
            lastReel = System.currentTimeMillis();
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND); // reel in
            biteAt = lastReel;
        }
    }
}
