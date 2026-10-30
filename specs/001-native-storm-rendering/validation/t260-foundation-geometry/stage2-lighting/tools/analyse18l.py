"""T260.18 stage 2 in-game frame metrics: does a lighting REVEAL D2's masses, or only brighten them?
usage: analyse18l.py SRC
Frames downsampled to 800x450, HUD rows (top 40 px) masked, sky excluded by colour, central crop (directly below:
central 70 %; oblique / edge-on: above the horizon band). Per frame:
  mean   mean luminance (8-bit)
  rel    mean |neighbour difference| / mean x 100 (fine structure independent of brightness)
  mass   sd of the band-pass (3 px blur - 24 px blur) / mean x 100: relative contrast at the scale of the masses
  corr27 correlation of that band-pass with the band-pass of the raking reference (mode 27) at the same camera and sun
         35 - how much of the relief the raking light exposes the lighting also shows (sign-free shape agreement)
Brighter is not more relief: compare mass and corr27, not mean."""
import os, sys
from PIL import Image, ImageFilter

SRC = sys.argv[1]


def crop(view, w, h):
    if view == "lunder":
        return int(.15 * w), max(40, int(.15 * h)), int(.85 * w), int(.85 * h)
    return int(.10 * w), 40, int(.90 * w), int(.60 * h)


def load(name):
    p = os.path.join(SRC, name + ".png")
    if not os.path.exists(p):
        return None
    rgb = Image.open(p).convert("RGB").resize((800, 450), Image.BILINEAR)
    lum = rgb.convert("L")
    return rgb, lum, lum.filter(ImageFilter.GaussianBlur(3)), lum.filter(ImageFilter.GaussianBlur(24))


def stats(name, view, ref=None):
    f = load(name)
    if f is None:
        return None
    rgb, lum, b3, b24 = f
    w, h = lum.size
    x0, y0, x1, y1 = crop(view, w, h)
    C, P, A, B = rgb.load(), lum.load(), b3.load(), b24.load()
    R = None
    if ref is not None:
        R = (ref[2].load(), ref[3].load(), ref[0].load())
    n = 0; s = 0.0; g = 0.0; gn = 0; bp = []; rb = []
    for y in range(y0, y1):
        for x in range(x0, x1):
            r, gg, b = C[x, y]
            if b > r + 40:
                continue
            if R is not None:
                rr, rg, rbb = R[2][x, y]
                if rbb > rr + 40:
                    continue
            v = P[x, y]
            s += v; n += 1
            if x + 1 < x1:
                g += abs(v - P[x + 1, y]); gn += 1
            if y + 1 < y1:
                g += abs(v - P[x, y + 1]); gn += 1
            bp.append(A[x, y] - B[x, y])
            if R is not None:
                rb.append(R[0][x, y] - R[1][x, y])
    if n < 2000:
        return None
    m = s / n
    mb = sum(bp) / len(bp)
    sd = (sum((v - mb) ** 2 for v in bp) / len(bp)) ** .5
    corr = float("nan")
    if rb:
        mr = sum(rb) / len(rb)
        sr = (sum((v - mr) ** 2 for v in rb) / len(rb)) ** .5
        if sd > 0 and sr > 0:
            corr = sum((a - mb) * (c - mr) for a, c in zip(bp, rb)) / len(bp) / sd / sr
    return m, 100 * g / max(gn, 1) / max(m, 1), 100 * sd / max(m, 1), corr, n


print(__doc__)
for seed in ["2609196", "2609101"]:
    for view in ["lunder", "lounder", "fprofile"]:
        for e in [35, 12]:
            ref = load(f"s{seed}_t18d2_sun35_{view}_m27") if view != "fprofile" else None
            print(f"\n{seed} {view} sun {e}")
            print(f"  {'mode':18s} {'mean':>6s} {'rel':>6s} {'mass':>6s} {'corr27':>7s} {'px':>7s}")
            for m, label in [(20, "20 current"), (35, "35 L-dir"), (40, "40 L-graz"), (41, "41 L-graz-c"), (27, "27 raking ref")]:
                if m == 27 and (e != 35 or view == "fprofile"):
                    continue
                st = stats(f"s{seed}_t18d2_sun{e}_{view}_m{m}", view, ref)
                if st is None:
                    print(f"  {label:18s} -")
                    continue
                print(f"  {label:18s} {st[0]:6.1f} {st[1]:6.2f} {st[2]:6.2f} {st[3]:+7.2f} {st[4]:7d}")
