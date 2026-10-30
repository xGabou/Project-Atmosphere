"""Paired-arm metrics for a ProductionCloudCapture set (stabilisation log, S0+).

usage: python -I ab_metrics.py <screenshots/prodcapture/<set> dir> <control arm> [arm ...]
       env AB_EXCLUDE_TOAST=1 drops the bottom-left HUD toast box (x < 340, y 760-840 of 1600x900) from the mask
       (captures taken before the driver hid the GUI).

Frames are named <type>__<view>__t<time>__<arm>.png. For every (type, view, time) the cloud mask is the set of pixels
where ANY arm differs from the control by > 4 levels (sky and terrain are identical across arms). Reports luma
mean / p10 / p50 / p90 over the mask, fine structure (mean |Laplacian| of luma inside the mask) and, for each arm,
the share of masked pixels that differ from the control by > 8 levels.
"""
import sys, os, glob, collections
import numpy as np
from PIL import Image

root, control, arms = sys.argv[1], sys.argv[2], sys.argv[3:]
groups = collections.defaultdict(dict)
for p in glob.glob(os.path.join(root, '*.png')):
    stem = os.path.basename(p)[:-4]
    parts = stem.split('__')
    if len(parts) != 4:
        continue
    groups[tuple(parts[:3])][parts[3]] = p


def luma(p):
    a = np.asarray(Image.open(p).convert('RGB'), dtype=np.float32)[40:]
    return 0.2126 * a[..., 0] + 0.7152 * a[..., 1] + 0.0722 * a[..., 2], a


for key in sorted(groups):
    g = groups[key]
    if control not in g:
        continue
    yc, rc = luma(g[control])
    present = [a for a in arms if a in g]
    mask = np.zeros(yc.shape, bool)
    for a in present:
        mask |= np.abs(luma(g[a])[1] - rc).max(axis=2) > 4
    if os.environ.get('AB_EXCLUDE_TOAST') == '1':
        mask[760 - 40:840 - 40, :340] = False
    if mask.sum() < 500:
        print(f"{'/'.join(key)}: cloud mask too small ({mask.sum()} px)")
        continue
    print(f"== {'/'.join(key)}  mask {mask.sum()} px")
    for a in [control] + present:
        y, _ = luma(g[a])
        v = y[mask]
        lap = np.abs(4 * y[1:-1, 1:-1] - y[:-2, 1:-1] - y[2:, 1:-1] - y[1:-1, :-2] - y[1:-1, 2:])
        diff = '' if a == control else f"  >8 vs {control}: {100 * (np.abs(y - yc)[mask] > 8).mean():5.1f}%"
        print(f"  {a:16s} mean {v.mean():6.1f} p10 {np.percentile(v, 10):6.1f} p50 {np.percentile(v, 50):6.1f} "
              f"p90 {np.percentile(v, 90):6.1f} lap {lap[mask[1:-1, 1:-1]].mean():5.2f}{diff}")
