"""T260.10 world-scale sheets: T260.9 (before) | T260.10 (after), no per-image normalisation; every tile carries the
measured dimensions of the system (dims.tsv). usage: sheets.py SRC OUT"""
import os, sys
from PIL import Image, ImageDraw, ImageFont

SRC, OUT = sys.argv[1:3]
os.makedirs(OUT, exist_ok=True)
try:
    FONT = ImageFont.truetype("arial.ttf", 18); SMALL = ImageFont.truetype("arial.ttf", 14)
except OSError:
    FONT = SMALL = ImageFont.load_default()

SEEDS = ["2609101", "2609102", "2609103", "2609104"]
MN = {"t9": "T260.9 (before)", "t10": "T260.10 (after)"}
DIMS = {}
for line in open(os.path.join(SRC, "dims.tsv"), encoding="utf-8").read().splitlines()[1:]:
    f = line.split("\t")
    DIMS[(f[0], f[1], f[2])] = f
T = {}

def label(model, seed, stage):
    f = DIMS.get((model, seed, stage))
    if not f: return "no dims"
    T[stage] = f[3]
    return (f"t={f[3]}  cells={f[4]}  W {float(f[5])/1000:.1f} x D {float(f[6])/1000:.1f} km  "
            f"base {float(f[7])/1000:.2f} km  top {float(f[8])/1000:.2f} km AGL  depth {float(f[9])/1000:.2f} km")

def sheet(name, title, cols, rows, scale):
    """cols/rows: lists of (key, label); cell keys (model, seed, stage, view)."""
    first = None
    for c, _ in cols:
        for r, _ in rows:
            p = path(c, r)
            if os.path.exists(p): first = Image.open(p); break
        if first: break
    w, h = int(first.size[0] * scale), int(first.size[1] * scale)
    lw, th, ch = 200, 34, 26
    im = Image.new("RGB", (lw + w * len(cols), th + ch + h * len(rows)), (24, 28, 36))
    d = ImageDraw.Draw(im)
    d.text((8, 7), title, fill=(235, 235, 235), font=FONT)
    for i, (_, lab) in enumerate(cols):
        d.text((lw + i * w + 8, th + 4), lab, fill=(250, 210, 120), font=SMALL)
    for j, (rk, rl) in enumerate(rows):
        d.text((8, th + ch + j * h + h // 2 - 8), rl, fill=(200, 220, 255), font=SMALL)
        for i, (ck, _) in enumerate(cols):
            p = path(ck, rk)
            x0, y0 = lw + i * w, th + ch + j * h
            if os.path.exists(p):
                im.paste(Image.open(p).resize((w, h), Image.LANCZOS), (x0, y0))
                m, s, st, _ = key(ck, rk)
                d.rectangle([x0, y0 + h - 22, x0 + w, y0 + h], fill=(16, 18, 24))
                d.text((x0 + 6, y0 + h - 19), label(m, s, st), fill=(225, 225, 225), font=SMALL)
            else:
                d.text((x0 + 20, y0 + 20), "missing", fill=(255, 80, 80), font=SMALL)
        d.line([(lw, th + ch + j * h), (im.size[0], th + ch + j * h)], fill=(60, 66, 80))
    for i in range(1, len(cols)):
        d.line([(lw + i * w, th + ch), (lw + i * w, im.size[1])], fill=(60, 66, 80))
    im.save(os.path.join(OUT, name), optimize=True)
    print("wrote", name, im.size)

def key(c, r):
    a, b = c.split("|"), r.split("|")
    k = dict(x.split("=") for x in a + b)
    return k["m"], k["s"], k["st"], k["v"]

def path(c, r):
    m, s, st, v = key(c, r)
    return f"{SRC}/{m}_{s}_stage{st}_{v}.png"

ROWS = [(f"m={m}|s={s}", f"{MN[m]}\nseed {s}") for s in SEEDS for m in ("t9", "t10")]
for v, vl, sc in [("side", "side", .8), ("three_quarter", "three-quarter", .8), ("overview", "overview", .55)]:
    note = {"side": "the T260.9 gallery camera (5249 blocks, fov 42) - deep towers leave the frame",
            "three_quarter": "the T260.9 gallery camera (5911 blocks, fov 42) - deep towers leave the frame",
            "overview": "ONE wider camera fitted once to the tallest system, shared by every image"}[v]
    sheet(f"t10_{v}.png", f"T260.10 vs T260.9 - {vl} - {note} - columns: developing | mature",
          [(f"st=0|v={v}", "developing"), (f"st=1|v={v}", "mature")], ROWS, sc)
for st, sl in [("1", "mature"), ("0", "developing")]:
    sheet(f"t10_gameplay_{sl}.png", f"T260.10 vs T260.9 - player on the ground (cloud base = seed's LCL) 6 km from the system centre, 70 deg lens pitched 28 deg up - {sl}",
          [(f"m=t9|st={st}|v=gameplay", MN["t9"]), (f"m=t10|st={st}|v=gameplay", MN["t10"])], [(f"s={s}", f"seed {s}") for s in SEEDS], .55)
