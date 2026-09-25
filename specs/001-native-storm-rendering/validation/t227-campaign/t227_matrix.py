# T227. The consistency matrix, one storm directory at a time. For every pose:
# image-quality properties from FINAL (P0) + ALPHA, and renderer state from the
# diagnostic views at the same pixels (L light path, D density, E pre-erosion,
# X macro envelope). Dome and lower mass are never pooled (neck split, T221).
import sys, os, json, math
import numpy as np
import t221_cells as C
from t221_ridge import gblur, ridge
from t221_regions import analyse

POSES = ["SIDE", "LATERAL", "FAR", "PVN", "LOWSIDE", "ABOVE", "NEAREDGE", "THROUGH"]
SPLIT = {"SIDE", "LATERAL", "FAR", "PVN", "LOWSIDE", "NEAREDGE"}


def magenta(rgb):
    return (rgb[..., 0] > 200) & (rgb[..., 1] < 60) & (rgb[..., 2] > 200)


def load(D, view, pose, gray=True):
    p = os.path.join(D, f'T227_{view}_{pose}.png')
    rgb = C.load(p)
    return rgb, rgb.mean(axis=2)


def lc(img, m):
    b = C.boxblur(img, 8)
    sd = np.sqrt(np.maximum(C.boxblur(img * img, 8) - b * b, 0))
    return float(sd[m].mean())


def plateau(img, m):
    # fraction of region pixels whose 17x17 neighbourhood varies < 1.5 levels:
    # large flat patches of one tone (the "shadow plateau")
    b = C.boxblur(img, 8)
    sd = np.sqrt(np.maximum(C.boxblur(img * img, 8) - b * b, 0))
    return float((sd[m] < 1.5).mean())


def region(P, L, Dn, E, X, bad, m):
    m = m & ~bad
    if m.sum() < 1500:
        return None
    r = ridge(P)
    od = -2.0 * np.log(np.clip(L / 255.0, 1e-3, 1.0))
    dens = Dn / 255.0; pre = E / 255.0
    bill = gblur(P, 4.0) - gblur(P, 24.0)
    return dict(
        px=int(m.sum()), luma=float(P[m].mean()), sd=float(P[m].std()), lc=lc(P, m),
        plateau=plateau(P, m), vein=float(np.percentile(r[m], 95) / max(P[m].std(), 1e-6)),
        billow=float(bill[m].std()), depth=float(np.percentile(P[m], 95) - np.percentile(P[m], 5)),
        od_med=float(np.median(od[m])), od_sd=float(od[m].std()), shadowed=float((od[m] > 4).mean()),
        lit=float((od[m] < 1).mean()), form=float(((od[m] >= 1) & (od[m] <= 4)).mean()),
        dens=float(dens[m].mean()), dens_sat=float((dens[m] > 0.98).mean()), dens_sd=float(dens[m].std()),
        erosion=float((pre[m] - dens[m]).mean()), env=float((X[m] / 255.0).mean()),
    )


def silhouette(A):
    solid = A >= 0.5 * 255
    if solid.sum() < 100:
        return {}
    ys, xs = np.nonzero(solid)
    area = solid.sum()
    edge = solid ^ (np.roll(solid, 1, 0) & np.roll(solid, -1, 0) & np.roll(solid, 1, 1) & np.roll(solid, -1, 1))
    perim = edge.sum()
    soft = ((A > 0.1 * 255) & (A < 0.9 * 255)).sum()
    # moments ellipse: how elliptical (smooth, featureless) the outline is
    cy, cx = ys.mean(), xs.mean()
    cov = np.cov(np.vstack([xs - cx, ys - cy]))
    ev = np.sort(np.linalg.eigvalsh(cov))[::-1]
    a, b = 2 * math.sqrt(ev[0]), 2 * math.sqrt(ev[1])
    yy, xx = np.mgrid[0:A.shape[0], 0:A.shape[1]]
    w, v = np.linalg.eigh(cov)
    d = np.stack([xx - cx, yy - cy], -1) @ v
    inside = (d[..., 1] / (2 * math.sqrt(w[1]))) ** 2 + (d[..., 0] / (2 * math.sqrt(w[0]))) ** 2 <= 1
    iou = (inside & solid).sum() / max((inside | solid).sum(), 1)
    return dict(area=int(area), compact=float(perim ** 2 / (4 * math.pi * area)), ellipse_iou=float(iou),
                soft_edge=float(soft / max(perim, 1)), aspect=float(a / max(b, 1e-6)),
                height_px=int(ys.max() - ys.min()), width_px=int(xs.max() - xs.min()))


def run(D):
    out = {}
    for pose in POSES:
        try:
            Prgb, P = load(D, 'P0', pose)
            _, A = load(D, 'P0', pose + '_ALPHA')
            Lrgb, L = load(D, 'L', pose); Drgb, Dn = load(D, 'D', pose)
            Ergb, E = load(D, 'E', pose); Xrgb, X = load(D, 'X', pose)
        except FileNotFoundError as e:
            print('missing', pose, e); continue
        bad = magenta(Lrgb) | magenta(Drgb) | magenta(Ergb) | magenta(Xrgb)
        Pl = C.lum(Prgb)
        cloud = A >= 0.5 * 255
        rec = dict(sil=silhouette(A), mismatch=float((bad & cloud).mean()))
        if pose in SPLIT:
            try:
                s, upper, lower, _, _ = analyse(D, pose, prefix='T227')
                inner = C.boxblur((A >= 250).astype(float), 4) >= 0.999
                rec['split'] = {k: s[k] for k in ('neck_px', 'upper_max', 'lower_max', 'lower_components',
                                                   'lower_largest_frac', 'lower_bbox_occupancy') if k in s}
                rec['dome'] = region(Pl, L, Dn, E, X, bad, upper & inner)
                rec['lower'] = region(Pl, L, Dn, E, X, bad, lower & inner)
                for sun in ('S2', 'S4'):
                    fp = os.path.join(D, f'T227_{sun}P0_{pose}.png')
                    if os.path.exists(fp):
                        Ps = C.lum(C.load(fp)); _, Ls = load(D, sun + 'L', pose)
                        rec[sun + '_dome'] = region(Ps, Ls, Dn, E, X, bad, upper & inner)
                        rec[sun + '_lower'] = region(Ps, Ls, Dn, E, X, bad, lower & inner)
            except Exception as e:
                rec['split_error'] = str(e)
                rec['body'] = region(Pl, L, Dn, E, X, bad, cloud)
        else:
            rec['body'] = region(Pl, L, Dn, E, X, bad, cloud)
        out[pose] = rec
    return out


if __name__ == '__main__':
    D = sys.argv[1]
    res = run(D)
    json.dump(res, open(sys.argv[2], 'w'), indent=1)
    for pose, rec in res.items():
        sil = rec.get('sil', {})
        print(f"{pose}: sil compact {sil.get('compact', 0):.2f} ellipseIoU {sil.get('ellipse_iou', 0):.3f} "
              f"softEdge {sil.get('soft_edge', 0):.2f} aspect {sil.get('aspect', 0):.2f} | split {rec.get('split')}")
        for reg in ('dome', 'lower', 'body', 'S2_dome', 'S2_lower', 'S4_dome', 'S4_lower'):
            r = rec.get(reg)
            if r:
                print(f"   {reg}: luma {r['luma']:.1f} lc {r['lc']:.2f} plateau {r['plateau']:.2f} vein {r['vein']:.3f} "
                      f"billow {r['billow']:.2f} depth {r['depth']:.1f} | od med {r['od_med']:.2f} sd {r['od_sd']:.2f} "
                      f"shadowed {r['shadowed']:.2f} form {r['form']:.2f} lit {r['lit']:.2f} | dens {r['dens']:.2f} sat {r['dens_sat']:.2f} "
                      f"sd {r['dens_sd']:.2f} erosion {r['erosion']:+.3f} env {r['env']:.2f}")
