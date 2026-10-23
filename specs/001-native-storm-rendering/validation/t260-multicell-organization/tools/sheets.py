"""T260.11 in-game comparison sheets: per view, rows = seeds, columns = T260.10 | T260.11 (same world anchor, scale,
FOV and camera rule; screenshots straight from the demo, no rescaling between candidates). usage: sheets.py SRC OUT"""
import os, sys
from PIL import Image, ImageDraw, ImageFont

SRC, OUT = sys.argv[1:3]
os.makedirs(OUT, exist_ok=True)
try:
    F = ImageFont.truetype("arial.ttf", 22)
except OSError:
    F = ImageFont.load_default()
SEEDS = ["2609101", "2609196", "2609102", "2609104"]
VIEWS = [("ground300", "ground, 300 blocks from the footprint edge"), ("ground800", "ground, 800 blocks from the edge"),
         ("ground1500", "ground, 1500 blocks from the edge"), ("three_quarter", "three-quarter overview"), ("above", "above")]
W, H, LW, TH = 800, 450, 150, 44
for v, title in VIEWS:
    im = Image.new("RGB", (LW + 2 * W, TH + 30 + H * len(SEEDS)), (24, 28, 36))
    d = ImageDraw.Draw(im)
    d.text((8, 10), f"T260.10 vs T260.11 - {title} - scale 0.085 blocks/m, player FOV 110, same world anchor", fill=(235, 235, 235), font=F)
    for i, m in enumerate(["T260.10 (mature, t=40)", "T260.11 (named stage, see HUD)"]):
        d.text((LW + i * W + 8, TH + 2), m, fill=(250, 210, 120), font=F)
    for j, s in enumerate(SEEDS):
        d.text((8, TH + 30 + j * H + H // 2 - 10), s, fill=(200, 220, 255), font=F)
        for i, c in enumerate(["t10", "t11"]):
            p = f"{SRC}/s{s}_{c}_{v}.png"
            if os.path.exists(p):
                im.paste(Image.open(p).convert("RGB").resize((W, H), Image.LANCZOS), (LW + i * W, TH + 30 + j * H))
    im.save(os.path.join(OUT, f"t11_{v}.png"), optimize=True)
    print("wrote", v, im.size)
