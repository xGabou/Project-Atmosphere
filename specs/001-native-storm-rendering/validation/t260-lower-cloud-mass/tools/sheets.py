"""T260.13 in-game sheets, seed 2609196 mature, identical cameras centred on the lower mass (frozen from variant A).
usage: sheets.py SRC OUT
- t13_comparison.png: rows = ground / elevated three-quarter / above / close; columns = T260.11 | T260.12 | A | B | C
- t13_diagnostics.png: A | B | C with HUD and component boundaries (groups yellow/red, components cyan, region magenta)"""
import os, sys
from PIL import Image, ImageDraw, ImageFont

SRC, OUT = sys.argv[1:3]
os.makedirs(OUT, exist_ok=True)
try:
    F = ImageFont.truetype("arial.ttf", 22)
except OSError:
    F = ImageFont.load_default()
COLS = [("t11", "T260.11 (reference)"), ("t12", "T260.12 (cells, not accepted)"), ("t13a", "A broad irregular mass"),
        ("t13b", "B overlapping masses, gaps"), ("t13c", "C tongues from both groups")]
ROWS = [("cground", "ground level"), ("cthree", "elevated three-quarter"), ("cabove", "above"), ("close", "close view")]
W, H, LW, TH = 640, 360, 200, 44


def grid(name, title, cols, rows, path):
    im = Image.new("RGB", (LW + W * len(cols), TH + 30 + H * len(rows)), (24, 28, 36))
    d = ImageDraw.Draw(im)
    d.text((8, 10), title, fill=(235, 235, 235), font=F)
    for i, (_, cl) in enumerate(cols):
        d.text((LW + i * W + 8, TH + 2), cl, fill=(250, 210, 120), font=F)
    for j, (rk, rl) in enumerate(rows):
        d.text((8, TH + 30 + j * H + H // 2 - 10), rl, fill=(200, 220, 255), font=F)
        for i, (ck, _) in enumerate(cols):
            p = path(ck, rk)
            if os.path.exists(p):
                im.paste(Image.open(p).convert("RGB").resize((W, H), Image.LANCZOS), (LW + i * W, TH + 30 + j * H))
            else:
                d.text((LW + i * W + 20, TH + 30 + j * H + 20), "missing", fill=(255, 120, 120), font=F)
    im.save(os.path.join(OUT, name), optimize=True)
    print("wrote", name, im.size)


grid("t13_comparison.png", "Seed 2609196 mature - G3-G6 lower cloud mass - scale 0.085, FOV 110, neutral light, identical cameras",
     COLS, ROWS, lambda c, r: f"{SRC}/s2609196_{c}_{r}.png")
grid("t13_diagnostics.png", "Component boundaries: groups (yellow / red), lower-mass components (cyan), lower-mass region volume (magenta)",
     COLS[2:], [("diagnostic_cthree", "elevated three-quarter")], lambda c, r: f"{SRC}/s2609196_{c}_{r}.png")
