"""T260.L-family sheets: neutral lighting (mode 20) against the production lighting family (mode 42) on frozen geometry;
identical seed, stage, bake, camera, FOV, scale, quality and sun in every row pair.
usage: sheets_lf.py SRC OUT
- compare_<cand>_<seed>.png   rows = the five views, columns = mode 20 | 42 | 43 (sun 35)
- d2_sun12_<seed>.png         the mature D2 at sun 12
- overview_sun35.png          every candidate x seed at the elevated side and below"""
import os, sys
from PIL import Image, ImageDraw, ImageFont

SRC, OUT = sys.argv[1:3]
os.makedirs(OUT, exist_ok=True)
try:
    F = ImageFont.truetype("arial.ttf", 22); FS = ImageFont.truetype("arial.ttf", 17)
except OSError:
    F = FS = ImageFont.load_default()
BG = (24, 28, 36)
MODES = [(20, "neutral lighting (mode 20)"), (42, "production family, baked (42)"), (43, "42 + 2-tap sun-ward skin cone (43)")]
VIEW = {"ground800": "ground (800 out)", "cthree": "three-quarter (t10: generic)", "eside": "side (elevated)", "lunder": "below", "close": "close-up"}
CANDS = {"t10": "T260.10 tower cluster", "t15": "T260.15 foundation", "t18d2": "T260.18 D2 foundation"}
STAGE = {"2609101": "mature", "2609196": "mature", "2609102": "developing"}
NOTE = "Geometry frozen. Identical bake and cameras per pair, lift +320, FOV 110, scale 0.085, quality normal, history off"


def frame(name):
    if "_t10_" in name and "_cthree_" in name: name = name.replace("_cthree_", "_three_quarter_")  # tlf10 supplement
    p = os.path.join(SRC, name + ".png")
    return Image.open(p).convert("RGB") if os.path.exists(p) else None


def grid(path, title, cols, rows, get, w, h, lw=190, note=None):
    th = 44 + (26 if note else 0)
    im = Image.new("RGB", (lw + w * len(cols), th + 30 + h * len(rows)), BG)
    d = ImageDraw.Draw(im)
    d.text((8, 10), title, fill=(235, 235, 235), font=F)
    if note: d.text((8, 40), note, fill=(190, 190, 190), font=FS)
    for i, (_, cl) in enumerate(cols): d.text((lw + i * w + 6, th + 4), cl, fill=(250, 210, 120), font=FS)
    for j, (rk, rl) in enumerate(rows):
        d.text((8, th + 30 + j * h + h // 2 - 12), rl, fill=(200, 220, 255), font=FS)
        for i, (ck, _) in enumerate(cols):
            f = get(ck, rk)
            if f is not None: im.paste(f.resize((w, h), Image.LANCZOS), (lw + i * w, th + 30 + j * h))
            else: d.text((lw + i * w + 12, th + 30 + j * h + 12), "-", fill=(160, 160, 160), font=F)
    im.save(os.path.join(OUT, path), optimize=True)
    print("wrote", path, im.size)


for c, cl in CANDS.items():
    for s, st in STAGE.items():
        grid(f"compare_{c}_{s}.png", f"{cl}, seed {s} {st}, sun 35 deg", MODES, list(VIEW.items()),
             lambda m, v, c=c, s=s: frame(f"s{s}_{c}_sun35_{v}_m{m}"), 560, 315, note=NOTE)
for s in ["2609101", "2609196"]:
    grid(f"d2_sun12_{s}.png", f"T260.18 D2 foundation, seed {s} mature, sun 12 deg", MODES, list(VIEW.items()),
         lambda m, v, s=s: frame(f"s{s}_t18d2_sun12_{v}_m{m}"), 560, 315, note=NOTE)
cols = [(f"{c}|{m}", f"{c} m{m}") for c in CANDS for m in (20, 42, 43)]
rows = [(f"{s}|{v}", f"{s} {VIEW[v]}") for s in STAGE for v in ("eside", "lunder")]
grid("overview_sun35.png", "Overview, sun 35: neutral (m20) vs production family (m42) and its skin-cone arm (m43)", cols, rows,
     lambda ck, rk: frame(f"s{rk.split('|')[0]}_{ck.split('|')[0]}_sun35_{rk.split('|')[1]}_m{ck.split('|')[1]}"), 320, 180, note=NOTE)
