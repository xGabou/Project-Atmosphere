# T234. FINAL (lean) vs diagnostic (monolith) under motion, with and without rain,
# one storm, one route. Per segment: alpha flicker (flip-backs), alpha dMean,
# luma dLumaMean / lumaPop / dLuma2Mean, ghost, coverage.
import sys, re
import numpy as np

PAT = re.compile(r'(\w+)=([-\w.]+)')
F = ('flicker', 'dMean', 'dLumaMean', 'lumaPop', 'dLuma2Mean', 'ghostMean', 'coverage', 'alphaMean', 'stepScale', 'gpuMs')


def parse(path):
    arms = {}
    for line in open(path, encoding='utf-8', errors='replace'):
        if 'T152_FRAME' not in line:
            continue
        kv = dict(PAT.findall(line[line.index('T152_FRAME'):]))
        row = {'seg': kv.get('segment')}
        for f in F:
            try:
                row[f] = float(kv.get(f))
            except (TypeError, ValueError):
                row[f] = np.nan
        arms.setdefault(kv['arm'], {})[int(kv['frame'])] = row
    return arms


def table(A, arms):
    base = A[arms[0]]
    segs = ['ALL'] + sorted(set(r['seg'] for r in base.values()))
    for seg in segs:
        print(f'  {seg}')
        for arm in arms:
            R = A[arm]
            sel = [r for r in R.values() if seg == 'ALL' or r['seg'] == seg]
            print('    %-13s n=%4d ' % (arm, len(sel)) + ' '.join(
                f'{f} {np.nanmean([r[f] for r in sel]):.4g}' for f in F))


def ratio(A, a, b):
    fa, fb = A[a], A[b]
    frames = sorted(set(fa) & set(fb))
    out = []
    for seg in ['ALL'] + sorted(set(fa[i]['seg'] for i in frames)):
        sel = [i for i in frames if seg == 'ALL' or fa[i]['seg'] == seg]
        parts = []
        for f in ('flicker', 'dMean', 'dLumaMean', 'lumaPop', 'dLuma2Mean'):
            x = np.nanmean([fa[i][f] for i in sel]); y = np.nanmean([fb[i][f] for i in sel])
            parts.append(f'{f} {y / max(x, 1e-12):.3f}x')
        out.append(f'    {seg:9s} ' + ' '.join(parts))
    return out


if __name__ == '__main__':
    A = parse(sys.argv[1])
    arms = [a for a in ('final_rain', 'mono_rain', 'final_dry', 'mono_dry', 'bridge_a_noprecompute', 'bridge_b_liveconst', 'final_pin05', 'mono_pin05', 'bridge_c_precompvalue', 'bridge_d_derivedvalue', 'final_rain_b') if a in A]
    table(A, arms)
    for arm in arms:
        sc = [A[arm][i]['stepScale'] for i in sorted(A[arm])]
        changes = sum(1 for a, b in zip(sc, sc[1:]) if a != b)
        vals = sorted(set(sc))
        print(f'  {arm}: stepScale values {vals} changes {changes}')
    for a, b in (('final_rain', 'final_rain_b'), ('mono_rain', 'final_rain'), ('mono_dry', 'final_dry'),
                 ('final_dry', 'final_rain'), ('mono_dry', 'mono_rain'),
                 ('mono_rain', 'bridge_a_noprecompute'), ('mono_rain', 'bridge_b_liveconst'),
                 ('bridge_a_noprecompute', 'final_rain'), ('bridge_b_liveconst', 'final_rain'),
                 ('mono_pin05', 'final_pin05'), ('final_pin05', 'final_rain'), ('mono_pin05', 'mono_rain'),
                 ('mono_rain', 'bridge_c_precompvalue'), ('mono_rain', 'bridge_d_derivedvalue'),
                 ('bridge_c_precompvalue', 'final_rain'), ('bridge_d_derivedvalue', 'bridge_a_noprecompute')):
        if a in A and b in A:
            print(f'\n== {b} / {a}')
            print('\n'.join(ratio(A, a, b)))
