# Block-grid detector: for period P, the share of 2nd-difference energy at each column/row phase.
# A 1/P share at every phase = no grid; a peak phase share >> 1/P = a P-pixel reconstruction grid.
import sys, glob, os
import numpy as np
from PIL import Image
def score(path):
    a = np.asarray(Image.open(path).convert("L"), dtype=np.float32)[40:]  # drop HUD row
    out = {}
    for P in (2, 3, 4, 8):
        dx = np.abs(a[:, 2:] - 2*a[:, 1:-1] + a[:, :-2]).mean(axis=0)
        dy = np.abs(a[2:, :] - 2*a[1:-1, :] + a[:-2, :]).mean(axis=1)
        sx = np.array([dx[i::P].sum() for i in range(P)]); sy = np.array([dy[i::P].sum() for i in range(P)])
        tot = sx.sum() + sy.sum()
        if tot <= 0: out[P] = 0; continue
        out[P] = (sx.max()/max(sx.sum(),1e-9) + sy.max()/max(sy.sum(),1e-9)) * P / 2  # 1.0 = no grid
    return out
paths = []
for g in sys.argv[1:]:
    paths += glob.glob(g, recursive=True)
for p in paths:
    try:
        s = score(p)
    except Exception as e:
        continue
    print(f"{s[2]:.2f} {s[3]:.2f} {s[4]:.2f} {s[8]:.2f} {p}")
