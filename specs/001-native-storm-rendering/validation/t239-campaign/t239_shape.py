# T236 silhouette metrics from neutral (mode 1) captures: cloud = grey, sky blue (b >= 250).
import sys, os, json
import numpy as np
PREFIX = os.environ.get('SHAPE_PREFIX', 'T239')
from PIL import Image


def mask(path):
    a = np.asarray(Image.open(path).convert('RGB')).astype(float)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    return (b < 225) & (r > 100) & (np.abs(g - r) < 30)


def comps(m):
    m = m[::2, ::2].copy(); seen = np.zeros_like(m); sizes = []
    H, W = m.shape
    for y0, x0 in zip(*np.nonzero(m)):
        if seen[y0, x0]: continue
        stack = [(y0, x0)]; seen[y0, x0] = True; s = 0
        while stack:
            y, x = stack.pop(); s += 1
            for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                yy, xx = y + dy, x + dx
                if 0 <= yy < H and 0 <= xx < W and m[yy, xx] and not seen[yy, xx]:
                    seen[yy, xx] = True; stack.append((yy, xx))
        sizes.append(s)
    sizes = sorted(sizes, reverse=True)
    return dict(components=len([s for s in sizes if s >= 10]),
                largest_frac=float(sizes[0] / sum(sizes)) if sizes else 0.0)


def perimeter(m):
    e = m & ~(np.roll(m, 1, 0) & np.roll(m, -1, 0) & np.roll(m, 1, 1) & np.roll(m, -1, 1))
    return int(e.sum())


def silhouette(m):
    rows = np.nonzero(m.sum(axis=1) > 0)[0]
    if len(rows) < 20:
        return None
    top, bot = int(rows.min()), int(rows.max())
    clipped_top = top == 0
    w = m.sum(axis=1).astype(float)
    sm = np.convolve(w, np.ones(9) / 9, mode='same')
    anvil = top + int(np.argmax(sm[top:bot + 1]))
    lim = top + int(0.95 * (bot - top))
    neck = anvil + int(np.argmin(sm[anvil:lim + 1])) if lim > anvil else anvil
    lower = neck + int(np.argmax(sm[neck:bot + 1]))
    aw = sm[anvil]
    # top edge of the anvil: first cloud row per column over the anvil's span
    xs = np.nonzero(m[anvil])[0]
    x0, x1 = int(xs.min()), int(xs.max())
    tops = []
    for x in range(x0, x1 + 1):
        col = np.nonzero(m[top:anvil + 1, x])[0]
        tops.append(top + col.min() if len(col) else anvil)
    tops = np.array(tops, float)
    peak = tops.min()
    rise = anvil - peak
    flat = float((tops <= peak + 0.05 * max(rise, 1)).mean())      # share of the anvil width within 5% of the crest
    xx = (np.arange(len(tops)) - (len(tops) - 1) / 2) / max((len(tops) - 1) / 2, 1)
    curv = float(np.polyfit(xx, tops, 2)[0] / max(aw, 1))           # >0: domed (edges lower than centre)
    # symmetry: mirror the silhouette about its centroid column
    cx = int(round(np.nonzero(m)[1].mean()))
    half = min(cx, m.shape[1] - cx - 1)
    L = m[:, cx - half:cx][:, ::-1]; R = m[:, cx + 1:cx + 1 + half]
    sym = float((L & R).sum() / max((L | R).sum(), 1))
    # T237: anvil rows (top .. neck) vs lower rows (neck .. bottom): horizontal
    # centroid offset, and the anvil's area right vs left of the lower centroid.
    ys, xs = np.nonzero(m)
    up = ys < neck; lo = ys > neck
    lower_cx = xs[lo].mean() if lo.any() else xs.mean()
    anvil_cx = xs[up].mean() if up.any() else xs.mean()
    right = float(((xs > lower_cx) & up).sum()); left = float(((xs < lower_cx) & up).sum())
    return dict(clipped_top=clipped_top, height=bot - top,
                anvil_offset_over_width=float((anvil_cx - lower_cx) / aw),
                anvil_right_over_left=right / max(left, 1.0),
                tower_rows=int(neck - anvil),
                anvil_w=float(aw), neck_w=float(sm[neck]), lower_w=float(sm[lower]),
                neck_over_anvil=float(sm[neck] / aw), lower_over_anvil=float(sm[lower] / aw),
                anvil_rise_over_width=float(rise / aw), top_flat_share=flat, top_curvature=curv,
                mirror_iou=sym, rows=dict(anvil=anvil - top, neck=neck - top, lower=lower - top))


def ellipse_err(m):
    ys, xs = np.nonzero(m)
    c = np.cov(np.vstack([xs, ys])); ev = np.linalg.eigvalsh(c)
    a, b = 2 * np.sqrt(ev[1]), 2 * np.sqrt(ev[0])
    return float(abs(m.sum() - np.pi * a * b) / m.sum()), float(b / a)


def run(D, arm, poses):
    out = {}
    for p in poses:
        f = os.path.join(D, f'{PREFIX}_{arm}_{p}.png')
        if not os.path.exists(f):
            continue
        m = mask(f)
        if m.sum() < 500:
            out[p] = dict(area=int(m.sum())); continue
        rec = dict(area=int(m.sum()), perimeter=perimeter(m))
        rec['perim_over_sqrt_area'] = rec['perimeter'] / np.sqrt(rec['area'])
        rec.update(comps(m))
        if p == 'ABOVE':
            rec['ellipse_err'], rec['minor_over_major'] = ellipse_err(m)
        else:
            rec['sil'] = silhouette(m)
        out[p] = rec
    return out


def show(r):
    for p, v in r.items():
        if 'perimeter' not in v:
            print(f'{p:12s} area {v["area"]} (no cloud)'); continue
        line = f'{p:12s} area {v["area"]:7d} P/sqrtA {v["perim_over_sqrt_area"]:5.1f} comps {v["components"]:3d} largest {v["largest_frac"]:.2f}'
        if 'ellipse_err' in v:
            line += f' ellipseErr {v["ellipse_err"]:.3f} minor/major {v["minor_over_major"]:.2f}'
        s = v.get('sil')
        if s:
            line += (f' | neck/anvil {s["neck_over_anvil"]:.2f} lower/anvil {s["lower_over_anvil"]:.2f}'
                     f' rise/W {s["anvil_rise_over_width"]:.2f} topFlat {s["top_flat_share"]:.2f}'
                     f' topCurv {s["top_curvature"]:+.3f} mirrorIoU {s["mirror_iou"]:.2f}'
                     f' anvilOff {s["anvil_offset_over_width"]:+.2f} R/L {s["anvil_right_over_left"]:.2f}'
                     + (' CLIPPED' if s['clipped_top'] else ''))
        print(line)


if __name__ == '__main__':
    D, arm = sys.argv[1], sys.argv[2]
    poses = ["FAR", "FAR_OPP", "FAR_LAT", "FAR_LAT_OPP", "SIDE", "SIDE_OPP", "LATERAL", "ABOVE",
             "LOWSIDE", "NEAREDGE", "TOWERLOW"]
    r = run(D, arm, poses)
    if len(sys.argv) > 3:
        json.dump(r, open(sys.argv[3], 'w'), indent=1)
    show(r)


def roles(path):
    """T238. Mode-17 role masks: anvil red, tower green, core blue, base grey."""
    a = np.asarray(Image.open(path).convert('RGB')).astype(int)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    red = (r > 200) & (g < 80) & (b < 80)
    tower = (g > 150) & (r < 150) & (b < 170)
    grey = (abs(r - g) < 12) & (abs(g - b) < 12) & (r < 140) & (r > 50)
    return red, tower, grey


def role_metrics(path):
    red, tower, grey = roles(path)
    cloud = red | tower | grey
    rows = np.nonzero(cloud.sum(1) > 3)[0]
    out = dict(tower_px_share=float(tower.sum() / max(cloud.sum(), 1)))
    if len(rows) == 0:
        return out
    h = rows.max() - rows.min()
    tr = np.nonzero(tower.sum(1) > 3)[0]
    out['tower_rows_share'] = float(len(tr) / max(h, 1))
    if len(tr) >= 8:
        t0, t1 = tr.min(), tr.max()
        w = tower.sum(1).astype(float)
        sm = np.convolve(w, np.ones(5) / 5, mode='same')
        # 25% / 50% / 75% of the visible tower height, measured from its BOTTOM.
        for q in (25, 50, 75):
            y = int(round(t1 - (t1 - t0) * q / 100.0))
            out[f'tower_w{q}'] = float(sm[y])
        out['tower_w_cv'] = float(np.std(sm[t0:t1 + 1]) / max(np.mean(sm[t0:t1 + 1]), 1))
    a = np.asarray(Image.open(path).convert('RGB')).astype(int)
    blue = (a[..., 2] > 200) & (a[..., 0] < 80) & (a[..., 1] < 80)
    out['transition_px_share'] = float(blue.sum() / max((cloud | blue).sum(), 1))
    gr = np.nonzero(grey.sum(1) > 3)[0]
    if len(gr) >= 4:
        out['base_h_over_w'] = float((gr.max() - gr.min()) / max(grey.sum(1).max(), 1))
    ar = np.nonzero(red.sum(1) > 3)[0]
    if len(ar) >= 4:
        aw = red.sum(1).max()
        out['anvil_thick_over_w'] = float((ar.max() - ar.min()) / max(aw, 1))
        out['anvil_over_tower_w'] = float(aw / max(out.get('tower_w50', 0.0), 1.0))
    return out


if __name__ == '__main__' and os.environ.get('ROLE_ARMS'):
    D = sys.argv[1]
    for arm in os.environ['ROLE_ARMS'].split(','):
        for p in ["SIDE", "LOWSIDE", "FAR", "LATERAL", "FAR_LAT", "ABOVE", "TOWERLOW", "NEAREDGE"]:
            f = os.path.join(D, f'{PREFIX}_{arm}_{p}.png')
            if os.path.exists(f):
                m = role_metrics(f)
                print(arm, p, ' '.join(f'{k}={v:.3f}' for k, v in m.items()))
