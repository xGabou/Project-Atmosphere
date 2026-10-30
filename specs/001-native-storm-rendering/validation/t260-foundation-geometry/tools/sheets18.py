"""T260.18 sheets from the in-game frames: identical seeds, stages, cameras, scale, FOV, lighting (mode 20) and quality.
usage: sheets18.py SRC OUT
Frames: s<seed>_<cand>_<view>_m<20|27>[_exact].png
- compare_<seed>.png    rows = 9 views (close from session 2: the session-1 close camera of 2609101 stood among trees), columns = T260.14 / T260.15 / T260.18 A / B / C (lighting mode 20)
- underside_<seed>.png  directly below, oblique underside and edge-on underside profile, LARGE (mode 20)
- raking_<seed>.png     the same underside views under the T260.16 raking diagnostic (mode 27, identical for every candidate)
- foundation_<seed>.png the foundation geometry alone (no towers), five views (mode 20)
- control_2609102.png   the developing dry seed: no organised pair, so every candidate shows the same independent groups"""
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
CANDS = [("t14", "T260.14 shared foundation"), ("t15", "T260.15 relief"), ("t18a", "T260.18 A deep hanging masses"),
         ("t18b", "T260.18 B uneven depth"), ("t18c", "T260.18 C integrated roots")]
VIEW = {"ground300": "ground 300", "ground800": "ground 800", "cthree": "three-quarter (foundation)", "eside": "elevated side", "above": "above",
        "close": "close", "close2": "close\n(session 2)", "lunder": "directly below", "lounder": "oblique underside", "uprofile": "underside edge-on\n(profile vs sky)"}
SEEDS = [("2609101", "2609101 mature t75"), ("2609196", "2609196 mature t75")]


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


NOTE = "Identical cameras (frozen per seed), lift (+320 blocks, every view), FOV, quality and lighting (T260.5 neutral, mode 20)"
for seed, title in SEEDS:
    views = ["ground300", "ground800", "cthree", "eside", "above", "close2", "lunder", "lounder", "uprofile"]
    grid(f"compare_{seed}.png", f"{title} - T260.14 / T260.15 / T260.18 A, B, C", CANDS, [(v, VIEW[v]) for v in views],
         lambda c, r, s=seed: frame(f"s{s}_{c}_{r}_m20"), 400, 225, note=NOTE)
    under = ["lunder", "lounder", "uprofile"]
    grid(f"underside_{seed}.png", f"{title} - underside views, large (mode 20)", CANDS, [(v, VIEW[v]) for v in under],
         lambda c, r, s=seed: frame(f"s{s}_{c}_{r}_m20"), 640, 360, note=NOTE)
    grid(f"raking_{seed}.png", f"{title} - underside views under the T260.16 raking diagnostic (mode 27, same for every candidate)", CANDS,
         [(v, VIEW[v]) for v in under], lambda c, r, s=seed: frame(f"s{s}_{c}_{r}_m27"), 640, 360,
         note="Diagnostic only: light from 12 deg below the horizon shows relief that mode 20 renders flat (T260.16)")
    fviews = ["cthree", "eside", "lunder", "lounder", "uprofile"]
    grid(f"foundation_{seed}.png", f"{title} - foundation geometry alone (no towers, diagnostic)", [(c + "f", l.replace("shared foundation", "foundation")) for c, l in CANDS],
         [(v, VIEW[v]) for v in fviews], lambda c, r, s=seed: frame(f"s{s}_{c}_{r}_m20"), 480, 270, note=NOTE)
grid("control_2609102.png", "2609102 developing t30 (dry) - no organised pair: every candidate keeps the independent T260.11 groups",
     [("t18a", "T260.18 A"), ("t14", "T260.14"), ("t18c", "T260.18 C")], [(v, VIEW[v]) for v in ["ground800", "eside", "above"]],
     lambda c, r: frame(f"s2609102_{c}_{r}_m20"), 480, 270, note=NOTE)
