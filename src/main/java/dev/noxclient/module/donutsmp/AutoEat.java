package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Eats when hungry. Picks the highest-saturation food in the hotbar,
 * golden apples first when "Gapple priority" is on. Humanized via Legitifier.
 */
public class AutoEat extends Module {
    private final Setting.Num hunger = add(new Setting.Num("Eat below", 16, 1, 20));
    private final Setting.Bool gapple = add(new Setting.Bool("Gapple priority", true));
    private final Setting.Bool offhand = add(new Setting.Bool("Use offhand", true));

    public AutoEat() { super("AutoEat", "Eats best food when hungry", Category.DONUTSMP); }

    @Override
    public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        if (mc.player.getFoodData().getFoodLevel() >= hunger.get()) return;
        if (mc.player.isUsingItem()) return; // let the current eat finish

        int slot = bestFoodSlot();
        InteractionHand hand = InteractionHand.MAIN_HAND;
        if (slot < 0 && offhand.on() && isFood(mc.player.getOffhandItem())) {
            hand = InteractionHand.OFF_HAND;
        } else if (slot < 0) return;

        if (hand == InteractionHand.MAIN_HAND) mc.player.getInventory().setSelectedSlot(slot);
        mc.gameMode.useItem(mc.player, hand);
    }

    private int bestFoodSlot() {
        int best = -1; double score = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack st = mc.player.getInventory().getItem(i);
            if (!isFood(st)) continue;
            FoodProperties f = st.get(DataComponents.FOOD);
            double s = f == null ? 0 : f.nutrition() + f.saturation();
            if (gapple.on() && (st.is(Items.GOLDEN_APPLE) || st.is(Items.ENCHANTED_GOLDEN_APPLE))) s += 100;
            if (s > score) { score = s; best = i; }
        }
        return best;
    }

    private boolean isFood(ItemStack st) {
        return !st.isEmpty() && st.has(DataComponents.FOOD);
    }
}
