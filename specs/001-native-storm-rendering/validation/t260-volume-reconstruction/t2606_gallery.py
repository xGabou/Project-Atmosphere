"""T260.6 gallery and checks: frozen D and the reconstruction arms A (merge), B (growth), C (interior density) and ABC
(combined), all derived from D's recipe, under the frozen T260.5 flat and neutral volumetric lighting at the T260.5
cameras; diagnostics; identity / camera / traversal / contamination / parity / timing checks.
Usage: python -I t2606_gallery.py <native dir> <runClient out log> [t2605 native dir | -] [out dir] [retake dir] [retake log]
Retakes: diagnostic frames rejected by the cross-mode alpha check are re-shot in a later session together with that
session's volumetric control; the retake replaces the frame and its alpha is checked against its own session."""
import html, json, re, shutil, sys
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

here = Path(__file__).parent
native, logp = Path(sys.argv[1]), Path(sys.argv[2])
prev = Path(sys.argv[3]) if len(sys.argv) > 3 and sys.argv[3] != '-' else None
out = here / (sys.argv[4] if len(sys.argv) > 4 else 'gallery')
for d in ('native', 'arms', 'flat', 'diag'): (out / d).mkdir(parents=True, exist_ok=True)
SEEDS = ['20261015', '20262741', '20264467', '20265193', '20266607', '20268011']
ARMS = ['D', 'A', 'B', 'C', 'ABC']
ARM_LABEL = {'D': 'D baseline', 'A': 'A merge only', 'B': 'B growth only', 'C': 'C density only', 'ABC': 'ABC combined'}
VIEWS = ['side', 'opposite_side', 'front', 'back', 'above', 'below', 'low_angle', 'close', 'crosswind_left', 'crosswind_right',
         'iso_side', 'iso_opposite_side', 'iso_front', 'iso_back', 'iso_above', 'iso_low_angle', 'z4_side', 'z4_front', 'z4_above']
MODES = [('flat', 'flat'), ('density', 'density'), ('od', 'sun optical depth'), ('direct', 'direct sun'), ('ambient', 'ambient'), ('vol', 'neutral volumetric')]
D_SIG = json.loads((here / 'frozen' / 't2606-start-freeze.json').read_text())['dSignatures']
BG = np.array([16, 21, 29], np.float32)

for f in native.glob('T260_t2606_*__raw.png'): shutil.copyfile(f, out / 'native' / f.name)  # raw cloud targets only (screenshots stay in run/)

kv = re.compile(r'(\w+)=(\{[^}]*\}|\[[^\]]*\]|Bounds\[[^\]]*\]|\([^)]*\)|\S+)')
shots, trav, rejected, bakes = {}, [], [], {}
for line in logp.read_text(encoding='utf-8', errors='replace').splitlines():
    m = re.search(r'(T260_\w+|T258_UPLOAD_VERIFY) (.*)$', line)
    if not m: continue
    tag, d = m.group(1), dict(kv.findall(m.group(2)))
    if tag == 'T260_CAPTURE_REJECTED': rejected.append(m.group(2)); continue
    if tag == 'T260_TRAVERSAL_BITS': trav.append(d)
    if tag == 'T260_BAKE': bakes[d.get('key', '?')] = m.group(2); continue
    s = shots.setdefault(d.get('shot', '?'), {'shot': d.get('shot', '?')})
    if tag == 'T260_GPU': s.update(rayP50=float(d['medianMs']), rayP95=float(d['p95Ms']))
    elif tag == 'T260_PIPELINE': s.update(p50=float(d['medianMs']), p95=float(d['p95Ms']))
    elif tag == 'T260_PARITY': s.update(parity=float(d['maxAbsError']))
    elif tag == 'T258_UPLOAD_VERIFY': s.update(upload=int(d['mismatched']))
    elif tag == 'T260_POSE': s.update(signature=d['signature'], camera=d['camera'], aim=d['aim'], textureBytes=int(d.get('textureBytes', 0)))
    elif tag == 'T260_LIGHT': s.update(baseSignature=d['baseSignature'], light={k: d[k] for k in ('grid', 'computed', 'texels', 'bakeMs') if k in d})
    elif tag == 'T260_HASH': s.update(rgba=d['rgba'], alpha=d['alpha'], occupied=int(d['occupied']), lighting=int(d['lighting']))
shots.pop('?', None)


def parse(path):
    out_ = {}
    for line in Path(path).read_text(encoding='utf-8', errors='replace').splitlines():
        m = re.search(r'(T260_\w+) (.*)$', line)
        if not m: continue
        d = dict(kv.findall(m.group(2)))
        s = out_.setdefault(d.get('shot', '?'), {'shot': d.get('shot', '?')})
        if m.group(1) == 'T260_PIPELINE': s.update(p50=float(d['medianMs']), p95=float(d['p95Ms']))
        elif m.group(1) == 'T260_HASH': s.update(rgba=d['rgba'], alpha=d['alpha'])
        elif m.group(1) == 'T260_POSE': s.update(signature=d['signature'], camera=d['camera'], aim=d['aim'])
    return out_


RETAKES = ['T260_t2606_ABC_ambient__20261015__side', 'T260_t2606_ABC_direct__20261015__z4_side']
retake_checks = []
if len(sys.argv) > 6:
    rdir, rshots = Path(sys.argv[5]), parse(sys.argv[6])
    for n in RETAKES:
        if n not in rshots: continue
        ctl = n.replace('_ambient__', '_vol__').replace('_direct__', '_vol__')
        retake_checks.append(dict(shot=n, firstSessionAlpha=shots.get(n, {}).get('alpha'), retakeAlpha=rshots[n].get('alpha'),
                                  retakeSessionVolAlpha=rshots.get(ctl, {}).get('alpha'), alphaEqualsOwnSessionVol=rshots[n].get('alpha') == rshots.get(ctl, {}).get('alpha')))
        shots[n] = dict(rshots[n], retake=True, retakeVolAlpha=rshots.get(ctl, {}).get('alpha'))
        shutil.copyfile(rdir / f'{n}__raw.png', out / 'native' / f'{n}__raw.png')


def name(arm, mode, seed, v): return f'T260_t2606_{arm}_{mode}__{seed}__{v}'


def img(arm, mode, seed, v):
    p = out / 'native' / f'{name(arm, mode, seed, v)}__raw.png'
    return np.asarray(Image.open(p).convert('RGBA'), np.float32) if p.exists() else None


def on_sky(a):
    rgb = a[..., :3] + BG * (1 - a[..., 3:] / 255)
    return Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8))


def label(im, text, col=(230, 230, 230)):
    ImageDraw.Draw(im).text((6, 4), text, fill=col)
    return im


def sheet(tiles, path, cols):
    """tiles: [(caption, rgba array)], half-size tiles in a grid."""
    h, w = tiles[0][1].shape[:2]
    tw, th = w // 2, h // 2
    rows = (len(tiles) + cols - 1) // cols
    im = Image.new('RGB', (cols * (tw + 4), rows * (th + 4)), tuple(int(c) for c in BG))
    for k, (cap, x) in enumerate(tiles):
        im.paste(label(on_sky(x).resize((tw, th), Image.LANCZOS), cap), ((k % cols) * (tw + 4), (k // cols) * (th + 4)))
    im.save(path, quality=90)


checks = dict(alphaFlatVsVol=[], camerasAcrossArms=[], decomposition=[], probes=[], traversal=[], dSignatures=[], dVsT2605=[])
attrib = []
for seed in SEEDS:
    for v in VIEWS:
        vols = [(a, img(a, 'vol', seed, v)) for a in ARMS]
        if all(x is not None for _, x in vols):
            sheet([(f"{ARM_LABEL[a]}  {seed} {v}  p95 {shots.get(name(a, 'vol', seed, v), {}).get('p95', float('nan')):.2f} ms", x) for a, x in vols],
                  out / 'arms' / f'arms-{seed}-{v}.jpg', 5)
        flats = [(a, img(a, 'flat', seed, v)) for a in ARMS]
        flats = [(a, x) for a, x in flats if x is not None]
        if len(flats) >= 2: sheet([(f"{ARM_LABEL[a]} flat  {seed} {v}", x) for a, x in flats], out / 'flat' / f'flat-{seed}-{v}.jpg', len(flats))
        # One camera for every arm and lighting mode of a seed/view.
        cams = {(s.get('camera'), s.get('aim')) for a in ARMS for mo, _ in MODES for s in [shots.get(name(a, mo, seed, v))] if s and 'camera' in s}
        if cams: checks['camerasAcrossArms'].append(dict(shot=f'{seed}/{v}', distinctCameras=len(cams)))
        for a in ARMS:
            # Lighting never changes alpha: every lighting mode of one arm/seed/view must carry the same float alpha hash.
            # A retaken frame is compared with the volumetric control of its own session.
            hs = {mo: (shots[name(a, 'vol', seed, v)]['alpha'] if shots[name(a, mo, seed, v)].get('alpha') == shots[name(a, mo, seed, v)].get('retakeVolAlpha') else shots[name(a, mo, seed, v)]['alpha'])
                  if shots[name(a, mo, seed, v)].get('retake') else shots[name(a, mo, seed, v)]['alpha']
                  for mo, _ in MODES if 'alpha' in shots.get(name(a, mo, seed, v), {})}
            if len(hs) > 1:
                ref = hs.get('vol', next(iter(hs.values())))
                checks.setdefault('alphaAcrossModes', []).append(dict(shot=f'{a} {seed}/{v}', modes=len(hs), mismatched=sorted(m for m, h in hs.items() if h != ref)))
            A, B = img(a, 'flat', seed, v), img(a, 'vol', seed, v)
            if A is None or B is None: continue
            sa, sb = shots.get(name(a, 'flat', seed, v), {}), shots.get(name(a, 'vol', seed, v), {})
            checks['alphaFlatVsVol'].append(dict(shot=f'{a} {seed}/{v}', floatAlphaHashEqual=sa.get('alpha') is not None and sa.get('alpha') == sb.get('alpha'),
                                                 png8MaxAlphaDiff=int(np.abs(A[..., 3] - B[..., 3]).max())))
        if prev is not None:
            P, Dv = prev / f'T260_t2605_vol__{seed}__{v}__raw.png', img('D', 'vol', seed, v)
            if P.exists() and Dv is not None:
                Q = np.asarray(Image.open(P).convert('RGBA'), np.float32)
                checks['dVsT2605'].append(dict(shot=f'{seed}/{v}', maxDiff=int(np.abs(Q - Dv).max()) if Q.shape == Dv.shape else -1))
    for a in ARMS:
        for v in ('side', 'below', 'z4_side'):
            tiles = [(lab, img(a, mo, seed, v)) for mo, lab in MODES]
            if any(x is None for _, x in tiles): continue
            T = dict(tiles)
            dec = np.abs(T['direct sun'][..., :3] + T['ambient'][..., :3] - T['neutral volumetric'][..., :3])
            checks['decomposition'].append(dict(shot=f'{a} {seed}/{v}', maxAbs=int(dec.max())))
            alpha = T['neutral volumetric'][..., 3]
            mask = np.asarray(Image.fromarray((alpha > 250).astype(np.uint8) * 255).filter(ImageFilter.MinFilter(25))) > 0
            row = dict(arm=a, shot=f'{seed}/{v}', maskPixels=int(mask.sum()))
            for lab, x in tiles:
                lum = x[..., :3].mean(axis=2) / np.maximum(x[..., 3], 1) * 255
                L = Image.fromarray(np.clip(lum, 0, 255).astype(np.uint8))
                c = np.asarray(L.filter(ImageFilter.GaussianBlur(6)), np.float32)
                f2 = lum - np.asarray(L.filter(ImageFilter.GaussianBlur(2)), np.float32)
                if mask.sum() > 500:
                    m = max(lum[mask].mean(), 1)
                    row[lab] = dict(mean=round(float(lum[mask].mean()), 2), coarseCV=round(float(c[mask].std() / m), 4), fineCV=round(float(f2[mask].std() / m), 4))
            attrib.append(row)
            sheet([(f'{ARM_LABEL[a]} {lab}  {seed} {v}', x) for lab, x in tiles], out / 'diag' / f'diag-{a}-{seed}-{v}.jpg', 3)
    c, p = shots.get(name('ABC', 'vol', seed, 'side_ctl'), {}), shots.get(name('ABC', 'vol_probe', seed, 'side'), {})
    if p and c: checks['probes'].append(dict(shot=f'ABC vol {seed}/side', rgbaHashEqual=c.get('rgba') == p.get('rgba')))
    c, p = shots.get(name('ABC', 'flat', seed, 'side'), {}), shots.get(name('ABC', 'flat_probe', seed, 'side'), {})
    if p and c: checks['probes'].append(dict(shot=f'ABC flat {seed}/side', rgbaHashEqual=c.get('rgba') == p.get('rgba')))
for t in trav: checks['traversal'].append(dict(shot=t['shot'], differingChannels=int(t['differingChannels']), bitIdentical=t['bitIdentical'] == 'true'))
for s in shots.values():
    m = re.match(r'T260_t2606_D_\w+?__(\d+)__', s['shot'])
    if m and 'signature' in s and '__iso_' not in s['shot']:
        checks['dSignatures'].append(dict(shot=s['shot'], match=s.get('baseSignature', s['signature']) == D_SIG[m.group(1)]))
sigs = {}
for s in shots.values():
    m = re.match(r'T260_t2606_(\w+?)_[a-z]+(?:_probe)?__(\d+)__(iso_)?', s['shot'])
    if m and 'signature' in s: sigs.setdefault(f"{m.group(1)}/{m.group(2)}/{'iso' if m.group(3) else 'full'}", set()).add(s.get('baseSignature', s['signature']))
checks['geometrySignatures'] = {k: sorted(v) for k, v in sigs.items()}


def timing(arm, mode):
    v = [s for s in shots.values() if s['shot'].startswith(f'T260_t2606_{arm}_{mode}__') and 'p95' in s and 'attribution' not in s['shot']]
    return dict(n=len(v), p50max=max((s['p50'] for s in v), default=None), p95max=max((s['p95'] for s in v), default=None),
                p95median=float(np.median([s['p95'] for s in v])) if v else None, rayP50max=max((s.get('rayP50', 0) for s in v), default=None),
                rayP95max=max((s.get('rayP95', 0) for s in v), default=None), worst=max(v, key=lambda s: s['p95'])['shot'] if v else None)


summary = {
    'timing': {f'{a}_{mo}': timing(a, mo) for a in ARMS for mo in ('flat', 'vol', 'density', 'direct', 'ambient', 'od')},
    'geometrySignatureSingle': all(len(v) == 1 for v in checks['geometrySignatures'].values()),
    'dSignaturesMatchT2605': f"{sum(c['match'] for c in checks['dSignatures'])}/{len(checks['dSignatures'])}",
    'dVolVsT2605Vol': f"max 8-bit diff {max((c['maxDiff'] for c in checks['dVsT2605']), default=None)} over {len(checks['dVsT2605'])} views",
    'alphaFlatVsVol': f"{sum(c['floatAlphaHashEqual'] for c in checks['alphaFlatVsVol'])}/{len(checks['alphaFlatVsVol'])} float hash",
    'camerasAcrossArms': f"{sum(c['distinctCameras'] == 1 for c in checks['camerasAcrossArms'])}/{len(checks['camerasAcrossArms'])} views with one camera",
    'alphaAcrossModes': f"{sum(not c['mismatched'] for c in checks.get('alphaAcrossModes', []))}/{len(checks.get('alphaAcrossModes', []))} arm/seed/views; mismatched: "
                        + json.dumps([c for c in checks.get('alphaAcrossModes', []) if c['mismatched']]),
    'retakes': retake_checks,
    'decompositionMaxAbs': max((c['maxAbs'] for c in checks['decomposition']), default=None),
    'probesBitIdentical': f"{sum(c['rgbaHashEqual'] for c in checks['probes'])}/{len(checks['probes'])}",
    'traversalBitIdentical': f"{sum(c['bitIdentical'] for c in checks['traversal'])}/{len(checks['traversal'])}",
    'parityMax': max((s['parity'] for s in shots.values() if 'parity' in s), default=None),
    'uploadMismatchesMax': max((s['upload'] for s in shots.values() if 'upload' in s), default=None),
    'textureMBMax': {a: max((s['textureBytes'] / 1048576 for s in shots.values() if s['shot'].startswith(f'T260_t2606_{a}_') and 'textureBytes' in s), default=None) for a in ARMS},
    'lightBakeMsMax': max((float(s['light']['bakeMs']) for s in shots.values() if 'light' in s), default=None),
    'rejected': rejected,
}
(out / 't2606-checks.json').write_text(json.dumps(dict(summary=summary, checks=checks, attribution=attrib, bakes=bakes), indent=1), encoding='utf-8')
(here / 't2606-native.json').write_text(json.dumps(dict(shots=list(shots.values())), indent=1), encoding='utf-8')

esc = html.escape
css = ("<style>body{font-family:Segoe UI,sans-serif;background:#10151d;color:#dde;margin:16px;max-width:1900px}a{color:#8cf}"
       "table{border-collapse:collapse;margin:8px 0}td,th{border:1px solid #334;padding:3px 7px;font-size:13px}"
       ".row{display:flex;gap:8px;flex-wrap:wrap}.row figure{margin:0}.row img{width:100%;max-width:1880px;height:auto}figcaption{font-size:12px;color:#aab}"
       ".note{color:#aab;font-size:13px;max-width:1200px}</style>")
P = [f'<!doctype html><meta charset=utf-8><title>T260.6 volume reconstruction</title>{css}',
     '<h1>T260.6 &mdash; convective volume reconstruction (D, A, B, C, ABC)</h1>',
     "<p class=note>Every arm is derived from the frozen candidate D recipe and rendered under the frozen T260.5 lighting at the T260.5 "
     "cameras: D baseline; A merge only (hierarchical scale-relative blending); B growth only (asymmetric growth columns and a coherent "
     "growth-dome population); C interior density only; ABC combined. Each strip: the five arms under neutral volumetric light, one camera. "
     "Report: <a href='../../t260-convective-volume-reconstruction.md'>t260-convective-volume-reconstruction.md</a>.</p>",
     '<h2>Checks</h2><table>' + ''.join(f'<tr><td>{esc(k)}</td><td>{esc(json.dumps(v))}</td></tr>' for k, v in summary.items()) + '</table>']
for seed in SEEDS:
    P.append(f'<h2>Seed {seed}</h2><h3>Neutral volumetric: D | A | B | C | ABC</h3><div class=row>')
    for v in VIEWS:
        n = f'arms/arms-{seed}-{v}.jpg'
        if (out / n).exists(): P.append(f"<figure><a href='{n}'><img src='{n}' loading=lazy></a><figcaption>{esc(v)}</figcaption></figure>")
    P.append('</div><h3>Flat silhouettes</h3><div class=row>')
    for v in VIEWS:
        n = f'flat/flat-{seed}-{v}.jpg'
        if (out / n).exists(): P.append(f"<figure><a href='{n}'><img src='{n}' loading=lazy></a><figcaption>{esc(v)}</figcaption></figure>")
    P.append('</div><h3>Diagnostics (flat, density, sun optical depth, direct, ambient, final)</h3><div class=row>')
    for a in ARMS:
        for v in ('side', 'below', 'z4_side'):
            n = f'diag/diag-{a}-{seed}-{v}.jpg'
            if (out / n).exists(): P.append(f"<figure><a href='{n}'><img src='{n}' loading=lazy style='max-width:920px'></a><figcaption>{esc(a)} {esc(v)}</figcaption></figure>")
    P.append('</div>')
P.append('<h2>Native timing</h2><table><tr><th>shot</th><th>ray p50</th><th>ray p95</th><th>renderer p50</th><th>renderer p95</th><th>parity</th><th>signature</th></tr>')
for s in shots.values():
    P.append(f"<tr><td>{esc(s['shot'])}</td><td>{s.get('rayP50', '-')}</td><td>{s.get('rayP95', '-')}</td><td>{s.get('p50', '-')}</td>"
             f"<td>{s.get('p95', '-')}</td><td>{s.get('parity', '')}</td><td>{s.get('signature', '')}</td></tr>")
P.append('</table>')
(out / 'index.html').write_text('\n'.join(P), encoding='utf-8')
print(json.dumps(summary, indent=1))
