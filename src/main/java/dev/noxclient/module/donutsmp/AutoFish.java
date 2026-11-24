package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;

/**
 * AutoFish — the classic DSMP AFK money maker. Equips a fishing rod from the
 * hotbar, casts, watches the bobber's velocity dip (a hooked fish yanks the
 * bobber down), reels in and recasts. Detection is pure client-side physics —
 * no packets beyond the two useItem calls a human makes anyway.
 *   recastSec — idle time without a bobber before re-using the rod (missed
 *     casts, broken line, re-log).
 *   biteVel   — downward y-velocity threshold that counts as a bite.
 *   maxWaitSec— force reel after this long (bobber stuck / server weirdness).
 */
public class AutoFish extends Module {
    private final Setting.Num recastSec = add(new Setting.Num("Recast after (s)", 2.5, 0.5, 20,
        "Idle time with no bobber before re-using the rod"));
    private final Setting.Num biteVel = add(new Setting.Num("Bite velocity", 0.04, 0.01, 0.3,
        "Downward bobber y-velocity that counts as a bite"));
    private final Setting.Num maxWaitSec = add(new Setting.Num("Max wait (s)", 60, 15, 300,
        "Force reel if no bite within this window"));
    private final Setting.Bool swapBack = add(new Setting.Bool("Swap back", false,
        "Return to your previous hotbar slot on disable"));

    private int rodSlot = -1, prevSlot = -1;
    private long nextUseAt, castAt;
    private double lastVelY;
    private int caught;

    public AutoFish() { super("AutoFish", "AFK fishing — cast, detect bite, reel, repeat",
        Category.DONUTSMP); }

    @Override protected void onEnable() {
        rodSlot = -1; nextUseAt = 0; castAt = 0; lastVelY = 0; caught = 0;
        prevSlot = mc.player != null ? mc.player.getInventory().getSelectedSlot() : -1;
    }

    @Override protected void onDisable() {
        if (swapBack.on() && prevSlot >= 0 && mc.player != null) select(prevSlot);
        rodSlot = -1; prevSlot = -1;
    }

    @Override public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        long now = System.currentTimeMillis();

        if (!(mc.player.getMainHandItem().getItem() instanceof FishingRodItem)) {
            int s = findRod();
            if (s < 0) { mark("no rod in hotbar"); return; }
            select(s);
            nextUseAt = now + 300;
            return;
        }

        FishingHook hook = mc.player.fishing;
        if (hook == null) {
            lastVelY = 0;
            if (now >= nextUseAt) {
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                castAt = now;
                nextUseAt = now + (long) (recastSec.get() * 1000);
                mark("cast");
            }
            return;
        }

        double velY = hook.getDeltaMovement().y;
        boolean dip = lastVelY > -0.01 && velY < -biteVel.get() && hook.isInWater();
        boolean stuck = castAt > 0 && now - castAt > maxWaitSec.get() * 1000;
        lastVelY = velY;
        if ((dip || stuck) && now >= nextUseAt) {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            nextUseAt = now + (long) (recastSec.get() * 1000);
            castAt = 0;
            if (dip) { caught++; mark("reel — bite #" + caught); }
            else mark("reel — timeout");
        }
    }

    private int findRod() {
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++)
            if (inv.getItem(i).getItem() instanceof FishingRodItem) { rodSlot = i; return i; }
        return -1;
    }

    private void select(int slot) {
        mc.player.getInventory().setSelectedSlot(slot);
        if (mc.getConnection() != null)
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
    }

    @Override public String status() {
        if (!isEnabled()) return "off";
        if (mc.player != null && !(mc.player.getMainHandItem().getItem() instanceof FishingRodItem))
            return rodSlot < 0 ? "no rod in hotbar" : "equipping rod";
        if (mc.player != null && mc.player.fishing == null) return "casting…";
        return "waiting for bite — " + caught + " caught";
    }
}
