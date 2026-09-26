# T235. Every route arm against the monolith ('mono') and current FINAL
# ('final'), per segment: alpha flicker, alpha dMean, luma dLumaMean, ghost,
# and cloud GPU ms. The 'final_b' repeat gives the floor.
import sys, re
import numpy as np

PAT = re.compile(r'(\w+)=([-\w.]+)')
F = ('flicker', 'dMean', 'dLumaMean', 'lumaPop', 'ghostMean', 'gpuMs', 'stepScale')


def parse(path):
    arms = {}
    for line in open(path, encoding='utf-8', errors='replace'):
        if 'T152_FRAME' not in line:
            continue
        kv = dict(PAT.findall(line[line.index('T152_FRAME'):]))
        if kv.get('arm') == 'production_050':
            continue
        row = {'seg': kv.get('segment')}
        for f in F:
            try:
                row[f] = float(kv.get(f))
            except (TypeError, ValueError):
                row[f] = np.nan
        arms.setdefault(kv['arm'], {})[int(kv['frame'])] = row
    return arms


def mean(R, seg, f):
    return np.nanmean([r[f] for r in R.values() if seg == 'ALL' or r['seg'] == seg])


if __name__ == '__main__':
    A = parse(sys.argv[1])
    segs = ['ALL', 'APPROACH', 'ENTRY', 'INTERIOR', 'OUTSIDE']
    arms = list(A)
    print('arms:', arms)
    for ref in ('mono', 'final'):
        if ref not in A:
            continue
        print(f'\n=== ratio to {ref}  (flicker / alpha dMean / luma dLuma / ghost)')
        for arm in arms:
            if arm == ref:
                continue
            parts = []
            for seg in segs:
                r = [mean(A[arm], seg, f) / max(mean(A[ref], seg, f), 1e-12)
                     for f in ('flicker', 'dMean', 'dLumaMean', 'ghostMean')]
                parts.append(f'{seg[:4]} {r[0]:.3f}/{r[1]:.3f}/{r[2]:.3f}/{r[3]:.2f}')
            print(f'  {arm:18s} ' + '  '.join(parts))
    print('\n=== cloud GPU ms (ALL / ENTRY / INTERIOR / OUTSIDE)')
    for arm in arms:
        print(f'  {arm:18s} ' + ' '.join(f'{mean(A[arm], s, "gpuMs"):.3f}' for s in ('ALL', 'ENTRY', 'INTERIOR', 'OUTSIDE')))
