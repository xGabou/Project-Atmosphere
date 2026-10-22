"""T260.8 review iteration: C0 (reviewed T260.8) | C1 | C2 contact sheets from the compare renders.
usage: sheets.py SRC OUT seed[,seed...] prefix times"""
import os, sys
src, out, seeds, prefix, times = sys.argv[1:6]
sys.argv = [sys.argv[0], src, out]
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "t2607", "sheets.py"), encoding="utf-8").read().split("VIEWS = [")[0])
T = times.split(",")
VN = {"0": "T260.8 (reviewed)", "1": "C1 multi-scale growth", "2": "C2 = C1 + sub-thermals"}
VIEWS = [("three_quarter", "3/4"), ("side", "side"), ("close", "crown close-up")]
for s in seeds.split(","):
    rows = [(f"{v}|{w}", f"{VN[v]}  {wl}") for w, wl in VIEWS for v in "012"]
    sheet(f"{prefix}_{s}.png", f"T260.8 review iteration - three_free seed {s} - columns: growth stages; rows: candidate x camera (one domain, one camera per view)",
          [(str(i), f"t={t}") for i, t in enumerate(T)], rows,
          lambda st, r, s=s: f"{SRC}/three_free_{s}_v{r.split('|')[0]}_stage{st}_{r.split('|')[1]}.png", scale=.8)

# Overview: last stage, three-quarter and side, rows = seeds, cols = candidates.
if len(sys.argv) > 0 and os.environ.get("T2608R_OVERVIEW"):
    SEEDS = seeds.split(",")
    for w, wl in [("three_quarter", "three-quarter"), ("side", "side")]:
        sheet(f"{prefix}_overview_{w}.png", f"T260.8 review iteration - all seeds at t={T[-1]} ({wl}): reviewed T260.8 | C1 | C2",
              [(v, VN[v]) for v in "012"], [(s, f"seed {s}") for s in SEEDS],
              lambda v, s, w=w: f"{SRC}/three_free_{s}_v{v}_stage{len(T) - 1}_{w}.png", scale=.8)
    PT = os.environ.get("T2608R_PAIR_TIMES", "14,26,46").split(",")
    rows = [(f"{v}|{w}", f"{VN[v]}  {wl}") for w, wl in VIEWS[:2] for v in "012"]
    sheet(f"{prefix}_pair_fixture.png", "T260.8 review iteration - diagnostic fixture: two-cell merge (pair, seed 2608201), merging unchanged",
          [(str(i), f"t={t}") for i, t in enumerate(PT)], rows,
          lambda st, r: f"{SRC}/pair_2608201_v{r.split('|')[0]}_stage{st}_{r.split('|')[1]}.png", scale=.8)
