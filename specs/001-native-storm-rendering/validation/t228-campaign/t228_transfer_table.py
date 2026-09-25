# T228 Task 2. The production lighting transfer against light-path od, ported
# line for line from evaluateLightingComponents (cloud_atmosphere_volume.fsh),
# with the uniforms the T228 baseline logged (T228_LIGHTING). Components:
# direct (scatter octaves + silver), ambient (height gradient x retention),
# the T217 deep-scatter fill (camera-gated: 0 for an exterior camera), the
# radiance before tone mapping, and the 8-bit result after it.
import sys, re, math
import numpy as np

PI = math.pi


def hg(c, g):
    g2 = g * g
    return (1 - g2) / (4 * PI * max(1 + g2 - 2 * g * c, 1e-4) ** 1.5)


def dual(c):
    return (1 - 0.72) * hg(c, -0.18) + 0.72 * hg(c, 0.62)


def smooth(a, b, x):
    t = min(max((x - a) / (b - a), 0.0), 1.0)
    return t * t * (3 - 2 * t)


def lum(v):
    return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2]


def transfer(od, U, dens=0.55, h01=0.6, cos=0.0, dist01=0.3, localStorm=0.36, dark=0.74, rain=0.0,
             camDensity=0.0, octaves=3, body=None):
    a, b, scatter, w = 1.0, 1.0, 0.0, 0.0
    for o in range(octaves):
        phase = 0.0795775 + (dual(cos) - 0.0795775) * a
        scatter += b * phase * math.exp(-od * a); w += b
        a *= 0.42; b *= 0.52
    scatter /= w
    powder = 1 - math.exp(-dens * 24)
    powderTerm = 1 + (min(powder * 1.35, 1) - 1) * (min(max(cos * 0.5 + 0.5, 0), 1) * 0.72)
    LC = np.array(U['lightColor']); AT = np.array(U['ambientTop']); AB = np.array(U['ambientBottom'])
    sunTint = np.array([1.0, 1.0, 1.0])
    combined = min(max(max(U['stormDarkening'] * 0.58, localStorm * 0.72), 0), 1)
    under = min(max((0.18 + dark * 0.82) * (1 - min(max(h01, 0), 1)) ** 0.70 * (0.46 + dens * 0.72), 0), 1)
    T = math.exp(-od)
    sun = LC * sunTint * scatter * powderTerm * (4 * PI)
    sun *= 1 + (0.76 - 1) * combined
    sun *= 1 + ((0.70 + (0.82 - 0.70) * combined) - 1) * under
    sun *= 1 + (0.80 - 1) * rain
    amb = AB + (AT - AB) * h01; sky = amb.copy()
    amb = amb * (1 + (0.68 - 1) * combined)
    amb = amb * (1 + ((0.58 + (0.74 - 0.58) * combined) - 1) * under)
    retention = 0.74 + (0.58 - 0.74) * dark
    amb = amb * (retention + (1 - retention) * T)
    amb = amb * (1 + (0.68 - 1) * rain)
    if body is not None:
        amb = amb * body(od)
    fwd = max(min(cos, 1), 0); silverPhase = fwd ** 12
    edge = smooth(0.008, 0.09, dens) * (1 - smooth(0.22, 0.66, dens))
    silver = silverPhase * math.sqrt(T) * edge * (1 + (0.26 - 1) * combined) * (1 - rain * 0.78)
    msOn = 1 - math.exp(-max(od - 2.0, 0) * 0.35)
    gate = smooth(0.05, 0.16, camDensity)
    ms = (sky + LC * sunTint * 0.12) * (0.46 * msOn * gate) * (1 + (0.78 - 1) * combined) * (1 + (0.72 - 1) * rain)
    amb = amb + ms / 0.86
    direct = sun + LC * sunTint * silver * 0.48
    rad = direct + amb * 0.86
    rad = np.maximum(rad, 0)
    out = 1 - np.exp(-rad * 1.30)
    return dict(direct=lum(direct), ambient=lum(amb * 0.86), ms=lum(ms), radiance=lum(rad), out8=255 * lum(out))


def parse_lighting(log):
    for line in open(log, encoding='utf-8', errors='replace'):
        if 'T228_LIGHTING' in line and 'shot=T228_P0_' in line:
            vec = lambda k: [float(x) for x in re.search(k + r'=\(([^)]*)\)', line).group(1).split(',')]
            sc = lambda k: float(re.search(k + r'=([-\d.]+)', line).group(1))
            noon = dict(lightDir=vec('lightDir'), lightColor=vec('lightColor'), ambientTop=vec('ambientTop'),
                        ambientBottom=vec('ambientBottom'), stormDarkening=sc('stormDarkening'))
            return noon, line
    return None, None


if __name__ == '__main__':
    U, line = parse_lighting(sys.argv[1])
    print('uniforms:', line[line.index('lightDir'):].strip() if line else 'none')
    print('od | direct | ambient | deep-scatter(exterior) | radiance | 8-bit | d(8bit)/d(od)')
    prev = None
    for od in (0, 1, 2, 4, 8, 16, 24, 32, 40):
        r = transfer(od, U)
        r1 = transfer(od + 0.5, U)
        slope = (r1['out8'] - r['out8']) / 0.5
        print(f"{od:3d} | {r['direct']:.4f} | {r['ambient']:.4f} | {r['ms']:.4f} | {r['radiance']:.4f} | "
              f"{r['out8']:6.2f} | {slope:+.3f}")
    print('inside-cloud camera (deep-scatter gate open):')
    for od in (4, 8, 16, 40):
        r = transfer(od, U, camDensity=0.2)
        print(f"{od:3d} | ms {r['ms']:.4f} | 8-bit {r['out8']:6.2f}")
