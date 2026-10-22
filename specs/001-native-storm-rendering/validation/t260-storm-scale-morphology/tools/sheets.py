"""T260.9 world-scale sheets: C4 | T260.9 per seed, no per-image normalisation (one camera distance per view).
usage: sheets.py SRC OUT"""
import os, sys
src, out = sys.argv[1:3]
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "t2607", "sheets.py"), encoding="utf-8").read().split("VIEWS = [")[0])
SEEDS = ["2609101", "2609102", "2609103", "2609104"]
MN = {"c4": "C4 (T260.8 three_free)", "t9": "T260.9 system"}
rows = [(f"{m}|{s}", f"{MN[m]}  seed {s}") for s in SEEDS for m in ("c4", "t9")]
for view, vl in [("side", "side"), ("three_quarter", "three-quarter")]:
    sheet(f"t9_{view}.png", f"T260.9 vs C4 - {vl} - ONE camera distance and fov for every image (true relative size) - columns: t=22 developing, t=44 mature",
          [("0", "t=22"), ("1", "t=44")], rows, lambda st, r, view=view: f"{SRC}/{r.split('|')[0]}_{r.split('|')[1]}_stage{st}_{view}.png", scale=.8)
sheet("t9_gameplay.png", "T260.9 vs C4 - gameplay view: player on the ground 6 km from the system centre, 70 deg lens, same for every image - t=44",
      [("c4", MN["c4"]), ("t9", MN["t9"])], [(s, f"seed {s}") for s in SEEDS],
      lambda m, s: f"{SRC}/{m}_{s}_stage1_gameplay.png", scale=.6)
sheet("t9_gameplay_t22.png", "T260.9 vs C4 - gameplay view (same camera rule) - t=22",
      [("c4", MN["c4"]), ("t9", MN["t9"])], [(s, f"seed {s}") for s in SEEDS],
      lambda m, s: f"{SRC}/{m}_{s}_stage0_gameplay.png", scale=.6)
