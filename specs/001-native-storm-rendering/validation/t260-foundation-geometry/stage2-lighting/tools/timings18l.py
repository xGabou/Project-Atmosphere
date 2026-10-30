"""T260.18 stage 2 timing table from CLOUD_PREVIEW_GPU16 lines (complete cloud renderer and cloud pass, ~120 GPU
samples per timed shot). usage: timings18l.py LOG [LOG...]"""
import re, sys
from collections import defaultdict
R = re.compile(r"GPU16 name=s(\d+)_t18d2_sun(\d+)_(\w+?)_m(\d+) cloudPass samples=\d+ p50Ms=([\d.]+) p95Ms=([\d.]+) \| completeRenderer samples=(\d+) p50Ms=([\d.]+) p95Ms=([\d.]+) maxMs=([\d.]+)")
rows = defaultdict(dict)
for f in sys.argv[1:]:
    for line in open(f, encoding="utf-8", errors="replace"):
        m = R.search(line)
        if m:
            seed, e, v, mode = m.group(1), m.group(2), m.group(3), int(m.group(4))
            rows[(seed, e, mode)][v] = dict(pass50=float(m.group(5)), pass95=float(m.group(6)), n=int(m.group(7)),
                                           p50=float(m.group(8)), p95=float(m.group(9)), mx=float(m.group(10)))
NAMES = {20: "20 current", 35: "35 L-dir", 40: "40 L-graz", 41: "41 L-graz-c"}
views = ["lunder", "lounder", "fprofile", "ground800", "eside"]
print("Complete cloud renderer per timed view: p50 / p95 / worst frame (ms); cloud-pass p95 in brackets. Hard ceiling p95 <= 10 ms.")
summary = defaultdict(list)
for seed in sorted({k[0] for k in rows}):
    for e in sorted({k[1] for k in rows if k[0] == seed}, key=int, reverse=True):
        print(f"\n== seed {seed}, sun {e} deg")
        print(f"{'mode':11s} " + " ".join(f"{v:>24s}" for v in views) + f" {'max p95':>8s} {'worst':>6s}")
        for mode in (20, 35, 40, 41):
            r = rows.get((seed, e, mode))
            if not r:
                continue
            cells = [f"{r[v]['p50']:5.2f}/{r[v]['p95']:5.2f}/{r[v]['mx']:5.2f} ({r[v]['pass95']:4.2f})" if v in r else f"{'-':>24s}" for v in views]
            print(f"{NAMES[mode]:11s} " + " ".join(cells) + f" {max(x['p95'] for x in r.values()):8.2f} {max(x['mx'] for x in r.values()):6.2f}")
            summary[mode] += list(r.values())
print("\nOver every timed shot (both mature seeds, both suns, five views):")
for mode in (20, 35, 40, 41):
    s = summary.get(mode)
    if not s:
        continue
    p50 = sorted(x["p50"] for x in s)
    print(f"  {NAMES[mode]:11s} shots={len(s):2d} median p50={p50[len(p50) // 2]:.2f}  max p95={max(x['p95'] for x in s):.2f}  worst frame={max(x['mx'] for x in s):.2f} ms")
