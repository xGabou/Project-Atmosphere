# T240. T207M route summary per build: every arm's per-segment means, and the
# production temporal arm (temporal_025) over the same run's non-temporal
# 0.5 reference (production_050) - the storm-normalised motion figure.
import sys, re
import numpy as np
PAT = re.compile(r'(\w+)=([-\w.]+)')
F = ('flicker', 'dMean', 'dLumaMean', 'lumaPop', 'ghostMean', 'gpuMs', 'coverage')
def parse(path):
    arms = {}
    for line in open(path, encoding='utf-8', errors='replace'):
        if 'T152_FRAME' not in line:
            continue
        kv = dict(PAT.findall(line[line.index('T152_FRAME'):]))
        row = {'seg': kv.get('segment')}
        for f in F:
            try: row[f] = float(kv.get(f))
            except (TypeError, ValueError): row[f] = np.nan
        arms.setdefault(kv['arm'], {})[int(kv['frame'])] = row
    return arms
def mean(R, seg, f):
    v = [r[f] for r in R.values() if seg == 'ALL' or r['seg'] == seg]
    return np.nanmean(v) if v else np.nan
segs = ['ALL', 'APPROACH', 'ENTRY', 'INTERIOR', 'OUTSIDE']
for path in sys.argv[1:]:
    A = parse(path)
    print(f'##### {path}  arms={list(A)}  frames={[len(v) for v in A.values()]}')
    for arm in A:
        print(f'  {arm:22s}', '  '.join(f'{s[:4]} fl={mean(A[arm],s,"flicker"):.5f} dL={mean(A[arm],s,"dLumaMean"):.5f} pop={mean(A[arm],s,"lumaPop"):.4f} gh={mean(A[arm],s,"ghostMean"):.5f}' for s in ('ALL','ENTRY','INTERIOR')))
    if 'temporal_025' in A and 'production_050' in A:
        t, r = A['temporal_025'], A['production_050']
        print('  temporal_025 / production_050:', '  '.join(
            f'{s[:4]} fl={mean(t,s,"flicker")/max(mean(r,s,"flicker"),1e-12):.3f} dL={mean(t,s,"dLumaMean")/max(mean(r,s,"dLumaMean"),1e-12):.3f} pop={mean(t,s,"lumaPop")/max(mean(r,s,"lumaPop"),1e-12):.3f}' for s in segs))
        print('  cloud gpuMs temporal_025:', ' '.join(f'{s[:4]}={mean(t,s,"gpuMs"):.3f}' for s in segs), ' coverage ALL', f'{mean(t,"ALL","coverage"):.4f}')
