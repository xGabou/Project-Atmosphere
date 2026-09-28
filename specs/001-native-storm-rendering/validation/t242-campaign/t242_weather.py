# Summarise t242-weather-<type>.csv dumps: R coverage, G base01, B top01 (8-bit).
import csv, glob, os, sys
import numpy as np

root = r'C:\Users\matga\Downloads\Project-Atmosphere\run'
for path in sorted(glob.glob(os.path.join(root, 't242-weather-*.csv'))):
    rows = list(csv.DictReader(open(path)))
    i = np.array([int(r['i']) for r in rows]); j = np.array([int(r['j']) for r in rows])
    R = np.array([int(r['r']) for r in rows]) / 255.0
    G = np.array([int(r['g']) for r in rows]) / 255.0
    B = np.array([int(r['b']) for r in rows]) / 255.0
    cov = R > 0.01
    name = os.path.basename(path)[13:-4]
    print(f'== {name}: texels with r>0.01: {cov.sum()}  r max {R.max():.3f}')
    if cov.sum():
        rr = R[cov]
        print('   r pct 10/50/90/max: %.3f %.3f %.3f %.3f' % tuple(np.percentile(rr, [10, 50, 90, 100])))
        cv = np.clip((rr - 0.012) / (0.42 - 0.012), 0, 1); cv = cv * cv * (3 - 2 * cv)
        print('   smoothstep(0.012,0.42,r) frac >0.95: %.2f  frac <0.5: %.2f' % ((cv > 0.95).mean(), (cv < 0.5).mean()))
        span = (B - G)[cov]
        print('   top-base (slab units) pct 10/50/90: %.3f %.3f %.3f' % tuple(np.percentile(span, [10, 50, 90])))
    grid = {(a, b): v for a, b, v in zip(i, j, R)}
    lo, hi = -24, 24
    chars = ' .:-=+*#%@'
    for b in range(lo, hi, 2):
        line = ''.join(chars[min(9, int(grid.get((a, b), 0) * 10 / max(R.max(), 1e-3) * 0.999))] for a in range(lo, hi))
        print('   |' + line + '|')
