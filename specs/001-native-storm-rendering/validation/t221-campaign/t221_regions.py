# T221. Split the storm at its neck into the upper dome and the lower mass,
# then measure cell statistics and light-path optical depth per region, plus
# the structural quantities of Task 9. Masks come from the composite's ALPHA
# view of the production frame (identical frame via the temporal-pair aux).
import sys, os
import numpy as np
import t221_cells as C

def comps(mask):
    h, w = mask.shape; seen = np.zeros_like(mask); sizes = []
    for sy, sx in zip(*np.nonzero(mask)):
        if seen[sy, sx]: continue
        st = [(sy, sx)]; seen[sy, sx] = True; n = 0
        while st:
            y, x = st.pop(); n += 1
            for dy, dx in ((1,0),(-1,0),(0,1),(0,-1)):
                ny, nx = y+dy, x+dx
                if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and not seen[ny, nx]:
                    seen[ny, nx] = True; st.append((ny, nx))
        sizes.append(n)
    return np.array(sorted(sizes, reverse=True))

def analyse(D, pose, has_alpha=True):
    P = C.lum(C.load(os.path.join(D, f'T221_P0_{pose}.png')))
    A = C.lum(C.load(os.path.join(D, f'T221_P0_{pose}_ALPHA.png')))
    rgb = C.load(os.path.join(D, f'T221_P0_{pose}_ALPHA.png'))
    skyish = (rgb[..., 2] - rgb[..., 0]) > 40          # discarded texels show sky
    cloud = (~skyish) & (A > 0.5 * 255 * 0.10)           # alpha >= ~0.10
    h, w = cloud.shape
    widths = cloud.sum(axis=1)
    rows = np.nonzero(widths > 0)[0]
    top, bot = rows.min(), rows.max()
    # the neck: narrowest row strictly between the two largest row-width maxima
    span = widths[top:bot+1].astype(float)
    sm = np.convolve(span, np.ones(9)/9, mode='same')
    upper_peak = top + int(np.argmax(sm[:len(sm)//2]))
    lower_peak = top + len(sm)//2 + int(np.argmax(sm[len(sm)//2:]))
    seg = sm[upper_peak-top:lower_peak-top+1]
    neck = upper_peak + int(np.argmin(seg))
    out = dict(neck_row=neck, neck_px=int(widths[neck]),
               upper_max=int(widths[upper_peak]), lower_max=int(widths[lower_peak]))
    lower = cloud.copy(); lower[:neck+1] = False
    upper = cloud.copy(); upper[neck:] = False
    # connected components of the lower mass at alpha>=0.5 (solid structure)
    solid = (~skyish) & (A >= 0.5 * 255)
    lsolid = solid.copy(); lsolid[:neck+1] = False
    cs = comps(lsolid[::2, ::2])
    out['lower_components'] = int((cs >= 10).sum())
    out['lower_largest_frac'] = float(cs[0] / max(cs.sum(), 1)) if cs.size else 0.0
    # occupancy of the lower mass inside its own bounding box
    ly, lx = np.nonzero(lower)
    bbox = lower[ly.min():ly.max()+1, lx.min():lx.max()+1]
    out['lower_bbox_occupancy'] = float(bbox.mean())
    # holes: sky pixels enclosed inside the lower mass's convex-ish row extent
    holes = 0
    for y in range(ly.min(), ly.max()+1):
        xs = np.nonzero(lower[y])[0]
        if xs.size: holes += int((~lower[y, xs.min():xs.max()+1]).sum())
    out['lower_hole_frac'] = holes / max(lower.sum() + holes, 1)
    return out, upper, lower, P, A

if __name__ == '__main__':
    D = sys.argv[1]
    for pose in sys.argv[2:]:
        s, upper, lower, P, A = analyse(D, pose)
        print(f'== {pose} ==')
        print('  structure:', ' '.join(f'{k}={v:.3f}' if isinstance(v, float) else f'{k}={v}' for k, v in s.items()))
        Lp = os.path.join(D, f'T221_L_{pose}.png')
        inner = C.boxblur((A >= 250).astype(float), 4) >= 0.999
        gy, gx = np.gradient(P); g = np.hypot(gx, gy)
        od = None
        if os.path.exists(Lp):
            od = -2.0 * np.log(np.clip(C.load(Lp).mean(axis=2) / 255.0, 1e-3, 1.0))
        for name, reg in (('upper dome', upper), ('lower mass', lower)):
            m = reg & inner
            if m.sum() < 500:
                print(f'  {name}: interior too small ({m.sum()} px)'); continue
            thr = np.percentile(g[m], 90)
            e = g[m] ** 2
            spars = e[g[m] >= thr].sum() / max(e.sum(), 1e-9)
            line = (f'  {name}: interior {m.sum()} px | gradE {g[m].mean():.3f} | flat '
                    f'{100*(g[m]<0.6).mean():.1f}% | sparsity {spars:.3f} | lum sd {P[m].std():.2f}')
            if od is not None:
                q = np.percentile(od[m], [10, 50, 90])
                mid = ((od[m] >= 1) & (od[m] < 4)).mean()
                line += f' | od p10/50/90 {q[0]:.2f}/{q[1]:.2f}/{q[2]:.2f} | od in 1-4: {100*mid:.1f}%'
            print(line)
