# T228. Structure by light-path od band, from the log-od view (mode 9):
# od = exp(v/255 * ln 65) - 1. Per band: share of visible cloud, density local
# contrast (D view), final-image local contrast, retention = image/density,
# flat-shadow fraction (image lc < 1.5), and the od field's own local spread.
# Arms: prefix '' production, 'C' candidate; sun: '' noon, 'S2' low sun.
import sys, os, json
import numpy as np
import t221_cells as C

POSES = ["LOWSIDE", "NEAREDGE", "THROUGH", "ABOVE", "LATERAL", "SIDE"]
BANDS = [(0, 1), (1, 4), (4, 8), (8, 16), (16, 40), (40, 1e9)]


def lcmap(img):
    b = C.boxblur(img, 8)
    return np.sqrt(np.maximum(C.boxblur(img * img, 8) - b * b, 0))


def od_from(path):
    v = C.load(path).mean(axis=2) / 255.0
    return np.exp(v * np.log(65.0)) - 1.0


def score(D, pose, sun='', arm='', gview=None):
    A = C.lum(C.load(os.path.join(D, f'T228_P0_{pose}_ALPHA.png')))
    inner = C.boxblur((A >= 250).astype(float), 4) >= 0.999
    od = od_from(os.path.join(D, f'T228_{gview or (sun + "G")}_{pose}.png'))
    dens = C.load(os.path.join(D, f'T228_D_{pose}.png')).mean(axis=2)
    P = C.lum(C.load(os.path.join(D, f'T228_{sun}{arm}P0_{pose}.png')))
    lcP, lcD, lcO = lcmap(P), lcmap(dens), lcmap(np.log1p(od) * 60.0)
    n = inner.sum()
    out = dict(pose=pose, sun=sun or 'noon', arm=arm or 'D0', luma=float(P[inner].mean()), sd=float(P[inner].std()),
               lc=float(lcP[inner].mean()), plateau=float((lcP[inner] < 1.5).mean()),
               dark=float((P[inner] < 90).mean()), bright=float((P[inner] > 170).mean()), bands={})
    deep = inner & (od > 4)
    out['deep_g_median'] = float(np.median(np.log1p(od[deep]) / np.log(41.0))) if deep.sum() > 500 else None
    out['deep_od_median'] = float(np.median(od[deep])) if deep.sum() > 500 else None
    for lo, hi in BANDS:
        m = inner & (od >= lo) & (od < hi)
        rec = dict(share=float(m.sum() / max(n, 1)))
        if m.sum() > 300:
            rec.update(lcP=float(lcP[m].mean()), lcD=float(lcD[m].mean()),
                       retention=float(lcP[m].mean() / max(lcD[m].mean(), 1e-3)),
                       plateau=float((lcP[m] < 1.5).mean()), luma=float(P[m].mean()),
                       od_spread=float(lcO[m].mean()))
        out['bands'][f'{lo}-{int(hi) if hi < 1e8 else "inf"}'] = rec
    return out


def report(rows):
    for r in rows:
        head = (f"{r['pose']:8s} {r['sun']:4s} {r['arm']:3s} luma {r['luma']:.1f} sd {r['sd']:.1f} lc {r['lc']:.2f} "
                f"plateau {r['plateau']:.2f} dark {r['dark']:.2f} bright {r['bright']:.2f} deepOdMed {r['deep_od_median']}")
        print(head)
        print('     ' + ' | '.join(f"{k}: {v['share']:.2f}" + (f" ret {v['retention']:.2f} plat {v['plateau']:.2f}"
                                                                 if 'retention' in v else '') for k, v in r['bands'].items()))


if __name__ == '__main__':
    D = sys.argv[1]
    arms = sys.argv[2].split(',') if len(sys.argv) > 2 else ['']
    rows = []
    for pose in POSES:
        for sun in ('', 'S2'):
            for arm in arms:
                try:
                    rows.append(score(D, pose, sun, arm))
                except FileNotFoundError as e:
                    print('missing', pose, sun, arm, os.path.basename(str(e)))
    json.dump(rows, open(sys.argv[3] if len(sys.argv) > 3 else 't228_bands.json', 'w'), indent=1)
    report(rows)
