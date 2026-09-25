# T224 static. Height pair (H0 below the band, H1 inside it) and the reach arms.
import sys, os, re
import numpy as np
import t221_cells as C
from t221_ridge import ridge
from t221_regions import analyse

def od_from_L(path):
    return -2.0 * np.log(np.clip(C.load(path).mean(axis=2) / 255.0, 1e-3, 1.0))

def lc(img, m):
    b = C.boxblur(img, 8); sd = np.sqrt(np.maximum(C.boxblur(img * img, 8) - b * b, 0))
    return float(sd[m].mean())

def region_stats(img, m):
    r = ridge(img)
    return dict(mean=float(img[m].mean()), sd=float(img[m].std()), lc=lc(img, m),
                vein=float(np.percentile(r[m], 95) / max(img[m].std(), 1e-6)))

def regions(D, pose):
    s, upper, lower, P, A = analyse(D, pose, prefix='T224')
    inner = C.boxblur((A >= 250).astype(float), 4) >= 0.999
    return {'DOME': upper & inner, 'LOWER': lower & inner}, s

def main(D):
    print('== HEIGHT PAIR (production at both heights; region statistics) ==')
    for pose in ('H0', 'H1'):
        regs, s = regions(D, pose)
        P = C.lum(C.load(os.path.join(D, f'T224_P0_{pose}.png')))
        od = od_from_L(os.path.join(D, f'T224_L0_{pose}.png'))
        for name, m in regs.items():
            if m.sum() < 2000: print(f'  {pose} {name}: too small'); continue
            st = region_stats(P, m)
            print(f'  {pose} {name} ({m.sum()} px): lum {st["mean"]:.2f} | sd {st["sd"]:.2f} | local contrast {st["lc"]:.3f} | '
                  f'vein {st["vein"]:.3f} | transmittance exp(-od) {np.exp(-od[m]).mean():.4f} | od median {np.median(od[m]):.2f} | '
                  f'shadowed (od>4) {100*(od[m]>4).mean():.1f}%')
    print('\n== RULE EFFECT, per pixel at one pose: production (P0) vs full reach (R1) ==')
    for pose in ('H0', 'H1', 'LOWSIDE', 'SIDE'):
        regs, s = regions(D, pose)
        imgs = {a: C.lum(C.load(os.path.join(D, f'T224_{a}_{pose}.png'))) for a in ('P0', 'R1', 'P0B', 'R1B')}
        for name, m in regs.items():
            if m.sum() < 2000: continue
            line = f'  {pose} {name} ({m.sum()} px):'
            for a in ('P0', 'R1'):
                st = region_stats(imgs[a], m)
                line += f' {a} lum {st["mean"]:.2f} lc {st["lc"]:.3f} vein {st["vein"]:.3f} |'
            d = imgs['R1'] - imgs['P0']; fl = np.abs(imgs['P0B'] - imgs['P0']); fr = np.abs(imgs['R1B'] - imgs['R1'])
            line += (f' R1-P0 mean {d[m].mean():+.2f}, |d| {np.abs(d[m]).mean():.3f}, >2 lvl {100*(np.abs(d[m])>2).mean():.1f}%'
                     f' | floors P0B {fl[m].mean():.3f} R1B {fr[m].mean():.3f}')
            print(line)
        if pose in ('H0', 'H1'):
            od0 = od_from_L(os.path.join(D, f'T224_L0_{pose}.png')); od1 = od_from_L(os.path.join(D, f'T224_L1_{pose}.png'))
            for name, m in regs.items():
                if m.sum() < 2000: continue
                print(f'    {pose} {name} light path: od median {np.median(od0[m]):.2f} -> {np.median(od1[m]):.2f} | '
                      f'shadowed(od>4) {100*(od0[m]>4).mean():.1f}% -> {100*(od1[m]>4).mean():.1f}% | '
                      f'transmittance {np.exp(-od0[m]).mean():.4f} -> {np.exp(-od1[m]).mean():.4f}')

def costs(log):
    print('\n== COST (capture-pin gpuMs, cloud pass) ==')
    rows = {}
    for line in open(log, encoding='utf-8', errors='replace'):
        m = re.search(r'T098_CAPTURE_CONTROLS shot=T224_(\w+?)_(H0|H1|LOWSIDE|SIDE) .*gpuMs=([0-9.]+).*lightReach=(\d).*leanFinal=(\w+)', line)
        if m: rows.setdefault(m.group(2), {}).setdefault(m.group(1), (float(m.group(3)), m.group(4), m.group(5)))
    for pose, arms in rows.items():
        p = [arms[a][0] for a in ('P0', 'P0B') if a in arms]; r = [arms[a][0] for a in ('R1', 'R1B') if a in arms]
        print(f'  {pose}: P0/P0B {p} R1/R1B {r} | mean delta {np.mean(r)-np.mean(p):+.3f} ms | '
              f'repeat spread P {abs(p[0]-p[-1]):.3f} R {abs(r[0]-r[-1]):.3f} | lean {[arms[a][2] for a in arms]}')

if __name__ == '__main__':
    main(sys.argv[1]); costs(sys.argv[2])
