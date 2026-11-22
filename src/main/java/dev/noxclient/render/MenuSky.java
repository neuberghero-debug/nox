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
    /** true once a pass actually submitted — render() may blit only then. */
    private static boolean ready;

    // Baked parallax sky (tools/render_sky.py + horizon split): far = full-res
    // milky-way plate, near = treeline cutout that drifts faster = depth.
    // Full-res sharp on any display, ~zero GPU cost — the raymarch below
    // stays as a fallback for clients without the baked assets.
    private static final Identifier FAR_RES =
        Identifier.fromNamespaceAndPath("noxclient", "sky4k/far.jpg");
    private static final Identifier NEAR_RES =
        Identifier.fromNamespaceAndPath("noxclient", "sky4k/near.png");
    private static final Identifier FAR_ID =
        Identifier.fromNamespaceAndPath("noxclient", "sky4k/far");
    private static final Identifier NEAR_ID =
        Identifier.fromNamespaceAndPath("noxclient", "sky4k/near");
    private static boolean heroTried, heroReady;
    private static int farW, farH, nearW, nearH;
    private static long heroStart;

    private static net.minecraft.client.renderer.texture.DynamicTexture loadTex(
        Identifier res, String name) throws Exception {
        var res1 = Minecraft.getInstance().getResourceManager().getResource(res).orElse(null);
        if (res1 == null) return null;
        com.mojang.blaze3d.platform.NativeImage img;
        try (var in = res1.open()) {
            var buf = javax.imageio.ImageIO.read(in);
            boolean alpha = res.getPath().endsWith(".png");
            img = new com.mojang.blaze3d.platform.NativeImage(
                buf.getWidth(), buf.getHeight(), alpha);
            for (int y = 0; y < buf.getHeight(); y++)
                for (int x = 0; x < buf.getWidth(); x++) {
                    int p = buf.getRGB(x, y);
                    img.setPixelABGR(x, y, (p & 0xFF00FF00)
                        | (p & 0xFF) << 16 | (p >> 16 & 0xFF));
                }
        }
        var t = new net.minecraft.client.renderer.texture.DynamicTexture(
            () -> name, img);
        Minecraft.getInstance().getTextureManager()
            .register(Identifier.fromNamespaceAndPath("noxclient", name), t);
        return t;
    }

    private static void ensureHero() {
        if (heroTried) return;
        heroTried = true;
        try {
            var far = loadTex(FAR_RES, "sky4k/far");
            var near = loadTex(NEAR_RES, "sky4k/near");
            if (far == null) return;
            farW = far.getPixels().getWidth();  farH = far.getPixels().getHeight();
            if (near != null) { nearW = near.getPixels().getWidth(); nearH = near.getPixels().getHeight(); }
            heroStart = System.nanoTime();
            heroReady = true;
        } catch (Throwable t) {
            System.err.println("[nox] menu hero load failed: " + t.getMessage());
        }
    }

    /** Twinkling star layer — deterministic, no per-frame allocation. */
    private static void drawStars(GuiGraphicsExtractor g, int w, int h, float t) {
        for (int i = 0; i < 90; i++) {
            int sx = (i * 137 + 41) % 997, sy = (i * 211 + 97) % 641;
            float x = sx / 997f * w, y = sy / 641f * h * 0.72f; // upper 72% only
            float tw = 0.5f + 0.5f * (float) Math.sin(t * (0.4 + (i % 7) * 0.13f) + i * 1.7f);
            int a = (int) (tw * tw * 160);
            if (a < 20) continue;
            int s = (i % 11 == 0) ? 2 : 1;
            g.fill((int) x, (int) y, (int) x + s, (int) y + s, a << 24 | 0xD8E4FF);
        }
        // shooting star every ~14s, streaks down-left over ~0.9s
        float cyc = (t % 14f);
        if (cyc < 0.9f) {
            float u = cyc / 0.9f;
            int x0 = (int) (w * 0.72f - u * w * 0.30f), y0 = (int) (h * 0.16f + u * h * 0.20f);
            int fade = (int) (200 * (1 - u));
            for (int k = 0; k < 7; k++) {
                int aa = fade * (7 - k) / 7;
                g.fill(x0 + k * 7, y0 - k * 5, x0 + k * 7 + 5, y0 - k * 5 + 1,
                    aa << 24 | 0xEAF2FF);
            }
        }
    }

    /** Parallax sky: far plate breathes slowly, near treeline drifts harder. */
    private static boolean renderHero(GuiGraphicsExtractor g, int w, int h) {
        if (!heroReady) return false;
        float t = (System.nanoTime() - heroStart) / 1e9f;
        // far layer: cover-fit + slow 1.00→1.05 zoom, drifting toward center-right
        float sa = (float) w / h, ta = (float) farW / farH;
        float sw, sh;
        if (ta > sa) { sh = farH; sw = farH * sa; }
        else         { sw = farW; sh = farW / sa; }
        float z = 1f + 0.05f * (0.5f + 0.5f * (float) Math.sin(t * (Math.PI * 2 / 50.0) - Math.PI / 2));
        sw /= z; sh /= z;
        float u = (farW - sw) * (0.5f + 0.12f * (float) Math.sin(t * 0.013f));
        float v = (farH - sh) * 0.42f;
        g.blit(FAR_ID, 0, 0, w, h,
            u / farW, (u + sw) / farW, v / farH, (v + sh) / farH);
        drawStars(g, w, h, t);
        if (nearW > 0) {
            // near layer: stretched to width, bottom-anchored, drifts ±10px
            // against the far plate → the parallax depth cue
            int nh = nearH * w / nearW;
            int drift = (int) (10 * Math.sin(t * 0.05f) + 4 * Math.sin(t * 0.021f));
            g.blit(NEAR_ID, -20 + drift, h - nh, w + 20 + drift, h,
                0f, 1f, 0f, 1f);
        }
        return true;
    }

    /** Menu palette — amber-hot inner rim cooling to steel-blue outer disk
     * (inner heat → cool outer disk), deep space + band tint. */
    private static final int[] PALETTE = {0x914331, 0xFFC07D, 0x314D74, 0x070B14, 0x101826};

    /**
     * Renders this frame's sky into the offscreen texture — called at
     * GameRenderer.extract HEAD (before ANY gui commands/glyph uploads record
     * this frame). Submitting mid-extract raced vanilla texture uploads and
     * corrupted font atlas pages, so the pass runs at the frame boundary.
     */
    public static void submitPass(int w, int h) {
        ready = false;
        ensureHero();
        if (heroReady) return;            // baked 4K frame replaces the march entirely
        if (RenderSystem.getDevice() == null) return;
        var compiled = RenderSystem.getCompiledPipelineNullable(PIPELINE);
        if (compiled == null) return;
        ensureBuffers();
        if (tri == null || uniforms == null) return;
        // iGPU budget: raymarch at a capped short edge and let the bilinear
        // blit upscale — the content is soft-focus anyway. Full physical res
        // on Intel UHD was a multi-second-per-frame freeze at the title.
        int shortEdge = Math.min(w, h);
        if (shortEdge <= 0) return;
        float ratio = Math.min(1f, 480f / shortEdge);
        int tw = Math.max(1, Math.round(w * ratio));
        int th = Math.max(1, Math.round(h * ratio));
        if (!ensureTexture(tw, th)) return;

        // advance time (frozen under reduced motion — still renders one frame)
        long now = System.nanoTime();
        if (!dev.noxclient.gui.UICustom.reducedMotion)
            time += lastNanos == 0 ? 0 : Math.min(0.1f, (now - lastNanos) / 1.0e9f);
        lastNanos = now;
        writeUniforms(tw / (float) Math.max(1, th));

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
        ready = true;
    }

    /** Queues the fullscreen blit; false → caller falls back to legacy skies. */
    public static boolean render(GuiGraphicsExtractor g, int w, int h) {
        if (renderHero(g, w, h)) return true;
        if (!ready || texView == null) return false;
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
        uniformData.putFloat(1.30f);
        // Amounts = (stars, nebula, band, aspect)
        uniformData.putFloat(0.88f);
        uniformData.putFloat(0.28f);
        uniformData.putFloat(0.18f);
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
        ready = false;
        texW = texH = 0;
    }
}
