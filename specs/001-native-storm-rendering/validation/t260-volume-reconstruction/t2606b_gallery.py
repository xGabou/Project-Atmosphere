"""T260.6 validation gaps: B vs ABC (with AB, BC: what A and C add beyond B) under the frozen T260.5 neutral volumetric
light at identical cameras, exposure and renderer settings (one native session, phase t2606b). Contact sheets, visible
difference measures, texture-uniformity statistics, correctness checks and timings.
Usage: python -I t2606b_gallery.py <native dir> <runClient out log> [out dir] [retake native dir] [retake log]
Retakes (frames rejected by the cross-mode alpha check, re-shot with their volumetric control in a later session) replace
the original frame and log entries; both retake shots come from one session, so their checks stay self-consistent."""
import html, json, re, shutil, sys
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

here = Path(__file__).parent
native, logp = Path(sys.argv[1]), Path(sys.argv[2])
out = here / (sys.argv[3] if len(sys.argv) > 3 else 'gallery-b')
for d in ('native', 'sheets', 'strips'): (out / d).mkdir(parents=True, exist_ok=True)
SEEDS = ['20261015', '20262741', '20264467', '20265193', '20266607', '20268011']
ARMS = ['D', 'B', 'AB', 'BC', 'ABC']
GROUPS = {
    'whole system': ['side', 'opposite_side', 'front', 'back', 'crosswind_left', 'crosswind_right', 'above', 'low_angle', 'below', 'close'],
    'isolated primary': ['iso_side', 'iso_opposite_side', 'iso_front', 'iso_back', 'iso_above', 'iso_low_angle'],
    'crown close-ups (4x)': ['z4_side', 'z4_front', 'z4_above'],
    'distance': ['far', 'gameplay'],
}
VIEWS = [v for g in GROUPS.values() for v in g]
MODES = ['flat', 'density', 'od', 'direct', 'ambient', 'vol']
BG = np.array([16, 21, 29], np.float32)
D_SIG = json.loads((here / 'frozen' / 't2606-start-freeze.json').read_text())['dSignatures']
AUDIT = {}
for line in (here / 'audit' / 't2606-audit-final.log').read_text().splitlines():
    d = dict(re.findall(r'(\w+)=(\S+)', line))
    if d.get('volume') == 'full': AUDIT[(d['seed'], d['arm'])] = d['signature']

for f in native.glob('T260_t2606_*__raw.png'): shutil.copyfile(f, out / 'native' / f.name)

kv = re.compile(r'(\w+)=(\{[^}]*\}|\[[^\]]*\]|Bounds\[[^\]]*\]|\([^)]*\)|\S+)')
shots, trav, rejected = {}, [], []
for line in logp.read_text(encoding='utf-8', errors='replace').splitlines():
    m = re.search(r'(T260_\w+|T258_UPLOAD_VERIFY) (.*)$', line)
    if not m: continue
    tag, d = m.group(1), dict(kv.findall(m.group(2)))
    if tag == 'T260_CAPTURE_REJECTED': rejected.append(m.group(2)); continue
    if tag == 'T260_TRAVERSAL_BITS': trav.append(d)
    s = shots.setdefault(d.get('shot', '?'), {'shot': d.get('shot', '?')})
    if tag == 'T260_GPU': s.update(rayP50=float(d['medianMs']), rayP95=float(d['p95Ms']))
    elif tag == 'T260_PIPELINE': s.update(p50=float(d['medianMs']), p95=float(d['p95Ms']))
    elif tag == 'T260_PARITY': s.update(parity=float(d['maxAbsError']))
    elif tag == 'T258_UPLOAD_VERIFY': s.update(upload=int(d['mismatched']))
    elif tag == 'T260_POSE': s.update(signature=d['signature'], camera=d['camera'], aim=d['aim'])
    elif tag == 'T260_LIGHT': s.update(baseSignature=d['baseSignature'])
    elif tag == 'T260_HASH': s.update(rgba=d['rgba'], alpha=d['alpha'])
shots.pop('?', None)
retakes = []
if len(sys.argv) > 5:
    rdir = Path(sys.argv[4])
    for line in Path(sys.argv[5]).read_text(encoding='utf-8', errors='replace').splitlines():
        m = re.search(r'(T260_\w+|T258_UPLOAD_VERIFY) (.*)$', line)
        if not m: continue
        tag, d = m.group(1), dict(kv.findall(m.group(2)))
        n = d.get('shot')
        if not n or not (rdir / f'{n}__raw.png').exists(): continue
        if n not in retakes:
            retakes.append(n)
            shots[n] = {'shot': n, 'retake': True}
            shutil.copyfile(rdir / f'{n}__raw.png', out / 'native' / f'{n}__raw.png')
        s = shots[n]
        if tag == 'T260_PIPELINE': s.update(p50=float(d['medianMs']), p95=float(d['p95Ms']))
        elif tag == 'T260_GPU': s.update(rayP50=float(d['medianMs']), rayP95=float(d['p95Ms']))
        elif tag == 'T260_POSE': s.update(signature=d['signature'], camera=d['camera'], aim=d['aim'])
        elif tag == 'T260_LIGHT': s.update(baseSignature=d['baseSignature'])
        elif tag == 'T260_HASH': s.update(rgba=d['rgba'], alpha=d['alpha'])


def name(arm, mode, seed, v): return f'T260_t2606_{arm}_{mode}__{seed}__{v}'


def img(arm, mode, seed, v):
    p = out / 'native' / f'{name(arm, mode, seed, v)}__raw.png'
    return np.asarray(Image.open(p).convert('RGBA'), np.float32) if p.exists() else None


def on_sky(a):
    return Image.fromarray(np.clip(a[..., :3] + BG * (1 - a[..., 3:] / 255), 0, 255).astype(np.uint8))


def lum(a): return a[..., :3].mean(axis=2)


def sheet(tiles, path, cols, scale=2):
    h, w = tiles[0][1].shape[:2]
    tw, th = w // scale, h // scale
    rows = (len(tiles) + cols - 1) // cols
    im = Image.new('RGB', (cols * (tw + 4), rows * (th + 4)), tuple(int(c) for c in BG))
    for k, (cap, x) in enumerate(tiles):
        t = on_sky(x).resize((tw, th), Image.LANCZOS)
        ImageDraw.Draw(t).text((6, 4), cap, fill=(235, 235, 235))
        im.paste(t, ((k % cols) * (tw + 4), (k // cols) * (th + 4)))
    im.save(path, quality=90)


def texture_stats(a):
    """Interior texture of a lit frame: bandpass (sigma 1.5 - sigma 6 px) detail energy in 32 px tiles of the eroded
    silhouette; tile-energy CV (low = uniform texture) and mean detail amplitude relative to the mean luminance."""
    al = a[..., 3]
    mask = np.asarray(Image.fromarray((al > 250).astype(np.uint8) * 255).filter(ImageFilter.MinFilter(15))) > 0
    if mask.sum() < 4000: return None
    L = Image.fromarray(np.clip(lum(a), 0, 255).astype(np.uint8))
    band = np.asarray(L.filter(ImageFilter.GaussianBlur(1.5)), np.float32) - np.asarray(L.filter(ImageFilter.GaussianBlur(6)), np.float32)
    e = []
    h, w = mask.shape
    for y in range(0, h - 32, 32):
        for x in range(0, w - 32, 32):
            m = mask[y:y + 32, x:x + 32]
            if m.mean() > .9: e.append(float((band[y:y + 32, x:x + 32][m] ** 2).mean()))
    if len(e) < 6: return None
    e = np.array(e)
    return dict(tiles=len(e), tileEnergyCV=round(float(e.std() / max(e.mean(), 1e-6)), 3),
                detailAmplitude=round(float(np.sqrt(e.mean()) / max(lum(a)[mask].mean(), 1)), 4))


checks = dict(cameras=[], alphaAcrossModes=[], decomposition=[], probes=[], traversal=[], signatures=[])
diffs, texture = [], []
for seed in SEEDS:
    for v in VIEWS:
        ims = {a: img(a, 'vol', seed, v) for a in ARMS}
        if any(x is None for x in ims.values()): continue
        cams = {(shots[name(a, mo, seed, v)].get('camera'), shots[name(a, mo, seed, v)].get('aim')) for a in ARMS for mo in MODES if name(a, mo, seed, v) in shots and 'camera' in shots[name(a, mo, seed, v)]}
        checks['cameras'].append(dict(shot=f'{seed}/{v}', distinct=len(cams)))
        p95 = lambda a: shots.get(name(a, 'vol', seed, v), {}).get('p95', float('nan'))
        sheet([(f'B growth only  {seed} {v}  p95 {p95("B"):.2f} ms', ims['B']), (f'ABC combined  {seed} {v}  p95 {p95("ABC"):.2f} ms', ims['ABC'])],
              out / 'sheets' / f'b-vs-abc-{seed}-{v}.jpg', 2, 1 if v == 'gameplay' else 2)
        sheet([(f'{a}  {seed} {v}  p95 {p95(a):.2f} ms', ims[a]) for a in ARMS], out / 'strips' / f'arms-{seed}-{v}.jpg', 5, 3)
        union = (ims['B'][..., 3] > 8) | (ims['ABC'][..., 3] > 8)
        row = dict(seed=seed, view=v, pixels=int(union.sum()))
        for a in ('AB', 'BC', 'ABC'):
            dl = np.abs(lum(ims[a]) - lum(ims['B']))[union]
            row[a] = dict(meanAbs=round(float(dl.mean()), 2), p95=round(float(np.percentile(dl, 95)), 1), over8=round(float((dl > 8).mean()), 4),
                          over16=round(float((dl > 16).mean()), 4))
        dl = np.abs(lum(ims['B']) - lum(ims['D']))[union | (ims['D'][..., 3] > 8)]
        row['B_vs_D'] = dict(meanAbs=round(float(dl.mean()), 2), over8=round(float((dl > 8).mean()), 4))
        diffs.append(row)
        for a in ARMS:
            t = texture_stats(ims[a])
            if t: texture.append(dict(seed=seed, view=v, arm=a, **t))
        for a in ARMS:
            hs = {mo: shots[name(a, mo, seed, v)]['alpha'] for mo in MODES if 'alpha' in shots.get(name(a, mo, seed, v), {})}
            if len(hs) > 1: checks['alphaAcrossModes'].append(dict(shot=f'{a} {seed}/{v}', modes=len(hs), equal=len(set(hs.values())) == 1))
    for a in ('B', 'ABC'):
        for v in ('side', 'below', 'z4_side'):
            T = {mo: img(a, mo, seed, v) for mo in ('direct', 'ambient', 'vol')}
            if any(x is None for x in T.values()): continue
            checks['decomposition'].append(dict(shot=f'{a} {seed}/{v}', maxAbs=int(np.abs(T['direct'][..., :3] + T['ambient'][..., :3] - T['vol'][..., :3]).max())))
        c, p = shots.get(name(a, 'vol', seed, 'side_ctl'), {}), shots.get(name(a, 'vol_probe', seed, 'side'), {})
        if c and p: checks['probes'].append(dict(shot=f'{a} {seed}/side', rgbaEqual=c.get('rgba') == p.get('rgba')))
for t in trav: checks['traversal'].append(dict(shot=t['shot'], bitIdentical=t['bitIdentical'] == 'true', differingChannels=int(t['differingChannels'])))
for s in shots.values():
    m = re.match(r'T260_t2606_(\w+?)_(vol|flat|density|direct|ambient|od)(?:_probe)?__(\d+)__(\w+)', s['shot'])
    if not m or 'signature' not in s or m.group(4).startswith('iso_'): continue
    arm, seed = m.group(1), m.group(3)
    want = D_SIG[seed] if arm == 'D' else AUDIT.get((seed, arm))
    if want: checks['signatures'].append(dict(shot=s['shot'], match=s.get('baseSignature', s['signature']) == want))


def timing(arm, mode, views=None):
    v = [s for s in shots.values() if re.match(rf'T260_t2606_{arm}_{mode}__\d+__', s['shot']) and 'p95' in s and 'attribution' not in s['shot'] and '_ctl' not in s['shot']
         and (views is None or s['shot'].split('__')[2] in views)]
    return dict(n=len(v), p50max=max((s['p50'] for s in v), default=None), p95max=max((s['p95'] for s in v), default=None),
                p95median=float(np.median([s['p95'] for s in v])) if v else None, rayP95max=max((s.get('rayP95', 0) for s in v), default=None),
                worst=max(v, key=lambda s: s['p95'])['shot'] if v else None)


def agg_diff(arm, views):
    r = [d[arm] for d in diffs if d['view'] in views]
    return dict(n=len(r), meanAbs=round(float(np.median([x['meanAbs'] for x in r])), 2), over8=round(float(np.median([x['over8'] for x in r])), 4),
                over16=round(float(np.median([x['over16'] for x in r])), 4)) if r else None


def agg_tex(arm, views):
    r = [t for t in texture if t['arm'] == arm and t['view'] in views]
    return dict(n=len(r), tileEnergyCV=round(float(np.median([t['tileEnergyCV'] for t in r])), 3),
                detailAmplitude=round(float(np.median([t['detailAmplitude'] for t in r])), 4)) if r else None


summary = {
    'timing': {f'{a}_{mo}': timing(a, mo) for a in ARMS for mo in ('vol', 'flat', 'density', 'direct', 'ambient', 'od') if timing(a, mo)['n']},
    'visibleDifferenceVsB (median over views; luminance 0-255)': {g: {a: agg_diff(a, vs) for a in ('AB', 'BC', 'ABC')} for g, vs in GROUPS.items()},
    'textureUniformity (median; tileEnergyCV low = uniform)': {g: {a: agg_tex(a, vs) for a in ARMS} for g, vs in GROUPS.items()},
    'camerasOnePerView': f"{sum(c['distinct'] == 1 for c in checks['cameras'])}/{len(checks['cameras'])}",
    'alphaAcrossModes': f"{sum(c['equal'] for c in checks['alphaAcrossModes'])}/{len(checks['alphaAcrossModes'])}",
    'decompositionMaxAbs': max((c['maxAbs'] for c in checks['decomposition']), default=None),
    'probesBitIdentical': f"{sum(c['rgbaEqual'] for c in checks['probes'])}/{len(checks['probes'])}",
    'traversalBitIdentical': f"{sum(c['bitIdentical'] for c in checks['traversal'])}/{len(checks['traversal'])}",
    'signaturesMatchAuditedBakes': f"{sum(c['match'] for c in checks['signatures'])}/{len(checks['signatures'])}",
    'parityMax': max((s['parity'] for s in shots.values() if 'parity' in s), default=None),
    'uploadMismatchesMax': max((s['upload'] for s in shots.values() if 'upload' in s), default=None),
    'rejected': rejected,
    'retakes': retakes,
}
(out / 't2606b-checks.json').write_text(json.dumps(dict(summary=summary, checks=checks, differences=diffs, texture=texture), indent=1), encoding='utf-8')
(here / 't2606b-native.json').write_text(json.dumps(dict(shots=list(shots.values())), indent=1), encoding='utf-8')

esc = html.escape
css = ("<style>body{font-family:Segoe UI,sans-serif;background:#10151d;color:#dde;margin:16px;max-width:1900px}a{color:#8cf}"
       "table{border-collapse:collapse;margin:8px 0}td,th{border:1px solid #334;padding:3px 7px;font-size:13px}"
       ".row{display:flex;gap:8px;flex-wrap:wrap}.row figure{margin:0}.row img{width:100%;max-width:1880px;height:auto}figcaption{font-size:12px;color:#aab}"
       ".note{color:#aab;font-size:13px;max-width:1200px}</style>")
P = [f'<!doctype html><meta charset=utf-8><title>T260.6 B vs ABC</title>{css}', '<h1>T260.6 &mdash; B vs ABC (and AB, BC)</h1>',
     "<p class=note>One native session; identical seeds, cameras, exposure, neutral volumetric light (frozen T260.5) and renderer settings. "
     "Contact sheets: B (growth only) | ABC (combined). Strips: D | B | AB | BC | ABC. ABC is the audited bake (FULL-skip fix). "
     "Report: <a href='../../t260-convective-volume-reconstruction.md'>t260-convective-volume-reconstruction.md</a>.</p>",
     '<h2>Summary</h2><table>' + ''.join(f'<tr><td>{esc(k)}</td><td>{esc(json.dumps(v))}</td></tr>' for k, v in summary.items()) + '</table>']
for g, vs in GROUPS.items():
    P.append(f'<h2>{esc(g)}: B | ABC</h2>')
    for seed in SEEDS:
        P.append(f'<h3>{seed}</h3><div class=row>')
        for v in vs:
            n = f'sheets/b-vs-abc-{seed}-{v}.jpg'
            if (out / n).exists(): P.append(f"<figure><a href='{n}'><img src='{n}' loading=lazy style='max-width:1200px'></a><figcaption>{esc(v)} &middot; <a href='strips/arms-{seed}-{v}.jpg'>D | B | AB | BC | ABC</a></figcaption></figure>")
        P.append('</div>')
(out / 'index.html').write_text('\n'.join(P), encoding='utf-8')
print(json.dumps(summary, indent=1))
