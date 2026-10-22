"""T260.7 fast review gallery: labeled contact sheets from the CPU preview renders."""
import os, sys
from PIL import Image, ImageDraw, ImageFont

SRC = sys.argv[1]   # run/t2607
OUT = sys.argv[2]   # validation gallery dir
os.makedirs(OUT, exist_ok=True)
try:
    FONT = ImageFont.truetype("arial.ttf", 18)
    SMALL = ImageFont.truetype("arial.ttf", 15)
except OSError:
    FONT = SMALL = ImageFont.load_default()

def sheet(name, title, cols, rows, path_of, scale=1.0):
    """cols/rows: lists of (key, label); path_of(colkey, rowkey) -> file or None."""
    first = None
    for c, _ in cols:
        for r, _ in rows:
            p = path_of(c, r)
            if p and os.path.exists(p):
                first = Image.open(p); break
        if first: break
    w, h = int(first.size[0] * scale), int(first.size[1] * scale)
    lw, th, ch = 215, 34, 28
    im = Image.new("RGB", (lw + w * len(cols), th + ch + h * len(rows)), (24, 28, 36))
    d = ImageDraw.Draw(im)
    d.text((8, 7), title, fill=(235, 235, 235), font=FONT)
    for i, (_, lab) in enumerate(cols):
        d.text((lw + i * w + 8, th + 4), lab, fill=(250, 210, 120), font=SMALL)
    for j, (rk, rl) in enumerate(rows):
        d.text((8, th + ch + j * h + h // 2 - 8), rl, fill=(200, 220, 255), font=SMALL)
        for i, (ck, _) in enumerate(cols):
            p = path_of(ck, rk)
            if p and os.path.exists(p):
                t = Image.open(p).resize((w, h), Image.LANCZOS)
                im.paste(t, (lw + i * w, th + ch + j * h))
            else:
                d.text((lw + i * w + 20, th + ch + j * h + 20), "missing", fill=(255, 80, 80), font=SMALL)
        d.line([(lw, th + ch + j * h), (im.size[0], th + ch + j * h)], fill=(60, 66, 80))
    for i in range(1, len(cols)):
        d.line([(lw + i * w, th + ch), (lw + i * w, im.size[1])], fill=(60, 66, 80))
    im.save(os.path.join(OUT, name), optimize=True)
    print("wrote", name, im.size)

VIEWS = [("side", "side"), ("three_quarter", "three-quarter"), ("close", "close-up (crown)")]
SEEDS = ["20261015", "20265193", "20268011"]
ARMS = [("B", "B (frozen T260.6)"), ("V1", "V1 irregular billows"), ("V2", "V2 + growth activity"), ("V3", "V3 + undercuts/valleys")]

# 1. Surface candidates, one sheet per seed.
for s in SEEDS:
    sheet(f"surface_{s}.png", f"T260.7 surface candidates - seed {s} - neutral volumetric light, exposure 3.2 (CPU preview)",
          ARMS, VIEWS, lambda a, v, s=s: f"{SRC}/g_surface/{a}_{s}_{v}.png")
# 1b. Close-ups of all seeds side by side (the billow-scale comparison).
sheet("surface_closeups.png", "T260.7 crown close-ups - all seeds - B vs V1 vs V2 vs V3", ARMS,
      [(s, f"seed {s}") for s in SEEDS], lambda a, s: f"{SRC}/g_surface/{a}_{s}_close.png")

# 2. System diversity (common framing per seed: absolute scale comparable).
CH = [("V2", "V2 (D layout)"), ("V2S-COMPACT", "COMPACT"), ("V2S-DOMINANT", "DOMINANT"), ("V2S-MULTICELL", "MULTICELL"),
      ("V2S-WIDE", "WIDE"), ("V2S-ASYMMETRIC", "ASYMMETRIC")]
sheet("system_characters_20261015.png", "T260.7 S: system characters on one seed (20261015), V2 surface, one common camera per view",
      CH, VIEWS[:2], lambda a, v: f"{SRC}/g_system/{a}_20261015_{v}.png", scale=.8)
sheet("system_own_character.png", "T260.7 S: each seed's own drawn character vs its D layout (V2 surface, common camera per seed)",
      [("V2", "V2 (D layout)"), ("V2S", "V2 + own character")], [(f"{s}|{v}", f"{s}\n{vl}") for s in SEEDS[1:] for v, vl in VIEWS[:2]],
      lambda a, k: f"{SRC}/g_system/{a}_{k.split('|')[0]}_{k.split('|')[1]}.png")

# 3. Growth continuity: same seed, 5 stages (4 developStep each).
GA = [("B", "B (one sequential stream)"), ("BP", "B + persistent slots"), ("V3", "V3"), ("V3P", "V3 + persistent slots")]
for v, vl in [("close", "close-up"), ("side", "side")]:
    sheet(f"growth_{v}.png", f"T260.7 same-seed growth (20261015), stages 0-4 = 0/4/8/12/16 developStep - {vl}",
          [(str(i), f"stage {i}") for i in range(5)], GA, lambda st, a, v=v: f"{SRC}/g_grow/{a}_20261015_stage{st}_{v}.png", scale=.7)
