#!/usr/bin/env python3
"""NOX menu hero plate — replaces the milky-way+treeline wallpaper.

Look target: high-end client menu backdrop (Rise/Novoline vibe), NOT a
photo wallpaper. Near-black obsidian base, a big soft violet nebula glow
right-of-center (nav column sits left), a faint indigo counter-wisp,
sparse dim baked stars, strong corner vignette, fine grain.

Outputs:
  assets/noxclient/sky4k/far.jpg   — the far plate (RGB)
  assets/noxclient/sky4k/near.png  — subtle fog ridge (RGBA parallax layer)
"""
import numpy as np
from PIL import Image, ImageFilter
import os

W = int(os.environ.get("HERO_W", "2560"))
H = int(os.environ.get("HERO_H", "1440"))
OUT = os.environ.get("HERO_OUT", os.path.join(os.path.dirname(__file__), "..",
                     "src", "main", "resources", "assets", "noxclient", "sky4k"))
SEED = int(os.environ.get("HERO_SEED", "7"))

rng = np.random.default_rng(SEED)


def noise(w, h, res, seed):
    r = np.random.default_rng(seed)
    g = r.random((max(2, h // res), max(2, w // res))).astype(np.float32)
    img = Image.fromarray((g * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
    return np.asarray(img, np.float32) / 255.0


def fbm(w, h, base_res, octaves, seed):
    out = np.zeros((h, w), np.float32)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        out += noise(w, h, max(8, base_res >> o), seed + o) * amp
        tot += amp
        amp *= 0.55
    return out / tot


YY, XX = np.mgrid[0:H, 0:W].astype(np.float32)

# ---------- far plate ----------
# vertical obsidian gradient: #0B0D13 top -> #07090F mid -> #04050A bottom
top = np.array([11, 13, 19], np.float32)
mid = np.array([10, 12, 19], np.float32)
bot = np.array([4, 5, 10], np.float32)
t = (YY / H)[..., None]
img = np.where(t < 0.45,
               top + (mid - top) * (t / 0.45),
               mid + (bot - mid) * ((t - 0.45) / 0.55)).astype(np.float32)

# big violet glow upper-right (nav column is left → interest goes right)
def add_gauss(base, cx, cy, rx, ry, amp, rgb):
    e = np.exp(-(((XX - cx) / rx) ** 2 + ((YY - cy) / ry) ** 2))
    for i in range(3):
        base[..., i] += e * amp * rgb[i]

add_gauss(img, W * 0.68, H * 0.34, W * 0.34, H * 0.30, 0.42, (122, 43, 226))  # accent violet
add_gauss(img, W * 0.80, H * 0.62, W * 0.30, H * 0.26, 0.26, (56, 40, 140))   # indigo
add_gauss(img, W * 0.60, H * 0.26, W * 0.20, H * 0.17, 0.22, (200, 60, 190))  # magenta rim
add_gauss(img, W * 0.30, H * 0.80, W * 0.30, H * 0.22, 0.20, (30, 60, 110))   # faint steel-blue low-left

# warped fbm nebula wisps, tinted to palette
def nebula(cx, cy, w, h, seed, tint, peak):
    n = fbm(w, h, int(min(w, h) * 0.28), 5, seed)
    warp = fbm(w, h, int(min(w, h) * 0.20), 4, seed + 40)
    n = np.clip(n + (warp - 0.5) * 0.75, 0, 1)
    n = np.clip((n - 0.44) / 0.52, 0, 1) ** 2.1
    wx = np.clip(np.minimum(np.arange(w), w - 1 - np.arange(w)) / (w * 0.22), 0, 1)
    wy = np.clip(np.minimum(np.arange(h), h - 1 - np.arange(h)) / (h * 0.22), 0, 1)
    a = n * wx[None, :] * wy[:, None] * peak
    x0, y0 = int(cx - w / 2), int(cy - h / 2)
    ys, xs = slice(max(0, y0), min(H, y0 + h)), slice(max(0, x0), min(W, x0 + w))
    ya, xa = slice(max(0, -y0), max(0, -y0) + (ys.stop - ys.start)), \
             slice(max(0, -x0), max(0, -x0) + (xs.stop - xs.start))
    for i in range(3):
        img[ys, xs, i] += a[ya, xa] * tint[i]

nebula(W * 0.70, H * 0.30, int(W * 0.55), int(H * 0.48), 11, (0.55, 0.20, 1.00), 58)
nebula(W * 0.85, H * 0.55, int(W * 0.45), int(H * 0.40), 29, (0.28, 0.22, 0.85), 42)
nebula(W * 0.22, H * 0.75, int(W * 0.42), int(H * 0.34), 47, (0.16, 0.30, 0.70), 28)

# sparse baked stars — dim and tiny; the live layer twinkles on top
n_stars = int(W * H / 9000)
sx = rng.uniform(0, W, n_stars)
sy = rng.uniform(0, H * 0.85, n_stars)
sm = rng.uniform(0.15, 1.0, n_stars) ** 2.4
stars = np.zeros((H, W, 3), np.float32)
for i in range(n_stars):
    b = sm[i] * 235
    if b < 12:
        continue
    x, y = int(sx[i]), int(sy[i])
    cool = rng.uniform(0.75, 1.0)
    stars[y, x] = (b * 0.9, b * 0.92, b * cool)
    if sm[i] > 0.75:
        stars[min(y + 1, H - 1), x] += b * 0.35
        stars[y, min(x + 1, W - 1)] += b * 0.35
img += stars

# vignette — corners fall to near-black
dx = (XX - W * 0.5) / (W * 0.60)
dy = (YY - H * 0.5) / (H * 0.58)
vig = np.clip(1.0 - (dx * dx + dy * dy) * 0.9, 0.18, 1.0)[..., None]
img *= vig

# fine grain
img += rng.normal(0, 1.6, (H, W, 1)).astype(np.float32)

far = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
os.makedirs(OUT, exist_ok=True)
far.save(os.path.join(OUT, "far.jpg"), quality=90)
print("far.jpg", far.size)

# ---------- near layer: fog ridge ----------
# smooth rolling ridge silhouette, near-black with a faint violet rim —
# reads as haze, not landscape. Alpha fades to 0 above the ridge line.
nw, nh = W, int(H * 0.30)
ridge1 = (fbm(nw, 1, nw // 5, 4, 71)[0] * 0.42 + 0.55) * nh     # main ridge
ridge2 = (fbm(nw, 1, nw // 8, 4, 91)[0] * 0.30 + 0.72) * nh     # closer, lower ridge
near = np.zeros((nh, nw, 4), np.float32)
xx1 = np.arange(nw)[None, :]
yy1 = np.arange(nh)[:, None]

for ridge, alpha, rgb in ((ridge1, 235, (7, 8, 13)), (ridge2, 255, (4, 5, 9))):
    m = (yy1 > ridge[None, :]).astype(np.float32)
    # soften the edge over ~6px
    soft = np.clip((yy1 - ridge[None, :]) / 7.0, 0, 1)
    a = np.maximum(near[..., 3], soft * alpha)
    for i in range(3):
        near[..., i] = np.where(soft > near[..., 3] / 255.0, rgb[i], near[..., i])
    near[..., 3] = a

# violet rim light right at the ridge line (catches the nebula)
for ridge, strength in ((ridge1, 0.55), (ridge2, 0.35)):
    rim = np.exp(-((yy1 - ridge[None, :]) / 9.0) ** 2) * strength
    near[..., 0] += rim * 26
    near[..., 1] += rim * 10
    near[..., 2] += rim * 52
    near[..., 3] = np.maximum(near[..., 3], rim * 160)

near_img = Image.fromarray(np.clip(near, 0, 255).astype(np.uint8), "RGBA")
near_img.save(os.path.join(OUT, "near.png"))
print("near.png", near_img.size)
