package dev.noxclient.mixin;

import dev.noxclient.module.donutsmp.ShulkerPeek;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws a 9x3 shulker-contents preview when hovering a shulker box —
 *  polardlc ShulkerPreview, adapted to the 26.3 extractor pipeline. */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin {
    @Shadow protected Slot hoveredSlot;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void nox$shulkerPeek(GuiGraphicsExtractor g, int mx, int my, float pt, CallbackInfo ci) {
        if (!ShulkerPeek.active() || hoveredSlot == null) return;
        ItemStack stack = hoveredSlot.getItem();
        var container = stack.get(DataComponents.CONTAINER);
        if (container == null) return;
        var items = new java.util.ArrayList<ItemStack>();
        container.itemCopies().forEach(items::add);
        if (items.isEmpty()) return;

        float s = ShulkerPeek.previewScale();
        int cell = (int) (18 * s), w = cell * 9 + 8, h = cell * 3 + 8;
        int x = Math.min(mx + 12, g.guiWidth() - w - 4);
        int y = Math.min(my + 12, g.guiHeight() - h - 4);
        g.fill(x - 2, y - 2, x + w, y + h, 0xF0101218);
        g.fill(x - 2, y - 2, x + w, y - 1, 0xFF7A2BE2);
        for (int i = 0; i < Math.min(items.size(), 27); i++) {
            ItemStack it = items.get(i);
            int sx = x + (i % 9) * cell + (cell - 16) / 2;
            int sy = y + (i / 9) * cell + (cell - 16) / 2;
            if (!it.isEmpty()) {
                g.item(it, sx, sy);
                g.itemDecorations(net.minecraft.client.Minecraft.getInstance().font, it, sx, sy);
            } else {
                g.fill(sx, sy, sx + 16, sy + 16, 0x20FFFFFF);
            }
        }
    }
}
