"""T260.L-family in-game frame metrics, mode 20 vs mode 42 at the identical camera.
usage: metrics_lf.py SRC
Frames downsampled to 800x450; HUD rows (top 40 px) masked. The cloud mask comes from the MODE 20 frame of the same camera
(neutral grey cloud: sky = blue > red + 40, terrain = green-dominant), so both lightings are measured on the same pixels.
Below: central 70 %; other views: rows 40 .. 75 % of the height. Per frame (8-bit luminance on the mask):
  mean    mean luminance
  lit90   luminance at the 90th percentile (the sunlit skin)        chroma90 mean (max-min RGB) of the brightest 10 %
  dark10  luminance at the 10th percentile (shadowed / underside)
  rel     mean |neighbour difference| / mean x 100 (fine structure, brightness independent)
  mass    sd of the band-pass (3 px - 24 px blur) / mean x 100 (structure at the scale of the domes / masses)
  bpLv    the same sd in absolute 8-bit levels
  corr20  correlation of this frame's band-pass with mode 20's (1 = the same structure only rescaled)"""
import os, sys
from PIL import Image, ImageFilter

SRC = sys.argv[1]
VIEWS = ["ground800", "cthree", "eside", "lunder", "close"]


def load(name):
    if "_t10_" in name and "_cthree_" in name: name = name.replace("_cthree_", "_three_quarter_")  # tlf10 supplement
    p = os.path.join(SRC, name + ".png")
    if not os.path.exists(p): return None
    rgb = Image.open(p).convert("RGB").resize((800, 450), Image.BILINEAR)
    lum = rgb.convert("L")
    return rgb, lum, lum.filter(ImageFilter.GaussianBlur(3)), lum.filter(ImageFilter.GaussianBlur(24))


def mask(ref, view):
    rgb = ref[0].load(); w, h = ref[0].size
    if view == "lunder": x0, y0, x1, y1 = int(.15 * w), max(40, int(.15 * h)), int(.85 * w), int(.85 * h)
    else: x0, y0, x1, y1 = 0, 40, w, int(.75 * h)
    out = []
    for y in range(y0, y1):
        for x in range(x0, x1):
            r, g, b = rgb[x, y]
            if b > r + 40 or (g > r + 15 and g > b + 10): continue
            out.append((x, y))
    return out


def stats(f, px, refbp=None):
    C, P, A, B = f[0].load(), f[1].load(), f[2].load(), f[3].load()
    S = set(px)
    vals = sorted(P[x, y] for x, y in px)
    n = len(vals); m = sum(vals) / n
    g = gn = 0
    for x, y in px:
        if (x + 1, y) in S: g += abs(P[x, y] - P[x + 1, y]); gn += 1
        if (x, y + 1) in S: g += abs(P[x, y] - P[x, y + 1]); gn += 1
    bp = [A[x, y] - B[x, y] for x, y in px]
    mb = sum(bp) / n; sd = (sum((v - mb) ** 2 for v in bp) / n) ** .5
    thr = vals[int(.9 * n)]
    top = [C[x, y] for x, y in px if P[x, y] >= thr]
    chroma = sum(max(c) - min(c) for c in top) / max(len(top), 1)
    corr = float("nan")
    if refbp is not None:
        mr = sum(refbp) / n; sr = (sum((v - mr) ** 2 for v in refbp) / n) ** .5
        if sd > 0 and sr > 0: corr = sum((a - mb) * (c - mr) for a, c in zip(bp, refbp)) / n / sd / sr
    return dict(mean=m, lit90=thr, chroma90=chroma, dark10=vals[int(.1 * n)], rel=100 * g / max(gn, 1) / max(m, 1),
                mass=100 * sd / max(m, 1), bpLv=sd, corr20=corr, n=n), bp


print(__doc__)
agg = {20: [], 42: [], 43: []}
for c in ("t10", "t15", "t18d2"):
    for seed in ("2609101", "2609196", "2609102"):
        for e in ((35, 12) if c == "t18d2" and seed != "2609102" else (35,)):
            print(f"\n== {c} seed {seed} sun {e}")
            print(f"  {'view':10s} {'mode':>4s} {'mean':>6s} {'lit90':>6s} {'chr90':>6s} {'dark10':>6s} {'rel':>6s} {'mass':>6s} {'bpLv':>6s} {'corr20':>7s} {'px':>7s}")
            for v in VIEWS:
                f20 = load(f"s{seed}_{c}_sun{e}_{v}_m20"); f42 = load(f"s{seed}_{c}_sun{e}_{v}_m42"); f43 = load(f"s{seed}_{c}_sun{e}_{v}_m43")
                if f20 is None or f42 is None or f43 is None:
                    print(f"  {v:10s} -"); continue
                px = mask(f20, v)
                if len(px) < 2000:
                    print(f"  {v:10s} too little cloud ({len(px)} px)"); continue
                s20, bp20 = stats(f20, px)
                s42, _ = stats(f42, px, bp20)
                s43, _ = stats(f43, px, bp20)
                for mode, s in ((20, s20), (42, s42), (43, s43)):
                    if e == 35: agg[mode].append((c, seed, v, s))
                    print(f"  {v:10s} {mode:4d} {s['mean']:6.1f} {s['lit90']:6d} {s['chroma90']:6.1f} {s['dark10']:6d} {s['rel']:6.2f} {s['mass']:6.2f} {s['bpLv']:6.2f} {s['corr20']:+7.2f} {s['n']:7d}")
print("\nSun 35 medians over every candidate / seed, per view (mode 20 / 42 / 43):")
for v in VIEWS:
    def med(mode, k):
        xs = sorted(s[k] for (_, _, vv, s) in agg[mode] if vv == v)
        return xs[len(xs) // 2] if xs else float("nan")
    print(f"  {v:10s} " + "  ".join(f"{k} {med(20, k):.1f}/{med(42, k):.1f}/{med(43, k):.1f}" for k in ("mean", "lit90", "dark10", "rel", "mass", "bpLv"))
          + f"  corr20 {med(42, 'corr20'):+.2f}/{med(43, 'corr20'):+.2f}")
