# T228 Task 12. Upper anvil vs lower mass, production vs candidate, per sun:
# luma, local contrast and flat fraction in each region (neck split, T221).
import sys, os
import numpy as np
import t221_cells as C
from t221_regions import analyse


def lcmap(img):
    b = C.boxblur(img, 8)
    return np.sqrt(np.maximum(C.boxblur(img * img, 8) - b * b, 0))


def main(D):
    for pose in ('LOWSIDE', 'NEAREDGE', 'LATERAL', 'SIDE'):
        try:
            s, upper, lower, _, A = analyse(D, pose, prefix='T228')
        except Exception as e:
            print(pose, 'split failed', e); continue
        inner = C.boxblur((A >= 250).astype(float), 4) >= 0.999
        for sun in ('', 'S2'):
            line = f'{pose:8s} {sun or "noon":4s}'
            for arm in ('', 'C'):
                P = C.lum(C.load(os.path.join(D, f'T228_{sun}{arm}P0_{pose}.png'))); lc = lcmap(P)
                for name, m in (('anvil', upper & inner), ('lower', lower & inner)):
                    if m.sum() < 1500:
                        continue
                    line += (f' | {arm or "D0"} {name} luma {P[m].mean():.1f} lc {lc[m].mean():.2f} '
                             f'flat {(lc[m] < 1.5).mean():.2f}')
            print(line)


if __name__ == '__main__':
    main(sys.argv[1])
