"""T260.17 sheets from the in-game frames: identical geometry (T260.15), cameras, exposure and resolution per seed.
usage: sheets.py SRC OUT
Frames: s<seed>_<cand>_sun<35|12>_<view>_m<mode>[ _res100 | _sp10 | _exact ].png
- compare_<seed>_sun<e>.png: rows = 8 views (foundation only = t15f underside), columns = modes 20 / 29 / A / B / C / D
- underside_<seed>.png:      the two underside views LARGE, modes 20 / 29 / C / D, daylight and low sun
- factors_<seed>.png:        Phase 1: one factor changed at a time (underside views, daylight)
- discrimination_<seed>.png: T260.14 vs T260.15 at the identical underside cameras under each lighting mode"""
import os, sys
from PIL import Image, ImageDraw, ImageFont

SRC, OUT = sys.argv[1:3]
os.makedirs(OUT, exist_ok=True)
try:
    F = ImageFont.truetype("arial.ttf", 22)
    FS = ImageFont.truetype("arial.ttf", 17)
except OSError:
    F = FS = ImageFont.load_default()
BG = (24, 28, 36)
MODE = {20: "current (T260.5)", 29: "T260.16 candidate", 31: "A directional ambient", 32: "B gradient-aware", 33: "C baked hybrid",
        38: "D grazing quadrature", 34: "F1 + transmission", 35: "F2 + directional ambient", 28: "F3 gradient normal",
        37: "baked normal", 36: "F6 display mapping", 22: "direct sun only", 23: "ambient only", 30: "F1 od uncapped", 39: "D ambient only"}
VIEW = {"ground300": "ground 300", "ground800": "ground 800", "eside": "elevated side", "close": "close side", "above": "above",
        "lunder": "directly below", "lounder": "oblique underside", "f_lunder": "foundation only\ndirectly below",
        "f_lounder": "foundation only\noblique underside"}
SEEDS = [("2609101", "2609101 mature t75"), ("2609196", "2609196 mature t75"), ("2609102", "2609102 developing t30 (dry)")]


def frame(name):
    p = os.path.join(SRC, name + ".png")
    return Image.open(p).convert("RGB") if os.path.exists(p) else None


def grid(path, title, cols, rows, get, w, h, lw=210, note=None):
    th = 44 + (26 if note else 0)
    im = Image.new("RGB", (lw + w * len(cols), th + 30 + h * len(rows)), BG)
    d = ImageDraw.Draw(im)
    d.text((8, 10), title, fill=(235, 235, 235), font=F)
    if note:
        d.text((8, 40), note, fill=(190, 190, 190), font=FS)
    for i, (_, cl) in enumerate(cols):
        d.text((lw + i * w + 6, th + 4), cl, fill=(250, 210, 120), font=FS)
    for j, (rk, rl) in enumerate(rows):
        d.text((8, th + 30 + j * h + h // 2 - 12), rl, fill=(200, 220, 255), font=FS)
        for i, (ck, _) in enumerate(cols):
            f = get(ck, rk)
            if f is not None:
                im.paste(f.resize((w, h), Image.LANCZOS), (lw + i * w, th + 30 + j * h))
            else:
                d.text((lw + i * w + 12, th + 30 + j * h + 12), "-", fill=(160, 160, 160), font=F)
    im.save(os.path.join(OUT, path), optimize=True)
    print("wrote", path, im.size)


CMP = [20, 29, 31, 32, 33, 38]
for seed, title in SEEDS:
    views = ["ground300", "ground800", "eside", "close", "above", "lunder", "lounder", "f_lunder", "f_lounder"]
    for sun in ["35", "12"]:
        def get(c, r, s=seed, e=sun):
            if r.startswith("f_"):
                return frame(f"s{s}_t15f_sun{e}_{r[2:]}_m{c}")
            return frame(f"s{s}_t15_sun{e}_{r}_m{c}")
        grid(f"compare_{seed}_sun{sun}.png", f"{title} - sun {sun} deg - identical geometry (T260.15), cameras, exposure and resolution",
             [(m, MODE[m]) for m in CMP], [(v, VIEW[v]) for v in views], get, 400, 225,
             note="Underside views: system lifted 240 blocks (identical for every mode); foundation only = T260.15 foundation without towers (diagnostic)")
    grid(f"underside_{seed}.png", f"{title} - underside views, large",
         [(m, MODE[m]) for m in [20, 29, 33, 38]],
         [(f"{v}:{e}", f"{VIEW[v]}\nsun {e} deg") for v in ["lunder", "lounder"] for e in ["35", "12"]],
         lambda c, r, s=seed: frame(f"s{s}_t15_sun{r.split(':')[1]}_{r.split(':')[0]}_m{c}"), 760, 428)
    grid(f"factors_{seed}.png", f"{title} - Phase 1: one factor at a time (sun 35 deg)",
         [("lunder", "directly below"), ("lounder", "oblique underside")],
         [(k, l) for k, l in [("m20", "current (T260.5 mode 20)"), ("m22", "direct sun only"), ("m30", "F1: sun od uncapped\n(red = beyond the cap of 8)"),
                              ("m34", "F1: + diffuse transmission"), ("m23", "F2: isotropic ambient only"), ("m35", "F2: + directional ambient"),
                              ("m28", "F3: density-gradient normal"), ("m37", "F3: baked filtered normal"), ("m20_sp10", "F4: lattice 10 blocks\n(default 14-16)"),
                              ("m20_res100", "F5: full resolution\n(default 1/4 linear)"), ("m36", "F6: display mapping"), ("m39", "D: grazing quadrature\nambient only")]],
         lambda c, r, s=seed: frame(f"s{s}_t15_sun35_{c}_{r}"), 640, 360, lw=260)
    grid(f"discrimination_{seed}.png", f"{title} - T260.14 vs T260.15 at the identical underside cameras (sun 35 deg)",
         [(f"{c}:{v}", f"{'T260.14' if c == 't14' else 'T260.15'} {VIEW[v]}") for v in ["lunder", "lounder"] for c in ["t14", "t15"]],
         [(m, MODE[m]) for m in CMP], lambda c, r, s=seed: frame(f"s{s}_{c.split(':')[0]}_sun35_{c.split(':')[1]}_m{r}"), 480, 270)
