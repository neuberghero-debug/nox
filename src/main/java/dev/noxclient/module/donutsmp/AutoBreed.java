package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Animal;

/** Feeds breedable animals in reach — farm automation. */
public class AutoBreed extends Module {
    public final Setting.Num range = add(new Setting.Num("Range", 4, 2, 6));
    public final Setting.Num cps = add(new Setting.Num("Feeds/s", 4, 1, 10));
    public final Setting.Bool onlyAdults = add(new Setting.Bool("Adults only", true));

    public AutoBreed() { super("AutoBreed", "Auto-feed animals", Category.DONUTSMP); }

    @Override public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        var held = mc.player.getMainHandItem();
        if (held.isEmpty()) return;
        double r2 = range.get() * range.get();
        for (var e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Animal a) || a.distanceToSqr(mc.player) > r2) continue;
            if (onlyAdults.on() && a.isBaby()) continue;
            if (a.isInLove()) continue;
            if (!a.isFood(held)) continue;
            if (!Legitifier.ready(this, cps.get(), 0.3)) return;
            mc.gameMode.interact(mc.player, a, new net.minecraft.world.phys.EntityHitResult(a, a.position().add(0, a.getBbHeight() * 0.5, 0)), InteractionHand.MAIN_HAND);
            mc.player.swing(InteractionHand.MAIN_HAND,
                net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
            return;
        }
    }
}
