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

W = int(os.environ.get("SKY_W", "1280"))
H = int(os.environ.get("SKY_H", "720"))
S = H / 720.0                        # px-constant scale vs. the 720p tuning
FRAMES = int(os.environ.get("SKY_FRAMES", "88"))
OUT = os.environ.get("SKY_OUT", os.path.join(os.path.dirname(__file__), "..",
                   "src", "main", "resources", "assets", "noxclient", "sky"))
QUALITY = int(os.environ.get("SKY_QUALITY", "87"))

CX = W * float(os.environ.get("SKY_CX", "0.20"))   # near-field: hole clipped top-left
CY = H * float(os.environ.get("SKY_CY", "0.30"))
ZOOM = float(os.environ.get("SKY_ZOOM", "2.0"))    # near-field: >1 = hole dominates the frame
MONO = os.environ.get("SKY_MONO", "1") == "1"      # crushed-black white-disk grade (default)
R_SH = 92.0 * S * ZOOM               # shadow radius (px)
R_PH = 97.0 * S * ZOOM               # photon ring radius
TILT = float(os.environ.get("SKY_TILT", "0.34"))  # disk inclination squash (edge-on ≈0.15)
R_DISK0, R_DISK1 = 106.0 * S * ZOOM, 330.0 * S * ZOOM  # disk inner/outer radius (disk plane)

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
N_STARS = int(850 * S * S)
sx = rng.uniform(0, W, N_STARS)
sy = rng.uniform(0, H, N_STARS)
smag = rng.uniform(0.25, 1.0, N_STARS) ** 2.2          # few bright, many dim
sfreq = rng.uniform(0.4, 1.6, N_STARS)                 # twinkle cycles/loop
sph = rng.uniform(0, 2 * math.pi, N_STARS)
stint = rng.uniform(0.5, 1.0, N_STARS)                 # tint: cool->warm
BRIGHT = np.argsort(smag)[-22:]                        # brightest get spikes

# Nebula sprites (RGBA) — generated once, drifted/faded per frame
def nebula_sprite(seed, tint):
    w, h = int(900 * S), int(620 * S)
    n = fbm(w, h, int(220 * S), 5, seed)
    warp = fbm(w, h, int(160 * S), 4, seed + 40)
    n = np.clip(n + (warp - 0.5) * 0.55, 0, 1)
    n = np.clip((n - 0.42) / 0.5, 0, 1) ** 1.6          # sparse wisps
    # soft window so sprites never show rectangle borders
    wx = np.clip(np.minimum(np.arange(w), w - 1 - np.arange(w)) / (170.0 * S), 0, 1)
    wy = np.clip(np.minimum(np.arange(h), h - 1 - np.arange(h)) / (140.0 * S), 0, 1)
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
HB = max(8, int(90 * S))
haze = fbm(W, HB, int(120 * S), 4, 91)
haze = np.asarray(Image.fromarray((haze * 255).astype(np.uint8)).resize((W, HB), Image.BICUBIC), np.float32) / 255.0
haze *= np.linspace(0.0, 1.0, HB)[:, None] ** 1.4       # fade in from top
HAZE = Image.fromarray((haze * 255).astype(np.uint8)).convert("L")
HAZE_COL = np.zeros((HB, W, 4), np.uint8)
HAZE_COL[..., 0], HAZE_COL[..., 1], HAZE_COL[..., 2] = 210, 170, 105

# Static star glow under everything
base = np.zeros((H, W, 3), np.float32)
add_gauss(base, CX, CY, 520 * S, 300 * S, 0.5, (26, 20, 14))    # warm ambient
add_gauss(base, W * 0.5, H * 0.52, W * 0.72, H * 0.6, 0.5, (12, 14, 18))
BASE = base

# Vignette
VIG = np.clip(1.0 - ((DX / (W * 0.62)) ** 2 + ((YY - H * 0.5) / (H * 0.62)) ** 2) * 0.85, 0.25, 1.0)

# Disk-plane coords: deproject screen pixels onto the inclined disk plane.
# theta = angle in the disk plane (drives swirl + doppler), dd = radius there.
DD = np.sqrt(DX * DX + (DY / TILT) ** 2)
THETA = np.arctan2(DY / TILT, DX)
# radial disk band: hot inner edge, noisy falloff to R_DISK1
disk_band = np.clip((DD - R_DISK0) / (R_DISK1 - R_DISK0), 0, 1)
disk_profile = (1.0 - disk_band) ** 0.75 * (DD > R_PH * 0.92)
# vertical disk thickness: gaussian around the plane + soft skirt
disk_thick = np.exp(-((DY - 0.0) / (TILT * DD * (1.5 if MONO else 0.9) + 16.0 * S)) ** 2) \
           + np.exp(-(DY / (30.0 * S)) ** 2) * (0.45 if MONO else 0.30)

# Far-side lensing (the Gargantua halo): screen pixels above the hole are
# remapped onto the disk plane as if the far side were bent over the top —
# low |DY| above the horizon maps to the disk's hot inner edge, rising |DY|
# walks outward → a bright shell hugging the shadow's top.
DD_halo = np.sqrt(DX * DX + ((np.abs(DY) * 1.6 - R_SH * 0.55) / TILT) ** 2)
halo_band = np.clip((DD_halo - R_DISK0 * 0.5) / (R_DISK1 * 0.85), 0, 1)
halo_sh = np.clip(1.0 - halo_band, 0, 1) ** 0.9
# only above the hole, hugging the shadow, fading toward the sides
far_disk = halo_sh * (DY < -R_SH * 0.30) * (DIST > R_SH * 0.78) \
         * np.clip(1.0 - (DIST - R_SH) / (R_SH * 2.6), 0, 1) \
         * np.clip((np.abs(DY) / (np.abs(DX) + 26.0 * S)) * 2.4, 0, 1)
# near-side disk: the band crossing in FRONT, below the hole centre
near_disk = disk_profile * disk_thick * (DY > -10)

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
        r = max(1, round(S)) if smag[i] > 0.42 else 0
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
        ln = int((2 + int(b * 4)) * S)
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
        dx = int(14 * S * math.sin(ph * [1, 2, 1, 1][i] + i))
        dy = int(8 * S * math.cos(ph * [1, 1, 2, 1][i] + i * 2))
        neb.alpha_composite(frame_sp, (int(CX - sp.width / 2 + ox + dx),
                                     int(CY - sp.height / 2 + oy + dy)))
    neb_rgb = np.asarray(neb, np.float32)
    img = img * (1 - neb_rgb[..., 3:4] / 255.0 * 0.75) + neb_rgb[..., :3] * (neb_rgb[..., 3:4] / 255.0)

    # --- accretion disk: tilted plane, swirl filaments, doppler beaming ---
    # rotating filaments in disk-plane angle -> real orbital motion
    swirl = 0.62 + 0.38 * np.sin(THETA * 5.0 - ph * 2.0) \
                + 0.22 * np.sin(THETA * 9.0 + ph * 3.0) \
                + 0.18 * np.sin(THETA * 17.0 - ph * 5.0) \
                + 0.10 * np.sin(DD * 0.9 + ph)
    swirl = np.clip(swirl, 0.15, 1.7 if MONO else 1.3)
    # doppler beaming: approaching side (-x) much brighter & hotter
    beam = 1.0 / (1.0 + np.exp(-DX / (DIST * 0.35 + 40.0 * S)))   # 0 left -> 1 right
    dop = 1.7 - 1.15 * beam
    disk = near_disk * swirl * dop * (3.6 if MONO else 1.9)
    # far-side halo over the top (lensed far disk), also doppler-tinted
    far = far_disk * (0.8 + 0.4 * np.sin(THETA * 5.0 - ph * 2.0)) * dop * (3.4 if MONO else 2.3)
    # heat map: inner edge white-blue hot, outer amber, far side cooler
    heat = np.clip(1.0 - disk_band, 0.25, 1.0)
    r  = disk * (255 - 30 * beam) + far * 235
    gc = disk * (200 - 55 * beam) * heat + far * 185
    bc = disk * (150 - 70 * beam) * heat + far * 125
    if MONO:
        gc = bc = r          # white disk — heat tint becomes luminance anyway
    img[..., 0] += r
    img[..., 1] += gc
    img[..., 2] += bc

    # --- photon ring + orbiting hotspot ---
    ring = np.exp(-((DIST - R_PH) / (1.55 * S)) ** 2)
    ring_glow = np.exp(-((DIST - R_PH) / (5.5 * S)) ** 2) * 0.4
    ang = np.arctan2(DY, DX)
    hot = np.exp(-((np.sin((ang - ph) / 2) * 2) ** 2) * 3.2)     # orbiting hotspot
    ring_full = (ring + ring_glow) * (0.72 + 0.75 * hot) * (DIST > R_SH * 0.94)
    img[..., 0] += ring_full * 255
    img[..., 1] += ring_full * 205
    img[..., 2] += ring_full * 140
    # inner glow hugging the shadow
    inner = np.exp(-((DIST - R_SH) / (16.0 * S)) ** 2) * 0.30 * (DIST > R_SH)
    img[..., 0] += inner * 255
    img[..., 1] += inner * 175
    img[..., 2] += inner * 95

    # --- event horizon: hard dark shadow ---
    shadow = DIST < R_SH
    img[shadow] *= 0.05
    rim = np.exp(-((DIST - R_SH) / (2.5 * S)) ** 2)
    img[shadow] += rim[shadow][:, None] * np.array([14, 9, 5], np.float32)

    # --- bloom ---
    pil = to_img(img)
    blur = pil.filter(ImageFilter.GaussianBlur(7 * S))
    img += np.asarray(blur, np.float32) * 0.45
    blur2 = pil.filter(ImageFilter.GaussianBlur(26 * S))
    img += np.asarray(blur2, np.float32) * 0.22

    # --- horizon haze ---
    hz = np.asarray(HAZE_COL, np.float32)
    hz[..., 3] = np.asarray(HAZE, np.float32) * (0.5 + 0.12 * math.sin(ph * 2))
    sl = slice(H - HB, H)
    a = hz[..., 3:4] / 255.0
    img[sl] = img[sl] * (1 - a * 0.75) + hz[..., :3] * a

    # --- shooting stars (3 per loop) ---
    sp_layer = Image.new("RGB", (W, H), (0, 0, 0))
    spd = ImageDraw.Draw(sp_layer)
    for i, (t0, x0, y0, vx, vy) in enumerate([(0.12, 150*S, 90*S, 16*S, 7*S),
                                             (0.47, 980*S, 60*S, -14*S, 9*S),
                                             (0.8, 300*S, 140*S, 18*S, 6*S)]):
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
    if MONO:
        # pure B&W grade — luminance, crushed blacks, blown disk whites
        lum = img @ np.array([0.2126, 0.7152, 0.0722], np.float32)
        lum = np.clip((lum - 9.0) * 1.14, 0, 255)
        img = np.repeat(lum[..., None], 3, axis=2)
    grain = rng.normal(0, float(os.environ.get("SKY_GRAIN", "2.6")),
                       (H, W, 1)).astype(np.float32)
    img += grain

    ext = os.environ.get("SKY_EXT", "jpg")
    to_img(img).save(os.path.join(OUT, f"frame_{f:03d}.{ext}"),
                     quality=QUALITY, optimize=True)
    if f % 22 == 0:
        print(" ", f)

print("done ->", OUT)
