# T224 run 3. The standard T152 route, body_prod vs body_cand (both the
# production temporal path). Radiance-only change: the luma metrics are the
# self-referential instability measures (alpha flicker cannot see it).
import sys, re
import numpy as np

PAT = re.compile(r'(\w+)=([-\d.]+|none)')


def parse(path):
    arms = {}
    for line in open(path, encoding='utf-8', errors='replace'):
        if 'T152_FRAME' not in line:
            continue
        s = line[line.index('T152_FRAME'):]
        arm = re.search(r'arm=(\S+)', s).group(1)
        seg = re.search(r'segment=(\S+)', s).group(1)
        kv = dict(PAT.findall(s))
        row = {'seg': seg}
        for f in ('frame', 'dLumaMean', 'dLumaMax', 'lumaPop', 'dLuma2Mean', 'dLuma2Max', 'lumaMean', 'flicker', 'cameraDensity'):
            v = kv.get(f); row[f] = None if v in (None, 'none') else float(v)
        arms.setdefault(arm, {})[int(row['frame'])] = row
    return arms


def main(path):
    A = parse(path)
    P, F = A['body_prod'], A['body_cand']
    frames = sorted(set(P) & set(F))
    segs = sorted(set(P[i]['seg'] for i in frames))
    print('segment | frames | metric | prod mean | full mean | full/prod')
    for seg in ['ALL'] + segs:
        sel = [i for i in frames if seg == 'ALL' or P[i]['seg'] == seg]
        for f in ('dLumaMean', 'lumaPop', 'dLuma2Mean', 'dLuma2Max', 'flicker'):
            a = [P[i][f] for i in sel if P[i][f] is not None and F[i][f] is not None]
            b = [F[i][f] for i in sel if P[i][f] is not None and F[i][f] is not None]
            if a:
                print(f'{seg} | {len(sel)} | {f} | {np.mean(a):.5f} | {np.mean(b):.5f} | {np.mean(b)/max(np.mean(a),1e-12):.3f}')
    for arm in ('production_050', 'body_prod', 'body_cand'):
        if arm in A:
            L = np.array([A[arm][i]['lumaMean'] for i in sorted(A[arm])]); dl = np.abs(np.diff(L)) * 255
            print(f'{arm}: frames with |d lumaMean| > 2 levels: {(dl > 2).sum()}, max {dl.max():.2f}')
    off = np.array([(P[i]['lumaMean'] - F[i]['lumaMean']) * 255 for i in frames])
    for seg in ['ALL'] + segs:
        m = np.array([seg == 'ALL' or P[i]['seg'] == seg for i in frames])
        print(f'radiance offset prod-full {seg}: mean {off[m].mean():+.3f} levels, median |d| {np.median(np.abs(off[m])):.3f}')
    # crawl: frames where full's self-referential dLuma2Mean exceeds prod's by >25%
    worse = sum(1 for i in frames if P[i]['dLuma2Mean'] and F[i]['dLuma2Mean'] and F[i]['dLuma2Mean'] > 1.25 * P[i]['dLuma2Mean'])
    better = sum(1 for i in frames if P[i]['dLuma2Mean'] and F[i]['dLuma2Mean'] and P[i]['dLuma2Mean'] > 1.25 * F[i]['dLuma2Mean'])
    print(f'per-frame dLuma2Mean: full worse by >25% in {worse} frames, better by >25% in {better}')


if __name__ == '__main__':
    main(sys.argv[1])
