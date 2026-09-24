# T222 Tasks 1/3/5. Which density stage carries the large form, which the veins.
#
# Regions come from the composite's ALPHA view of the production frame, split at
# the neck (T221's rule: never pool dome and lower mass). Bands:
#   vein   - Hessian ridge response at sigma 1.5 px (thin lines)
#   billow - band-pass between Gaussian sigma 4 and 24 px (large forms)
# Stage views paint magenta where the stage could not be matched to the lit
# sample; those pixels are excluded and counted.
import sys, os
import numpy as np
import t221_cells as C
from t221_ridge import gblur, ridge
from t221_regions import analyse

def magenta(rgb):
    return (rgb[..., 0] > 200) & (rgb[..., 1] < 60) & (rgb[..., 2] > 200)

def billow(img):
    return gblur(img, 4.0) - gblur(img, 24.0)

def local_contrast(img, m):
    b = C.boxblur(img, 8)
    sd = np.sqrt(np.maximum(C.boxblur(img * img, 8) - b * b, 0))
    return float(sd[m].mean())

def region_metrics(img, m, L=None):
    gy, gx = np.gradient(img); g = np.hypot(gx, gy)
    thr = np.percentile(g[m], 90); e = g[m] ** 2
    r = ridge(img)
    bl = billow(img)
    out = dict(
        flat=100 * (g[m] < 0.6).mean(), sd=img[m].std(), lc=local_contrast(img, m),
        vein_abs=float(np.percentile(r[m], 95)),
        vein=float(np.percentile(r[m], 95) / max(img[m].std(), 1e-6)),
        billow=float(bl[m].std()), spars=float(e[g[m] >= thr].sum() / max(e.sum(), 1e-9)),
    )
    if L is not None:
        gL = np.hypot(*np.gradient(L))
        out['lcorr'] = float(np.corrcoef(g[m], gL[m])[0, 1])
    return out, r, bl

def run(D, pose):
    s, upper, lower, P, A = analyse(D, pose, prefix='T222')
    inner = C.boxblur((A >= 250).astype(float), 4) >= 0.999
    L = C.lum(C.load(os.path.join(D, f'T222_L_{pose}.png')))
    print(f'\n== {pose} ==  neck {s["neck_px"]} px')
    for name, reg in (('DOME', upper), ('LOWER', lower)):
        m = reg & inner
        if m.sum() < 3000:
            print(f'  {name}: too small ({m.sum()} px)'); continue
        base, rP, bP = region_metrics(P, m, L)
        _, rL, bL = region_metrics(L, m)
        print(f'  {name} ({m.sum()} px) baseline P0: flat {base["flat"]:.1f}% | lum sd {base["sd"]:.2f} | '
              f'local contrast {base["lc"]:.3f} | billow {base["billow"]:.3f} | vein {base["vein"]:.3f} '
              f'(abs {base["vein_abs"]:.3f}) | sparsity {base["spars"]:.3f} | light-path grad corr {base["lcorr"]:.3f}')
        print(f'    stage | valid% | mean | sd | p05 | p50 | p95 | vein corr vs P0 | vs L | billow corr vs P0 | vs L')
        for lab in ('D', 'PRE', 'POST', 'ENV'):
            path = os.path.join(D, f'T222_{lab}_{pose}.png')
            rgb = C.load(path)
            bad = magenta(rgb)
            mm = m & ~bad
            val = rgb.mean(axis=2) / 255.0          # grey views: channel mean = stage value
            img = C.lum(rgb)
            _, r, b = region_metrics(img, mm)
            q = np.percentile(val[mm], [5, 50, 95])
            vc_P = np.corrcoef(r[mm], rP[mm])[0, 1]; vc_L = np.corrcoef(r[mm], rL[mm])[0, 1]
            bc_P = np.corrcoef(b[mm], bP[mm])[0, 1]; bc_L = np.corrcoef(b[mm], bL[mm])[0, 1]
            print(f'    {lab:4s} | {100*mm.sum()/m.sum():5.1f} | {val[mm].mean():.4f} | {val[mm].std():.4f} | '
                  f'{q[0]:.4f} | {q[1]:.4f} | {q[2]:.4f} | {vc_P:+.3f} | {vc_L:+.3f} | {bc_P:+.3f} | {bc_L:+.3f}')

if __name__ == '__main__':
    D = sys.argv[1]
    for pose in sys.argv[2:]:
        run(D, pose)
