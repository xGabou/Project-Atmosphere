# T224 run 2. ABBA capture-pin cost per pose, P (production) vs R (full reach),
# plus the image identity of TOPOUT (out of band: R must equal P).
import sys, re, os
import numpy as np

def main(log, D):
    rows = {}
    for line in open(log, encoding='utf-8', errors='replace'):
        m = re.search(r'T098_CAPTURE_CONTROLS shot=T224Q_([PR])(\d+)_(\w+) .*?gpuMs=([0-9.]+).*lightReach=(\d).*leanFinal=(\w+)', line)
        if m:
            rows.setdefault(m.group(3), []).append((m.group(1), int(m.group(2)), float(m.group(4)), m.group(5), m.group(6)))
    print('pose | n P/R | P mean (sd) | R mean (sd) | R-P mean | pairwise ABBA delta mean +- se | lean | reach flags ok')
    for pose, r in rows.items():
        seen = {}
        for a, n, g, lr, lean in r:
            seen.setdefault((a, n), (g, lr, lean))
        P = [v[0] for (a, n), v in sorted(seen.items()) if a == 'P']
        R = [v[0] for (a, n), v in sorted(seen.items()) if a == 'R']
        ok = all((v[1] == '1') == (a == 'R') for (a, n), v in seen.items())
        # adjacent-pair deltas in ABBA order: (0,1) (2,3) ... each pair one P one R
        seq = [seen[k] for k in sorted(seen, key=lambda k: k[1])]
        codes = [k[0] for k in sorted(seen, key=lambda k: k[1])]
        d = []
        for i in range(0, len(seq) - 1, 2):
            p = seq[i][0] if codes[i] == 'P' else seq[i + 1][0]
            q = seq[i + 1][0] if codes[i] == 'P' else seq[i][0]
            d.append(q - p)
        d = np.array(d)
        print(f'{pose} | {len(P)}/{len(R)} | {np.mean(P):.3f} ({np.std(P):.3f}) | {np.mean(R):.3f} ({np.std(R):.3f}) | '
              f'{np.mean(R)-np.mean(P):+.3f} | {d.mean():+.3f} +- {d.std(ddof=1)/np.sqrt(len(d)) if len(d)>1 else float("nan"):.3f} | '
              f'{set(v[2] for v in seen.values())} | {ok}')
    from PIL import Image
    for pose in ('TOPOUT', 'TOPIN', 'PVN', 'SIDE'):
        a = os.path.join(D, f'T224Q_P0_{pose}.png'); b = os.path.join(D, f'T224C_R1_{pose}.png')
        if os.path.exists(a) and os.path.exists(b):
            A = np.asarray(Image.open(a).convert('RGB'), dtype=float); B = np.asarray(Image.open(b).convert('RGB'), dtype=float)
            dd = np.abs(A - B).mean(axis=2)
            print(f'image {pose}: R1 vs P0 mean |d| {dd.mean():.4f}, px >2 levels {100*(dd>2).mean():.3f}%, max {dd.max():.1f}')

if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
