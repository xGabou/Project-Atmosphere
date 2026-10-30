"""Box-counting dimension of cloud silhouette outlines (stabilisation, shape root cause).

usage: python -I silhouette_dimension.py <image> [<image> ...]
       env SKY=dark  for photos with a dark sky (cloud = brighter than the per-row sky by > 40 levels)

Cloud mask: a pixel differs from its row's sky colour (median of the 24 leftmost and rightmost pixels of the row,
rows below the horizon excluded by requiring the row to be sky at both ends) by more than 12 levels (game frames).
Outline: mask pixels with a 4-neighbour outside the mask. Dimension: slope of log N(s) vs log 1/s for box sizes
s = 2..32 px. A smooth outline gives ~1.0; real cumulus outlines measure ~1.3-1.4 (Lovejoy 1982, cloud perimeters 1.35).
"""
import sys, os
import numpy as np
from PIL import Image


def mask_of(path):
    a = np.asarray(Image.open(path).convert('RGB'), dtype=np.float32)
    h, w, _ = a.shape
    a = a[int(0.045 * h):]  # HUD row
    edges = np.concatenate([a[:, :24], a[:, -24:]], axis=1)
    sky = np.median(edges, axis=1)  # per-row sky colour
    if os.environ.get('SKY') == 'dark':
        lum = a.mean(axis=2)
        m = lum - sky.mean(axis=1)[:, None] > 40
    else:
        m = np.abs(a - sky[:, None, :]).max(axis=2) > 12
    return m


def dimension(m):
    inner = m[1:-1, 1:-1]
    edge = inner & ~(m[:-2, 1:-1] & m[2:, 1:-1] & m[1:-1, :-2] & m[1:-1, 2:])
    ys, xs = np.nonzero(edge)
    if len(xs) < 200:
        return float('nan'), len(xs)
    sizes = [2, 3, 4, 6, 8, 12, 16, 24, 32]
    counts = [len(set(zip((xs // s).tolist(), (ys // s).tolist()))) for s in sizes]
    slope = np.polyfit(np.log(1.0 / np.array(sizes)), np.log(counts), 1)[0]
    return slope, len(xs)


for p in sys.argv[1:]:
    d, n = dimension(mask_of(p))
    print(f"{d:5.3f}  outline_px={n:6d}  {os.path.basename(p)}")
