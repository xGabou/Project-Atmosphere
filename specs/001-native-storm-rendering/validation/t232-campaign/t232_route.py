# T232M. Motion route: relief_on (production, packed form) vs relief_off (shade 0).
import sys, re
import numpy as np

PAT = re.compile(r'(\w+)=([-\w.]+)')


def parse(path):
    arms = {}
    for line in open(path, encoding='utf-8', errors='replace'):
        if 'T152_FRAME' not in line:
            continue
        s = line[line.index('T152_FRAME'):]
        kv = dict(PAT.findall(s))
        row = {'seg': kv.get('segment')}
        for f in ('frame', 'dLumaMean', 'lumaPop', 'dLuma2Mean', 'dLuma2Max', 'flicker', 'lumaMean'):
            try:
                row[f] = float(kv.get(f))
            except (TypeError, ValueError):
                row[f] = np.nan
        arms.setdefault(kv['arm'], {})[int(row['frame'])] = row
    return arms


def main(path):
    A = parse(path)
    for bname, cand in (('relief_off', 'relief_on'), ('relief_off', 'relief_on_b'), ('relief_on', 'relief_on_b')):
        base = A[bname]
        C = A[cand]
        frames = sorted(set(base) & set(C))
        print(f'\n== {cand} vs {bname} ({len(frames)} frames)')
        for seg in ['ALL'] + sorted(set(base[i]['seg'] for i in frames)):
            sel = [i for i in frames if seg == 'ALL' or base[i]['seg'] == seg]
            line = f'  {seg:8s}'
            for f in ('dLumaMean', 'lumaPop', 'dLuma2Mean', 'flicker'):
                a = np.nanmean([base[i][f] for i in sel]); b = np.nanmean([C[i][f] for i in sel])
                line += f' {f} {b / max(a, 1e-12):.3f}x'
            print(line)
        worse = sum(1 for i in frames if C[i]['dLuma2Mean'] > 1.25 * base[i]['dLuma2Mean'])
        better = sum(1 for i in frames if base[i]['dLuma2Mean'] > 1.25 * C[i]['dLuma2Mean'])
        Lb = np.array([base[i]['lumaMean'] for i in frames]) * 255; Lc = np.array([C[i]['lumaMean'] for i in frames]) * 255
        jb = (np.abs(np.diff(Lb)) > 2).sum(); jc = (np.abs(np.diff(Lc)) > 2).sum()
        print(f'  per-frame dLuma2 >25% worse {worse} / better {better} | frames >2-level jump {jb} -> {jc} | '
              f'mean offset {np.mean(Lc - Lb):+.2f} levels')


if __name__ == '__main__':
    main(sys.argv[1])
