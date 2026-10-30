"""T260.18 stage 2 sheets: the frozen D2 geometry under the current lighting (mode 20) and the two lighting candidates,
L-dir (mode 35) and L-graz (mode 40); identical seed, stage, cameras, FOV, scale, quality, exposure and sun per row.
usage: sheets18l.py SRC OUT
- compare_<seed>_sun<e>.png  rows = directly below, oblique underside, foundation edge-on, ground, elevated side
- underside_<seed>.png       the underside views large, both suns, plus the raking reference (mode 27)
- control_2609102.png        the disconnected control under the three lightings, both suns"""
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
MODES = [(20, "current lighting (mode 20)"), (35, "L-dir: baked local directional shading (35)"), (40, "L-graz: + bounded grazing shadows (40)"), (41, "L-graz-c: per-surface grazing (41)")]
VIEW = {"lunder": "directly below", "lounder": "oblique underside", "fprofile": "foundation edge-on", "ground800": "ground level",
        "eside": "elevated side"}
NOTE = "D2 geometry frozen. Identical cameras (frozen per seed at the D2 bake), lift +320, FOV 110, scale 0.085, quality normal, exposure 3.2"


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


for seed in ["2609196", "2609101"]:
    for e in [35, 12]:
        grid(f"compare_{seed}_sun{e}.png", f"{seed} mature t75, D2, sun {e} deg - current lighting / L-dir / L-graz", MODES,
             [(v, VIEW[v]) for v in VIEW], lambda m, v, s=seed, e=e: frame(f"s{s}_t18d2_sun{e}_{v}_m{m}"), 560, 315, note=NOTE)
    rows = [(f"{e}|{v}", f"{VIEW[v]}\nsun {e} deg") for e in [35, 12] for v in ["lunder", "lounder", "fprofile"]]
    cols = MODES + [(27, "reference: raking diagnostic (27)")]
    grid(f"underside_{seed}.png", f"{seed} mature t75, D2 - underside views, large", cols, rows,
         lambda m, r, s=seed: frame(f"s{s}_t18d2_sun{r.split('|')[0]}_{r.split('|')[1]}_m{m}"), 640, 360, note=NOTE + "; mode 27 is a non-physical diagnostic (sun 35 only)")
rows = [(f"{e}|{v}", f"{VIEW[v]}\nsun {e} deg") for e in [35, 12] for v in ["ground800", "eside", "lunder"]]
grid("control_2609102.png", "2609102 developing t30 (no organised pair) - disconnected control under the three lightings", MODES, rows,
     lambda m, r: frame(f"s2609102_t18d2_sun{r.split('|')[0]}_{r.split('|')[1]}_m{m}"), 560, 315, note=NOTE)
