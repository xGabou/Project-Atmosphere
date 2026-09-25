# T221 Task 2/3/4. Quantify the "flat cell" look and attribute it.
#
# Everything is measured inside one BODY mask taken from the production frame
# (sky excluded, silhouette eroded 3 px so the sky/cloud edge does not dominate
# a statistic about the surface). Arms of the same pose share that mask.
#
# Cell descriptors (a cellular / piecewise-flat surface has near-flat plateaus
# separated by thin, straight, high-gradient boundaries):
#   flatFrac      share of body pixels whose gradient is below FLAT (8-bit/px)
#   plateauDiam   mean equivalent diameter of connected flat regions >= 20 px
#   gradSparsity  share of total gradient energy carried by the top 10% pixels
#                 (smooth shading spreads it; plateaus+ridges concentrate it)
#   coherence     structure-tensor coherence on the strongest 10% of gradients
#                 (1 = locally straight edges, 0 = isotropic / curved)
#   gradCorr      correlation of |grad| with production's |grad| in the body:
#                 which component's structure does production's follow?
import sys
import os
import glob
import numpy as np
from PIL import Image

FLAT = 0.6


def load(p):
    return np.asarray(Image.open(p).convert('RGB'), dtype=float)


def lum(rgb):
    return rgb @ np.array([0.2126, 0.7152, 0.0722])


def boxblur(a, k):
    pad = np.pad(a, ((k, k), (k, k)), mode='edge')
    cs = np.pad(pad.cumsum(0).cumsum(1), ((1, 0), (1, 0)))
    n = 2 * k + 1
    h, w = a.shape
    return (cs[n:n + h, n:n + w] - cs[0:h, n:n + w]
            - cs[n:n + h, 0:w] + cs[0:h, 0:w]) / (n * n)


def body_mask(ref_path):
    rgb = load(ref_path)
    f = rgb[..., 2] - rgb[..., 0]
    sky, cloud = np.percentile(f, 95), np.percentile(f, 20)
    body = f < cloud + 0.35 * (sky - cloud)
    return boxblur(body.astype(float), 3) >= 0.999


def components_mean_diam(mask, min_px=20):
    h, w = mask.shape
    seen = np.zeros_like(mask)
    diams = []
    ys, xs = np.nonzero(mask)
    for sy, sx in zip(ys, xs):
        if seen[sy, sx]:
            continue
        stack = [(sy, sx)]
        seen[sy, sx] = True
        n = 0
        while stack:
            y, x = stack.pop()
            n += 1
            for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                ny, nx = y + dy, x + dx
                if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and not seen[ny, nx]:
                    seen[ny, nx] = True
                    stack.append((ny, nx))
        if n >= min_px:
            diams.append(np.sqrt(4.0 * n / np.pi))
    return (float(np.mean(diams)) if diams else 0.0), len(diams)


def describe(path, m, ref_grad=None):
    L = lum(load(path))
    gy, gx = np.gradient(L)
    g = np.hypot(gx, gy)
    gb = g[m]
    flat = (g < FLAT) & m
    # Plateau size on a decimated grid so the flood fill stays cheap.
    diam, count = components_mean_diam(flat[::2, ::2])
    diam *= 2.0
    energy = gb ** 2
    thr = np.percentile(gb, 90)
    sparsity = energy[gb >= thr].sum() / max(energy.sum(), 1e-9)
    jxx, jyy, jxy = boxblur(gx * gx, 3), boxblur(gy * gy, 3), boxblur(gx * gy, 3)
    lam = np.sqrt((jxx - jyy) ** 2 + 4 * jxy ** 2)
    coh = lam / np.maximum(jxx + jyy, 1e-9)
    strong = (g >= thr) & m
    out = dict(
        mean=float(L[m].mean()), sd=float(L[m].std()),
        gradE=float(gb.mean()), flatFrac=float(flat[m].mean()),
        plateauDiam=diam, plateaus=count, sparsity=float(sparsity),
        coherence=float(coh[strong].mean()) if strong.any() else 0.0,
        grad=g,
    )
    if ref_grad is not None:
        a, b = g[m], ref_grad[m]
        out['gradCorr'] = float(np.corrcoef(a, b)[0, 1])
    return out


def band_error(a, b, m):
    """Absolute luminance error split into low / mid / high frequency bands."""
    d = a - b
    lo = boxblur(d, 16)
    mid = boxblur(d, 3) - lo
    hi = d - boxblur(d, 3)
    return (float(np.abs(d[m]).mean()), float(np.abs(lo[m]).mean()),
            float(np.abs(mid[m]).mean()), float(np.abs(hi[m]).mean()))


LABELS = ['P0', 'P0B', 'REF', 'M', 'F', 'U', 'L', 'P0_ALPHA', 'P0_DEPTH']


def run(directory, pose):
    ref = os.path.join(directory, f'T221_P0_{pose}.png')
    if not os.path.exists(ref):
        print(f'# {pose}: no production frame')
        return
    m = body_mask(ref)
    p0 = describe(ref, m)
    Lp0 = lum(load(ref))
    print(f'\n== {pose} (body {100*m.mean():.1f}% of frame) ==')
    print('arm|mean|sd|gradE|flatFrac|plateauDiam px|plateaus|gradSparsity|'
          'edgeCoherence|gradCorr vs P0|err abs|err lo|err mid|err hi')
    for lab in LABELS:
        path = os.path.join(directory, f'T221_{lab}_{pose}.png')
        if not os.path.exists(path):
            continue
        d = describe(path, m, p0['grad'])
        e = band_error(lum(load(path)), Lp0, m)
        print(f'{lab}|{d["mean"]:.2f}|{d["sd"]:.2f}|{d["gradE"]:.3f}|'
              f'{100*d["flatFrac"]:.1f}%|{d["plateauDiam"]:.1f}|{d["plateaus"]}|'
              f'{d["sparsity"]:.3f}|{d["coherence"]:.3f}|{d.get("gradCorr", 1.0):.3f}|'
              f'{e[0]:.3f}|{e[1]:.3f}|{e[2]:.3f}|{e[3]:.3f}')


if __name__ == '__main__':
    d = sys.argv[1]
    for pose in sys.argv[2:] or ['SIDE', 'NEAREDGE', 'LOWSIDE', 'LATERAL', 'THROUGH']:
        run(d, pose)
