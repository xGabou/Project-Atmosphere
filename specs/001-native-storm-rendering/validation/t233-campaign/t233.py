# T233. Lower-anvil second octave. Arms at one camera, all on the monolith:
# W lower-deck weight (mode 12), M1 primary macro signal (13), M2 second-octave
# signal (15), F1 second-octave relief only, F2 relief + matched shading,
# F0 production (with ALPHA for F0/F1/F2).
import sys, os, json, shutil
import numpy as np
import t221_cells as C
from t221_ridge import gblur, ridge
from t221_regions import analyse

POSES = ["THROUGH", "NEAREDGE", "LOWSIDE", "SIDE", "LATERAL", "ABOVE"]
SHADE2 = 0.5


def lcmap(img):
    b = C.boxblur(img, 8)
    return np.sqrt(np.maximum(C.boxblur(img * img, 8) - b * b, 0))


def L(D, v, pose, suffix=''):
    return C.lum(C.load(os.path.join(D, f'T233_{v}_{pose}{suffix}.png')))


def sig(D, v, pose):
    return (C.load(os.path.join(D, f'T233_{v}_{pose}.png')).mean(axis=2) / 255.0 - 0.5) * 5.0


def acf_len(x, m):
    """Pixels at which the field's horizontal autocorrelation inside m drops below 0.5."""
    x = np.where(m, x - x[m].mean(), 0.0)
    var = (x[m] ** 2).mean()
    for lag in range(1, 200):
        a, b = x[:, :-lag], x[:, lag:]
        mm = m[:, :-lag] & m[:, lag:]
        if mm.sum() < 500:
            return None
        if (a[mm] * b[mm]).mean() / var < 0.5:
            return lag
    return None


def fine_share(x, m):
    """Share of the field's variance above ~2 px (vein / fine-gradient score)."""
    hp = x - gblur(x, 2.0)
    return float(hp[m].var() / max(x[m].var(), 1e-9))


def istats(P, m, form=None):
    lc = lcmap(P)
    out = dict(luma=float(P[m].mean()), lc=float(lc[m].mean()),
               flat=float((lc[m] < 1.5).mean()), broad=float(gblur(P, 8.0)[m].std()))
    if form is not None:
        out['coherence'] = float(np.corrcoef(gblur(P, 8.0)[m], form[m])[0, 1])
    return out


def run(D):
    res = {}
    for pose in POSES:
        try:
            Wrgb = C.load(os.path.join(D, f'T233_W_{pose}.png')).astype(float)
            W = Wrgb.mean(axis=2) / 255.0
            m1, m2 = sig(D, 'M1', pose), sig(D, 'M2', pose)
        except FileNotFoundError as e:
            print('missing', pose, e); continue
        # The composite's ALPHA view does not apply on the forced monolith, so
        # the cloud mask comes from the mode-12 image: shaded samples are grey
        # (R=G=B); sky is blue and unowned samples magenta.
        grey = (np.abs(Wrgb[..., 2] - Wrgb[..., 0]) < 6) & (np.abs(Wrgb[..., 1] - Wrgb[..., 0]) < 6)
        inner = C.boxblur(grey.astype(float), 4) >= 0.999
        region = inner & (W > 0.2)
        rec = dict(region_frac=float(region.sum() / max(inner.sum(), 1)))
        if region.sum() > 2000:
            fetched = region & ((np.abs(m1) > 0.03) | (np.abs(m2) > 0.03))
            f = fetched
            comb = m1 + SHADE2 * m2
            rec['signal'] = dict(
                fetched_frac=float(f.sum() / region.sum()),
                m1=dict(mean=float(m1[f].mean()), sd=float(m1[f].std()),
                        p=[round(float(v), 2) for v in np.percentile(m1[f], [5, 50, 95])],
                        spread=float(np.percentile(m1[f], 95) - np.percentile(m1[f], 5)),
                        acf_px=acf_len(m1, f), fine=fine_share(m1, f)),
                m2=dict(mean=float(m2[f].mean()), sd=float(m2[f].std()),
                        p=[round(float(v), 2) for v in np.percentile(m2[f], [5, 50, 95])],
                        spread=float(np.percentile(m2[f], 95) - np.percentile(m2[f], 5)),
                        acf_px=acf_len(m2, f), fine=fine_share(m2, f)),
                comb_sd=float(comb[f].std()),
                corr_m1_m2=float(np.corrcoef(m1[f], m2[f])[0, 1]))
            form0 = -gblur(m1 * W, 8.0)
            form2 = -gblur(comb * W, 8.0)
        else:
            form0 = form2 = None
        arms = {}
        for arm in ('F0', 'F1', 'F2'):
            try:
                P = L(D, arm, pose)
            except FileNotFoundError:
                continue
            fm = form2 if arm == 'F2' else form0
            arms[arm] = dict(frame=istats(P, inner),
                             region=istats(P, region, fm) if region.sum() > 2000 else None)
        try:
            P0, P1 = L(D, 'F0', pose), L(D, 'F1', pose)
            d = np.abs(P1 - P0)
            rec['geom_frame_abs'] = float(d.mean())
            rec['geom_region_abs'] = float(d[region].mean()) if region.sum() > 2000 else None
            rec['geom_outside_abs'] = float(d[inner & ~(C.boxblur((W > 0.05).astype(float), 12) > 0)].mean()) if inner.sum() else None
        except FileNotFoundError:
            pass
        rec['arms'] = arms
        if pose in ('LOWSIDE', 'SIDE', 'LATERAL'):
            try:
                for arm in ('F0', 'F2'):
                    for sfx in ('', '_ALPHA'):
                        shutil.copyfile(os.path.join(D, f'T233_{arm}_{pose}{sfx}.png'),
                                        os.path.join(D, f'T233{arm}_P0_{pose}{sfx}.png'))
                vein = {}
                for arm in ('F0', 'F2'):
                    s, upper, lower, _, _ = analyse(D, pose, prefix='T233' + arm)
                    lm = lower & inner
                    P = L(D, arm, pose)
                    g = np.hypot(*np.gradient(P))
                    vein[arm] = dict(vein=float(np.percentile(ridge(P)[lm], 95) / max(P[lm].std(), 1e-6)),
                                     fine=float((P - gblur(P, 2.0))[lm].std()))
                rec['lower_vein'] = vein
            except Exception as e:
                rec['vein_error'] = str(e)
        res[pose] = rec
    return res


if __name__ == '__main__':
    r = run(sys.argv[1])
    json.dump(r, open(sys.argv[2] if len(sys.argv) > 2 else 't233.json', 'w'), indent=1)
    for pose, rec in r.items():
        print(f"\n== {pose} (lower-deck region {rec['region_frac']:.2f} of cloud)"
              + (f" | F1-F0 frame {rec['geom_frame_abs']:.2f} region {rec['geom_region_abs']} outside {rec['geom_outside_abs']}" if 'geom_frame_abs' in rec else ''))
        if 'signal' in rec:
            s = rec['signal']
            for k in ('m1', 'm2'):
                v = s[k]
                print(f"   {k}: mean {v['mean']:+.3f} sd {v['sd']:.3f} p5/50/95 {v['p']} spread {v['spread']:.2f} "
                      f"acf0.5 {v['acf_px']} px fine {v['fine']:.3f}")
            print(f"   corr(m1,m2) {s['corr_m1_m2']:+.3f}  combined sd {s['comb_sd']:.3f}  fetched {s['fetched_frac']:.2f}")
        for arm, a in rec['arms'].items():
            f, g = a['frame'], a['region']
            line = f"   {arm} frame luma {f['luma']:.1f} lc {f['lc']:.2f} flat {f['flat']:.2f}"
            if g:
                line += (f" | region luma {g['luma']:.1f} lc {g['lc']:.2f} flat {g['flat']:.2f} "
                         f"broad {g['broad']:.2f} coherence {g.get('coherence', float('nan')):+.2f}")
            print(line)
        if 'lower_vein' in rec:
            print('   lower mass: ' + ' | '.join(f"{k} vein {v['vein']:.3f} fine {v['fine']:.2f}" for k, v in rec['lower_vein'].items()))
        if 'vein_error' in rec:
            print('   vein error', rec['vein_error'])
