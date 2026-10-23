"""T260.12 in-game sheets (identical cameras per seed, beauty captures without HUD / outlines). usage: sheets.py SRC OUT
- t12_vs_t11_<view>.png: rows = seeds, columns = T260.11 | T260.12
- with_without_connections.png: seed 2609196 mature, rows = views, columns = WITHOUT (t12off) | WITH (t12)"""
import os, sys
from PIL import Image, ImageDraw, ImageFont

SRC, OUT = sys.argv[1:3]
os.makedirs(OUT, exist_ok=True)
try:
    F = ImageFont.truetype("arial.ttf", 22)
except OSError:
    F = ImageFont.load_default()
SEEDS = [("2609101", "2609101 mature"), ("2609196", "2609196 mature"), ("2609102", "2609102 developing")]
VIEWS = [("ground300", "ground, 300 from the footprint edge"), ("ground800", "ground, 800 from the edge"), ("three_quarter", "three-quarter"),
         ("above", "above"), ("close", "close view of a connection")]
W, H, LW, TH = 800, 450, 230, 44


def sheet(name, title, cols, rows):
    im = Image.new("RGB", (LW + W * len(cols), TH + 30 + H * len(rows)), (24, 28, 36))
    d = ImageDraw.Draw(im)
    d.text((8, 10), title, fill=(235, 235, 235), font=F)
    for i, (_, cl) in enumerate(cols):
        d.text((LW + i * W + 8, TH + 2), cl, fill=(250, 210, 120), font=F)
    for j, (rk, rl) in enumerate(rows):
        d.text((8, TH + 30 + j * H + H // 2 - 10), rl, fill=(200, 220, 255), font=F)
        for i, (ck, _) in enumerate(cols):
            p = f"{SRC}/{ck(rk)}.png"
            if os.path.exists(p):
                im.paste(Image.open(p).convert("RGB").resize((W, H), Image.LANCZOS), (LW + i * W, TH + 30 + j * H))
            else:
                d.text((LW + i * W + 20, TH + 30 + j * H + 20), "no connection to show" if "close" in p else "missing", fill=(255, 120, 120), font=F)
    im.save(os.path.join(OUT, name), optimize=True)
    print("wrote", name, im.size)


for v, vt in VIEWS:
    sheet(f"t12_vs_t11_{v}.png", f"T260.11 vs T260.12 - {vt} - scale 0.085, FOV 110, identical cameras per seed",
          [((lambda s, v=v: f"s{s}_t11_{v}"), "T260.11"), ((lambda s, v=v: f"s{s}_t12_{v}"), "T260.12 (connections)")], SEEDS)
sheet("with_without_connections.png", "Seed 2609196 mature - the same T260.12 system WITHOUT and WITH connections (identical cameras)",
      [((lambda v: f"s2609196_t12off_{v}"), "WITHOUT connections (t12off)"), ((lambda v: f"s2609196_t12_{v}"), "WITH connections (t12)")], VIEWS)
