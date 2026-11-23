package dev.noxclient.module.combat;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * AutoCrystal [PREMIUM] — places end crystals on obsidian/bedrock near the
 * target and detonates them, paced through Legitifier so place/break timing
 * stays inside human CPS bounds.
 */
public class AutoCrystal extends Module {
    private final Setting.Num range = add(new Setting.Num("Range", 5.0, 2, 7));
    private final Setting.Num placeCps = add(new Setting.Num("Place CPS", 8, 1, 15));
    private final Setting.Num breakCps = add(new Setting.Num("Break CPS", 10, 1, 20));
    private final Setting.Bool antiSelf = add(new Setting.Bool("Self damage check", true));

    public AutoCrystal() { super("AutoCrystal", "Places + detonates crystals", Category.COMBAT); premium = true; }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;
        LivingEntity target = nearestEnemy();
        if (target == null) return;

        // 1) detonate existing crystals near the target
        for (Entity e : mc.level.entitiesForRendering()) {
            // attack reach is 3.05 — hitting a crystal 6 blocks away flags.
            if (e instanceof EndCrystal c
                && dev.noxclient.util.Interact.inAttackReach(c)
                && c.distanceToSqr(target) < 36 && Legitifier.ready(this, breakCps.get(), 0.3)) {
                dev.noxclient.util.Interact.aim(c.getEyePosition());
                mc.gameMode.attack(mc.player, c);
                mc.player.swing(InteractionHand.MAIN_HAND,
                    net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
                return;
            }
        }
        // 2) place a new crystal
        if (!mc.player.getMainHandItem().is(Items.END_CRYSTAL)
            && !mc.player.getOffhandItem().is(Items.END_CRYSTAL)) return;
        if (!Legitifier.ready(this, placeCps.get(), 0.35)) return;
        BlockPos spot = findSpot(target);
        if (spot == null) return;
        if (antiSelf.on() && spot.distSqr(mc.player.blockPosition()) < 9) return;
        if (!dev.noxclient.util.Interact.inReach(spot)) return; // place reach 4.5
        InteractionHand hand = mc.player.getMainHandItem().is(Items.END_CRYSTAL)
            ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        mc.gameMode.useItemOn(mc.player, hand,
            dev.noxclient.util.Interact.placeOn(spot, Direction.UP));
    }

    private BlockPos findSpot(LivingEntity t) {
        BlockPos tb = t.blockPosition();
        int r = (int) Math.ceil(range.get());
        for (BlockPos p : BlockPos.betweenClosed(tb.offset(-r, -2, -r), tb.offset(r, 0, r))) {
            var b = mc.level.getBlockState(p).getBlock();
            if ((b == Blocks.OBSIDIAN || b == Blocks.BEDROCK)
                && mc.level.getBlockState(p.above()).isAir()
                && p.above().distSqr(tb) <= range.get() * range.get())
                return p.immutable();
        }
        return null;
    }

    private LivingEntity nearestEnemy() {
        LivingEntity best = null; double bd = 64;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof Player p && e != mc.player && p.isAlive()) {
                double d = mc.player.distanceToSqr(p);
                if (d < bd) { bd = d; best = p; }
            }
        }
        return best;
    }
}
