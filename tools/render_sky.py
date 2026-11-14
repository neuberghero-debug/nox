#!/usr/bin/env python3
"""Cinematic black-hole sky renderer for the NOX title screen.

Look target: Interstellar-style photoreal frame — big dark shadow, thin
bright photon ring, razor-thin edge-on disk line with doppler beaming,
colored fbm nebula wisps (teal/green/amber), golden horizon haze, dense
starfield, film grain. Everything animated on a seamless integer-cycle
loop: light orbits the ring, disk filaments flow, nebula drift & fade,
stars twinkle, shooting stars cross.

Output: assets/noxclient/sky/frame_XXX.jpg  (played by AnimatedSky)
"""
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
import os, sys

W, H = 1280, 720
FRAMES = int(os.environ.get("SKY_FRAMES", "88"))
OUT = os.environ.get("SKY_OUT", os.path.join(os.path.dirname(__file__), "..",
                   "src", "main", "resources", "assets", "noxclient", "sky"))
QUALITY = 87

CX, CY = W * 0.5, H * 0.545          # hole slightly below center like ref
R_SH = 64.0                          # shadow radius (px)
R_PH = 68.5                          # photon ring radius
DISK_HALF = 430.0                    # half-length of the thin disk line

rng = np.random.default_rng(7)


# ---------- helpers ----------

def noise(w, h, res, seed):
    """Value noise: low-res random grid upscaled bicubic -> [0,1]."""
    r = np.random.default_rng(seed)
    g = r.random((max(2, h // res), max(2, w // res))).astype(np.float32)
    img = Image.fromarray((g * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
    return np.asarray(img, np.float32) / 255.0


def fbm(w, h, base_res, octaves, seed):
    out = np.zeros((h, w), np.float32)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        out += noise(w, h, base_res >> o if base_res >> o >= 8 else 8, seed + o) * amp
        tot += amp
        amp *= 0.55
    return out / tot


def add_gauss(base, cx, cy, rx, ry, amp, rgb):
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    e = np.exp(-(((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2))
    for i in range(3):
        base[..., i] += e * amp * rgb[i]


def to_img(rgb):
    return Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8))


# ---------- static layers ----------

YY, XX = np.mgrid[0:H, 0:W].astype(np.float32)
DX, DY = XX - CX, YY - CY
DIST = np.sqrt(DX * DX + DY * DY)

# Starfield: 850 stars, brightness/mag spread, per-star twinkle freq+phase
N_STARS = 850
sx = rng.uniform(0, W, N_STARS)
sy = rng.uniform(0, H, N_STARS)
smag = rng.uniform(0.25, 1.0, N_STARS) ** 2.2          # few bright, many dim
sfreq = rng.uniform(0.4, 1.6, N_STARS)                 # twinkle cycles/loop
sph = rng.uniform(0, 2 * math.pi, N_STARS)
stint = rng.uniform(0.5, 1.0, N_STARS)                 # tint: cool->warm
BRIGHT = np.argsort(smag)[-22:]                        # brightest get spikes

# Nebula sprites (RGBA) — generated once, drifted/faded per frame
def nebula_sprite(seed, tint):
    w, h = 900, 620
    n = fbm(w, h, 220, 5, seed)
    warp = fbm(w, h, 160, 4, seed + 40)
    n = np.clip(n + (warp - 0.5) * 0.55, 0, 1)
    n = np.clip((n - 0.42) / 0.5, 0, 1) ** 1.6          # sparse wisps
    # soft window so sprites never show rectangle borders
    wx = np.clip(np.minimum(np.arange(w), w - 1 - np.arange(w)) / 170.0, 0, 1)
    wy = np.clip(np.minimum(np.arange(h), h - 1 - np.arange(h)) / 140.0, 0, 1)
    a = (n * wx[None, :] * wy[:, None] * 255).astype(np.uint8)
    col = np.zeros((h, w, 4), np.uint8)
    col[..., 0], col[..., 1], col[..., 2], col[..., 3] = tint[0], tint[1], tint[2], a
    return Image.fromarray(col, "RGBA")

NEBS = [
    (nebula_sprite(11, (36, 160, 110, 0)), (-210, -190), 0.95),   # green upper-left
    (nebula_sprite(23, (30, 190, 130, 0)), (230, -110), 0.85),    # teal-green right
    (nebula_sprite(37, (150, 110, 60, 0)), (-40, 130), 0.6),      # amber low-mid
    (nebula_sprite(53, (90, 60, 150, 0)), (330, 190), 0.45),      # violet corner
]

# Horizon haze: warm golden band across the bottom (planet-limb feel)
haze = fbm(W, 90, 120, 4, 91)
haze = np.asarray(Image.fromarray((haze * 255).astype(np.uint8)).resize((W, 90), Image.BICUBIC), np.float32) / 255.0
haze *= np.linspace(0.0, 1.0, 90)[:, None] ** 1.4       # fade in from top
HAZE = Image.fromarray((haze * 255).astype(np.uint8)).convert("L")
HAZE_COL = np.zeros((90, W, 4), np.uint8)
HAZE_COL[..., 0], HAZE_COL[..., 1], HAZE_COL[..., 2] = 210, 170, 105

# Static star glow under everything
base = np.zeros((H, W, 3), np.float32)
add_gauss(base, CX, CY, 520, 300, 0.5, (26, 20, 14))    # warm ambient
add_gauss(base, W * 0.5, H * 0.52, W * 0.72, H * 0.6, 0.5, (12, 14, 18))
BASE = base

# Vignette
VIG = np.clip(1.0 - ((DX / (W * 0.62)) ** 2 + ((YY - H * 0.5) / (H * 0.62)) ** 2) * 0.85, 0.25, 1.0)

# Disk profile: radial falloff past the hole, tapered ends
disk_radial = np.clip(1.0 - (np.abs(DX) - R_PH) / DISK_HALF, 0, 1) ** 1.15
disk_radial *= (DIST > R_PH * 0.8)

print("rendering", FRAMES, "frames ...")
os.makedirs(OUT, exist_ok=True)
for old in os.listdir(OUT):
    if old.startswith("frame_"):
        os.remove(os.path.join(OUT, old))

for f in range(FRAMES):
    ph = f / FRAMES * 2 * math.pi
    img = BASE.copy()

    # --- stars ---
    star_layer = Image.new("RGB", (W, H), (0, 0, 0))
    sd = ImageDraw.Draw(star_layer)
    tw = 0.62 + 0.38 * np.sin(ph * sfreq + sph)
    for i in range(N_STARS):
        b = smag[i] * tw[i] * 235
        if b < 14:
            continue
        r = 1 if smag[i] > 0.42 else 0
        c = int(b)
        tint = stint[i]
        col = (c, int(c * (0.82 + 0.18 * tint)), int(c * (0.7 + 0.3 * (1 - tint))))
        if r:
            sd.ellipse([sx[i] - r, sy[i] - r, sx[i] + r, sy[i] + r], fill=col)
        else:
            sd.point([sx[i], sy[i]], fill=col)
    # spikes on the brightest
    for i in BRIGHT:
        b = smag[i] * tw[i]
        if b < 0.55:
            continue
        c = int(b * 160)
        ln = 2 + int(b * 4)
        sd.line([sx[i] - ln, sy[i], sx[i] + ln, sy[i]], fill=(c // 3, c // 3, c // 3))
        sd.line([sx[i], sy[i] - ln, sx[i], sy[i] + ln], fill=(c // 3, c // 3, c // 3))
    img += np.asarray(star_layer, np.float32)

    # --- nebulas: drift + breathe ---
    neb = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    for i, (sp, (ox, oy), strength) in enumerate(NEBS):
        cyc = [1, 1, 2, 1][i]
        a = strength * (0.55 + 0.45 * math.sin(ph * cyc + i * 1.9))
        frame_sp = sp.copy()
        frame_sp.putalpha(sp.getchannel("A").point(lambda p: int(p * a)))
        dx = int(14 * math.sin(ph * [1, 2, 1, 1][i] + i))
        dy = int(8 * math.cos(ph * [1, 1, 2, 1][i] + i * 2))
        neb.alpha_composite(frame_sp, (int(CX - sp.width / 2 + ox + dx),
                                     int(CY - sp.height / 2 + oy + dy)))
    neb_rgb = np.asarray(neb, np.float32)
    img = img * (1 - neb_rgb[..., 3:4] / 255.0 * 0.75) + neb_rgb[..., :3] * (neb_rgb[..., 3:4] / 255.0)

    # --- thin disk line + doppler beaming + flowing filaments ---
    # vertical falloff: razor core + glow skirt
    core = np.exp(-(DY / 1.7) ** 2)
    skirt = np.exp(-(DY / 9.0) ** 2) * 0.32
    # filaments flow along x (integer wraps -> seamless)
    flow1 = fbm(W, 1, 90, 3, 101)[0][None, :]
    flow2 = fbm(W, 1, 34, 3, 202)[0][None, :]
    shift1 = int(ph / (2 * math.pi) * 140) % W
    shift2 = int(ph / (2 * math.pi) * 60) % W
    flow1 = np.roll(flow1, shift1, axis=1)
    flow2 = np.roll(flow2, -shift2, axis=1)
    fil = 0.72 + 0.28 * flow1 + 0.18 * flow2
    # doppler: left side approaching (hot/blue-white), right receding
    beam = 1.0 / (1.0 + np.exp(-DX / 110.0))             # 0 left -> 1 right
    dop_amp = 1.45 - 0.95 * beam                          # left brighter
    disk = (core + skirt) * disk_radial * fil * dop_amp
    # color: white-hot core left, amber right
    r = disk * (255 - 40 * beam)
    gch = disk * (215 - 60 * beam)
    bch = disk * (165 - 75 * beam)
    img[..., 0] += r
    img[..., 1] += gch
    img[..., 2] += bch
    # faint far-disk arcs above/below (lensed light over the pole)
    for sgn in (-1, 1):
        arc_r = R_PH + 14 + 3 * math.sin(ph + sgn)
        dy_arc = np.sqrt(np.clip(arc_r ** 2 - DX ** 2, 0, None))
        arc = np.exp(-((np.abs(DY) - dy_arc * 1.0) / 3.2) ** 2) * \
              np.clip(1 - np.abs(DX) / (arc_r + 55), 0, 1) * \
              (np.abs(DX) < arc_r + 55)
        arc *= 0.5 * (DIST > R_SH)
        img[..., 0] += arc * 235
        img[..., 1] += arc * 190
        img[..., 2] += arc * 130

    # --- photon ring + orbiting hotspot ---
    ring = np.exp(-((DIST - R_PH) / 1.55) ** 2)
    ring_glow = np.exp(-((DIST - R_PH) / 5.5) ** 2) * 0.4
    ang = np.arctan2(DY, DX)
    hot = np.exp(-((np.sin((ang - ph) / 2) * 2) ** 2) * 3.2)     # orbiting hotspot
    ring_full = (ring + ring_glow) * (0.72 + 0.75 * hot) * (DIST > R_SH * 0.94)
    img[..., 0] += ring_full * 255
    img[..., 1] += ring_full * 205
    img[..., 2] += ring_full * 140
    # inner glow hugging the shadow
    inner = np.exp(-((DIST - R_SH) / 16.0) ** 2) * 0.30 * (DIST > R_SH)
    img[..., 0] += inner * 255
    img[..., 1] += inner * 175
    img[..., 2] += inner * 95

    # --- event horizon: hard dark shadow ---
    shadow = DIST < R_SH
    img[shadow] *= 0.05
    rim = np.exp(-((DIST - R_SH) / 2.5) ** 2)
    img[shadow] += rim[shadow][:, None] * np.array([14, 9, 5], np.float32)

    # --- bloom ---
    pil = to_img(img)
    blur = pil.filter(ImageFilter.GaussianBlur(7))
    img += np.asarray(blur, np.float32) * 0.45
    blur2 = pil.filter(ImageFilter.GaussianBlur(26))
    img += np.asarray(blur2, np.float32) * 0.22

    # --- horizon haze ---
    hz = np.asarray(HAZE_COL, np.float32)
    hz[..., 3] = np.asarray(HAZE, np.float32) * (0.5 + 0.12 * math.sin(ph * 2))
    sl = slice(H - 90, H)
    a = hz[..., 3:4] / 255.0
    img[sl] = img[sl] * (1 - a * 0.75) + hz[..., :3] * a

    # --- shooting stars (3 per loop) ---
    sp_layer = Image.new("RGB", (W, H), (0, 0, 0))
    spd = ImageDraw.Draw(sp_layer)
    for i, (t0, x0, y0, vx, vy) in enumerate([(0.12, 150, 90, 16, 7),
                                             (0.47, 980, 60, -14, 9),
                                             (0.8, 300, 140, 18, 6)]):
        tt = (f / FRAMES - t0) % 1.0
        if tt < 0.05:
            u = tt / 0.05
            x1, y1 = x0 + vx * u * 8, y0 + vy * u * 8
            fade = int(230 * (1 - u))
            spd.line([x1 - vx * 0.9, y1 - vy * 0.9, x1, y1],
                     fill=(fade, int(fade * 0.9), int(fade * 0.75)))
    img += np.asarray(sp_layer, np.float32)

    # --- tonemap, vignette, grain ---
    img = 255 * (1 - np.exp(-img / 200.0))                # soft filmic roll
    img *= VIG[..., None]
    grain = rng.normal(0, 2.6, (H, W, 1)).astype(np.float32)
    img += grain

    to_img(img).save(os.path.join(OUT, f"frame_{f:03d}.jpg"),
                     quality=QUALITY, optimize=True)
    if f % 22 == 0:
        print(" ", f)

print("done ->", OUT)
