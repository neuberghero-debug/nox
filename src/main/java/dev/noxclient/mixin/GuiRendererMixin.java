package dev.noxclient.mixin;

import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Anchor only — the NVG overlay used to draw here at render() TAIL, but later
 * renderpearl submits inside GameRenderer.render() could still rewrite the
 * render target afterwards, which produced frame-dependent flicker. The pass
 * now runs in PresentMixin right before GpuSurface.blitFromTexture — the last
 * moment the RT can be touched before it is copied to the window surface.
 */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
}
