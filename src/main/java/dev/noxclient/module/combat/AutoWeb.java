package dev.noxclient.module.combat;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * AutoWeb [PREMIUM] — DonutSMP PvP meta: drops cobwebs into the nearest
 * enemy's feet/head slot so they can't sprint or jump out of the trade.
 * Paced through Legitifier.
 */
public class AutoWeb extends Module {
    private final Setting.Num range = add(new Setting.Num("Range", 4.5, 1, 6));
    private final Setting.Num cps = add(new Setting.Num("Place CPS", 6, 1, 12));
    private final Setting.Bool head = add(new Setting.Bool("Head too", true));
    private final Setting.Bool rotate = add(new Setting.Bool("Rotate", false));

    public AutoWeb() { super("AutoWeb", "Webs enemies' feet", Category.COMBAT); premium = true; }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || !Legitifier.ready(this, cps.get(), 0.3)) return;
        int slot = findWeb();
        if (slot < 0) return;
        Player target = nearest();
        if (target == null) return;

        BlockPos feet = target.blockPosition();
        BlockPos spot = null, support = null;
        if (replaceable(feet)) { spot = feet; support = feet.below(); }
        else if (head.on() && replaceable(feet.above())) { spot = feet.above(); support = feet; }
        if (spot == null || !solid(support)) return;

        int prev = mc.player.getInventory().getSelectedSlot();
        float oldYaw = mc.player.getYRot(), oldPitch = mc.player.getXRot();
        mc.player.getInventory().setSelectedSlot(slot);
        if (rotate.on()) face(support);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(support), Direction.UP, support, false));
        mc.player.swing(InteractionHand.MAIN_HAND,
            net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        // restore camera — rotate used to yank yaw/pitch up to 6x/s
        mc.player.setYRot(oldYaw);
        mc.player.setXRot(oldPitch);
        mc.player.getInventory().setSelectedSlot(prev);
    }

    private int findWeb() {
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) if (inv.getItem(i).is(Items.COBWEB)) return i;
        return -1;
    }

    private boolean replaceable(BlockPos p) {
        return mc.level.getBlockState(p).canBeReplaced();
    }

    private boolean solid(BlockPos p) {
        return !mc.level.getBlockState(p).isAir();
    }

    private void face(BlockPos p) {
        Vec3 c = Vec3.atCenterOf(p);
        double dx = c.x - mc.player.getX(), dz = c.z - mc.player.getZ();
        double dy = c.y - mc.player.getEyeY();
        mc.player.setYRot((float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90f);
        mc.player.setXRot((float) (-Math.atan2(dy, Math.hypot(dx, dz)) * 180 / Math.PI));
    }

    private Player nearest() {
        Player best = null; double bd = range.get() * range.get();
        for (Entity e : mc.level.entitiesForRendering())
            if (e instanceof Player p && e != mc.player && p.isAlive()) {
                double d = mc.player.distanceToSqr(p);
                if (d < bd) { bd = d; best = p; }
            }
        return best;
    }
}
