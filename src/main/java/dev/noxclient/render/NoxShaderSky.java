package dev.noxclient.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import dev.noxclient.NoxClient;
import dev.noxclient.module.render.CustomSky;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Full shader-sky renderer — replaces the vanilla sky disc with a custom
 * renderpearl pipeline (raymarched gargantua / aurora / ringed planet /
 * deep-space nebula, driven by the CustomSky module). Draws a unit cube
 * around the camera inside the same render pass vanilla uses for the sky
 * disc, so depth/state ordering stays identical.
 */
public final class NoxShaderSky {
    private NoxShaderSky() {}

    public static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
        .withLocation(net.minecraft.resources.Identifier.fromNamespaceAndPath("noxclient", "pipeline/nox_sky"))
        .withVertexShader(net.minecraft.resources.Identifier.fromNamespaceAndPath("noxclient", "core/nox_sky"))
        .withFragmentShader(net.minecraft.resources.Identifier.fromNamespaceAndPath("noxclient", "core/nox_sky"))
        .withCull(false)
        .withVertexBinding(0, DefaultVertexFormat.POSITION)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withColorTargetState(ColorTargetState.DEFAULT)
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform("NoxSky", UniformType.UNIFORM_BUFFER).build())
        .build();

    private static GpuBuffer cube;
    private static GpuBuffer uniforms;
    private static ByteBuffer uniformData;
    private static float time;

    /** true when CustomSky selected a shader-driven sky mode. */
    public static boolean active() {
        var m = NoxClient.INSTANCE.modules.get("CustomSky");
        return m instanceof CustomSky cs && cs.isEnabled() && cs.shaderMode() >= 0;
    }

    /** Upload this frame's uniforms — call BEFORE the sky render pass opens. */
    public static void prepareFrame() {
        if (!active()) return;
        ensureBuffers();
        if (uniforms == null) return;
        time += Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks() / 20f;
        writeUniforms();
        RenderSystem.getDevice().createCommandEncoder()
            .writeToBuffer(uniforms.slice(), uniformData);
    }

    /** Draw our sky onto the vanilla sky render pass; false → let vanilla run. */
    public static boolean render(RenderPass pass) {
        if (!active() || cube == null || uniforms == null) return false;
        var compiled = RenderSystem.getCompiledPipelineNullable(PIPELINE);
        if (compiled == null) return false;

        GpuBufferSlice transform = RenderSystem.getDynamicUniforms()
            .writeTransform(RenderSystem.getModelViewMatrixCopy(), new Vector4f(1f, 1f, 1f, 1f));

        pass.pushDebugGroup(() -> "nox shader sky");
        pass.setPipeline(compiled);
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform("DynamicTransforms", transform);
        pass.setUniform("NoxSky", uniforms.slice());
        pass.setVertexBuffer(0, cube.slice());
        var seq = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        pass.setIndexBuffer(seq.getBuffer(36), seq.type());
        pass.drawIndexed(36, 1, 0, 0, 0);
        pass.popDebugGroup();
        return true;
    }

    private static void writeUniforms() {
        var m = NoxClient.INSTANCE.modules.get("CustomSky");
        if (!(m instanceof CustomSky cs)) return;
        uniformData.clear();
        // Look = (mode, alpha, time, exposure)
        uniformData.putFloat(cs.shaderMode());
        uniformData.putFloat(1.0f);
        uniformData.putFloat(time);
        uniformData.putFloat(cs.exposure.get().floatValue());
        // Amounts = (stars, nebula/aurora, band, pixelScale)
        uniformData.putFloat(cs.stars.get().floatValue());
        uniformData.putFloat(cs.nebula.get().floatValue());
        uniformData.putFloat(cs.band.get().floatValue());
        uniformData.putFloat(1.0f);
        int[] p = cs.palette();
        for (int i = 0; i < 5; i++) {
            int c = p[i];
            uniformData.putFloat(((c >> 16) & 0xFF) / 255f);
            uniformData.putFloat(((c >> 8) & 0xFF) / 255f);
            uniformData.putFloat((c & 0xFF) / 255f);
            uniformData.putFloat(1.0f);
        }
        uniformData.flip();
    }

    private static void ensureBuffers() {
        if (cube != null) return;
        ByteBufferBuilder bb = new ByteBufferBuilder(24 * 12);
        BufferBuilder buf = new BufferBuilder(bb, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION);
        face(buf, -1, -1, -1, -1, -1, 1, 1, -1, 1, 1, -1, -1);
        face(buf, -1, 1, -1, 1, 1, -1, 1, 1, 1, -1, 1, 1);
        face(buf, -1, -1, -1, 1, -1, -1, 1, 1, -1, -1, 1, -1);
        face(buf, -1, -1, 1, -1, 1, 1, 1, 1, 1, 1, -1, 1);
        face(buf, -1, -1, -1, -1, 1, -1, -1, 1, 1, -1, -1, 1);
        face(buf, 1, -1, -1, 1, -1, 1, 1, 1, 1, 1, 1, -1);
        MeshData data = buf.buildOrThrow();
        cube = RenderSystem.getDevice().createBuffer(() -> "NOX sky cube",
            GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, data.vertexBuffer());
        data.close();
        bb.close();
        uniforms = RenderSystem.getDevice().createBuffer(() -> "NOX sky uniforms",
            GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_WRITE, 112L);
        uniformData = BufferUtils.createByteBuffer(112).order(ByteOrder.nativeOrder());
    }

    private static void face(BufferBuilder b, float... v) {
        for (int i = 0; i < 4; i++) b.addVertex(v[i * 3], v[i * 3 + 1], v[i * 3 + 2]);
    }

    public static void close() {
        if (cube != null) { cube.close(); cube = null; }
        if (uniforms != null) { uniforms.close(); uniforms = null; }
        uniformData = null;
    }
}
