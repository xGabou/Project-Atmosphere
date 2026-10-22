"""T260.3 system-layout metrics, identical code on the T259 photographic calvus masks and on T260 CPU masks
(largest connected component, normalised to height; T259 keeps the largest component of every photograph too).
  aspect        W/H of the connected system (references cropped at a frame edge are excluded)
  summit_pos    main-summit offset from the silhouette centre / W (0 = centred, 0.5 = at an end; cropped excluded)
  shoulder      fraction of columns whose top lies in 0.25-0.70 H (connected middle-mass proportion)
  w50, w75      width at 0.50 / 0.75 H over the width at 0.25 H (how far broad growth extends up; cropped excluded)
  base_gap      fraction of the base span (bottom 12 % H) that is sky (gaps between attached cells reaching down;
                references only where the base is visible)
  + the T259 metrics (summits, notches, convexity deficit) for the same masks.
Usage: python -I t260l_layout_metrics.py <out json> <label=cpu gallery dir> [label=dir ...]"""
import json, statistics as st, sys
from pathlib import Path
import numpy as np
from PIL import Image
here = Path(__file__).parent
ref = here.parent / 't259-reference-morphology'
sys.path.insert(0, str(ref))
import t259_silhouette as sil

VIEWS = ['side', 'opposite_side', 'front', 'back', 'crosswind_left', 'crosswind_right', 'upwind', 'downwind']

def layout(m, cropped=False, base=True):
    m = sil.largest(m)
    m = sil.normalise(m)
    H, W = m.shape
    cols = m.any(0)
    top = np.where(cols, H - m.argmax(0), 0) / H  # top height per column (fraction of H)
    xs = np.nonzero(cols)[0]
    def width(f):
        row = m[int(round(H * (1 - f))) - 1]
        nz = np.nonzero(row)[0]
        return 0 if len(nz) == 0 else nz[-1] - nz[0] + 1
    w25 = max(1, width(.25))
    out = dict(shoulder=float(((top >= .25) & (top <= .70))[xs].mean()))
    if not cropped:
        out.update(aspect=W / H, summit_pos=abs(int(np.argmax(top)) - (xs[0] + xs[-1]) / 2) / W, w50=width(.5) / w25, w75=width(.75) / w25)
    if base:
        b = m[int(H * .88):].any(0)[xs[0]:xs[-1] + 1]
        out['base_gap'] = float(1 - b.mean())
    t = sil.metrics(m)
    out.update({k: t.get(k) for k in ('crown_summits', 'notches', 'convexity_deficit')})
    return out

def summary(rows):
    keys = sorted({k for r in rows for k in r if k not in ('seed', 'view', 'name')})
    return {k: dict(mean=round(st.mean(v), 3), min=round(min(v), 3), max=round(max(v), 3), n=len(v))
            for k in keys if (v := [r[k] for r in rows if r.get(k) is not None])}

res = {}
ann = json.loads((ref / 't259-reference-annotations.json').read_text(encoding='utf-8'))['references']
rows = []
for name, cfg in ann.items():
    if not name.startswith('Cumulonimbus_calvus'): continue
    m = np.asarray(Image.open(ref / 'masks' / f'{name}.png')) > 127
    rows.append(dict(name=name, **layout(m, cropped=cfg.get('truncated', 'none') != 'none', base=bool(cfg.get('base_visible')))))
res['T259 photographic calvus'] = dict(rows=rows, summary=summary(rows))
for arg in sys.argv[2:]:
    label, d = arg.split('=', 1)
    rows = []
    for sd in sorted(p for p in Path(d).iterdir() if p.is_dir()):
        for v in VIEWS:
            f = sd / 'masks' / f'{v}__mask.png'
            if f.exists(): rows.append(dict(seed=sd.name, view=v, **layout(np.asarray(Image.open(f)) > 127)))
    res[label] = dict(rows=rows, summary=summary(rows))
Path(sys.argv[1]).write_text(json.dumps(res, indent=1), encoding='utf-8')
keys = ['aspect', 'summit_pos', 'shoulder', 'w50', 'w75', 'base_gap', 'crown_summits', 'notches', 'convexity_deficit']
print(f"{'metric':18s}" + ''.join(f'{k[:22]:>24s}' for k in res))
for k in keys:
    print(f'{k:18s}' + ''.join(f"{(s := r['summary'].get(k)) and f'{s[chr(109)+chr(101)+chr(97)+chr(110)]:.3f} ({s[chr(110)]})' or '-':>24s}" for r in res.values()))
