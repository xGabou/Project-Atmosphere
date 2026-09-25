# The pop a base crossing would show: the switch is binary and frame-uniform, so
# the one-frame change at a crossing is R1 vs P0 at that pose (zero parallax).
import sys, os, numpy as np
import t221_cells as C
from t221_regions import analyse
D = sys.argv[1]
for pose in ('H0', 'H1', 'LOWSIDE', 'SIDE'):
    s, upper, lower, P, A = analyse(D, pose, prefix='T224')
    cov = A >= 250
    L = {a: C.lum(C.load(os.path.join(D, f'T224_{a}_{pose}.png'))) / 255.0 for a in ('P0', 'R1', 'P0B')}
    d = np.abs(L['R1'] - L['P0'])[cov]; f = np.abs(L['P0B'] - L['P0'])[cov]
    print(f'{pose}: covered {cov.sum()} px | switch dLumaMean {d.mean():.4f} max {d.max():.3f} pop(>0.010) {100*(d>0.010).mean():.1f}% '
          f'| repeat floor dLumaMean {f.mean():.5f} pop {100*(f>0.010).mean():.2f}% | signed {np.mean((L["R1"]-L["P0"])[cov])*255:+.2f} levels')
