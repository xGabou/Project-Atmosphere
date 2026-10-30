"""T260.16 visible-relief proxy on the in-game frames: luminance statistics of the cloud pixels in the underside views.
usage: contrast.py SRC > contrast.txt
For each seed / candidate / underside view / mode: mean luminance, standard deviation and the mean absolute luminance
gradient between neighbouring pixels (8-bit units, frame downsampled to 800x450), over the central 70 % of the frame
(lunder: straight up, the frame is cloud; lounder: the upper 55 % of the central crop, above the horizon line).
This measures what the camera shows, not the geometry: identical numbers for T260.14 and T260.15 mean identical images."""
import os, sys
from PIL import Image

SRC = sys.argv[1]
MODES = [20, 26, 27, 21, 28, 30, 29, 22, 23, 24, 25]


def stats(path, view):
    im = Image.open(path).convert("L").resize((800, 450), Image.BILINEAR)
    w, h = im.size
    x0, x1 = int(.15 * w), int(.85 * w)
    y0, y1 = (int(.15 * h), int(.85 * h)) if view == "lunder" else (int(.05 * h), int(.55 * h))
    px = im.load()
    vals, grad, n = [], 0.0, 0
    for y in range(y0, y1):
        for x in range(x0, x1):
            v = px[x, y]
            vals.append(v)
            if x + 1 < x1 and y + 1 < y1:
                grad += abs(v - px[x + 1, y]) + abs(v - px[x, y + 1])
                n += 2
    m = sum(vals) / len(vals)
    sd = (sum((v - m) ** 2 for v in vals) / len(vals)) ** .5
    return m, sd, grad / n


print("seed      cand view     mode                         mean    sd   grad")
for seed in ["2609101", "2609196", "2609102"]:
    for view in ["lunder", "lounder"]:
        for mode in MODES:
            for cand in ["t14", "t15", "t14f", "t15f"]:
                p = os.path.join(SRC, f"s{seed}_{cand}_{view}_m{mode}.png")
                if not os.path.exists(p):
                    continue
                m, sd, g = stats(p, view)
                print(f"{seed} {cand:5s} {view:8s} m{mode:<3d}                       {m:6.1f} {sd:5.1f} {g:6.2f}")
