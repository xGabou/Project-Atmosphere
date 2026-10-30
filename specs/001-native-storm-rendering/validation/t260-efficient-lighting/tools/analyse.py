"""T260.17 image metrics on the in-game frames (what the camera shows, not the geometry).
usage: analyse.py SRC > metrics.txt

Each underside frame is downsampled to 800x450, the top 40 px (third-party FPS overlay) are masked, and the cloud
pixels inside the central crop are measured (lunder: central 70 %; lounder: the upper 55 % of the central 70 % - above
the horizon line; sky pixels are excluded by colour):
  mean  - mean luminance (8-bit)
  grad  - mean absolute luminance difference between neighbouring pixels (structure, absolute)
  rel   - grad / mean x 100 (structure independent of brightness: brighter is NOT more structure)
  hp    - standard deviation of (image - 9x9 box blur) (local relief contrast at the scale of the masses)
  disc  - T260.15 / T260.14 ratio of hp at the identical camera and mode (geometry discrimination: 1.0 = the two
          undersides look the same; T260.16 raking light gave about 1.4-1.5 on grad)
"""
import os, sys
from PIL import Image, ImageFilter

SRC = sys.argv[1]


def crop(view, w, h):
    if view == "lunder":
        return int(.15 * w), int(.15 * h), int(.85 * w), int(.85 * h)
    return int(.15 * w), max(10, int(.05 * h)), int(.85 * w), int(.55 * h)


def stats(path, view):
    rgb = Image.open(path).convert("RGB").resize((800, 450), Image.BILINEAR)
    lum = rgb.convert("L")
    blur = lum.filter(ImageFilter.BoxBlur(4))
    w, h = lum.size
    x0, y0, x1, y1 = crop(view, w, h)
    y0 = max(y0, 10)
    P, B, C = lum.load(), blur.load(), rgb.load()
    n = 0; s = 0.0; g = 0.0; gn = 0; hp = []
    for y in range(y0, y1):
        for x in range(x0, x1):
            r, gg, b = C[x, y]
            if b > r + 40:  # sky
                continue
            v = P[x, y]
            s += v; n += 1
            hp.append(v - B[x, y])
            if x + 1 < x1:
                g += abs(v - P[x + 1, y]); gn += 1
            if y + 1 < y1:
                g += abs(v - P[x, y + 1]); gn += 1
    if n < 1000:
        return None
    m = s / n
    hm = sum(hp) / len(hp)
    hsd = (sum((v - hm) ** 2 for v in hp) / len(hp)) ** .5
    return m, g / max(gn, 1), 100 * g / max(gn, 1) / max(m, 1), hsd


def get(name, view):
    p = os.path.join(SRC, name + ".png")
    return stats(p, view) if os.path.exists(p) else None


MODES = [(20, "current"), (29, "T260.16 cand"), (31, "A dir ambient"), (32, "B gradient"), (33, "C baked"),
         (34, "f1 +transmission"), (35, "f2 +dir ambient"), (28, "f3 normals"), (37, "baked normals"), (36, "f6 display map"),
         (22, "direct only"), (23, "ambient only"), (30, "od uncapped"), (38, "D grazing quad"), (39, "D ambient only")]
print(__doc__.split("\n\n")[1] if False else "T260.17 underside metrics (8-bit luminance; see tools/analyse.py)")
for seed in ["2609101", "2609196", "2609102"]:
    for sun in ["35", "12"]:
        for view in ["lunder", "lounder"]:
            print(f"\n== seed {seed} sun {sun} {view}")
            print(f"{'mode':22s} {'mean':>6s} {'grad':>6s} {'rel':>6s} {'hp':>6s} | {'t14 hp':>7s} {'disc':>5s} | {'f mean':>6s} {'f hp':>6s} | {'res100 hp':>9s} {'sp10 hp':>8s}")
            for m, label in MODES:
                a = get(f"s{seed}_t15_sun{sun}_{view}_m{m}", view)
                if a is None:
                    continue
                b = get(f"s{seed}_t14_sun{sun}_{view}_m{m}", view)
                f = get(f"s{seed}_t15f_sun{sun}_{view}_m{m}", view)
                r = get(f"s{seed}_t15_sun{sun}_{view}_m{m}_res100", view)
                q = get(f"s{seed}_t15_sun{sun}_{view}_m{m}_sp10", view)
                print(f"{m:<3d}{label:19s} {a[0]:6.1f} {a[1]:6.2f} {a[2]:6.2f} {a[3]:6.2f} | "
                      f"{(f'{b[3]:7.2f}' if b else '      -')} {(f'{a[3] / b[3]:5.2f}' if b and b[3] > 0 else '    -')} | "
                      f"{(f'{f[0]:6.1f}' if f else '     -')} {(f'{f[3]:6.2f}' if f else '     -')} | "
                      f"{(f'{r[3]:9.2f}' if r else '        -')} {(f'{q[3]:8.2f}' if q else '       -')}")
