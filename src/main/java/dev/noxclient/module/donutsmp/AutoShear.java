package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.Items;

/** Shears wooly sheep in reach. */
public class AutoShear extends Module {
    public final Setting.Num range = add(new Setting.Num("Range", 4, 2, 6));
    public final Setting.Num cps = add(new Setting.Num("Shears/s", 4, 1, 10));

    public AutoShear() { super("AutoShear", "Auto-shear sheep", Category.DONUTSMP); }

    @Override public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        if (!mc.player.getMainHandItem().is(Items.SHEARS)) return;
        double r2 = range.get() * range.get();
        for (var e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Sheep s) || s.distanceToSqr(mc.player) > r2
                || s.isSheared() || s.isBaby()) continue;
            if (!Legitifier.ready(this, cps.get(), 0.3)) return;
            mc.gameMode.interact(mc.player, s, new net.minecraft.world.phys.EntityHitResult(s, s.position().add(0, s.getBbHeight() * 0.5, 0)), InteractionHand.MAIN_HAND);
            mc.player.swing(InteractionHand.MAIN_HAND,
                net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
            return;
        }
    }
}
