package dev.noxclient.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal glTF 2.0 binary (GLB) loader for cosmetic meshes.
 * Supports: POSITION/NORMAL/TEXCOORD_0/COLOR_0 attributes, indexed or
 * non-indexed triangle primitives, node TRS hierarchies (baked into the
 * vertex data at load — real-time animation is procedural, not skeletal).
 * Vertex colors are the primary color path; baseColorFactor is the fallback.
 */
public class GlbModel {

    public static class Vertex {
        public float x, y, z, nx, ny, nz, u, v, r, g, b, a;
    }

    public static class Prim {
        public Vertex[] verts;
        public int[] indices;        // may be null -> sequential
        public float[] baseColor = {1, 1, 1, 1};
    }

    public final List<Prim> prims = new ArrayList<>();
    public final float[] bounds = new float[6]; // min/max xyz

    /** Parse a GLB from a stream. Throws IOException on malformed data. */
    public static GlbModel load(InputStream in) throws IOException {
        ByteBuffer glb = ByteBuffer.wrap(in.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
        if (glb.remaining() < 12 || glb.getInt() != 0x46546C67)
            throw new IOException("not a GLB");
        glb.getInt(); glb.getInt();  // version, length
        byte[] jsonBytes = null, binBytes = null;
        while (glb.remaining() >= 8) {
            int clen = glb.getInt(), ctype = glb.getInt();
            byte[] chunk = new byte[Math.min(clen, glb.remaining())];
            glb.get(chunk);
            if (ctype == 0x4E4F534A) jsonBytes = chunk;          // JSON
            else if (ctype == 0x004E4942) binBytes = chunk;      // BIN\0
        }
        if (jsonBytes == null) throw new IOException("no JSON chunk");
        JsonObject root = JsonParser.parseString(new String(jsonBytes)).getAsJsonObject();
        ByteBuffer bin = binBytes == null ? null
            : ByteBuffer.wrap(binBytes).order(ByteOrder.LITTLE_ENDIAN);

        GlbModel m = new GlbModel();
        for (int i = 0; i < 6; i += 3) { m.bounds[i] = Float.MAX_VALUE; m.bounds[i+1] = Float.MAX_VALUE; m.bounds[i+2] = Float.MAX_VALUE; }
        m.bounds[3] = m.bounds[4] = m.bounds[5] = -Float.MAX_VALUE;

        JsonArray scenes = root.getAsJsonArray("scenes");
        JsonArray nodes = root.getAsJsonArray("nodes");
        JsonArray meshes = root.getAsJsonArray("meshes");
        if (meshes == null || scenes == null || nodes == null) return m;
        int sceneIdx = root.has("scene") ? root.get("scene").getAsInt() : 0;
        JsonArray roots = scenes.get(sceneIdx).getAsJsonObject().getAsJsonArray("nodes");
        if (roots == null) return m;
        for (JsonElement rn : roots)
            walkNode(m, root, nodes, meshes, bin, rn.getAsInt(), new Matrix4f());
        return m;
    }

    private static void walkNode(GlbModel m, JsonObject root, JsonArray nodes,
            JsonArray meshes, ByteBuffer bin, int nodeIdx, Matrix4f parent) {
        JsonObject node = nodes.get(nodeIdx).getAsJsonObject();
        Matrix4f local = new Matrix4f();
        if (node.has("matrix")) {
            JsonArray mat = node.getAsJsonArray("matrix");
            float[] f = new float[16];
            for (int i = 0; i < 16; i++) f[i] = mat.get(i).getAsFloat();
            local.set(f);                       // column-major — glTF order
        } else {
            float[] t = arr(node, "translation", 0f, 0f, 0f);
            float[] r = arr(node, "rotation", 0f, 0f, 0f, 1f);
            float[] s = arr(node, "scale", 1f, 1f, 1f);
            local.translationRotateScale(t[0], t[1], t[2],
                r[0], r[1], r[2], r[3], s[0], s[1], s[2]);
        }
        Matrix4f world = new Matrix4f(parent).mul(local);
        if (node.has("mesh")) {
            JsonArray prims = meshes.get(node.get("mesh").getAsInt())
                .getAsJsonObject().getAsJsonArray("primitives");
            for (JsonElement pe : prims) m.addPrim(root, pe.getAsJsonObject(), bin, world);
        }
        if (node.has("children"))
            for (JsonElement c : node.getAsJsonArray("children"))
                walkNode(m, root, nodes, meshes, bin, c.getAsInt(), world);
    }

    private static float[] arr(JsonObject o, String k, float... def) {
        if (!o.has(k)) return def;
        JsonArray a = o.getAsJsonArray(k);
        float[] f = new float[a.size()];
        for (int i = 0; i < f.length; i++) f[i] = a.get(i).getAsFloat();
        return f;
    }

    /** Accessor data of the wanted attribute -> float[][] rows. */
    private static float[][] accessor(JsonObject root, int idx, ByteBuffer bin,
            int comps) {
        JsonObject acc = root.getAsJsonArray("accessors").get(idx).getAsJsonObject();
        int count = acc.get("count").getAsInt();
        int ctype = acc.get("componentType").getAsInt();
        JsonObject bv = root.getAsJsonArray("bufferViews")
            .get(acc.get("bufferView").getAsInt()).getAsJsonObject();
        int off = (bv.has("byteOffset") ? bv.get("byteOffset").getAsInt() : 0)
            + (acc.has("byteOffset") ? acc.get("byteOffset").getAsInt() : 0);
        int compSize = switch (ctype) {
            case 5126, 5125 -> 4;
            case 5123, 5122 -> 2;
            default -> 1;
        };
        int stride = bv.has("byteStride") ? bv.get("byteStride").getAsInt() : 0;
        if (stride == 0) stride = comps * compSize;
        float[][] out = new float[count][comps];
        ByteBuffer view = bin.slice().order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < count; i++) {
            int base = off + stride * i;
            for (int c = 0; c < comps; c++) {
                view.position(base + c * compSize);
                out[i][c] = switch (ctype) {
                    case 5126 -> view.getFloat();
                    case 5123 -> (view.getShort() & 0xFFFF) / 65535f;
                    case 5122 -> view.getShort() / 32767f;
                    case 5121 -> (view.get() & 0xFF) / 255f;
                    case 5120 -> view.get() / 127f;
                    default -> view.getFloat();
                };
            }
        }
        return out;
    }

    private void addPrim(JsonObject root, JsonObject prim, ByteBuffer bin, Matrix4f world) {
        JsonObject attrs = prim.getAsJsonObject("attributes");
        if (bin == null || attrs == null || !attrs.has("POSITION")) return;
        float[][] pos = accessor(root, attrs.get("POSITION").getAsInt(), bin, 3);
        float[][] nrm = attrs.has("NORMAL") ? accessor(root, attrs.get("NORMAL").getAsInt(), bin, 3) : null;
        float[][] uv  = attrs.has("TEXCOORD_0") ? accessor(root, attrs.get("TEXCOORD_0").getAsInt(), bin, 2) : null;
        float[][] col = attrs.has("COLOR_0") ? accessor(root, attrs.get("COLOR_0").getAsInt(), bin, 4) : null;
        // COLOR_0 VEC3 exists in the wild — retry as 3 comps if sizes mismatch
        if (col != null && col.length != pos.length) col = null;

        Prim p = new Prim();
        p.verts = new Vertex[pos.length];
        Matrix4f nm = new Matrix4f(world).invert().transpose();  // normal matrix
        for (int i = 0; i < pos.length; i++) {
            Vertex v = new Vertex();
            Vector3f t = new Vector3f(pos[i][0], pos[i][1], pos[i][2]).mulPosition(world);
            v.x = t.x; v.y = t.y; v.z = t.z;
            if (nrm != null) {
                Vector3f n = new Vector3f(nrm[i][0], nrm[i][1], nrm[i][2]).mulDirection(nm).normalize();
                v.nx = n.x; v.ny = n.y; v.nz = n.z;
            } else { v.ny = 1; }
            if (uv != null) { v.u = uv[i][0]; v.v = uv[i][1]; }
            if (col != null) { v.r = col[i][0]; v.g = col[i][1]; v.b = col[i][2]; v.a = col[i][3]; }
            else v.r = v.g = v.b = v.a = 1;
            p.verts[i] = v;
            bounds[0] = Math.min(bounds[0], v.x); bounds[3] = Math.max(bounds[3], v.x);
            bounds[1] = Math.min(bounds[1], v.y); bounds[4] = Math.max(bounds[4], v.y);
            bounds[2] = Math.min(bounds[2], v.z); bounds[5] = Math.max(bounds[5], v.z);
        }
        if (prim.has("indices")) {
            JsonObject acc = root.getAsJsonArray("accessors")
                .get(prim.get("indices").getAsInt()).getAsJsonObject();
            int count = acc.get("count").getAsInt();
            int ctype = acc.get("componentType").getAsInt();
            JsonObject bv = root.getAsJsonArray("bufferViews")
                .get(acc.get("bufferView").getAsInt()).getAsJsonObject();
            int off = (bv.has("byteOffset") ? bv.get("byteOffset").getAsInt() : 0)
                + (acc.has("byteOffset") ? acc.get("byteOffset").getAsInt() : 0);
            ByteBuffer view = bin.slice().order(ByteOrder.LITTLE_ENDIAN);
            view.position(off);
            p.indices = new int[count];
            for (int i = 0; i < count; i++)
                p.indices[i] = switch (ctype) {
                    case 5125 -> view.getInt();
                    case 5123 -> view.getShort() & 0xFFFF;
                    case 5121 -> view.get() & 0xFF;
                    default -> view.getShort() & 0xFFFF;
                };
        }
        if (prim.has("material")) {
            JsonObject mat = root.getAsJsonArray("materials")
                .get(prim.get("material").getAsInt()).getAsJsonObject();
            if (mat.has("pbrMetallicRoughness")) {
                JsonObject pbr = mat.getAsJsonObject("pbrMetallicRoughness");
                if (pbr.has("baseColorFactor")) {
                    JsonArray f = pbr.getAsJsonArray("baseColorFactor");
                    for (int i = 0; i < 4; i++) p.baseColor[i] = f.get(i).getAsFloat();
                }
            }
        }
        prims.add(p);
    }

    /** Emit every triangle into the consumer (pose transforms positions). */
    public void render(PoseStack.Pose pose, VertexConsumer vc, int light,
            float rMul, float gMul, float bMul, float aMul) {
        Vector3f t = new Vector3f();
        for (Prim p : prims) {
            int n = p.indices != null ? p.indices.length : p.verts.length;
            for (int i = 0; i < n; i++) {
                Vertex v = p.verts[p.indices != null ? p.indices[i] : i];
                t.set(v.x, v.y, v.z);
                vc.addVertex(pose, t)
                    .setColor(v.r * p.baseColor[0] * rMul,
                            v.g * p.baseColor[1] * gMul,
                            v.b * p.baseColor[2] * bMul,
                            v.a * p.baseColor[3] * aMul)
                    .setUv(v.u, v.v)
                    .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, new Vector3f(v.nx, v.ny, v.nz));
            }
        }
    }
}
