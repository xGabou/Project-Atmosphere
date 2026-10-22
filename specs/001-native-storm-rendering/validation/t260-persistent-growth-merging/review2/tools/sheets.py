"""T260.8 review 2: C1 (selected baseline) | C3 (crown + surface refinement) contact sheets.
usage: sheets.py SRC OUT"""
import os, sys
src, out = sys.argv[1:3]
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "t2607", "sheets.py"), encoding="utf-8").read().split("VIEWS = [")[0])
SEEDS = ["2608401", "2608402", "2608403"]
T = ["12", "28", "44"]
VN = {"1": "C1 (baseline)", "3": "C3 crown+surface"}
VIEWS = [("three_quarter", "3/4"), ("side", "side"), ("close", "crown close-up")]
for s in SEEDS:
    rows = [(f"{v}|{w}", f"{VN[v]}  {wl}") for w, wl in VIEWS for v in "13"]
    sheet(f"r2_{s}.png", f"T260.8 review 2 - three_free seed {s} - C1 vs C3 - columns: growth stages (one domain, one camera per view, neutral light)",
          [(str(i), f"t={t}") for i, t in enumerate(T)], rows,
          lambda st, r, s=s: f"{SRC}/three_free_{s}_v{r.split('|')[0]}_stage{st}_{r.split('|')[1]}.png", scale=.8)
for w, wl in VIEWS:
    sheet(f"r2_overview_{w}.png", f"T260.8 review 2 - all seeds, mature stage t=44 ({wl}): C1 | C3",
          [(v, VN[v]) for v in "13"], [(s, f"seed {s}") for s in SEEDS],
          lambda v, s, w=w: f"{SRC}/three_free_{s}_v{v}_stage2_{w}.png", scale=.8)
rows = [(f"{v}|{w}", f"{VN[v]}  {wl}") for w, wl in VIEWS[:2] for v in "13"]
sheet("r2_pair_fixture.png", "T260.8 review 2 - diagnostic fixture: two-cell merge (pair 2608201), merging unchanged",
      [(str(i), f"t={t}") for i, t in enumerate(["14", "26", "46"])], rows,
      lambda st, r: f"{SRC}/pair_2608201_v{r.split('|')[0]}_stage{st}_{r.split('|')[1]}.png", scale=.8)
