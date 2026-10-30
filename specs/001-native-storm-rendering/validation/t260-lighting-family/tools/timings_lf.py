"""T260.L-family timing table from CLOUD_PREVIEW_GPU16 lines (complete cloud renderer and the fixture march pass,
~120 GPU samples per timed shot). usage: timings_lf.py LOG [LOG...]"""
import re, sys
from collections import defaultdict
R = re.compile(r"GPU16 name=s(\d+)_(\w+?)_sun(\d+)_(\w+?)_m(\d+) cloudPass samples=\d+ p50Ms=([\d.]+) p95Ms=([\d.]+) \| completeRenderer samples=(\d+) p50Ms=([\d.]+) p95Ms=([\d.]+) maxMs=([\d.]+)")
rows = defaultdict(dict)
for f in sys.argv[1:]:
    for line in open(f, encoding="utf-8", errors="replace"):
        m = R.search(line)
        if m:
            seed, c, e, v, mode = m.group(1), m.group(2), m.group(3), m.group(4), int(m.group(5))
            rows[(c, seed, mode)][v] = dict(pass50=float(m.group(6)), pass95=float(m.group(7)), n=int(m.group(8)),
                                            p50=float(m.group(9)), p95=float(m.group(10)), mx=float(m.group(11)))
views = ["ground800", "cthree", "eside", "lunder", "close"]
print("Complete cloud renderer per view: p50 / p95 / worst frame (ms); march (cloud pass) p95 in brackets.")
print("Hard ceiling: complete p95 <= 10 ms. Preferred: march <= 4.2 ms.")
summary = defaultdict(list)
for c in ("t10", "t15", "t18d2"):
    for seed in ("2609101", "2609196", "2609102"):
        print(f"\n== {c} seed {seed}")
        print(f"{'mode':5s} " + " ".join(f"{v:>24s}" for v in views) + f" {'max p95':>8s} {'march p95':>9s}")
        for mode in (20, 42, 43):
            r = rows.get((c, seed, mode))
            if not r: continue
            cells = [f"{r[v]['p50']:5.2f}/{r[v]['p95']:5.2f}/{r[v]['mx']:5.2f} ({r[v]['pass95']:4.2f})" if v in r else f"{'-':>24s}" for v in views]
            print(f"{mode:<5d} " + " ".join(cells) + f" {max(x['p95'] for x in r.values()):8.2f} {max(x['pass95'] for x in r.values()):9.2f}")
            summary[mode] += [dict(x, key=(c, seed, v)) for v, x in r.items()]
print("\nOver every timed shot:")
for mode in (20, 42, 43):
    s = summary.get(mode)
    if not s: continue
    p50 = sorted(x["p50"] for x in s); m50 = sorted(x["pass50"] for x in s)
    w = max(s, key=lambda x: x["p95"])
    print(f"  mode {mode}: shots={len(s)} median complete p50={p50[len(p50)//2]:.2f}  max complete p95={max(x['p95'] for x in s):.2f} ({'/'.join(w['key'])})"
          f"  worst frame={max(x['mx'] for x in s):.2f}  median march p50={m50[len(m50)//2]:.2f}  max march p95={max(x['pass95'] for x in s):.2f} ms")
for mm in (42, 43):
    d = [rows[(k[0], k[1], mm)][v]["p95"] - rows[(k[0], k[1], 20)][v]["p95"] for k in {(c, s) for (c, s, _) in rows} for v in views
         if v in rows.get((k[0], k[1], mm), {}) and v in rows.get((k[0], k[1], 20), {})]
    if d:
        ds = sorted(d)
        print(f"  paired complete-p95 delta ({mm} - 20): median {ds[len(ds)//2]:+.2f}  min {ds[0]:+.2f}  max {ds[-1]:+.2f} ms over {len(ds)} pairs")
