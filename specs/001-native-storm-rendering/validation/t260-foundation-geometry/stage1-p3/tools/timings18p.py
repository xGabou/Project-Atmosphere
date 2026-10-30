"""T260.18 timing table from CLOUD_PREVIEW_GPU16 lines (complete cloud renderer and cloud pass, ~120 GPU samples per shot).
usage: timings18.py LOG [LOG...]"""
import re, sys
from collections import defaultdict
R = re.compile(r"GPU16 name=s(\d+)_(t\w+?)_(\w+?)_m20 cloudPass samples=\d+ p50Ms=([\d.]+) p95Ms=([\d.]+) \| completeRenderer samples=(\d+) p50Ms=([\d.]+) p95Ms=([\d.]+) maxMs=([\d.]+)")
rows = defaultdict(dict)
for f in sys.argv[1:]:
    for line in open(f, encoding="utf-8", errors="replace"):
        m = R.search(line)
        if m:
            seed, c, v = m.group(1), m.group(2), m.group(3)
            rows[(seed, c)][v] = tuple(float(m.group(i)) for i in (4, 5, 7, 8, 9))
views = sorted({v for r in rows.values() for v in r})
print("complete renderer p95 ms per view (cloud-pass p95 in brackets); worst = max frame over all timed views; hard ceiling p95 <= 10 ms")
for seed in sorted({k[0] for k in rows}):
    print(f"\n== seed {seed}")
    print(f"{'cand':6s} " + " ".join(f"{v:>15s}" for v in views) + f" {'median p50':>11s} {'max p95':>8s} {'worst':>6s}")
    for c in ["t15", "t18d2", "t18g"]:
        r = rows.get((seed, c))
        if not r:
            continue
        cells = [f"{r[v][3]:7.2f} ({r[v][1]:4.2f})" if v in r else f"{'-':>15s}" for v in views]
        p50 = sorted(x[2] for x in r.values()); med = p50[len(p50) // 2]
        print(f"{c:6s} " + " ".join(cells) + f" {med:11.2f} {max(x[3] for x in r.values()):8.2f} {max(x[4] for x in r.values()):6.2f}")
