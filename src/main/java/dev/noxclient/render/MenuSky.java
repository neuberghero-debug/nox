package dev.noxclient.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Menu sky — renders the same raymarched shader sky as CustomSky (black hole /
 * deep space / aurora / ringed planet) into an offscreen texture via its own
 * renderpearl pass, then blits the texture into the GUI batch so all widgets
 * still draw on top in the same ordered pass. No raw GL, no feedback loop —
 * the encoder submits before the vanilla GUI pass opens.
 */
public final class MenuSky {
    private MenuSky() {}

    private static final Identifier TEX_ID = Identifier.fromNamespaceAndPath("noxclient", "menu_sky");

    public static final RenderPipeline PIPELINE = RenderPipeline.builder()
        .withLocation(Identifier.fromNamespaceAndPath("noxclient", "pipeline/nox_menu_sky"))
        .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/nox_menu_sky"))
        .withFragmentShader(Identifier.fromNamespaceAndPath("minecraft", "core/nox_menu_sky"))
        .withCull(false)
        .withVertexBinding(0, DefaultVertexFormat.POSITION)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withColorTargetState(ColorTargetState.DEFAULT)
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform("NoxSky", UniformType.UNIFORM_BUFFER).build())
        .build();

    private static GpuBuffer tri;
    private static GpuBuffer uniforms;
    private static ByteBuffer uniformData;
    private static GpuTexture tex;
    private static GpuTextureView texView;
    private static SkyTexture skyTex;
    private static int texW, texH;
    private static float time;
    private static long lastNanos;

    /** Menu palette — steel-blue gargantua (inner heat → cool outer disk). */
    private static final int[] PALETTE = {0x3E5F8F, 0x9FB8E8, 0x6E86B8, 0x04060C, 0x101828};

    /**
     * Renders this frame's sky into the offscreen texture — called at
     * GameRenderer.extract HEAD (before ANY gui commands/glyph uploads record
     * this frame). Submitting mid-extract raced vanilla texture uploads and
     * corrupted font atlas pages, so the pass runs at the frame boundary.
     */
    public static void submitPass(int w, int h) {
        if (RenderSystem.getDevice() == null) return;
        var compiled = RenderSystem.getCompiledPipelineNullable(PIPELINE);
        if (compiled == null) return;
        ensureBuffers();
        if (tri == null || uniforms == null) return;
        if (!ensureTexture(w, h)) return;

        // advance time
        long now = System.nanoTime();
        time += lastNanos == 0 ? 0 : Math.min(0.1f, (now - lastNanos) / 1.0e9f);
        lastNanos = now;
        writeUniforms(w / (float) Math.max(1, h));

        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniforms.slice(), uniformData);
        try (RenderPass pass = encoder.createRenderPass(() -> "nox menu sky",
            texView, Optional.of(new Vector4f(0f, 0f, 0f, 1f)), null, OptionalDouble.empty())) {
            pass.pushDebugGroup(() -> "nox menu sky");
            pass.setPipeline(compiled);
            // bind every program uniform — setPipeline dirties ALL slots and
            // unset ones NPE in setupDraw (no-ops when the program lacks them)
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms()
                .writeTransform(new Matrix4f(), new Vector4f(1f, 1f, 1f, 1f)));
            pass.setUniform("NoxSky", uniforms.slice());
            pass.setVertexBuffer(0, tri.slice());
            var seq = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            pass.setIndexBuffer(seq.getBuffer(6), seq.type());
            pass.drawIndexed(6, 1, 0, 0, 0);
            pass.popDebugGroup();
        }
        encoder.submit();
    }

    /** Queues the fullscreen blit; false → caller falls back to legacy skies. */
    public static boolean render(GuiGraphicsExtractor g, int w, int h) {
        if (texView == null) return false;
        g.blit(TEX_ID, 0, 0, w, h, 0f, 1f, 0f, 1f);
        return true;
    }

    private static boolean ensureTexture(int w, int h) {
        if (w <= 0 || h <= 0) return false;
        if (tex != null && texW == w && texH == h) return true;
        if (tex != null) { tex.close(); tex = null; }
        if (texView != null) { texView.close(); texView = null; }
        tex = RenderSystem.getDevice().createTexture("nox menu sky",
            GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING,
            GpuFormat.RGBA8_UNORM, w, h, 1, 1);
        texView = RenderSystem.getDevice().createTextureView(tex);
        texW = w; texH = h;
        if (skyTex == null) {
            skyTex = new SkyTexture();
            Minecraft.getInstance().getTextureManager().register(TEX_ID, skyTex);
        }
        skyTex.bind(tex, texView);
        return true;
    }

    private static void writeUniforms(float aspect) {
        uniformData.clear();
        // Look = (mode=3 blackHole, alpha, time, exposure)
        uniformData.putFloat(3f);
        uniformData.putFloat(1f);
        uniformData.putFloat(time);
        uniformData.putFloat(1.25f);
        // Amounts = (stars, nebula, band, aspect)
        uniformData.putFloat(0.85f);
        uniformData.putFloat(0.55f);
        uniformData.putFloat(0.4f);
        uniformData.putFloat(aspect);
        for (int c : PALETTE) {
            uniformData.putFloat(((c >> 16) & 0xFF) / 255f);
            uniformData.putFloat(((c >> 8) & 0xFF) / 255f);
            uniformData.putFloat((c & 0xFF) / 255f);
            uniformData.putFloat(1f);
        }
        uniformData.flip();
    }

    private static void ensureBuffers() {
        if (tri != null) return;
        ByteBufferBuilder bb = new ByteBufferBuilder(12 * 4);
        BufferBuilder buf = new BufferBuilder(bb, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION);
        buf.addVertex(-1f, -1f, 0f);
        buf.addVertex(1f, -1f, 0f);
        buf.addVertex(1f, 1f, 0f);
        buf.addVertex(-1f, 1f, 0f);
        MeshData data = buf.buildOrThrow();
        tri = RenderSystem.getDevice().createBuffer(() -> "NOX menu sky tri",
            GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, data.vertexBuffer());
        data.close();
        bb.close();
        uniforms = RenderSystem.getDevice().createBuffer(() -> "NOX menu sky uniforms",
            GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_WRITE, 112L);
        uniformData = BufferUtils.createByteBuffer(112).order(ByteOrder.nativeOrder());
    }

    /** TextureManager-facing wrapper so GuiGraphicsExtractor.blit can sample our pass output. */
    private static final class SkyTexture extends AbstractTexture {
        private GpuSampler sampler;

        void bind(GpuTexture t, GpuTextureView v) {
            this.texture = t;
            this.textureView = v;
            if (sampler == null) {
                sampler = RenderSystem.getDevice().createSampler(
                    AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.empty());
            }
            this.sampler = sampler;
        }

        @Override public void close() {
            if (sampler != null) { sampler.close(); sampler = null; }
            texture = null;
            textureView = null;
        }
    }

    public static void close() {
        if (tri != null) { tri.close(); tri = null; }
        if (uniforms != null) { uniforms.close(); uniforms = null; }
        if (tex != null) { tex.close(); tex = null; }
        if (texView != null) { texView.close(); texView = null; }
        uniformData = null;
        texW = texH = 0;
    }
}
