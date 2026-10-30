"""T260.18 image metrics on the in-game frames (what the camera shows). Supporting evidence only: visual realism is the
acceptance criterion, not these numbers.
usage: analyse18.py SRC > metrics.txt

Underside frames (lunder, lounder), downsampled to 800x450, top 40 px (FPS overlay) masked, sky excluded by colour:
  cover - share of cloud pixels in the central crop
  mean  - mean luminance of the cloud pixels (8-bit)
  rel   - mean neighbour luminance difference / mean x 100 (structure independent of brightness)
  hp    - std of (image - 9x9 box blur): local contrast at the scale of the masses
Edge-on profile (uprofile): for every image column, the lowest cloud pixel above the horizon (the underside silhouette
  against the sky; the lower half of the frame holds terrain and is excluded);
  prof_sd   - std of that silhouette height (px at 800x450): large = the base terminates at different altitudes
  prof_tv   - mean |step| between neighbouring columns (px): fine texture of the silhouette
  prof_span - P95 - P5 of the silhouette height (px)
"""
import os, sys
from PIL import Image, ImageFilter

SRC = sys.argv[1]
CANDS = ["t14", "t15", "t18a", "t18b", "t18c"]


def sky(r, g, b):
    return b > r + 25 and b > 120


def under(path, view):
    rgb = Image.open(path).convert("RGB").resize((800, 450), Image.BILINEAR)
    lum = rgb.convert("L")
    blur = lum.filter(ImageFilter.BoxBlur(4))
    w, h = lum.size
    if view == "lunder":
        x0, y0, x1, y1 = int(.15 * w), int(.15 * h), int(.85 * w), int(.85 * h)
    else:
        x0, y0, x1, y1 = int(.15 * w), max(10, int(.05 * h)), int(.85 * w), int(.55 * h)
    y0 = max(y0, 10)
    P, B, C = lum.load(), blur.load(), rgb.load()
    n = tot = 0; s = g = 0.0; gn = 0; hp = []
    for y in range(y0, y1):
        for x in range(x0, x1):
            tot += 1
            if sky(*C[x, y]):
                continue
            v = P[x, y]; s += v; n += 1; hp.append(v - B[x, y])
            if x + 1 < x1: g += abs(v - P[x + 1, y]); gn += 1
            if y + 1 < y1: g += abs(v - P[x, y + 1]); gn += 1
    if n < 500:
        return None
    m = s / n; hm = sum(hp) / n
    return 100 * n / tot, m, 100 * g / max(gn, 1) / max(m, 1), (sum((v - hm) ** 2 for v in hp) / n) ** .5


def profile(path):
    rgb = Image.open(path).convert("RGB").resize((800, 450), Image.BILINEAR)
    C = rgb.load()
    w, h = rgb.size
    lows = []
    for x in range(40, w - 40):
        low = None
        for y in range(int(.5 * h), 40, -1):  # above the horizon (terrain below it): first non-sky pixel above a sky run
            r, g, b = C[x, y]
            if not sky(r, g, b):
                if y + 1 < h and sky(*C[x, y + 1]):
                    low = y
                    break
        if low is not None:
            lows.append(low)
    if len(lows) < 50:
        return None
    m = sum(lows) / len(lows)
    sd = (sum((v - m) ** 2 for v in lows) / len(lows)) ** .5
    tv = sum(abs(lows[i + 1] - lows[i]) for i in range(len(lows) - 1)) / (len(lows) - 1)
    q = sorted(lows)
    return len(lows), sd, tv, q[int(.95 * (len(q) - 1))] - q[int(.05 * (len(q) - 1))]


print("T260.18 image metrics (see tools/analyse18.py); supporting evidence only")
for seed in ["2609101", "2609196"]:
    for suffix, label in [("", "with towers"), ("f", "foundation alone")]:
        for mode in [20, 27]:
            print(f"\n== seed {seed} {label} mode {mode}")
            print(f"{'cand':6s} | {'lunder cover mean rel hp':>28s} | {'lounder cover mean rel hp':>29s} | {'uprofile cols sd tv span':>26s}")
            for c in CANDS:
                row = f"{c + suffix:6s} |"
                for v in ["lunder", "lounder"]:
                    p = os.path.join(SRC, f"s{seed}_{c}{suffix}_{v}_m{mode}.png")
                    a = under(p, v) if os.path.exists(p) else None
                    row += f" {a[0]:5.0f}% {a[1]:5.1f} {a[2]:5.2f} {a[3]:5.2f}   |" if a else f" {'-':>26s}   |"
                p = os.path.join(SRC, f"s{seed}_{c}{suffix}_uprofile_m{mode}.png")
                pr = profile(p) if os.path.exists(p) else None
                row += f" {pr[0]:4d} {pr[1]:6.1f} {pr[2]:5.2f} {pr[3]:5.0f}" if pr else "  -"
                print(row)
