"""Flatness metrics, same code for photographs and renders.

mask    : the largest low-saturation bright component (the cloud), sky = saturated blue.
H       : mask bounding-box height in pixels; every scale below is a fraction of H.
edge    : 10-90 % luminance rise width across the silhouette, in % of H (smaller = crisper).
detail@s: RMS of (L - gaussian(L, s * H)) inside the eroded mask, divided by the mask's mean L
          (relative local contrast at that scale: the 'bumps' a viewer reads as form).
spread  : (p95 - p5) of L inside the mask (global light/shadow range); clip = share of mask at L >= 0.97.
"""
import sys, json
import numpy as np
from PIL import Image
from scipy import ndimage as ndi


def load(path, max_side=1200):
    im = Image.open(path).convert('RGB')
    s = max_side / max(im.size)
    if s < 1:
        im = im.resize((int(im.size[0] * s), int(im.size[1] * s)), Image.LANCZOS)
    a = np.asarray(im).astype(np.float32) / 255.0
    return a


def metrics(path):
    a = load(path)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    mx, mn = a.max(-1), a.min(-1)
    sat = (mx - mn) / np.maximum(mx, 1e-4)
    L = 0.2126 * r + 0.7152 * g + 0.0722 * b
    # sky: blue-dominant and saturated; cloud: low saturation, not dark (trees / ground excluded)
    sky = (b > r + 0.06) & (sat > 0.22)
    cloud = (sat < 0.24) & (mx > 0.42) & ~sky
    lab, n = ndi.label(cloud)
    if n == 0:
        return None
    sizes = ndi.sum(cloud, lab, range(1, n + 1))
    m = lab == (1 + int(np.argmax(sizes)))
    m = ndi.binary_fill_holes(m)
    ys, xs = np.nonzero(m)
    H = ys.max() - ys.min() + 1
    inner = ndi.binary_erosion(m, iterations=max(2, int(0.02 * H)))
    out = {'file': path.replace('\\', '/').split('/')[-2] + '/' + path.replace('\\', '/').split('/')[-1],
           'H_px': int(H)}
    Lm = L[inner]
    out['meanL'] = round(float(Lm.mean()), 3)
    out['spread'] = round(float(np.percentile(Lm, 95) - np.percentile(Lm, 5)), 3)
    out['clip'] = round(float((Lm >= 0.97).mean()), 3)
    for s in (0.01, 0.02, 0.04, 0.08):
        hp = L - ndi.gaussian_filter(L, s * H)
        out[f'detail{int(s * 100)}'] = round(float(np.sqrt((hp[inner] ** 2).mean()) / max(Lm.mean(), 1e-3)), 4)
    # edge width: along the boundary, luminance contrast cloud vs sky measured on a profile of distance
    dist_in = ndi.distance_transform_edt(m)
    dist_out = ndi.distance_transform_edt(~m)
    signed = np.where(m, dist_in, -dist_out)
    band = np.abs(signed) <= 0.12 * H
    # sky-to-cloud transition measured on |L - Lsky| normalised
    Lsky = np.median(L[sky]) if sky.any() else np.median(L[~m])
    Lcl = np.percentile(L[inner], 50)
    t = np.clip((L - Lsky) / max(Lcl - Lsky, 1e-3), -0.5, 1.5)
    bins = np.linspace(-0.12 * H, 0.12 * H, 49)
    idx = np.digitize(signed[band], bins)
    prof = np.array([np.median(t[band][idx == k]) if np.any(idx == k) else np.nan for k in range(1, len(bins))])
    centers = 0.5 * (bins[1:] + bins[:-1])
    ok = ~np.isnan(prof)
    prof, centers = prof[ok], centers[ok]
    prof = np.maximum.accumulate(prof)
    try:
        lo = centers[np.argmax(prof >= 0.1)]
        hi = centers[np.argmax(prof >= 0.9)]
        out['edge'] = round(float((hi - lo) / H * 100.0), 2)
    except Exception:
        out['edge'] = None
    return out


if __name__ == '__main__':
    res = [metrics(p) for p in sys.argv[1:]]
    for r in res:
        print(json.dumps(r))
