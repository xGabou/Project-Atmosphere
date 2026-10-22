"""T260.7 V2 refinement (R1-R3) review sheets."""
import os, sys
sys.argv = [sys.argv[0], sys.argv[1], sys.argv[2]]
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "sheets.py"), encoding="utf-8").read().split("VIEWS = [")[0])

VIEWS = [("side", "side"), ("three_quarter", "three-quarter"), ("close", "crown close-up"), ("gameplay", "gameplay (3 km, player lens)")]
SEEDS = ["20261015", "20265193", "20268011"]
ARMS = [("V2", "V2 (frozen reference)"), ("R1", "R1 compound billows + local relief"), ("R2", "R2 + regional anatomy"), ("R3", "R3 + tower/crown irregularity")]

for s in SEEDS:
    sheet(f"refine_{s}.png", f"T260.7 refinement - seed {s} - V2 | R1 | R2 | R3 - neutral volumetric light, exposure 3.2 (CPU preview)",
          ARMS, VIEWS, lambda a, v, s=s: f"{SRC}/r_surface/{a}_{s}_{v}.png")
sheet("refine_closeups.png", "T260.7 refinement - crown close-ups, all seeds", ARMS, [(s, f"seed {s}") for s in SEEDS],
      lambda a, s: f"{SRC}/r_surface/{a}_{s}_close.png")
sheet("refine_three_quarter.png", "T260.7 refinement - three-quarter, all seeds (seed diversity)", ARMS, [(s, f"seed {s}") for s in SEEDS],
      lambda a, s: f"{SRC}/r_surface/{a}_{s}_three_quarter.png")
GA = [("V2P", "V2 + persistent slots"), ("R2P", "R2 + persistent slots"), ("R3P", "R3 + persistent slots")]
for v, vl in [("close", "close-up"), ("side", "side")]:
    sheet(f"refine_growth_{v}.png", f"T260.7 refinement - same-seed growth (20261015), stages 0-4 = 0/4/8/12/16 developStep - {vl}",
          [(str(i), f"stage {i}") for i in range(5)], GA, lambda st, a, v=v: f"{SRC}/r_grow/{a}_20261015_stage{st}_{v}.png", scale=.7)
sheet("refine_gameplay.png", "T260.7 refinement - gameplay view (ground, 3 km, player lens), seed 20261015 - central crop of a 2400x1350 frame, 1:1 pixels",
      ARMS, [("g", "gameplay crop")], lambda a, g: f"{SRC}/r_game/{a}_20261015_gamecrop.png", scale=.85)
