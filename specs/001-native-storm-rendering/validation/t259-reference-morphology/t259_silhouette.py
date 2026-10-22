"""T259 reference-driven morphology: silhouette masks and normalised anatomy metrics.

The same metric code reads photographic reference masks and native model frames (B v3, rejected C), so
the comparison is like for like. Every silhouette is cropped to its bounding box and resampled to a fixed
height (NORM_H px) with its aspect kept, so all lengths below are fractions of the silhouette height H.

Photographic masks: sky is grown by flood fill from the top border through "sky-coloured" pixels (blue
excess and saturation, or a per-image brightness rule), the cloud is what remains above an annotated
horizon inside an annotated storm crop; the largest component is kept and small enclosed sky pockets are
filled. Every mask is written next to an overlay so it can be checked by eye.
numpy + Pillow only.
"""
import json, math, sys
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

NORM_H = 400


# ----------------------------------------------------------------------------------------- masks

def flood(seed_mask, passable):
    """Binary flood fill (4-connected) of 'passable' from seed pixels."""
    out = seed_mask & passable
    while True:
        grown = out.copy()
        grown[1:, :] |= out[:-1, :]; grown[:-1, :] |= out[1:, :]
        grown[:, 1:] |= out[:, :-1]; grown[:, :-1] |= out[:, 1:]
        grown &= passable
        if (grown == out).all(): return out
        # Fast path: several steps per check.
        for _ in range(30):
            g2 = grown.copy()
            g2[1:, :] |= grown[:-1, :]; g2[:-1, :] |= grown[1:, :]
            g2[:, 1:] |= grown[:, :-1]; g2[:, :-1] |= grown[:, 1:]
            grown = g2 & passable
        out = grown


def components(m):
    """Label 8-connected components (numpy union-find over rows). Returns label array, sizes."""
    h, w = m.shape
    lab = np.zeros((h, w), np.int32)
    parent = [0]
    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]; a = parent[a]
        return a
    nxt = 1
    for y in range(h):
        row = m[y]
        for x in np.nonzero(row)[0]:
            nb = []
            for dy, dx in ((-1, -1), (-1, 0), (-1, 1), (0, -1)):
                yy, xx = y + dy, x + dx
                if 0 <= yy < h and 0 <= xx < w and lab[yy, xx]: nb.append(lab[yy, xx])
            if not nb:
                parent.append(nxt); lab[y, x] = nxt; nxt += 1
            else:
                r = min(find(a) for a in nb); lab[y, x] = r
                for a in nb:
                    ra = find(a)
                    if ra != r: parent[ra] = r
    roots = np.array([find(i) for i in range(nxt)])
    lab = roots[lab]
    sizes = np.bincount(lab.ravel(), minlength=nxt)
    sizes[0] = 0
    return lab, sizes


def largest(m):
    if not m.any(): return m
    small = m
    lab, sizes = components(small)
    return lab == int(np.argmax(sizes))


def fill_holes(m, max_frac=.02):
    """Fill enclosed background pockets smaller than max_frac of the silhouette area."""
    bg = ~m
    border = np.zeros_like(m); border[0, :] = border[-1, :] = True; border[:, 0] = border[:, -1] = True
    outside = flood(border, bg)
    holes = bg & ~outside
    if not holes.any(): return m
    lab, sizes = components(holes)
    area = m.sum()
    fill = np.isin(lab, [i for i, s in enumerate(sizes) if 0 < s < max_frac * area])
    return m | fill


def photo_mask(img, cfg):
    """Cloud silhouette from a photograph and its annotation (crop, horizon, sky rule)."""
    a = np.asarray(img.convert("RGB")).astype(np.float32)
    h, w, _ = a.shape
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    mx, mn = a.max(-1), a.min(-1)
    sat = (mx - mn) / np.maximum(mx, 1)
    rule = cfg.get("sky", {})
    # Auto calibration from the sky along the top border (inside the crop columns): a pixel is sky only if its
    # saturation and blue excess reach a fraction of the measured sky values; bright, blue-shaded cloud has far
    # lower saturation than the sky behind it.
    top = slice(0, max(2, int(.03 * h)))
    xs0, xs1 = int(cfg["crop"][0] * w), max(int(cfg["crop"][0] * w) + 4, int(cfg["crop"][2] * w))
    sky_sat = float(np.median(sat[top, xs0:xs1])); sky_blue = float(np.median((b - r)[top, xs0:xs1]))
    k = rule.get("auto_fraction", .55)
    blue = (b - r) >= max(rule.get("blue_excess", 4), k * sky_blue)
    skyish = blue & (sat >= max(rule.get("min_sat", .03), k * sky_sat))
    if "max_brightness" in rule: skyish |= (mx <= rule["max_brightness"])  # dark sky (dusk) rule
    if rule.get("warm_sky"): skyish |= ((r - b) >= 30) & (mx >= 110)  # orange/yellow twilight sky near the horizon
    if "min_brightness_sky" in rule: skyish &= (mx >= rule["min_brightness_sky"])
    x0, y0, x1, y1 = [int(round(v * (w if i % 2 == 0 else h))) for i, v in enumerate(cfg["crop"])]
    horizon = int(round(cfg.get("horizon", 1.0) * h))
    seed = np.zeros((h, w), bool); seed[0, :] = True; seed[:, 0] = True; seed[:, -1] = True
    for sx, sy in cfg.get("sky_seeds", []): seed[int(sy * h), int(sx * w)] = True
    sky = flood(seed, skyish)
    cloud = ~sky
    if "min_cloud_brightness" in cfg: cloud &= mx >= cfg["min_cloud_brightness"]  # dark occluders (trees, roofs)
    cloud[horizon:, :] = False
    keep = np.zeros_like(cloud); keep[y0:y1, x0:x1] = True
    cloud &= keep
    for poly in cfg.get("exclude", []):
        ex = Image.new("L", (w, h), 0); ImageDraw.Draw(ex).polygon([(px * w, py * h) for px, py in poly], fill=1)
        cloud &= ~np.asarray(ex, bool)
    for poly in cfg.get("include", []):
        inc = Image.new("L", (w, h), 0); ImageDraw.Draw(inc).polygon([(px * w, py * h) for px, py in poly], fill=1)
        cloud |= np.asarray(inc, bool)
    # Morphological clean-up of single-pixel speckle: majority 3x3.
    m = np.asarray(Image.fromarray(cloud.astype(np.uint8) * 255).filter(ImageFilter.MedianFilter(5)), bool)
    m = largest(m)
    return fill_holes(m)


def model_mask(raw_png):
    a = np.asarray(Image.open(raw_png).convert("RGBA"))
    return a[..., 3] > 2


# ----------------------------------------------------------------------------------------- metrics

def normalise(m):
    ys, xs = np.nonzero(m)
    c = m[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    h, w = c.shape
    W = max(1, int(round(w * NORM_H / h)))
    im = Image.fromarray(c.astype(np.uint8) * 255).resize((W, NORM_H), Image.BILINEAR)
    return np.asarray(im) > 127


def flip(m): return m[:, ::-1]


def disk_dilate(m, r):
    """Binary dilation by a disk of radius r (FFT convolution)."""
    if r <= 0: return m.copy()
    h, w = m.shape
    yy, xx = np.mgrid[-r:r + 1, -r:r + 1]
    k = ((xx * xx + yy * yy) <= r * r).astype(np.float32)
    H, W = h + 2 * r + 1, w + 2 * r + 1
    fm = np.fft.rfft2(m.astype(np.float32), s=(H, W)); fk = np.fft.rfft2(k, s=(H, W))
    conv = np.fft.irfft2(fm * fk, s=(H, W))[r:r + h, r:r + w]
    return conv > .5


def closing(m, r):
    pad = r + 2
    p = np.pad(m, pad)
    d = disk_dilate(p, r)
    e = ~disk_dilate(~d, r)
    return e[pad:-pad, pad:-pad]


def metrics(m, view_note=""):
    """Normalised anatomy metrics of a silhouette (H = silhouette height). Downwind = the side with more top mass."""
    n = normalise(m)
    H, W = n.shape
    # Orient so that the anvil/top mass extends to the right (downwind right).
    top = n[: H // 3]
    cx_top = np.nonzero(top)[1].mean() if top.any() else W / 2
    bot = n[-H // 5:]
    cx_bot = np.nonzero(bot)[1].mean() if bot.any() else W / 2
    if cx_top < cx_bot: n = flip(n); cx_top, cx_bot = W - 1 - cx_top, W - 1 - cx_bot
    rows = []
    for y in range(H):
        xs = np.nonzero(n[y])[0]
        rows.append((xs.min(), xs.max(), len(xs)) if len(xs) else (np.nan, np.nan, 0))
    rows = np.array(rows, float)
    width = rows[:, 1] - rows[:, 0] + 1
    def wat(f):  # width at height fraction f above the base (base = bottom row)
        y = int(round((1 - f) * (H - 1)))
        return np.nanmean(width[max(0, y - 2):y + 3]) / H
    profile = [round(float(wat(f)), 4) for f in np.linspace(.05, .95, 19)]
    base_w = float(np.nanmean(width[int(.92 * H):int(.98 * H)]) / H)
    mid = width[int(.30 * H):int(.75 * H)]
    waist = float(np.nanmin(mid) / H)
    waist_h = 1 - (int(.30 * H) + int(np.nanargmin(mid))) / (H - 1)
    max_w = float(np.nanmax(width) / H)
    max_h = 1 - int(np.nanargmax(width)) / (H - 1)
    # Top profile and crown.
    tops = np.array([np.nonzero(n[:, x])[0].min() if n[:, x].any() else np.nan for x in range(W)], float)
    hi = np.nanmin(tops)
    flat = float(np.mean(tops[~np.isnan(tops)] <= hi + .03 * H))
    # Crown summits: peaks of the top profile with prominence >= 3 % H.
    prof = -(tops - np.nanmax(tops)); prof = np.nan_to_num(prof)
    summits = 0
    for x in range(1, W - 1):
        if prof[x] >= prof[x - 1] and prof[x] > prof[x + 1] and prof[x] > .25 * H:
            l = prof[:x][::-1]; rr = prof[x + 1:]
            lmin = l[:np.argmax(l > prof[x])].min() if (l > prof[x]).any() else l.min() if len(l) else prof[x]
            rmin = rr[:np.argmax(rr > prof[x])].min() if (rr > prof[x]).any() else rr.min() if len(rr) else prof[x]
            if prof[x] - max(lmin, rmin) >= .03 * H: summits += 1
    # Anvil: rows from the top whose width exceeds 1.3 x waist, contiguous; overhang beyond the waist span.
    an_rows = 0
    for y in range(H):
        if not np.isnan(width[y]) and width[y] > 1.3 * waist * H: an_rows += 1
        elif an_rows > 0 or y > .5 * H: break
    wy = int(.30 * H) + int(np.nanargmin(mid))
    waist_l, waist_r = rows[wy, 0], rows[wy, 1]
    over_r = (np.nanmax(rows[: max(an_rows, 1), 1]) - waist_r) / H if an_rows else 0
    over_l = (waist_l - np.nanmin(rows[: max(an_rows, 1), 0])) / H if an_rows else 0
    # Anvil thickness along its downwind overhang: vertical extent of the first run below the top per column.
    thick = []
    for x in range(int(waist_r) + 1, W):
        col = n[:, x]
        if not col.any(): continue
        t = np.nonzero(col)[0][0]; b = t
        while b + 1 < H and col[b + 1]: b += 1
        if b < .75 * H: thick.append((b - t + 1) / H)
    thick = np.array(thick)
    # Lean: top-quarter centroid relative to the bottom-quarter centroid.
    lean = (np.nonzero(n[: H // 4])[1].mean() - np.nonzero(n[-H // 4:])[1].mean()) / H
    # Outline notches (closing with a disk of 0.10 H): open-to-sky valleys and their depth.
    R = max(4, int(round(.10 * H)))
    cl = closing(n, R)
    gap = cl & ~n
    notches, deepest = 0, 0.0
    if gap.any():
        lab, sizes = components(gap)
        for i in range(1, len(sizes)):
            if sizes[i] == 0: continue
            ys, xs = np.nonzero(lab == i)
            depth = 0
            # Depth: farthest valley pixel from the closed outline (approx by distance to closed boundary).
            region = lab == i
            ring = region.copy()
            for k in range(1, R + 1):
                inner = region & np.roll(ring, 1, 0) & np.roll(ring, -1, 0) & np.roll(ring, 1, 1) & np.roll(ring, -1, 1)
                if not inner.any(): break
                ring = inner; depth = k
            covered = sum(1 for y, x in zip(ys, xs) if n[:y, x].any())
            if depth / H >= .015 and covered < len(ys) / 2:
                notches += 1; deepest = max(deepest, depth / H)
    # Convexity deficit.
    pts = []
    for y in range(H):
        if rows[y, 2]: pts += [(rows[y, 0], y), (rows[y, 1] + 1, y), (rows[y, 0], y + 1), (rows[y, 1] + 1, y + 1)]
    hull = convex_hull(pts)
    ha = poly_area(hull)
    cd = max(0.0, 1 - n.sum() / ha) if ha > 0 else 0
    return dict(aspect=round(W / H, 4), base_width=round(base_w, 4), waist_width=round(waist, 4), waist_height=round(waist_h, 3),
                max_width=round(max_w, 4), max_width_height=round(max_h, 3), width_profile=profile,
                top_flat_fraction=round(flat, 4), crown_summits=summits,
                anvil_depth=round(an_rows / H, 4), anvil_overhang_downwind=round(float(over_r), 4), anvil_overhang_upwind=round(float(over_l), 4),
                anvil_thickness_mean=round(float(thick.mean()), 4) if len(thick) else None,
                anvil_thickness_cv=round(float(thick.std() / thick.mean()), 4) if len(thick) > 3 else None,
                lean=round(float(lean), 4), notches=notches, deepest_notch=round(deepest, 4), convexity_deficit=round(float(cd), 4))


def convex_hull(pts):
    pts = sorted(set(pts))
    if len(pts) < 3: return pts
    def cross(o, a, b): return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
    lo, up = [], []
    for p in pts:
        while len(lo) >= 2 and cross(lo[-2], lo[-1], p) <= 0: lo.pop()
        lo.append(p)
    for p in reversed(pts):
        while len(up) >= 2 and cross(up[-2], up[-1], p) <= 0: up.pop()
        up.append(p)
    return lo[:-1] + up[:-1]


def poly_area(p):
    return abs(sum(p[i][0] * p[(i + 1) % len(p)][1] - p[(i + 1) % len(p)][0] * p[i][1] for i in range(len(p)))) / 2 if len(p) > 2 else 0


if __name__ == "__main__":
    print(json.dumps(metrics(model_mask(sys.argv[1])), indent=1))
