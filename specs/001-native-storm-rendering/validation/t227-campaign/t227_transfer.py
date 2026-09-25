# T227. Where does visible structure die? Per image, cloud pixels are split by
# light-path regime (L view: lit od<1, form 1-4, shadowed od>4). In each regime:
# local contrast of the DENSITY field (D view, same pixels) vs local contrast of
# the final LUMINANCE. gain = luminance lc / density lc (8-bit per 8-bit).
# Structure present in density but absent in luminance = the lighting transfer
# erased it; absent in both = morphology/thresholding never made it.
import sys, os
import numpy as np
import t221_cells as C

POSES = ["SIDE", "LATERAL", "FAR", "PVN", "LOWSIDE", "ABOVE", "NEAREDGE", "THROUGH"]


def lcmap(img):
    b = C.boxblur(img, 8)
    return np.sqrt(np.maximum(C.boxblur(img * img, 8) - b * b, 0))


def magenta(rgb):
    return (rgb[..., 0] > 200) & (rgb[..., 1] < 60) & (rgb[..., 2] > 200)


def run(D, label, skip=()):
    rows = []
    for pose in POSES:
        for sun, pv, lv in (('noon', 'P0', 'L'), ('S2', 'S2P0', 'S2L'), ('S4', 'S4P0', 'S4L')):
            if (pose, sun) in skip:
                continue
            fp = os.path.join(D, f'T227_{pv}_{pose}.png')
            if not os.path.exists(fp):
                continue
            P = C.lum(C.load(fp)); A = C.lum(C.load(os.path.join(D, f'T227_P0_{pose}_ALPHA.png')))
            Lr = C.load(os.path.join(D, f'T227_{lv}_{pose}.png')); Dr = C.load(os.path.join(D, f'T227_D_{pose}.png'))
            bad = magenta(Lr) | magenta(Dr)
            od = -2.0 * np.log(np.clip(Lr.mean(axis=2) / 255.0, 1e-3, 1.0))
            dens = Dr.mean(axis=2)
            inner = (C.boxblur((A >= 250).astype(float), 4) >= 0.999) & ~bad
            lcP, lcD = lcmap(P), lcmap(dens)
            n = inner.sum()
            if n < 2000:
                continue
            out = dict(storm=label, pose=pose, sun=sun, screen=float(n / P.size))
            for name, m in (('lit', od < 1), ('form', (od >= 1) & (od <= 4)), ('shadow', od > 4)):
                mm = inner & m
                out[name + '_frac'] = float(mm.sum() / n)
                if mm.sum() > 500:
                    out[name + '_lcP'] = float(lcP[mm].mean()); out[name + '_lcD'] = float(lcD[mm].mean())
                    out[name + '_gain'] = float(lcP[mm].mean() / max(lcD[mm].mean(), 1e-3))
                    out[name + '_luma'] = float(P[mm].mean())
            out['lcP_all'] = float(lcP[inner].mean()); out['plateau_all'] = float((lcP[inner] < 1.5).mean())
            rows.append(out)
    return rows


if __name__ == '__main__':
    import json
    rows = []
    for arg in sys.argv[1:]:
        D, label, *skips = arg.split('|')
        rows += run(D, label, skip={tuple(s.split(':')) for s in skips})
    json.dump(rows, open('t227_transfer.json', 'w'), indent=1)
    for r in rows:
        def g(k):
            v = r.get(k); return f'{v:.2f}' if isinstance(v, float) else ' -  '
        print(f"{r['storm']} {r['pose']:8s} {r['sun']:4s} screen {r['screen']:.3f} | lit {g('lit_frac')} form {g('form_frac')} "
              f"shadow {g('shadow_frac')} | gain lit {g('lit_gain')} form {g('form_gain')} shadow {g('shadow_gain')} | "
              f"lcD lit {g('lit_lcD')} shadow {g('shadow_lcD')} | lcP all {g('lcP_all')} plateau {g('plateau_all')}")
