# T234 static: FINAL (P) vs monolith (M) on one storm, adjacent; their own
# 8-frame re-grabs (static noise floor); mode 16 precompute error (E).
import sys, os, json
import numpy as np
from PIL import Image

POSES = ["FAR", "THROUGH", "NEAREDGE", "LOWSIDE", "SIDE", "LATERAL", "ABOVE"]


def load(D, name):
    return np.asarray(Image.open(os.path.join(D, name + '.png')).convert('RGB')).astype(float)


def lum(a):
    return a[..., 0] * 0.2126 + a[..., 1] * 0.7152 + a[..., 2] * 0.0722


def cloudmask(a):
    return (a[..., 2] - a[..., 0]) < 40      # sky is strongly blue


def cmp(a, b):
    d = np.abs(a - b)
    ma, mb = cloudmask(a), cloudmask(b)
    return dict(rms=float(np.sqrt((d ** 2).mean())), maxAbs=float(d.max()),
                px_gt1=float((d.max(axis=2) > 1).mean()), px_gt2=float((d.max(axis=2) > 2).mean()),
                luma_delta=float((lum(b) - lum(a)).mean()),
                sil_iou=float((ma & mb).sum() / max((ma | mb).sum(), 1)))


def run(D):
    out = {}
    for pose in POSES:
        try:
            M, P = load(D, f'T234_M_{pose}'), load(D, f'T234_P_{pose}')
            M8, P8 = load(D, f'T234_M_{pose}_T8'), load(D, f'T234_P_{pose}_T8')
        except FileNotFoundError as e:
            print('missing', e); continue
        try:
            E = load(D, f'T234_E_{pose}')
        except FileNotFoundError:
            E = None
        if E is None:
            out[pose] = dict(final_vs_mono=cmp(M, P), mono_static=cmp(M, M8), final_static=cmp(P, P8))
            continue
        cm = cloudmask(E) & (np.abs(E[..., 0] - E[..., 2]) < 3)      # grey = shaded sample
        v = E[..., 0][cm] / 255.0
        nz = v[v > 0]
        err = np.exp2(nz * 40.0 - 40.0) if nz.size else np.array([0.0])
        rec = dict(
            final_vs_mono=cmp(M, P),
            mono_static=cmp(M, M8), final_static=cmp(P, P8),
            precompute=dict(grey_px=int(cm.sum()), nonexact_frac=float(nz.size / max(v.size, 1)),
                            err_p50=float(np.percentile(err, 50)), err_p99=float(np.percentile(err, 99)),
                            err_max=float(err.max())))
        out[pose] = rec
    return out


if __name__ == '__main__':
    r = run(sys.argv[1])
    json.dump(r, open(sys.argv[2] if len(sys.argv) > 2 else 't234s.json', 'w'), indent=1)
    for pose, rec in r.items():
        print(f'\n== {pose}')
        for k in ('final_vs_mono', 'mono_static', 'final_static'):
            c = rec[k]
            print(f"   {k:14s} rms {c['rms']:.3f} maxAbs {c['maxAbs']:.0f} px>1 {c['px_gt1']:.4f} px>2 {c['px_gt2']:.4f} "
                  f"luma {c['luma_delta']:+.3f} silIoU {c['sil_iou']:.4f}")
        if 'precompute' not in rec:
            continue
        pc = rec['precompute']
        print(f"   precompute: grey px {pc['grey_px']} non-exact {pc['nonexact_frac']:.3f} "
              f"rel err p50 {pc['err_p50']:.2e} p99 {pc['err_p99']:.2e} max {pc['err_max']:.2e}")
