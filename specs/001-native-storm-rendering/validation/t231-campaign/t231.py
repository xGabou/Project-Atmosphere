# T231. Lower-anvil relief shading. Arms at one camera: P0 production (M0),
# R T230 relief (M1), RS relief + shading 0.12 (M2), RH shading 0.06 (M3),
# RSL M2 + the exact T228 curve; P0B repeat floor; RM macro signal (mode 13),
# W lower-deck weight (12); S2 = low sun.
import sys, os, json
import numpy as np
import t221_cells as C
from t221_ridge import gblur, ridge
from t221_regions import analyse

POSES = ["THROUGH", "NEAREDGE", "LOWSIDE", "SIDE", "LATERAL", "ABOVE"]


def lcmap(img):
    b = C.boxblur(img, 8)
    return np.sqrt(np.maximum(C.boxblur(img * img, 8) - b * b, 0))


def L(D, v, pose):
    return C.lum(C.load(os.path.join(D, f'T231_{v}_{pose}.png')))


def istats(P, m, form=None):
    lc = lcmap(P); bil = gblur(P, 4.0) - gblur(P, 24.0)
    out = dict(luma=float(P[m].mean()), sd=float(P[m].std()), lc=float(lc[m].mean()),
               flat=float((lc[m] < 1.5).mean()), broad=float(gblur(P, 8.0)[m].std()),
               dark=float((P[m] < 90).mean()), bright=float((P[m] > 170).mean()))
    if form is not None:
        out['coherence'] = float(np.corrcoef(gblur(P, 8.0)[m], form[m])[0, 1])
    return out


def run(D):
    res = {}
    for pose in POSES:
        try:
            A0 = C.lum(C.load(os.path.join(D, f'T231_P0_{pose}_ALPHA.png')))
            A1 = C.lum(C.load(os.path.join(D, f'T231_R_{pose}_ALPHA.png')))
            W = C.load(os.path.join(D, f'T231_W_{pose}.png')).mean(axis=2) / 255.0
            Mv = C.load(os.path.join(D, f'T231_RM_{pose}.png')).mean(axis=2) / 255.0
        except FileNotFoundError as e:
            print('missing', pose, e); continue
        ms = (Mv - 0.5) * 5.0
        inner = C.boxblur((A0 >= 250).astype(float), 4) >= 0.999
        region = inner & (W > 0.2)
        rec = dict(pose=pose, region_frac=float(region.sum() / max(inner.sum(), 1)))
        if region.sum() > 2000:
            fetched = region & (np.abs(Mv - 0.5) > 1.5 / 255)
            rec['signal'] = dict(mean=float(ms[fetched].mean()) if fetched.sum() else None,
                                 sd=float(ms[fetched].std()) if fetched.sum() else None,
                                 p=[float(x) for x in np.percentile(ms[fetched], [1, 5, 50, 95, 99])] if fetched.sum() else None,
                                 min=float(ms[fetched].min()) if fetched.sum() else None,
                                 max=float(ms[fetched].max()) if fetched.sum() else None,
                                 fetched_frac=float(fetched.sum() / region.sum()))
            edge = region & (np.abs(A1 - A0) > 2)
            if edge.sum() > 200:
                rec['sign_corr_alphaGrowth_vs_signal'] = float(np.corrcoef((A1 - A0)[edge], ms[edge])[0, 1])
            form = -gblur(ms * W, 8.0)
        else:
            form = None
        arms = {}
        for sun in ('', 'S2'):
            for arm in ('P0', 'R', 'RS', 'RH', 'RSL', 'P0B'):
                v = sun + arm
                if sun and arm in ('RH', 'P0B'):
                    continue
                try:
                    P = L(D, v, pose)
                except FileNotFoundError:
                    continue
                arms[v] = dict(frame=istats(P, inner),
                               region=istats(P, region, form) if region.sum() > 2000 else None)
        rec['arms'] = arms
        if pose in ('LOWSIDE', 'SIDE', 'LATERAL'):
            try:
                s, upper, lower, _, _ = analyse(D, pose, prefix='T231')
                lm = lower & inner
                vein = {}
                for arm in ('P0', 'R', 'RS', 'RSL'):
                    P = L(D, arm, pose); g = np.hypot(*np.gradient(P)); fine = (P - gblur(P, 2.0))
                    thr = np.percentile(g[lm], 90); e = g[lm] ** 2
                    vein[arm] = dict(vein=float(np.percentile(ridge(P)[lm], 95) / max(P[lm].std(), 1e-6)),
                                     fine=float(fine[lm].std()), sparsity=float(e[g[lm] >= thr].sum() / max(e.sum(), 1e-9)))
                rec['lower_vein'] = vein
                am = upper & inner & (W > 0.2)
            except Exception as e:
                rec['split_error'] = str(e)
        res[pose] = rec
    return res


if __name__ == '__main__':
    r = run(sys.argv[1])
    json.dump(r, open('t231.json', 'w'), indent=1)
    for pose, rec in r.items():
        print(f"\n== {pose} (lower-deck region {rec['region_frac']:.2f} of cloud)")
        if 'signal' in rec:
            s = rec['signal']
            print(f"   macro signal: mean {s['mean']:+.3f} sd {s['sd']:.3f} min {s['min']:+.2f} max {s['max']:+.2f} "
                  f"p1/5/50/95/99 {[round(x,2) for x in s['p']]} fetched {s['fetched_frac']:.2f} | sign corr(alpha growth, signal) "
                  f"{rec.get('sign_corr_alphaGrowth_vs_signal', float('nan')):+.2f}")
        for arm, a in rec['arms'].items():
            f = a['frame']; g = a['region']
            line = f"   {arm:6s} frame luma {f['luma']:.1f} lc {f['lc']:.2f} flat {f['flat']:.2f}"
            if g:
                line += (f" | region luma {g['luma']:.1f} sd {g['sd']:.2f} lc {g['lc']:.2f} flat {g['flat']:.2f} "
                         f"broad {g['broad']:.2f} coherence {g.get('coherence', float('nan')):+.2f}")
            print(line)
        if 'lower_vein' in rec:
            print('   lower mass: ' + ' | '.join(f"{k} vein {v['vein']:.3f} fine {v['fine']:.2f} spars {v['sparsity']:.3f}"
                                               for k, v in rec['lower_vein'].items()))
