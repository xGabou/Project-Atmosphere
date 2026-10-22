"""T260: the T259 silhouette metrics (identical code, t259_silhouette.metrics) on the T260 calvus-system masks, against
the T259 calvus references, B v3 and rejected C. Masks: CPU renders at the native camera plan with the photographic lens
(crosswind/side family, the views that match ground photographs). Also a side-by-side sheet at normalised height.
Usage: python -I t260_compare_t259.py <t260 cpu gallery dir (per-seed folders with masks/)> <out dir>"""
import json, statistics as st, sys
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
here = Path(__file__).parent
ref = here.parent / 't259-reference-morphology'
sys.path.insert(0, str(ref))
import t259_silhouette as sil

src, out = Path(sys.argv[1]), Path(sys.argv[2])
out.mkdir(parents=True, exist_ok=True)
VIEWS = ['side', 'opposite_side', 'front', 'back', 'crosswind_left', 'crosswind_right', 'upwind', 'downwind']
KEYS = ['aspect', 'max_width_height', 'crown_summits', 'notches', 'deepest_notch', 'top_flat_fraction', 'lean', 'convexity_deficit']
rows, shapes = [], []
for seed_dir in sorted(p for p in src.iterdir() if p.is_dir()):
    for v in VIEWS:
        for kind in ('', 'iso_'):
            f = seed_dir / 'masks' / f'{kind}{v}__mask.png'
            if not f.exists(): continue
            m = np.asarray(Image.open(f)) > 127
            if not m.any(): continue
            r = dict(source='T260 system' if kind == '' else 'T260 primary (isolated)', seed=seed_dir.name, view=v, **sil.metrics(m))
            rows.append(r)
            if kind == '' and v in ('side', 'front', 'crosswind_left'): shapes.append((f'{seed_dir.name[-4:]}/{v}', sil.normalise(m)))
summary = {}
t259 = json.loads((ref / 't259-summary.json').read_text(encoding='utf-8'))['calvus']
for src_name in ('T260 system', 'T260 primary (isolated)'):
    rs = [r for r in rows if r['source'] == src_name]
    summary[src_name] = {k: dict(mean=round(st.mean([r[k] for r in rs if r.get(k) is not None]), 4),
                                 min=round(min(r[k] for r in rs if r.get(k) is not None), 4), max=round(max(r[k] for r in rs if r.get(k) is not None), 4),
                                 n=len([1 for r in rs if r.get(k) is not None])) for k in KEYS if any(r.get(k) is not None for r in rs)}
for s in ('reference', 'B v3', 'C (rejected)'):
    summary['T259 ' + s] = {k: t259[s].get(k) for k in KEYS}
(out / 't260-vs-t259.json').write_text(json.dumps(dict(rows=rows, summary=summary), indent=1), encoding='utf-8')
print(f"{'metric':22s}" + ''.join(f'{s[:18]:>20s}' for s in summary))
for k in KEYS:
    line = f'{k:22s}'
    for s, d in summary.items():
        v = d.get(k)
        line += f"{(v['mean'] if v else float('nan')):20.3f}"
    print(line)
# Silhouette sheet: references (T259 masks) vs T260 systems, normalised to height 160.
H = 160
def tiles(items):
    return [(n, Image.fromarray((m * 255).astype(np.uint8)).resize((max(1, int(m.shape[1] * H / m.shape[0])), H))) for n, m in items]
refs = []
ann = json.loads((ref / 't259-reference-annotations.json').read_text(encoding='utf-8'))['references']
for name, cfg in ann.items():
    if name.startswith('Cumulonimbus_calvus') and (ref / 'masks' / f'{name}.png').exists():
        refs.append((name.replace('Cumulonimbus_calvus_', 'ref '), sil.normalise(np.asarray(Image.open(ref / 'masks' / f'{name}.png')) > 127)))
sets = [('T259 photographic calvus references (silhouette masks, height-normalised)', tiles(refs)), ('T260 calvus systems (CPU, native camera plan, photographic lens)', tiles(shapes))]
W = 1900
height = sum(30 + ((sum(t.width + 12 for _, t in ts) // W) + 1) * (H + 22) for _, ts in sets) + 10
sheet = Image.new('RGB', (W, height), (16, 21, 29)); d = ImageDraw.Draw(sheet); y = 6
for title, ts in sets:
    d.text((8, y), title, fill=(255, 220, 120)); y += 22; x = 8
    for n, t in ts:
        if x + t.width > W: x = 8; y += H + 22
        sheet.paste(Image.merge('RGB', [t, t, t]), (x, y + 14)); d.text((x, y), n, fill=(140, 200, 255)); x += t.width + 12
    y += H + 30
sheet.save(out / 't260-vs-t259-silhouettes.png')
print('sheet', out / 't260-vs-t259-silhouettes.png')
