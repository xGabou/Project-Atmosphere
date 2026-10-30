"""T260.14 in-game sheets: identical cameras per seed (frozen from the T260.14 system), scale 0.085, FOV 110, neutral light.
usage: sheets.py SRC OUT
- t14_<seed>.png: rows = ground300 / ground800 / three_quarter / above / close / elevated side;
  columns = T260.11 baseline | T260.13 A | T260.14 foundation
- t14_diagnostics.png: T260.14 per seed with HUD and component boundaries (groups yellow/red, foundation masses cyan,
  foundation region magenta)"""
import os, sys
from PIL import Image, ImageDraw, ImageFont

SRC, OUT = sys.argv[1:3]
os.makedirs(OUT, exist_ok=True)
try:
    F = ImageFont.truetype("arial.ttf", 22)
except OSError:
    F = ImageFont.load_default()
COLS = [("t11", "T260.11 baseline"), ("t13a", "T260.13 A"), ("t14", "T260.14 shared foundation")]
ROWS = [("ground300", "ground 300"), ("ground800", "ground 800"), ("three_quarter", "three-quarter"), ("above", "above"),
        ("close", "close"), ("eside", "elevated side")]
SEEDS = [("2609196", "Seed 2609196 mature (t75): moist, weak shear - two organised complexes"),
         ("2609101", "Seed 2609101 mature (t75): strong shear line - one organised complex, three independent groups"),
         ("2609102", "Seed 2609102 developing (t30): dry - no organised pair (must stay disconnected)")]
W, H, LW, TH = 560, 315, 170, 44


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


for seed, title in SEEDS:
    grid(f"t14_{seed}.png", title + " - identical cameras, scale 0.085, FOV 110", COLS, ROWS, lambda c, r, s=seed: f"{SRC}/s{s}_{c}_{r}.png")
grid("t14_diagnostics.png", "T260.14 component boundaries: groups (yellow / red), foundation masses (cyan), foundation region (magenta)",
     [(s, f"seed {s}") for s, _ in SEEDS], [("diag", "elevated side")], lambda c, r: f"{SRC}/s{c}_t14_diagnostic_eside.png")
