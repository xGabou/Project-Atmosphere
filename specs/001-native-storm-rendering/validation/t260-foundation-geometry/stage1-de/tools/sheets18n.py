"""T260.18 stage 1 (second pass) sheets: T260.15 against geometry candidates D and E, identical seeds, stages, cameras,
scale, FOV, quality and lighting (mode 20; raking mode 27 only as an identical underside diagnostic).
usage: sheets18n.py SRC OUT
- compare_<seed>.png    rows = directly below, oblique underside, ground, elevated side, three-quarter, underside edge-on
- underside_<seed>.png  the three underside views, large (mode 20)
- raking_<seed>.png     the same under the T260.16 raking diagnostic (mode 27)
- foundation_<seed>.png the foundation geometry alone (no towers)
- control_2609102.png   the developing dry seed (no organised pair)"""
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
CANDS = [("t15", "T260.15 (baseline)"), ("t18d", "T260.18 D broad compound masses + roots"), ("t18e", "T260.18 E asymmetric lowered base + roots")]
VIEW = {"lunder": "directly below", "lounder": "oblique underside", "ground800": "ground level", "eside": "elevated side",
        "cthree": "three-quarter", "uprofile": "underside edge-on\n(profile vs sky)"}
SEEDS = [("2609196", "2609196 mature t75"), ("2609101", "2609101 mature t75")]
NOTE = "Identical cameras (frozen per seed), lift (+320 blocks), FOV 110, quality normal, lighting T260.5 neutral (mode 20), sun 35 deg"


def frame(name):
    p = os.path.join(SRC, name + ".png")
    return Image.open(p).convert("RGB") if os.path.exists(p) else None


def grid(path, title, cols, rows, get, w, h, lw=200, note=None):
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


for seed, title in SEEDS:
    views = ["lunder", "lounder", "ground800", "eside", "cthree", "uprofile"]
    grid(f"compare_{seed}.png", f"{title} - T260.15 / T260.18 D / T260.18 E", CANDS, [(v, VIEW[v]) for v in views],
         lambda c, r, s=seed: frame(f"s{s}_{c}_{r}_m20"), 560, 315, note=NOTE)
    under = ["lunder", "lounder", "uprofile"]
    grid(f"underside_{seed}.png", f"{title} - underside views, large (mode 20)", CANDS, [(v, VIEW[v]) for v in under],
         lambda c, r, s=seed: frame(f"s{s}_{c}_{r}_m20"), 800, 450, note=NOTE)
    grid(f"raking_{seed}.png", f"{title} - underside views under the T260.16 raking diagnostic (mode 27, same for every candidate)", CANDS,
         [(v, VIEW[v]) for v in under], lambda c, r, s=seed: frame(f"s{s}_{c}_{r}_m27"), 800, 450,
         note="Diagnostic only (not a lighting proposal): light from 12 deg below the horizon shows relief that mode 20 renders flat")
    grid(f"foundation_{seed}.png", f"{title} - foundation geometry alone (no towers, diagnostic)", [(c + "f", l) for c, l in CANDS],
         [(v, VIEW[v]) for v in ["lunder", "lounder", "eside", "uprofile"]], lambda c, r, s=seed: frame(f"s{s}_{c}_{r}_m20"), 560, 315, note=NOTE)
grid("control_2609102.png", "2609102 developing t30 (dry) - no organised pair: every candidate keeps the independent T260.11 groups", CANDS,
     [(v, VIEW[v]) for v in ["ground800", "eside", "cthree", "lunder"]], lambda c, r: frame(f"s2609102_{c}_{r}_m20"), 560, 315, note=NOTE)
