"""T260.3 crown-repetition study on the isolated primary (CPU masks, native camera plan).
For every seed and the 8 horizontal views, the crown profile = top height per column above 0.70 H, resampled to
64 samples across the crown width (normalised). Cross-seed nearest distance: for each crown, the smallest mean
absolute difference to any crown of ANOTHER seed (mirror allowed); low = crowns repeat between storms.
Within-seed spread: mean distance between the views of one seed (low = the crown looks the same from every side).
Usage: python -I t260l_crown_study.py <out dir> <label=cpu gallery dir> [label=dir ...]"""
import json, sys, itertools, statistics as st
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
VIEWS = ['side', 'opposite_side', 'front', 'back', 'crosswind_left', 'crosswind_right', 'upwind', 'downwind']
out = Path(sys.argv[1]); out.mkdir(parents=True, exist_ok=True)

def crown(m):
    ys, xs = np.nonzero(m)
    m = m[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    H, W = m.shape
    cols = m.any(0)
    top = np.where(cols, (H - m.argmax(0)) / H, 0)
    sel = np.nonzero(top >= .70)[0]
    seg = top[sel[0]:sel[-1] + 1]
    seg = np.maximum(seg, .70)
    return np.interp(np.linspace(0, len(seg) - 1, 64), np.arange(len(seg)), seg), m

def dist(a, b): return min(np.abs(a - b).mean(), np.abs(a - b[::-1]).mean())

res, sheets = {}, []
for arg in sys.argv[2:]:
    label, d = arg.split('=', 1)
    prof, tiles = {}, []
    for sd in sorted(p for p in Path(d).iterdir() if p.is_dir()):
        for v in VIEWS:
            f = sd / 'masks' / f'iso_{v}__mask.png'
            if f.exists():
                c, m = crown(np.asarray(Image.open(f)) > 127)
                prof[(sd.name, v)] = c
                if v in ('side', 'front', 'crosswind_left'): tiles.append((f'{sd.name[-4:]} {v[:6]}', m))
    keys = list(prof)
    cross = [min(dist(prof[k], prof[j]) for j in keys if j[0] != k[0]) for k in keys]
    within = [st.mean(dist(prof[a], prof[b]) for a, b in itertools.combinations([k for k in keys if k[0] == s], 2)) for s in sorted({k[0] for k in keys})]
    res[label] = dict(crossSeedNearest=round(st.mean(cross), 4), crossSeedNearestMin=round(min(cross), 4), withinSeedSpread=round(st.mean(within), 4), crowns=len(keys))
    # Crown montage: upper 40 % of each isolated-primary silhouette, normalised height.
    H = 110
    ims = []
    for n, m in tiles:
        top = m[: int(m.shape[0] * .40)]
        im = Image.fromarray((top * 255).astype(np.uint8)).resize((max(1, int(top.shape[1] * H / top.shape[0])), H))
        ims.append((n, im))
    W = sum(i.width + 8 for _, i in ims[:9]) + 8
    sheet = Image.new('RGB', (max(W, 400), 2 * (H + 18) + 24 if len(ims) > 9 else H + 42), (16, 21, 29)); dr = ImageDraw.Draw(sheet)
    dr.text((6, 4), f'{label}: isolated-primary crowns (upper 40 %), 6 seeds x side/front/crosswind_left', fill=(255, 220, 120))
    x, y = 8, 22
    for i, (n, im) in enumerate(ims):
        if i == 9: x, y = 8, y + H + 18
        sheet.paste(Image.merge('RGB', [im] * 3), (x, y + 14)); dr.text((x, y), n, fill=(140, 200, 255)); x += im.width + 8
    sheet.save(out / f'crowns-{label}.png')
(out / 't260l-crown-study.json').write_text(json.dumps(res, indent=1), encoding='utf-8')
print(json.dumps(res, indent=1))
