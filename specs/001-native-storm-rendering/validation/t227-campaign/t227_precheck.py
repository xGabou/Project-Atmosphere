# T227 pre-check. Production (P0) against the ambient-depth arms K25 / K12 at
# the same camera. Regimes come from production's light-path view, so every arm
# is scored on the same pixels.
import sys, os
import numpy as np
import t221_cells as C

POSES = ["SIDE", "LATERAL", "LOWSIDE", "ABOVE", "NEAREDGE", "THROUGH"]


def lcmap(img):
    b = C.boxblur(img, 8)
    return np.sqrt(np.maximum(C.boxblur(img * img, 8) - b * b, 0))


def main(D):
    for pose in POSES:
        try:
            A = C.lum(C.load(os.path.join(D, f'T227_P0_{pose}_ALPHA.png')))
            Lr = C.load(os.path.join(D, f'T227_L_{pose}.png')); Dr = C.load(os.path.join(D, f'T227_D_{pose}.png'))
        except FileNotFoundError:
            print('missing', pose); continue
        od = -2.0 * np.log(np.clip(Lr.mean(axis=2) / 255.0, 1e-3, 1.0))
        inner = C.boxblur((A >= 250).astype(float), 4) >= 0.999
        lcD = lcmap(Dr.mean(axis=2))
        line = f'{pose:8s} shadow {((od > 4) & inner).sum() / max(inner.sum(), 1):.2f} |'
        base = None
        for arm in ('P0', 'K25P0', 'K12P0'):
            P = C.lum(C.load(os.path.join(D, f'T227_{arm}_{pose}.png')))
            lc = lcmap(P)
            sh = inner & (od > 4); li = inner & (od < 1)
            vals = (P[inner].mean(), lc[inner].mean(), (lc[inner] < 1.5).mean(),
                    lc[sh].mean() if sh.sum() > 500 else np.nan,
                    lc[sh].mean() / max(lcD[sh].mean(), 1e-3) if sh.sum() > 500 else np.nan,
                    P[sh].mean() if sh.sum() > 500 else np.nan, P[li].mean() if li.sum() > 500 else np.nan)
            if base is None:
                base = vals
            line += (f' {arm}: luma {vals[0]:.1f} lc {vals[1]:.2f} plateau {vals[2]:.2f} shadow lc {vals[3]:.2f} '
                     f'gain {vals[4]:.2f} shadowLuma {vals[5]:.1f} litLuma {vals[6]:.1f} |')
        print(line)


if __name__ == '__main__':
    main(sys.argv[1])
