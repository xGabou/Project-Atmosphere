"""T260.16 sheets from the in-game frames (identical cameras and lighting per seed; scale 0.085, FOV 110, daylight frozen).
usage: sheets.py SRC OUT
Frames are named s<seed>_<cand>_<view>_m<mode>.png (cand t15 / t14 / t15f / t14f; mode = PaDiagnosticLightingMode).
- compare_<seed>.png:   rows = views, columns = T260.14 | T260.15 under T260.5 neutral (20) and under the candidate (29)
- underside_<seed>.png: the two underside views LARGE, T260.14 vs T260.15, neutral / raking / gradient / candidate
- lighting_<seed>.png:  the lighting diagnostic gallery: one row per mode, T260.14 | T260.15 at lounder and lunder
- foundation_only.png:  foundation geometry alone (diagnostic), T260.14 vs T260.15, neutral and candidate
- checks.png:           fast vs exact (interval skip) and baked vs per-sample reference light, with |difference| x8"""
import os, sys
from PIL import Image, ImageDraw, ImageFont, ImageChops

SRC, OUT = sys.argv[1:3]
os.makedirs(OUT, exist_ok=True)
try:
    F = ImageFont.truetype("arial.ttf", 22)
    FS = ImageFont.truetype("arial.ttf", 18)
except OSError:
    F = FS = ImageFont.load_default()
MODE = {20: "1 neutral (T260.5)", 26: "2 enhanced fill", 27: "3 raking light", 21: "4 density", 28: "5 gradient normal",
        30: "6 optical depth (uncapped)", 29: "7 candidate", 22: "direct sun only", 23: "ambient only", 24: "sun od (baked, cap 8)",
        25: "neutral, reference marches"}
VIEW = {"ground300": "ground 300", "ground800": "ground 800", "eside": "elevated side", "close": "close",
        "lunder": "directly below", "lounder": "oblique underneath"}
SEEDS = [("2609101", "Seed 2609101 mature (t75), strong shear"), ("2609196", "Seed 2609196 mature (t75), moist, weak shear"),
         ("2609102", "Seed 2609102 developing (t30), dry, disconnected control")]
BG = (24, 28, 36)


def frame(name):
    p = os.path.join(SRC, name + ".png")
    return Image.open(p).convert("RGB") if os.path.exists(p) else None


def grid(path, title, cols, rows, get, w, h, lw=230, note=None):
    th = 44 + (28 if note else 0)
    im = Image.new("RGB", (lw + w * len(cols), th + 30 + h * len(rows)), BG)
    d = ImageDraw.Draw(im)
    d.text((8, 10), title, fill=(235, 235, 235), font=F)
    if note:
        d.text((8, 40), note, fill=(190, 190, 190), font=FS)
    for i, (_, cl) in enumerate(cols):
        d.text((lw + i * w + 8, th + 2), cl, fill=(250, 210, 120), font=F)
    for j, (rk, rl) in enumerate(rows):
        d.text((8, th + 30 + j * h + h // 2 - 10), rl, fill=(200, 220, 255), font=FS)
        for i, (ck, _) in enumerate(cols):
            f = get(ck, rk)
            if f is not None:
                im.paste(f.resize((w, h), Image.LANCZOS), (lw + i * w, th + 30 + j * h))
            else:
                d.text((lw + i * w + 20, th + 30 + j * h + 20), "missing", fill=(255, 120, 120), font=F)
    im.save(os.path.join(OUT, path), optimize=True)
    print("wrote", path, im.size)


for seed, title in SEEDS:
    views = ["ground300", "ground800", "eside", "close", "lunder", "lounder"]
    grid(f"compare_{seed}.png", title + " - identical cameras and lighting",
         [("t14:20", "T260.14  neutral"), ("t15:20", "T260.15  neutral"), ("t14:29", "T260.14  candidate"), ("t15:29", "T260.15  candidate")],
         [(v, VIEW[v]) for v in views], lambda c, r, s=seed: frame(f"s{s}_{c.split(':')[0]}_{r}_m{c.split(':')[1]}"), 480, 270, lw=170,
         note="Underside views: system lifted 240 blocks (same lift and cameras for both); neutral = T260.5 mode 20, candidate = T260.16 mode 29")
    grid(f"underside_{seed}.png", title + " - underside views, large",
         [("t14", "T260.14"), ("t15", "T260.15")],
         [(f"{v}:{m}", f"{VIEW[v]}\n{MODE[m]}") for v in ["lounder", "lunder"] for m in [20, 27, 28, 29]],
         lambda c, r, s=seed: frame(f"s{s}_{c}_{r.split(':')[0]}_m{r.split(':')[1]}"), 960, 540, lw=230)
    for view in ["lounder", "lunder", "eside"]:
        modes = [20, 26, 27, 21, 28, 30, 29] + ([22, 23, 24, 25] if view != "eside" else [])
        grid(f"lighting_{seed}_{view}.png", title + f" - lighting diagnostics, {VIEW[view]}",
             [("t14", "T260.14"), ("t15", "T260.15")], [(m, MODE[m]) for m in modes],
             lambda c, r, s=seed, v=view: frame(f"s{s}_{c}_{v}_m{r}"), 640, 360, lw=260)
grid("foundation_only.png", "DIAGNOSTIC ONLY - foundation geometry without towers (same cameras as the seed's systems)",
     [("2609101_t14f", "2609101 T260.14"), ("2609101_t15f", "2609101 T260.15"), ("2609196_t14f", "2609196 T260.14"), ("2609196_t15f", "2609196 T260.15")],
     [(f"{v}:{m}", f"{VIEW[v]}\n{MODE[m]}") for v in ["eside", "lounder", "lunder"] for m in [20, 29]],
     lambda c, r: frame(f"s{c}_{r.split(':')[0]}_m{r.split(':')[1]}"), 560, 315, lw=230)


def diff(a, b):
    if a is None or b is None:
        return None, None
    dd = ImageChops.difference(a, b)
    px = list(dd.getdata())
    mx = max(max(p) for p in px)
    mean = sum(sum(p) for p in px) / (3.0 * len(px))
    return dd.point(lambda v: min(255, v * 8)), (mx, mean)


rows, stats = [], []
for seed, _ in SEEDS[:2]:
    for cand in ["t15", "t14"]:
        a, b = frame(f"s{seed}_{cand}_eside_m20"), frame(f"s{seed}_{cand}_eside_m20_exact")
        dimg, st = diff(a, b)
        rows.append((f"{seed} {cand} eside: fast (interval skip) | exact | |diff| x8", [a, b, dimg]))
        stats.append(f"fast_vs_exact s{seed} {cand} eside: max |diff| {st[0] if st else 'n/a'} / 255, mean {st[1]:.4f}" if st else f"fast_vs_exact s{seed} {cand}: missing")
        for view in ["lounder", "lunder"]:
            a, b = frame(f"s{seed}_{cand}_{view}_m20"), frame(f"s{seed}_{cand}_{view}_m25")
            dimg, st = diff(a, b)
            rows.append((f"{seed} {cand} {view}: baked light | reference marches | |diff| x8", [a, b, dimg]))
            stats.append(f"baked_vs_reference s{seed} {cand} {view}: max |diff| {st[0]} / 255, mean {st[1]:.4f}" if st else f"baked_vs_reference s{seed} {cand} {view}: missing")
w, h = 480, 270
im = Image.new("RGB", (w * 3, 44 + len(rows) * (h + 30)), BG)
d = ImageDraw.Draw(im)
d.text((8, 10), "Checks: fast vs exact march, baked vs per-sample reference light (identical camera, |difference| x8)", fill=(235, 235, 235), font=F)
for j, (label, imgs) in enumerate(rows):
    y = 44 + j * (h + 30)
    d.text((8, y + 4), label, fill=(200, 220, 255), font=FS)
    for i, f in enumerate(imgs):
        if f is not None:
            im.paste(f.resize((w, h), Image.LANCZOS), (i * w, y + 28))
im.save(os.path.join(OUT, "checks.png"), optimize=True)
print("wrote checks.png", im.size)
open(os.path.join(OUT, "image_checks.txt"), "w").write("\n".join(stats) + "\n")
print("\n".join(stats))
