# T230. Lower-deck macro relief: geometry (displaced surface depth, mode 11),
# image (flat / contrast / luma / veins) and shape collateral (alpha), P0 vs R.
import sys, os, json
import numpy as np
import t221_cells as C
from t221_ridge import gblur, ridge
from t221_regions import analyse

POSES = ["THROUGH", "NEAREDGE", "LOWSIDE", "SIDE", "LATERAL", "ABOVE"]


def magenta(rgb):
    return (rgb[..., 0] > 200) & (rgb[..., 1] < 60) & (rgb[..., 2] > 200)


def lcmap(img):
    b = C.boxblur(img, 8)
    return np.sqrt(np.maximum(C.boxblur(img * img, 8) - b * b, 0))


def depth(D, view, pose):
    rgb = C.load(os.path.join(D, f'T230_{view}_{pose}.png'))
    return rgb.mean(axis=2) / 255.0 * 600.0 - 150.0, magenta(rgb)


def spectrum(v, m):
    tot = v[m].var() + 1e-9
    fine = (v - gblur(v, 2.0))[m].var(); mid = (gblur(v, 2.0) - gblur(v, 8.0))[m].var()
    broad = (gblur(v, 8.0) - gblur(v, 32.0))[m].var()
    return fine / tot, mid / tot, broad / tot


def img_stats(P, m):
    lc = lcmap(P)
    return dict(luma=float(P[m].mean()), sd=float(P[m].std()), lc=float(lc[m].mean()),
                flat=float((lc[m] < 1.5).mean()), dark=float((P[m] < 90).mean()), bright=float((P[m] > 170).mean()))


def run(D):
    out = {}
    for pose in POSES:
        try:
            A0 = C.lum(C.load(os.path.join(D, f'T230_P0_{pose}_ALPHA.png')))
            A1 = C.lum(C.load(os.path.join(D, f'T230_R_{pose}_ALPHA.png')))
            W = C.load(os.path.join(D, f'T230_W_{pose}.png')).mean(axis=2) / 255.0
            U0, bad0 = depth(D, 'U', pose); U1, bad1 = depth(D, 'RU', pose)
            P0 = C.lum(C.load(os.path.join(D, f'T230_P0_{pose}.png')))
            P1 = C.lum(C.load(os.path.join(D, f'T230_R_{pose}.png')))
            PB = C.lum(C.load(os.path.join(D, f'T230_P0B_{pose}.png')))
        except FileNotFoundError as e:
            print('missing', pose, e); continue
        c0 = A0 >= 128; c1 = A1 >= 128
        inner = (C.boxblur((A0 >= 250).astype(float), 4) >= 0.999)
        region = inner & (W > 0.2) & ~bad0 & ~bad1
        rec = dict(pose=pose, region_px=int(region.sum()), region_frac=float(region.sum() / max(inner.sum(), 1)),
                   area_delta=float((c1.sum() - c0.sum()) / max(c0.sum(), 1)),
                   iou=float((c0 & c1).sum() / max((c0 | c1).sum(), 1)),
                   floor=float(np.abs(PB - P0)[inner].mean()))
        if region.sum() > 2000:
            added = (U1 - U0)
            rec['geo'] = dict(
                sd0=float(U0[region].std()), sd1=float(U1[region].std()),
                local0=float(lcmap(U0)[region].mean()), local1=float(lcmap(U1)[region].mean()),
                spec0=spectrum(U0, region), spec1=spectrum(U1, region),
                protrude=float(np.percentile(added[region], 95)), recede=float(np.percentile(added[region], 5)),
                added_mean=float(added[region].mean()))
            rec['img_region'] = (img_stats(P0, region), img_stats(P1, region))
        rec['img_frame'] = (img_stats(P0, inner), img_stats(P1, inner))
        if pose in ('LOWSIDE', 'SIDE', 'LATERAL'):
            try:
                s, upper, lower, _, _ = analyse(D, pose, prefix='T230')
                lm = lower & inner
                if lm.sum() > 2000:
                    v0 = float(np.percentile(ridge(P0)[lm], 95) / max(P0[lm].std(), 1e-6))
                    v1 = float(np.percentile(ridge(P1)[lm], 95) / max(P1[lm].std(), 1e-6))
                    rec['lower_vein'] = (v0, v1)
                    rec['lower_lc'] = (float(lcmap(P0)[lm].mean()), float(lcmap(P1)[lm].mean()))
            except Exception as e:
                rec['split_error'] = str(e)
        out[pose] = rec
    return out


if __name__ == '__main__':
    res = run(sys.argv[1])
    json.dump(res, open('t230_geo.json', 'w'), indent=1)
    for pose, r in res.items():
        print(f"{pose:8s} relief region {r['region_frac']:.2f} of cloud ({r['region_px']} px) | area {r['area_delta']:+.4f} "
              f"iou {r['iou']:.4f} | repeat floor {r['floor']:.3f}")
        g = r.get('geo')
        if g:
            print(f"   geometry: depth SD {g['sd0']:.1f} -> {g['sd1']:.1f} blocks, local {g['local0']:.2f} -> {g['local1']:.2f}, "
                  f"spectrum fine/mid/broad {tuple(round(x,2) for x in g['spec0'])} -> {tuple(round(x,2) for x in g['spec1'])}, "
                  f"added shift p5 {g['recede']:+.1f} p95 {g['protrude']:+.1f} mean {g['added_mean']:+.1f}")
            a, b = r['img_region']
            print(f"   underside image: luma {a['luma']:.1f} -> {b['luma']:.1f}, sd {a['sd']:.2f} -> {b['sd']:.2f}, "
                  f"lc {a['lc']:.2f} -> {b['lc']:.2f}, flat {a['flat']:.2f} -> {b['flat']:.2f}")
        a, b = r['img_frame']
        print(f"   frame: luma {a['luma']:.1f} -> {b['luma']:.1f}, lc {a['lc']:.2f} -> {b['lc']:.2f}, flat {a['flat']:.2f} -> {b['flat']:.2f}, "
              f"dark {a['dark']:.2f} -> {b['dark']:.2f}, bright {a['bright']:.2f} -> {b['bright']:.2f}")
        if 'lower_vein' in r:
            print(f"   lower mass: vein {r['lower_vein'][0]:.3f} -> {r['lower_vein'][1]:.3f}, lc {r['lower_lc'][0]:.2f} -> {r['lower_lc'][1]:.2f}")
