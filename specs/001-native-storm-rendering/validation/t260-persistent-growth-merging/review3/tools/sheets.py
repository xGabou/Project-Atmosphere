"""T260.8 review 3: C1 (baseline) | C3 | C4 contact sheets.  usage: sheets.py SRC OUT"""
import os, sys
src, out = sys.argv[1:3]
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "t2607", "sheets.py"), encoding="utf-8").read().split("VIEWS = [")[0])
SEEDS = ["2608401", "2608402", "2608403"]
T = ["22", "44"]
VN = {"1": "C1 (baseline)", "3": "C3 (review 2)", "4": "C4 (new)"}
VIEWS = [("three_quarter", "3/4"), ("side", "side"), ("close", "crown close-up"), ("transition", "tower->crown")]
for s in SEEDS:
    sheet(f"r3_{s}.png", f"T260.8 review 3 - three_free seed {s} - columns: C1 | C3 | C4 at t=22 (developing) and t=44 (mature); one camera per view",
          [(f"{v}|{i}", f"{VN[v]}  t={t}") for i, t in enumerate(T) for v in "134"], [(w, wl) for w, wl in VIEWS],
          lambda c, w, s=s: f"{SRC}/three_free_{s}_v{c.split('|')[0]}_stage{c.split('|')[1]}_{w}.png", scale=.55)
for w, wl in VIEWS:
    sheet(f"r3_overview_{w}.png", f"T260.8 review 3 - all seeds, mature t=44 ({wl}): C1 | C3 | C4",
          [(v, VN[v]) for v in "134"], [(s, f"seed {s}") for s in SEEDS],
          lambda v, s, w=w: f"{SRC}/three_free_{s}_v{v}_stage1_{w}.png", scale=.8)
