# T224 Task 9. The altitude sweep across the camera-height light-reach rule.
# reach_prod (production, capped in band) against reach_full (no switch) on the
# same frames. A pop is luminance change AT the crossing frames beyond what the
# no-switch control shows on the very same frames.
import sys, re
import numpy as np

PAT = re.compile(r'(\w+)=([-\d.]+|none)')
FIELDS = ('dLumaMean', 'dLumaMax', 'lumaPop', 'dLuma2Mean', 'dLuma2Max', 'lumaMean',
          'flicker', 'dMean', 'rayFloorY', 'slabBaseY', 'maxPrecip', 'cameraDensity')


def parse(path):
    arms, top, boundary = {}, None, None
    for line in open(path, encoding='utf-8', errors='replace'):
        if 'T224M_ALTITUDE' in line:
            kv = dict(PAT.findall(line)); top = float(kv['slabTopY'])
            boundary = line[line.index('T224M_ALTITUDE'):].strip()
        if 'T152_FRAME' not in line:
            continue
        s = line[line.index('T152_FRAME'):]
        arm = re.search(r'arm=(\S+)', s).group(1)
        cam = re.search(r'cam=\(([-\d.]+),([-\d.]+),([-\d.]+)\)', s)
        kv = dict(PAT.findall(s))
        row = {'frame': int(kv['frame']), 'y': float(cam.group(2))}
        for f in FIELDS:
            v = kv.get(f); row[f] = None if v in (None, 'none') else float(v)
        arms.setdefault(arm, []).append(row)
    return arms, top, boundary


def main(path):
    arms, top, boundary = parse(path)
    print(boundary)
    for arm, rows in arms.items():
        ys = [r['y'] for r in rows]
        print(f'{arm}: frames {len(rows)}, y {min(ys):.1f}..{max(ys):.1f}, rayFloorY '
              f'{rows[0]["rayFloorY"]}, slabBaseY {rows[0]["slabBaseY"]}, maxPrecip {rows[0]["maxPrecip"]}')
    P = {r['frame']: r for r in arms['reach_prod']}
    F = {r['frame']: r for r in arms['reach_full']}
    frames = sorted(set(P) & set(F))
    floor = P[frames[0]]['rayFloorY']
    inband = {i: floor <= P[i]['y'] <= top for i in frames}
    cross = [i for i in frames if i - 1 in inband and inband[i] != inband[i - 1]]
    near = set(); [near.update(range(c - 1, c + 2)) for c in cross]
    print(f'\nboundary crossings: {len(cross)} at frames {cross[:20]}')
    print(f'frames in band {sum(inband.values())} / {len(frames)}')

    def agg(A, sel, f):
        v = [A[i][f] for i in sel if A[i][f] is not None]
        return (np.mean(v), np.max(v)) if v else (float('nan'), float('nan'))

    other = [i for i in frames if i not in near and i - 1 in inband]
    print('\nfield | prod@crossing mean/max | full@crossing mean/max | prod elsewhere mean/max | full elsewhere mean/max')
    for f in ('dLumaMean', 'dLumaMax', 'lumaPop', 'dLuma2Mean', 'dLuma2Max', 'flicker', 'dMean'):
        a, b, c, d = agg(P, cross, f), agg(F, cross, f), agg(P, other, f), agg(F, other, f)
        print(f'{f} | {a[0]:.4f}/{a[1]:.4f} | {b[0]:.4f}/{b[1]:.4f} | {c[0]:.4f}/{c[1]:.4f} | {d[0]:.4f}/{d[1]:.4f}')

    # The switch as a step: per-frame lumaMean offset prod - full, in band vs out.
    off = np.array([P[i]['lumaMean'] - F[i]['lumaMean'] for i in frames])
    ib = np.array([inband[i] for i in frames])
    print(f'\nlumaMean offset prod-full: in band {off[ib].mean():+.3f} (sd {off[ib].std():.3f}), '
          f'out of band {off[~ib].mean():+.3f} (sd {off[~ib].std():.3f}) -> step {off[ib].mean()-off[~ib].mean():+.3f} levels')
    # Per-crossing: frame-to-frame lumaMean change, prod vs full, same frames.
    print('\nper crossing: frame | y prev->now | direction | prod dLumaMeanFrame | full dLumaMeanFrame | prod dLumaMean | full dLumaMean | prod lumaPop | full lumaPop')
    for c in cross:
        dp = P[c]['lumaMean'] - P[c - 1]['lumaMean']; df = F[c]['lumaMean'] - F[c - 1]['lumaMean']
        print(f'{c} | {P[c-1]["y"]:.1f}->{P[c]["y"]:.1f} | {"enter" if inband[c] else "leave"} | {dp:+.3f} | {df:+.3f} | '
              f'{P[c]["dLumaMean"]} | {F[c]["dLumaMean"]} | {P[c]["lumaPop"]} | {F[c]["lumaPop"]}')
    # Noise floor: the same frame-to-frame lumaMean change away from crossings.
    dpo = [abs(P[i]['lumaMean'] - P[i - 1]['lumaMean']) for i in other]
    dfo = [abs(F[i]['lumaMean'] - F[i - 1]['lumaMean']) for i in other]
    print(f'\n|d lumaMean| per frame away from crossings: prod mean {np.mean(dpo):.3f} p99 {np.percentile(dpo,99):.3f} max {np.max(dpo):.3f}; '
          f'full mean {np.mean(dfo):.3f} p99 {np.percentile(dfo,99):.3f} max {np.max(dfo):.3f}')
    if 'production_050' in arms:
        Q = {r['frame']: r for r in arms['production_050']}
        rep = [abs(Q[i]['lumaMean'] - P[i]['lumaMean']) for i in frames if i in Q]
        print(f'production_050 vs reach_prod (identical settings, repeat floor): |lumaMean| mean {np.mean(rep):.3f} max {np.max(rep):.3f}')


if __name__ == '__main__':
    main(sys.argv[1])
