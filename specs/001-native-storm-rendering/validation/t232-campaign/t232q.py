# T232 quality guard. Per pose, one storm, adjacent arms:
#   F  decode check (mode 14): red = |decoded - returned form| x25, green = form != 0
#   R  T231 shading off (monolith)   M  production on the monolith   P  production (lean)
import sys, os, json
import numpy as np
import t221_cells as C

POSES = ["THROUGH", "NEAREDGE", "LOWSIDE", "SIDE", "LATERAL", "ABOVE"]


def lcmap(img):
    b = C.boxblur(img, 8)
    return np.sqrt(np.maximum(C.boxblur(img * img, 8) - b * b, 0))


def run(D):
    out = {}
    for pose in POSES:
        try:
            F = C.load(os.path.join(D, f'T232_F_{pose}.png')).astype(float)
            R = C.lum(C.load(os.path.join(D, f'T232_R_{pose}.png')))
            M = C.lum(C.load(os.path.join(D, f'T232_M_{pose}.png')))
            P = C.lum(C.load(os.path.join(D, f'T232_P_{pose}.png')))
        except FileNotFoundError as e:
            print('missing', pose, e); continue
        red, green, blue = F[..., 0], F[..., 1], F[..., 2]
        carried = blue > 8
        form = green > 8
        rec = dict(carried_frac=float(carried.mean()), form_frac=float(form.mean()))
        if carried.sum():
            rec['decode_red_max'] = float(red[carried].max())
            rec['decode_red_p99'] = float(np.percentile(red[carried], 99.9))
            rec['decode_red_over_80'] = int((red[carried] > 80).sum())
        rec['P_vs_M_mean_abs'] = float(np.abs(P - M).mean())
        rec['P_vs_M_p999'] = float(np.percentile(np.abs(P - M), 99.9))
        d = P - R
        rec['P_vs_R_mean_abs_frame'] = float(np.abs(d).mean())
        region = C.boxblur(form.astype(float), 4) >= 0.999
        rec['region_frac'] = float(region.mean())
        if region.sum() > 2000:
            lcR, lcP = lcmap(R), lcmap(P)
            rec['region'] = dict(
                mean_shift=float(d[region].mean()), mean_abs=float(np.abs(d[region]).mean()),
                lc_R=float(lcR[region].mean()), lc_P=float(lcP[region].mean()),
                flat_R=float((lcR[region] < 1.5).mean()), flat_P=float((lcP[region] < 1.5).mean()))
            rec['outside_mean_abs'] = float(np.abs(d[~C.boxblur(form.astype(float), 12).astype(bool)]).mean())
        out[pose] = rec
    return out


if __name__ == '__main__':
    r = run(sys.argv[1])
    json.dump(r, open(sys.argv[2] if len(sys.argv) > 2 else 't232q.json', 'w'), indent=1)
    for pose, rec in r.items():
        print(pose, json.dumps(rec))
