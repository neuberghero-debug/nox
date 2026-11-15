#!/usr/bin/env python3
"""NOX cosmetic GLB generator — low-poly vertex-colored models (no textures).

Builds real glTF 2.0 binaries (GLB): box builders + octahedra + rings.
Units: 1.0 = one MC block (renderer scales 0.0625 into model space).

Usage: python3 tools/glb_gen.py            # all assets -> src/main/resources/assets/noxclient/models/
"""
import json, math, struct, sys
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/noxclient/models"

P, N, C, I = [], [], [], []          # positions, normals, colors, indices

def vert(x, y, z, nx, ny, nz, col):
    P.extend((x, y, z)); N.extend((nx, ny, nz)); C.extend(col)
    return len(P) // 3 - 1

def quad(a, b, c, d):                # two tris, CCW
    I.extend((a, b, c, a, c, d))

def box(cx, cy, cz, w, h, d, col):
    """Centered box; col = (r,g,b,a) 0..1 — slight per-face shade for depth."""
    x0, x1 = cx - w / 2, cx + w / 2
    y0, y1 = cy - h / 2, cy + h / 2
    z0, z1 = cz - d / 2, cz + d / 2
    sh = lambda k: [min(1, c * k) for c in col[:3]] + [col[3]]
    # +x -x +y -y +z -z faces (normals out)
    for nx, ny, nz, vs, k in [
        ( 1,0,0, [(x1,y0,z1),(x1,y0,z0),(x1,y1,z0),(x1,y1,z1)], .92),
        (-1,0,0, [(x0,y0,z0),(x0,y0,z1),(x0,y1,z1),(x0,y1,z0)], .92),
        (0, 1,0, [(x0,y1,z1),(x1,y1,z1),(x1,y1,z0),(x0,y1,z0)], 1.0),
        (0,-1,0, [(x0,y0,z0),(x1,y0,z0),(x1,y0,z1),(x0,y0,z1)], .75),
        (0,0, 1, [(x0,y0,z1),(x1,y0,z1),(x1,y1,z1),(x0,y1,z1)], .96),
        (0,0,-1, [(x1,y0,z0),(x0,y0,z0),(x0,y1,z0),(x1,y1,z0)], .96)]:
        idx = [vert(*p, nx, ny, nz, sh(k)) for p in vs]
        quad(*idx)

def octa(cx, cy, cz, r, col, squash=1.0):
    t = vert(cx, cy + r*squash, cz, 0, 1, 0, col)
    b = vert(cx, cy - r*squash, cz, 0,-1, 0, col)
    ring = [vert(cx + r*math.cos(a), cy, cz + r*math.sin(a), 0, 0, 0, col)
            for a in [0, math.pi/2, math.pi, 3*math.pi/2]]
    for i in range(4):
        j = (i + 1) % 4
        I.extend((t, ring[i], ring[j]))
        I.extend((b, ring[j], ring[i]))

def ring(cx, cy, cz, r, thick, col, seg=10, tilt=0.0):
    """Segmented flat ring around Y (optionally tilted around X)."""
    ct, st = math.cos(tilt), math.sin(tilt)
    for i in range(seg):
        a = i / seg * 2 * math.pi
        seg_len = 2 * math.pi * r / seg * 1.12
        x, z = math.cos(a) * r, math.sin(a) * r
        # rotate a local box to tangential orientation
        ca, sa = math.cos(a + math.pi / 2), math.sin(a + math.pi / 2)
        ox, oy, oz = cx + x, cy + x * st * 0.0, cz + z
        # cheap tilt: displace y by radius*tilt*sin(a)
        box_t(ox, cy + math.sin(a) * r * math.sin(tilt), oz,
              seg_len, thick, thick, a + math.pi / 2, col)

def box_t(cx, cy, cz, w, h, d, roty, col):
    """Y-rotated box (manual rot of the 8 corners)."""
    ca, sa = math.cos(roty), math.sin(roty)
    def rot(px, pz): return (cx + px*ca - pz*sa, cz + px*sa + pz*ca)
    x0, x1 = -w/2, w/2; z0, z1 = -d/2, d/2; y0, y1 = cy-h/2, cy+h/2
    cs = {}
    for (px, pz) in [(x0,z0),(x1,z0),(x1,z1),(x0,z1)]:
        cs[(px,pz)] = rot(px, pz)
    (ax,az),(bx,bz),(cx_,cz_),(dx,dz) = cs[(x0,z0)],cs[(x1,z0)],cs[(x1,z1)],cs[(x0,z1)]
    k_top = [min(1,c*1.0) for c in col[:3]]+[col[3]]
    k_bot = [min(1,c*.75) for c in col[:3]]+[col[3]]
    k_sid = [min(1,c*.9)  for c in col[:3]]+[col[3]]
    quad(vert(ax,y0,az,0,-1,0,k_bot),vert(bx,y0,bz,0,-1,0,k_bot),
         vert(cx_,y0,cz_,0,-1,0,k_bot),vert(dx,y0,dz,0,-1,0,k_bot))
    quad(vert(dx,y1,dz,0,1,0,k_top),vert(cx_,y1,cz_,0,1,0,k_top),
         vert(bx,y1,bz,0,1,0,k_top),vert(ax,y1,az,0,1,0,k_top))
    for (p0,p1) in [((ax,az),(bx,bz)),((bx,bz),(cx_,cz_)),((cx_,cz_),(dx,dz)),((dx,dz),(ax,az))]:
        nx,nz = (p1[1]-p0[1]), -(p1[0]-p0[0])
        l = math.hypot(nx,nz) or 1; nx,nz = nx/l,nz/l
        quad(vert(p0[0],y0,p0[1],nx,0,nz,k_sid),vert(p1[0],y0,p1[1],nx,0,nz,k_sid),
             vert(p1[0],y1,p1[1],nx,0,nz,k_sid),vert(p0[0],y1,p0[1],nx,0,nz,k_sid))

def flush(name):
    """Bake accumulated arrays into <name>.glb and reset."""
    global P, N, C, I
    if not I: return
    bin_ = b''.join(
        struct.pack('<f', v) for v in P) + b''.join(
        struct.pack('<f', v) for v in N) + b''.join(
        struct.pack('<f', v) for v in C) + b''.join(
        struct.pack('<I', v) for v in I)
    np_, nn, nc, ni = len(P), len(N), len(C), len(I)
    js = {
        "asset": {"version": "2.0", "generator": "nox-glb-gen"},
        "scene": 0,
        "scenes": [{"nodes": [0]}],
        "nodes": [{"mesh": 0}],
        "meshes": [{"primitives": [{"attributes": {"POSITION": 0, "NORMAL": 1, "COLOR_0": 2},
                                    "indices": 3, "mode": 4}]}],
        "materials": [{"pbrMetallicRoughness": {"baseColorFactor": [1,1,1,1],
                        "metallicFactor": 0.0, "roughnessFactor": 0.9}}],
        "accessors": [
            {"bufferView": 0, "componentType": 5126, "count": np_//3, "type": "VEC3"},
            {"bufferView": 1, "componentType": 5126, "count": nn//3, "type": "VEC3"},
            {"bufferView": 2, "componentType": 5126, "count": nc//4, "type": "VEC4"},
            {"bufferView": 3, "componentType": 5125, "count": ni,    "type": "SCALAR"}],
        "bufferViews": [
            {"buffer": 0, "byteOffset": 0,        "byteLength": np_*4},
            {"buffer": 0, "byteOffset": np_*4,    "byteLength": nn*4},
            {"buffer": 0, "byteOffset": (np_+nn)*4,"byteLength": nc*4},
            {"buffer": 0, "byteOffset": (np_+nn+nc)*4, "byteLength": ni*4}],
        "buffers": [{"byteLength": len(bin_)}],
    }
    jb = json.dumps(js, separators=(',', ':')).encode()
    jb += b' ' * (-len(jb) % 4)
    pad = -len(bin_) % 4; bin_ += b'\0' * pad
    glb = struct.pack('<III', 0x46546C67, 2, 12 + 8 + len(jb) + 8 + len(bin_))
    glb += struct.pack('<II', len(jb), 0x4E4F534A) + jb
    glb += struct.pack('<II', len(bin_), 0x004E4942) + bin_
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / f"{name}.glb").write_bytes(glb)
    print(f"{name}.glb  verts={np_//3} tris={ni//3} ({len(glb)}B)")
    P, N, C, I = [], [], [], []

# ---------- palettes ----------
DRAGON   = (0.34, 0.20, 0.62, 1)     # deep purple
DRAGON_B = (0.62, 0.44, 0.95, 1)     # belly / accents
DRAGON_W = (0.22, 0.12, 0.45, 1)     # wing membrane
GOLD     = (0.95, 0.75, 0.25, 1)
GOLD_D   = (0.75, 0.55, 0.15, 1)
NOX      = (0.55, 0.36, 0.96, 1)
BONE     = (0.88, 0.85, 0.78, 1)
GREEN    = (0.35, 0.8, 0.45, 1)
RED      = (0.85, 0.25, 0.2, 1)

# ---------- dragon (shoulder pet, ~0.55 block tall) ----------
def dragon():
    box(0, 0.20, 0,        .30, .22, .46, DRAGON)             # body
    box(0, 0.14, -0.02,    .24, .10, .38, DRAGON_B)           # belly
    box(0, 0.36, 0.30,     .26, .24, .24, DRAGON)             # head
    box(0, 0.30, 0.46,     .16, .10, .14, DRAGON_B)           # snout
    box(-.09, .50, 0.26,   .05, .12, .05, GOLD)               # horn L
    box( .09, .50, 0.26,   .05, .12, .05, GOLD)               # horn R
    box(-.085, .38, 0.42,  .045,.045,.02, (1.0,0.9,0.4,1))    # eye L (glow gold)
    box( .085, .38, 0.42,  .045,.045,.02, (1.0,0.9,0.4,1))    # eye R
    # wings — two flat flares
    box(-.24, .34, -0.02,  .20, .02, .26, DRAGON_W)
    box( .24, .34, -0.02,  .20, .02, .26, DRAGON_W)
    box(-.36, .30, -0.06,  .10, .02, .18, DRAGON_W)
    box( .36, .30, -0.06,  .10, .02, .18, DRAGON_W)
    box(0, 0.20, -0.32,    .10, .10, .22, DRAGON)             # tail
    box(0, 0.24, -0.46,    .06, .06, .10, DRAGON_B)           # tail tip
    box(-.09, .06, 0.12,   .07, .14, .07, DRAGON)             # legs
    box( .09, .06, 0.12,   .07, .14, .07, DRAGON)
    box(-.09, .06, -0.12,  .07, .14, .07, DRAGON)
    box( .09, .06, -0.12,  .07, .14, .07, DRAGON)
    flush("dragon")

# ---------- parrot ----------
def parrot():
    box(0, .16, 0,     .20, .26, .22, (0.2,0.55,0.85,1))      # body
    box(0, .34, .06,   .18, .18, .18, (0.25,0.65,0.95,1))     # head
    box(0, .32, .18,   .10, .06, .08, (1.0,0.65,0.15,1))      # beak
    box(-.075,.36,.15, .04,.04,.02, (0.05,0.05,0.05,1))       # eyes
    box( .075,.36,.15, .04,.04,.02, (0.05,0.05,0.05,1))
    box(-.13,.16,-.02, .06, .20, .16, (0.15,0.45,0.75,1))     # wings folded
    box( .13,.16,-.02, .06, .20, .16, (0.15,0.45,0.75,1))
    box(0, .10,-.20,   .12, .06, .20, (0.9,0.3,0.3,1))        # tail
    box(0, .04, .02,   .14, .08, .14, (0.5,0.3,0.1,1))        # feet
    flush("parrot")

# ---------- blob (slime pet) ----------
def blob():
    box(0, .16, 0,    .34, .30, .34, (0.45,0.85,0.5,0.92))
    box(0, .12, 0,    .26, .18, .26, (0.3,0.7,0.4,0.9))
    box(-.07, .20, .171, .05, .07, .01, (0.05,0.1,0.05,1))
    box( .07, .20, .171, .05, .07, .01, (0.05,0.1,0.05,1))
    box(0, .10, .171, .08, .03, .01, (0.05,0.1,0.05,1))
    flush("blob")

# ---------- wisp (floating glow spirit) ----------
def wisp():
    octa(0, .22, 0, .16, (0.6,0.5,1.0,1), 1.3)
    octa(0, .22, 0, .10, (0.85,0.75,1.0,1), 1.5)
    box(0, .42, 0, .04, .06, .04, (0.8,0.7,1,1))
    flush("wisp")

# ---------- crown ----------
def crown():
    ring(0, 0.04, 0, .30, .07, GOLD, seg=8)
    for i in range(5):
        a = i / 5 * 2 * math.pi
        box(math.cos(a)*.28, .16, math.sin(a)*.28, .05, .12, .05, GOLD)
        box(math.cos(a)*.28, .24, math.sin(a)*.28, .07, .03, .07, (0.9,0.2,0.35,1))
    box(0, .10, .30, .07, .10, .04, (0.25,0.7,1.0,1))        # front gem
    flush("crown")

# ---------- horns ----------
def horns():
    for s in (-1, 1):
        box(s*.24, .02, 0,  .09, .16, .09, BONE)
        box(s*.28, .14, 0,  .07, .10, .07, (0.78,0.72,0.62,1))
        box(s*.31, .22, 0,  .05, .06, .05, (0.65,0.6,0.5,1))
    flush("horns")

# ---------- halo ----------
def halo():
    ring(0, .34, 0, .34, .045, (1.0,0.85,0.3,1), seg=12)
    flush("halo")

# ---------- beanie ----------
def beanie():
    ring(0, .02, 0, .34, .10, NOX, seg=10)
    box(0, .10, 0, .52, .14, .52, NOX)
    box(0, .19, 0, .44, .10, .44, (0.45,0.28,0.85,1))
    box(0, .27, 0, .12, .10, .12, (1,1,1,1))                 # pom
    flush("beanie")

# ---------- orbitring (aura) ----------
def orbitring():
    ring(0, 0, 0, .55, .05, NOX, seg=14, tilt=.35)
    for i in range(3):
        a = i / 3 * 2 * math.pi
        octa(math.cos(a)*.55, math.sin(a)*.18, math.sin(a)*.55, .07, (0.75,0.6,1,1))
    flush("orbitring")

# ---------- sigil (flat summoning circle) ----------
def sigil():
    ring(0, 0, 0, .50, .05, (0.6,0.3,1,1), seg=10)
    ring(0, 0, 0, .34, .04, (0.75,0.5,1,1), seg=8)
    for i in range(5):                                        # inner star spokes
        a = i / 5 * 2 * math.pi + math.pi/2
        box_t(math.cos(a)*.17, 0, math.sin(a)*.17, .36, .03, .05, -a, (0.9,0.7,1,1))
    flush("sigil")

# ---------- shards (orbiting crystals) ----------
def shards():
    for i in range(3):
        a = i / 3 * 2 * math.pi
        octa(math.cos(a)*.5, 0, math.sin(a)*.5, .09, (0.55,0.4,1,1), 2.0)
    flush("shards")

for f in (dragon, parrot, blob, wisp, crown, horns, halo, beanie,
          orbitring, sigil, shards):
    f()
print("done ->", OUT)
