# T221. Thin-line (vein) response: largest-magnitude Hessian eigenvalue of the
# luminance after a small Gaussian blur, i.e. how strongly each pixel sits on a
# thin bright or dark line. Normalised by the region's own luminance SD so arms
# of different contrast compare fairly.
import numpy as np
import t221_cells as C

def gblur(a, s):
    r = int(3 * s + 1); x = np.arange(-r, r + 1); k = np.exp(-x * x / (2 * s * s)); k /= k.sum()
    a = np.apply_along_axis(lambda v: np.convolve(np.pad(v, r, mode='edge'), k, 'valid'), 0, a)
    return np.apply_along_axis(lambda v: np.convolve(np.pad(v, r, mode='edge'), k, 'valid'), 1, a)

def ridge(img, s=1.5):
    b = gblur(img, s)
    gy, gx = np.gradient(b); hyy, hyx = np.gradient(gy); hxy, hxx = np.gradient(gx)
    tr = hxx + hyy; det = hxx * hyy - hxy * hyx
    disc = np.sqrt(np.maximum(tr * tr / 4 - det, 0))
    l1, l2 = tr / 2 + disc, tr / 2 - disc
    return np.where(np.abs(l1) > np.abs(l2), np.abs(l1), np.abs(l2)) * s * s

def vein_score(img, m):
    r = ridge(img)
    sd = max(img[m].std(), 1e-6)
    return float(np.percentile(r[m], 95) / sd), r
