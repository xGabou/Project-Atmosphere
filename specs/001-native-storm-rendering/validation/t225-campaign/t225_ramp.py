# T225. Stationary rain ramp: four fixed cameras, MaxPrecipitation ramped
# 0 -> 0.9 -> 0 under each. hold_body = fixed scalar, no rain pass (noise floor);
# ramp_body = ramp, no rain pass (cloud body only); ramp_full = ramp with rain.
import sys, re
import numpy as np

PAT = re.compile(r'(\w+)=([-\w.]+)')
NUM = ('frame', 'maxPrecip', 'override', 'padding', 'rayFloorY', 'inRayVolume', 'inCloudGate', 't0', 't1',
       'lumaMean', 'alphaMean', 'coverage', 'silW', 'silH', 'dLumaMean', 'dLumaMax', 'lumaPop', 'dLuma2Mean',
       'dLuma2Max', 'dMean', 'dMax', 'flicker', 'cameraDensity', 'localPrecip', 'remotePrecip', 'naturalMax')
SPAN, SETTLE = 550, 40


def parse(path):
    arms = {}
    for line in open(path, encoding='utf-8', errors='replace'):
        if 'T152_FRAME' not in line:
            continue
        s = line[line.index('T152_FRAME'):]
        kv = dict(PAT.findall(s))
        row = {'seg': kv['segment'], 'arm': kv['arm'], 'path': kv.get('lightPath')}
        cam = re.search(r'cam=\(([-\d.]+),([-\d.]+),([-\d.]+)\)', s)
        row['y'] = float(cam.group(2))
        for f in NUM:
            v = kv.get(f)
            try:
                row[f] = float(v)
            except (TypeError, ValueError):
                row[f] = np.nan
        arms.setdefault(kv['arm'], {})[int(row['frame'])] = row
    return arms


def arr(A, frames, f):
    return np.array([A[i][f] for i in frames], dtype=float)


def main(path):
    arms = parse(path)
    print('arms', {a: len(v) for a, v in arms.items()})
    H, R, F, X = arms.get('hold_body'), arms.get('ramp_body'), arms.get('ramp_full'), arms.get('ramp_body_fixed')
    any_arm = R or H
    f0 = sorted(any_arm)[0]
    r0 = any_arm[f0]
    print(f"slab base {r0['rayFloorY'] + r0['padding']:.1f}, naturalMax {r0['naturalMax']}, "
          f"localPrecip {r0['localPrecip']}, remotePrecip {r0['remotePrecip']}")
    for p in range(4):
        frames = [i for i in range(p * SPAN + SETTLE + 1, (p + 1) * SPAN) if all(i in a for a in (H, R, F, X) if a)]
        seg = any_arm[frames[0]]['seg']
        print(f'\n===== {seg} (frames {frames[0]}-{frames[-1]}), cam y {any_arm[frames[0]]["y"]:.1f}, '
              f'camDensity {np.nanmax(arr(any_arm, frames, "cameraDensity")):.3f} =====')
        for name, A in (('hold_body', H), ('ramp_body', R), ('ramp_full', F), ('ramp_body_fixed', X)):
            if not A:
                continue
            L = arr(A, frames, 'lumaMean') * 255; Al = arr(A, frames, 'alphaMean'); C = arr(A, frames, 'coverage')
            ov = arr(A, frames, 'override'); iv = arr(A, frames, 'inRayVolume'); ic = arr(A, frames, 'inCloudGate')
            dL = np.abs(np.diff(L)); dA = np.abs(np.diff(Al)); dC = np.abs(np.diff(C))
            flips = [frames[k + 1] for k in range(len(frames) - 1) if iv[k] != iv[k + 1]]
            cflips = [frames[k + 1] for k in range(len(frames) - 1) if ic[k] != ic[k + 1]]
            print(f' {name}: override {np.nanmin(ov):.3f}..{np.nanmax(ov):.3f} | floor {np.nanmin(arr(A,frames,"rayFloorY")):.1f}..'
                  f'{np.nanmax(arr(A,frames,"rayFloorY")):.1f} | inRayVolume flips {flips} | inCloudGate flips {cflips} '
                  f'(gate on {int(np.nansum(ic))} frames) | lightPath {sorted(set(A[i]["path"] for i in frames))}')
            print(f'   luma {L.min():.2f}..{L.max():.2f} (range {L.max()-L.min():.2f}) | max 1-frame |dLumaMean| {dL.max():.3f} '
                  f'p99 {np.percentile(dL,99):.3f} | alpha {Al.min():.4f}..{Al.max():.4f} max 1-frame {dA.max():.5f} | '
                  f'coverage {C.min():.4f}..{C.max():.4f} max 1-frame {dC.max():.5f}')
            print(f'   per-pixel: dLumaMean mean {np.nanmean(arr(A,frames,"dLumaMean")):.5f} max {np.nanmax(arr(A,frames,"dLumaMean")):.5f} | '
                  f'lumaPop mean {np.nanmean(arr(A,frames,"lumaPop")):.5f} max {np.nanmax(arr(A,frames,"lumaPop")):.5f} | '
                  f'dLuma2Mean mean {np.nanmean(arr(A,frames,"dLuma2Mean")):.5f} max {np.nanmax(arr(A,frames,"dLuma2Mean")):.5f} | '
                  f'alpha dMean max {np.nanmax(arr(A,frames,"dMean")):.5f} flicker max {np.nanmax(arr(A,frames,"flicker")):.5f}')
            for fl in flips:
                k = frames.index(fl)
                win = range(max(1, k - 3), min(len(frames), k + 4))
                print(f'   flip @ {fl} (override {ov[k]:.3f}, floor {A[fl]["rayFloorY"]:.1f}, cam y {A[fl]["y"]:.1f}): '
                      f'd lumaMean around flip {[round(float(L[j]-L[j-1]),3) for j in win]}, '
                      f'dLumaMean {[A[frames[j]]["dLumaMean"] for j in win]}, lumaPop {[A[frames[j]]["lumaPop"] for j in win]}')
            if name != 'hold_body':
                up = ov[: len(ov) // 2]; dn = ov[len(ov) // 2:]
                Lu = L[: len(L) // 2]; Ld = L[len(L) // 2:]
                pts = []
                for target in (0.0, 0.1, 0.2, 0.3, 0.45, 0.6, 0.75, 0.9):
                    iu = int(np.argmin(np.abs(up - target))); idn = int(np.argmin(np.abs(dn - target)))
                    pts.append(f'{target:.2f}:{Lu[iu]:.2f}/{Ld[idn]:.2f}')
                print(f'   luma vs override (up/down): {"  ".join(pts)}')


if __name__ == '__main__':
    main(sys.argv[1])
