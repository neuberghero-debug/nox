package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * FastPlace — re-fires the block-use interaction each tick while the use key is
 * held, removing vanilla's 4-tick right-click delay. Rate capped by the CPS
 * setting so it doesn't send a packet storm.
 */
public class FastPlace extends Module {
    private final Setting.Num cps = add(new Setting.Num("Place CPS", 12, 5, 20));
    private long next;

    public FastPlace() { super("FastPlace", "Places blocks without delay", Category.DONUTSMP); }

    @Override
    public void onTick() {
        if (mc.player == null || mc.gameMode == null || !mc.options.keyUse.isDown()) return;
        long now = System.currentTimeMillis();
        if (now < next) return;
        next = now + (long) (1000.0 / cps.get() + (Math.random() * 12 - 6));

        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            for (InteractionHand hand : InteractionHand.values()) {
                var r = mc.gameMode.useItemOn(mc.player, hand, hit);
                if (r.consumesAction()) break;
            }
        } else {
            for (InteractionHand hand : InteractionHand.values()) {
                var r = mc.gameMode.useItem(mc.player, hand);
                if (r.consumesAction()) break;
            }
        }
    }
}
