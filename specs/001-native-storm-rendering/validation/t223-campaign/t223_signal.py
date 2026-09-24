# T223. Does one coarser version of the base field (B1, an emulated mip-2 fetch)
# separate the dome's large form from the vein network, against the current
# base field (B0), at the same visible samples? Dome and lower mass separately.
import sys, os, math
import numpy as np
import t221_cells as C
from t221_ridge import gblur, ridge
from t221_regions import analyse

FOV = math.radians(70.0); FRAME_H = 900.0

def magenta(rgb):
    return (rgb[..., 0] > 200) & (rgb[..., 1] < 60) & (rgb[..., 2] > 200)

def billow(img):
    return gblur(img, 4.0) - gblur(img, 24.0)

def fine(img):
    return img - gblur(img, 2.0)

def ac_length(img, m, maxlag=80):
    """1/e radial autocorrelation length (px) of the region, mean-filled outside it."""
    ys, xs = np.nonzero(m)
    y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
    a = img[y0:y1, x0:x1].copy(); mm = m[y0:y1, x0:x1]
    a[~mm] = a[mm].mean(); a = a - a.mean()
    f = np.fft.rfft2(a); ac = np.fft.irfft2(f * np.conj(f), s=a.shape)
    ac = np.fft.fftshift(ac); ac /= ac.max()
    cy, cx = ac.shape[0] // 2, ac.shape[1] // 2
    L = min(maxlag, cy - 1, cx - 1)
    yy, xx = np.mgrid[-L:L + 1, -L:L + 1]; rr = np.hypot(yy, xx)
    win = ac[cy - L:cy + L + 1, cx - L:cx + L + 1]
    for lag in range(1, L + 1):
        sel = (rr >= lag - 0.5) & (rr < lag + 0.5)
        if win[sel].mean() < 1.0 / math.e:
            return float(lag)
    return float('inf')

def signal_stats(v, m):
    r = ridge(v); b = billow(v); fn = fine(v)
    gy, gx = np.gradient(v); g = np.hypot(gx, gy); thr = np.percentile(g[m], 90); e = g[m] ** 2
    vein = float(np.percentile(r[m], 95))
    return dict(r=r, b=b, sd=float(v[m].std()), low=float(b[m].std()), hi=float(fn[m].std()),
                vein=vein, ratio=float(b[m].std() / max(vein, 1e-9)),
                spars=float(e[g[m] >= thr].sum() / max(e.sum(), 1e-9)))

def corr(a, b, m):
    return float(np.corrcoef(a[m], b[m])[0, 1])

def run(D, pose, radius, factor):
    s, upper, lower, P, A = analyse(D, pose, prefix='T223')
    inner = C.boxblur((A >= 250).astype(float), 4) >= 0.999
    tan = math.sqrt(max((radius * factor) ** 2 - radius ** 2, 1.0)); bpp = tan * FOV / FRAME_H
    rP = ridge(P); bP = billow(P)
    print(f'\n== {pose} ==  neck {s["neck_px"]} px, ~{bpp:.2f} blocks/px at the silhouette tangent')
    for name, reg in (('DOME', upper), ('LOWER', lower)):
        m = reg & inner
        if m.sum() < 3000:
            print(f'  {name}: too small'); continue
        out = {}
        for lab in ('B0', 'B1'):
            rgb = C.load(os.path.join(D, f'T223_{lab}_{pose}.png'))
            bad = magenta(rgb); mm = m & ~bad
            v = rgb.mean(axis=2) / 255.0
            st = signal_stats(v, mm)
            st['mis'] = int((m & bad).sum()); st['mm'] = mm
            st['vc'] = corr(st['r'], rP, mm); st['fc'] = corr(st['b'], bP, mm)
            st['ac'] = ac_length(v, mm)
            out[lab] = st
        topo = corr(out['B1']['r'], out['B0']['r'], out['B0']['mm'] & out['B1']['mm'])
        print(f'  {name} ({m.sum()} px): signal | mismatches | vein corr vs P0 | form (billow) corr vs P0 | '
              f'low-band sd | fine-band sd | vein energy | billow:vein | sparsity | feature scale px (blocks)')
        for lab in ('B0', 'B1'):
            t = out[lab]
            print(f'     {lab} | {t["mis"]} | {t["vc"]:+.3f} | {t["fc"]:+.3f} | {t["low"]:.4f} | {t["hi"]:.4f} | '
                  f'{t["vein"]:.4f} | {t["ratio"]:.2f} | {t["spars"]:.3f} | {t["ac"]:.0f} ({t["ac"]*bpp:.1f})')
        print(f'     B1 ridge map vs B0 ridge map (same-topology check): {topo:+.3f}   '
              f'ratio change {out["B1"]["ratio"]/out["B0"]["ratio"]:.2f}x')

if __name__ == '__main__':
    D, radius = sys.argv[1], float(sys.argv[2])
    for pose, factor in (('SIDE', 1.70), ('LOWSIDE', 1.45), ('LATERAL', 1.90), ('NEAREDGE', 1.12)):
        if os.path.exists(os.path.join(D, f'T223_B1_{pose}.png')):
            run(D, pose, radius, factor)
