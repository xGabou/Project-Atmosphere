# T229 Tasks 2-3. Is there form in the surface-depth signal where light-path od
# is flat? Region = deep-shadow cloud (od > 8) per pose. Signals at identical
# pixels: A light-path od (mode 9, log-decoded), B surface depth (mode 10,
# blocks below the macro envelope), C density (mode 3), D production luminance.
import sys, os, math, json
import numpy as np
import t221_cells as C
from t221_ridge import gblur, ridge

POSES = ["NEAREDGE", "THROUGH", "LOWSIDE", "SIDE", "LATERAL", "ABOVE"]


def magenta(rgb):
    return (rgb[..., 0] > 200) & (rgb[..., 1] < 60) & (rgb[..., 2] > 200)


def lcmap(img):
    b = C.boxblur(img, 8)
    return np.sqrt(np.maximum(C.boxblur(img * img, 8) - b * b, 0))


def ac_length(img, m, maxlag=60):
    ys, xs = np.nonzero(m)
    y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
    a = img[y0:y1, x0:x1].copy(); mm = m[y0:y1, x0:x1]
    a[~mm] = a[mm].mean(); a = a - a.mean()
    f = np.fft.rfft2(a); ac = np.fft.fftshift(np.fft.irfft2(f * np.conj(f), s=a.shape)); ac /= ac.max()
    cy, cx = ac.shape[0] // 2, ac.shape[1] // 2
    L = min(maxlag, cy - 1, cx - 1)
    yy, xx = np.mgrid[-L:L + 1, -L:L + 1]; rr = np.hypot(yy, xx); win = ac[cy - L:cy + L + 1, cx - L:cx + L + 1]
    for lag in range(1, L + 1):
        if win[(rr >= lag - 0.5) & (rr < lag + 0.5)].mean() < 1 / math.e:
            return float(lag)
    return float(L)


def stats(v, m, ref_ridge, vis_billow):
    sd = float(v[m].std())
    if sd < 1e-6:
        return dict(sd=0.0)
    r = ridge(v); bil = gblur(v, 4.0) - gblur(v, 24.0)
    gy, gx = np.gradient(v); g = np.hypot(gx, gy); thr = np.percentile(g[m], 90); e = g[m] ** 2
    return dict(sd=sd, lc=float(lcmap(v)[m].mean()), ac=ac_length(v, m),
                billow_frac=float(bil[m].std() / sd),
                vein=float(np.percentile(r[m], 95) / sd),
                sparsity=float(e[g[m] >= thr].sum() / max(e.sum(), 1e-9)),
                vein_corr=float(np.corrcoef(r[m], ref_ridge[m])[0, 1]),
                form_corr=float(np.corrcoef(bil[m], vis_billow[m])[0, 1]))


def run(D, tag):
    rows = []
    for pose in POSES:
        for sun in ('', 'S2'):
            try:
                A = C.lum(C.load(os.path.join(D, f'T229_P0_{pose}_ALPHA.png')))
                Grgb = C.load(os.path.join(D, f'T229_{sun}G_{pose}.png'))
                Urgb = C.load(os.path.join(D, f'T229_{sun}U_{pose}.png'))
                Drgb = C.load(os.path.join(D, f'T229_D_{pose}.png'))
                P = C.lum(C.load(os.path.join(D, f'T229_{sun}P0_{pose}.png')))
            except FileNotFoundError:
                continue
            od = np.exp(Grgb.mean(axis=2) / 255.0 * np.log(65.0)) - 1.0
            logod = 60.0 * np.log1p(od)
            depth = Urgb.mean(axis=2) / 255.0 * 600.0 - 150.0      # blocks below the surface
            dens = Drgb.mean(axis=2)
            inner = (C.boxblur((A >= 250).astype(float), 4) >= 0.999) & ~magenta(Urgb) & ~magenta(Drgb) & ~magenta(Grgb)
            m = inner & (od > 8)
            if m.sum() < 2000:
                rows.append(dict(pose=pose, sun=sun or 'noon', px=int(m.sum()), note='too few deep pixels')); continue
            ref_ridge = ridge(dens); vis_billow = gblur(P, 4.0) - gblur(P, 24.0)
            row = dict(pose=pose, sun=sun or 'noon', px=int(m.sum()),
                       unowned=float((magenta(Urgb) & (C.boxblur((A >= 250).astype(float), 4) >= 0.999)).mean()),
                       depth_med=float(np.median(depth[m])), depth_p5=float(np.percentile(depth[m], 5)),
                       depth_p95=float(np.percentile(depth[m], 95)))
            for name, v in (('od', logod), ('depth', depth), ('density', dens), ('luma', P)):
                row[name] = stats(v, m, ref_ridge, vis_billow)
            rows.append(row)
    json.dump(rows, open(f't229_signal_{tag}.json', 'w'), indent=1)
    for r in rows:
        if 'od' not in r:
            print(r); continue
        print(f"{r['pose']:8s} {r['sun']:4s} deep px {r['px']} | depth below surface med {r['depth_med']:.0f} "
              f"(p5 {r['depth_p5']:.0f}, p95 {r['depth_p95']:.0f}) blocks, unowned {r['unowned']:.3f}")
        for name in ('od', 'depth', 'density', 'luma'):
            s = r[name]
            if s.get('sd', 0) == 0:
                print(f'   {name:8s} flat'); continue
            print(f"   {name:8s} sd {s['sd']:7.2f} lc {s['lc']:6.2f} acLen {s['ac']:4.0f}px billowFrac {s['billow_frac']:.2f} "
                  f"vein {s['vein']:.3f} sparsity {s['sparsity']:.3f} veinCorr(vs density) {s['vein_corr']:+.2f} "
                  f"formCorr(vs luma billow) {s['form_corr']:+.2f}")


if __name__ == '__main__':
    run(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else 'a')
