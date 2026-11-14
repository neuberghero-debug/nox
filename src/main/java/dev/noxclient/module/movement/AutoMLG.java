package dev.noxclient.module.movement;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Water-bucket clutch when falling. */
public class AutoMLG extends Module {
    public final Setting.Num minFall = add(new Setting.Num("Min fall", 4, 3, 10));
    public final Setting.Bool silentLook = add(new Setting.Bool("Look down", true));
    private int slot = -1; private float prevPitch; private boolean placed;

    public AutoMLG() { super("AutoMLG", "Water clutch on falls", Category.MOVEMENT); }

    @Override public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        if (slot < 0) {
            var inv = mc.player.getInventory();
            for (int i = 0; i < 9; i++)
                if (inv.getItem(i).is(Items.WATER_BUCKET)) { slot = i; break; }
            if (slot < 0) return;
            prevPitch = mc.player.getXRot();
        }
        if (mc.player.onGround() || mc.player.fallDistance < minFall.get()) {
            if (placed) { mc.player.setXRot(prevPitch); placed = false; }
            return;
        }
        var below = mc.player.blockPosition().below();
        var state = mc.level.getBlockState(below);
        var below2 = below.below();
        if (!state.isSolid() && !mc.level.getBlockState(below2).isSolid()) return;
        if (!state.isSolid()) below = below2;
        var inv = mc.player.getInventory();
        if (!inv.getItem(slot).is(Items.WATER_BUCKET)) { slot = -1; return; }
        inv.setSelectedSlot(slot);
        if (silentLook.on()) mc.player.setXRot(90f);
        var hit = new BlockHitResult(Vec3.atCenterOf(below), Direction.UP, below, false);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        placed = true;
    }

    @Override protected void onDisable() {
        if (placed && mc.player != null) mc.player.setXRot(prevPitch);
        placed = false; slot = -1;
    }
}
