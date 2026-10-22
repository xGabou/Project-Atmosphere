"""T260.5 gallery and checks: frozen D under flat (A) and controlled neutral volumetric (B) lighting at identical cameras,
per-sample diagnostics (density, direct sun, ambient, sun optical depth), and the identity / contamination / traversal /
timing checks. Usage: python -I t2605_gallery.py <native dir> <runClient out log> [t2604 native dir] [out dir]"""
import html, json, re, shutil, sys
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

here = Path(__file__).parent
native, logp = Path(sys.argv[1]), Path(sys.argv[2])
prev = Path(sys.argv[3]) if len(sys.argv) > 3 and sys.argv[3] != '-' else None
out = here / (sys.argv[4] if len(sys.argv) > 4 else 'gallery')
for d in ('native', 'pairs', 'diag', 'ref'): (out / d).mkdir(parents=True, exist_ok=True)
SEEDS = ['20261015', '20262741', '20264467', '20265193', '20266607', '20268011']
VIEWS = ['side', 'opposite_side', 'front', 'back', 'above', 'below', 'low_angle', 'close', 'crosswind_left', 'crosswind_right',
         'iso_side', 'iso_opposite_side', 'iso_front', 'iso_back', 'iso_above', 'iso_low_angle', 'z4_side', 'z4_front', 'z4_above']
DIAG_VIEWS = ['side', 'front', 'back', 'above', 'below', 'crosswind_left', 'iso_side', 'z4_side']
DIAG = [('t2605_flat', 'A flat'), ('t2605_density', 'density'), ('t2605_od', 'sun optical depth'),
        ('t2605_direct', 'direct sun'), ('t2605_ambient', 'ambient'), ('t2605_vol', 'B volumetric (final)')]
D_SIG = json.loads((here / 'frozen' / 't2605-start-freeze.json').read_text())['dSignatures']
BG = np.array([16, 21, 29], np.float32)

for f in native.glob('T260_t2605_*.png'): shutil.copyfile(f, out / 'native' / f.name)

# ---- log: timings, parity, hashes, traversal, signatures
kv = re.compile(r'(\w+)=(\{[^}]*\}|\[[^\]]*\]|Bounds\[[^\]]*\]|\S+)')
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
    elif tag == 'T260_LIGHT': s.update(baseSignature=d['baseSignature'], light={k: d[k] for k in ('grid', 'spacing', 'computed', 'texels', 'bakeMs')})
    elif tag == 'T260_HASH': s.update(rgba=d['rgba'], alpha=d['alpha'], occupied=int(d['occupied']), lighting=int(d['lighting']))
shots.pop('?', None)


def name(t, seed, v): return f'T260_{t}__{seed}__{v}'


def img(t, seed, v):
    p = out / 'native' / f'{name(t, seed, v)}__raw.png'
    return np.asarray(Image.open(p).convert('RGBA'), np.float32) if p.exists() else None


def on_sky(a):
    """Premultiplied cloud target over the dark gallery background."""
    rgb = a[..., :3] + BG * (1 - a[..., 3:] / 255)
    return Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8))


def label(im, text, col=(230, 230, 230)):
    ImageDraw.Draw(im).text((6, 4), text, fill=col)
    return im


checks = dict(alphaFlatVsVol=[], decomposition=[], probes=[], traversal=[], signatures=[], t2604Identity=[], cameras=[], bakeVsReference=[])
attrib = []
for seed in SEEDS:
    for v in VIEWS:
        A, B = img('t2605_flat', seed, v), img('t2605_vol', seed, v)
        if A is None or B is None: continue
        sa, sb = shots.get(name('t2605_flat', seed, v), {}), shots.get(name('t2605_vol', seed, v), {})
        checks['alphaFlatVsVol'].append(dict(shot=f'{seed}/{v}', png8MaxAlphaDiff=int(np.abs(A[..., 3] - B[..., 3]).max()),
                                             floatAlphaHashEqual=sa.get('alpha') is not None and sa.get('alpha') == sb.get('alpha')))
        checks['cameras'].append(dict(shot=f'{seed}/{v}', identical=sa.get('camera') == sb.get('camera') and sa.get('aim') == sb.get('aim')))
        if prev is not None and not v.startswith(('iso_', 'z4_')):
            q = prev / f'T260_t2604_d__{seed}__{v}__raw.png'
            if q.exists():
                P = np.asarray(Image.open(q).convert('RGBA'), np.float32)
                checks['t2604Identity'].append(dict(shot=f'{seed}/{v}', maxDiff=int(np.abs(P - A).max()) if P.shape == A.shape else -1))
        h, w = A.shape[:2]
        im = Image.new('RGB', (w * 2 + 6, h + 22), tuple(int(c) for c in BG))
        im.paste(on_sky(A), (0, 22)); im.paste(on_sky(B), (w + 6, 22))
        d = ImageDraw.Draw(im)
        d.text((6, 5), f"A flat neutral  {seed} {v}  renderer p95 {sa.get('p95', float('nan')):.2f} ms", fill=(150, 200, 255))
        d.text((w + 12, 5), f"B neutral volumetric  renderer p95 {sb.get('p95', float('nan')):.2f} ms  alpha identical: "
               f"{'yes' if checks['alphaFlatVsVol'][-1]['floatAlphaHashEqual'] else 'NO'}", fill=(140, 230, 140))
        im.save(out / 'pairs' / f'pair-{seed}-{v}.jpg', quality=90)
    for v in DIAG_VIEWS:
        tiles = [(lab, img(t, seed, v)) for t, lab in DIAG]
        if any(x is None for _, x in tiles): continue
        Dn, Dd, Da, Dv = (dict(tiles)[k] for k in ('density', 'direct sun', 'ambient', 'B volumetric (final)'))
        # Linear decomposition: final = direct + ambient per sample, so the integrated images add up (8-bit rounding).
        dec = np.abs(Dd[..., :3] + Da[..., :3] - Dv[..., :3])
        checks['decomposition'].append(dict(shot=f'{seed}/{v}', maxAbs=int(dec.max()), p999=float(np.percentile(dec, 99.9))))
        # Attribution: interior structure (eroded silhouette mask) of each straight-colour image, as the standard deviation
        # of the image (coarse = blur sigma 6 px; fine = residual of a sigma 2 px blur), relative to its own mean.
        a = Dv[..., 3]
        mask = np.asarray(Image.fromarray((a > 250).astype(np.uint8) * 255).filter(ImageFilter.MinFilter(25))) > 0
        row = dict(shot=f'{seed}/{v}', maskPixels=int(mask.sum()))
        for lab, x in tiles:
            lum = (x[..., :3].mean(axis=2) / np.maximum(x[..., 3], 1) * 255)
            L = Image.fromarray(np.clip(lum, 0, 255).astype(np.uint8))
            c = np.asarray(L.filter(ImageFilter.GaussianBlur(6)), np.float32)
            f2 = lum - np.asarray(L.filter(ImageFilter.GaussianBlur(2)), np.float32)
            if mask.sum() > 500:
                m = max(lum[mask].mean(), 1)
                row[lab] = dict(mean=round(float(lum[mask].mean()), 2), coarseCV=round(float(c[mask].std() / m), 4),
                                fineCV=round(float(f2[mask].std() / m), 4))
        attrib.append(row)
        h, w = Dv.shape[:2]
        tw, th = w // 2, h // 2
        im = Image.new('RGB', (tw * 3 + 8, (th + 4) * 2), tuple(int(c) for c in BG))
        for k, (lab, x) in enumerate(tiles):
            im.paste(label(on_sky(x).resize((tw, th)), f'{lab}  {seed} {v}'), ((k % 3) * (tw + 4), (k // 3) * (th + 4)))
        im.save(out / 'diag' / f'diag-{seed}-{v}.jpg', quality=90)
    for v in ('side', 'below', 'iso_side', 'z4_side'):
        R, B = img('t2605_ref', seed, v), img('t2605_vol', seed, v)
        if R is None or B is None: continue
        m = R[..., 3] > 128
        df = (B[..., :3] - R[..., :3]).mean(axis=2)
        checks['bakeVsReference'].append(dict(shot=f'{seed}/{v}', meanAbs=round(float(np.abs(df[m]).mean()), 2), bias=round(float(df[m].mean()), 2),
                                              p99=float(np.percentile(np.abs(df[m]), 99)), refMean=round(float(R[..., :3].mean(axis=2)[m].mean()), 1),
                                              alphaEqual=bool(np.array_equal(R[..., 3], B[..., 3]))))
        h, w = R.shape[:2]
        im = Image.new('RGB', (w * 3 + 12, h + 22), tuple(int(c) for c in BG))
        im.paste(on_sky(R), (0, 22)); im.paste(on_sky(B), (w + 6, 22))
        e = np.clip(np.abs(df) * 8, 0, 255).astype(np.uint8); im.paste(Image.fromarray(e).convert('RGB'), (2 * w + 12, 22))
        d = ImageDraw.Draw(im)
        d.text((6, 5), f'reference (per-sample marches)  {seed} {v}', fill=(255, 200, 140))
        d.text((w + 12, 5), 'B baked (budget path)', fill=(140, 230, 140))
        d.text((2 * w + 18, 5), f"|baked - reference| x8, mean {checks['bakeVsReference'][-1]['meanAbs']}/255", fill=(220, 220, 220))
        im.save(out / 'ref' / f'ref-{seed}-{v}.jpg', quality=90)
    for v in ('side', 'below'):
        c, p = shots.get(name('t2605_vol', seed, v), {}), shots.get(name('t2605_vol_probe', seed, v), {})
        if p: checks['probes'].append(dict(shot=f'vol {seed}/{v}', rgbaHashEqual=c.get('rgba') == p.get('rgba'), control=c.get('rgba'), probe=p.get('rgba')))
    c, p = shots.get(name('t2605_flat', seed, 'side'), {}), shots.get(name('t2605_flat_probe', seed, 'side'), {})
    if p: checks['probes'].append(dict(shot=f'flat {seed}/side', rgbaHashEqual=c.get('rgba') == p.get('rgba'), control=c.get('rgba'), probe=p.get('rgba')))
for t in trav: checks['traversal'].append(dict(shot=t['shot'], differingChannels=int(t['differingChannels']), bitIdentical=t['bitIdentical'] == 'true'))
for s in shots.values():
    m = re.match(r'T260_t2605_\w+?__(\d+)__', s['shot'])
    # Whole-system shots carry D's frozen signature; isolated-primary shots carry the primary-only bake (checked for
    # equality across every lighting mode of a seed below).
    if m and 'signature' in s and '__iso_' not in s['shot']:
        checks['signatures'].append(dict(shot=s['shot'], match=s.get('baseSignature', s['signature']) == D_SIG[m.group(1)]))
iso = {}
for s in shots.values():
    m = re.match(r'T260_t2605_\w+?__(\d+)__iso_', s['shot'])
    if m and 'signature' in s: iso.setdefault(m.group(1), set()).add(s.get('baseSignature', s['signature']))
checks['isoSignatures'] = {k: sorted(v) for k, v in iso.items()}


def timing(kind):
    v = [s for s in shots.values() if s['shot'].startswith(f'T260_{kind}__') and 'p95' in s and 'dev_' not in s['shot']]
    return dict(n=len(v), p50max=max((s['p50'] for s in v), default=None), p95max=max((s['p95'] for s in v), default=None),
                p95median=float(np.median([s['p95'] for s in v])) if v else None,
                worst=max(v, key=lambda s: s['p95'])['shot'] if v else None)


summary = {
    'timing': {k: timing(k) for k in ('t2605_flat', 't2605_vol', 't2605_density', 't2605_direct', 't2605_ambient', 't2605_od', 't2605_ref')},
    'bakeVsReference': dict(meanAbsMax=max((c['meanAbs'] for c in checks['bakeVsReference']), default=None),
                            meanAbsMedian=float(np.median([c['meanAbs'] for c in checks['bakeVsReference']])) if checks['bakeVsReference'] else None,
                            biasRange=[min((c['bias'] for c in checks['bakeVsReference']), default=None), max((c['bias'] for c in checks['bakeVsReference']), default=None)]),
    'bakes': sorted({f"{s['light']['grid']} computed={s['light']['computed']} texels={s['light']['texels']}" for s in shots.values() if 'light' in s}),
    'bakeMsMax': max((float(s['light']['bakeMs']) for s in shots.values() if 'light' in s), default=None),
    'isoSignatureSingle': all(len(v) == 1 for v in checks['isoSignatures'].values()),
    'alphaIdentical': f"{sum(c['floatAlphaHashEqual'] for c in checks['alphaFlatVsVol'])}/{len(checks['alphaFlatVsVol'])} float hash, "
                      f"max 8-bit alpha diff {max((c['png8MaxAlphaDiff'] for c in checks['alphaFlatVsVol']), default=None)}",
    'camerasIdentical': f"{sum(c['identical'] for c in checks['cameras'])}/{len(checks['cameras'])}",
    'decompositionMaxAbs': max((c['maxAbs'] for c in checks['decomposition']), default=None),
    'probesBitIdentical': f"{sum(c['rgbaHashEqual'] for c in checks['probes'])}/{len(checks['probes'])}",
    'traversalBitIdentical': f"{sum(c['bitIdentical'] for c in checks['traversal'])}/{len(checks['traversal'])}",
    'signaturesMatchD': f"{sum(c['match'] for c in checks['signatures'])}/{len(checks['signatures'])}",
    # Cross-session: the fixture origin follows the session's suite fixture (a few blocks), so 8-bit rounding may move by 1.
    't2604FlatVsT2604D': f"max 8-bit diff {max((c['maxDiff'] for c in checks['t2604Identity']), default=None)} over {len(checks['t2604Identity'])} views",
    'parityMax': max((s['parity'] for s in shots.values() if 'parity' in s), default=None),
    'uploadMismatchesMax': max((s['upload'] for s in shots.values() if 'upload' in s), default=None),
    'rejected': rejected,
}
(out / 't2605-checks.json').write_text(json.dumps(dict(summary=summary, checks=checks, attribution=attrib), indent=1), encoding='utf-8')
(here / 't2605-native.json').write_text(json.dumps(dict(shots=list(shots.values())), indent=1), encoding='utf-8')

esc = html.escape
css = ("<style>body{font-family:Segoe UI,sans-serif;background:#10151d;color:#dde;margin:16px;max-width:1900px}a{color:#8cf}"
       "table{border-collapse:collapse;margin:8px 0}td,th{border:1px solid #334;padding:3px 7px;font-size:13px}"
       ".row{display:flex;gap:8px;flex-wrap:wrap}.row figure{margin:0}.row img{height:240px;width:auto}figcaption{font-size:12px;color:#aab}"
       ".note{color:#aab;font-size:13px;max-width:1200px}</style>")
P = [f'<!doctype html><meta charset=utf-8><title>T260.5 neutral lighting</title>{css}',
     '<h1>T260.5 &mdash; controlled neutral volumetric lighting (frozen candidate D)</h1>',
     "<p class=note>Every pair shares one frozen geometry and one camera (the T260.4 cameras). Left (A): flat neutral light. Right (B): "
     "neutral volumetric light &mdash; white sun at 35&deg; elevation from the side camera's left (from -x, +z), single scattering with "
     "an isotropic phase, volumetric self-shadowing marched through the fixture density, white sky and grey ground ambient whose "
     "visibility is marched through the same density, one fixed exposure, no tone curve, no world data. Diagnostic sheets: A flat, local "
     "density, sun optical depth (log), direct sun only, ambient only, B final (= direct + ambient). The light inputs are baked per frozen "
     "system on the CPU through the same density (10-block lattice) and compared with a per-sample reference march of the identical "
     "estimator. Report: "
     "<a href='../../t260-neutral-lighting.md'>t260-neutral-lighting.md</a>.</p>",
     '<h2>Checks</h2><table>' + ''.join(f'<tr><td>{esc(k)}</td><td>{esc(json.dumps(v))}</td></tr>' for k, v in summary.items()) + '</table>']
for seed in SEEDS:
    P.append(f'<h2>Seed {seed}</h2><h3>A flat | B volumetric</h3><div class=row>')
    for v in VIEWS:
        n = f'pairs/pair-{seed}-{v}.jpg'
        if (out / n).exists(): P.append(f"<figure><a href='{n}'><img src='{n}'></a><figcaption>{esc(v)}</figcaption></figure>")
    P.append('</div><h3>Diagnostics</h3><div class=row>')
    for v in DIAG_VIEWS:
        n = f'diag/diag-{seed}-{v}.jpg'
        if (out / n).exists(): P.append(f"<figure><a href='{n}'><img src='{n}'></a><figcaption>{esc(v)}</figcaption></figure>")
    P.append('</div><h3>Baked light vs per-sample reference</h3><div class=row>')
    for v in ('side', 'below', 'iso_side', 'z4_side'):
        n = f'ref/ref-{seed}-{v}.jpg'
        if (out / n).exists(): P.append(f"<figure><a href='{n}'><img src='{n}'></a><figcaption>{esc(v)}</figcaption></figure>")
    P.append('</div>')
P.append('<h2>Native timing</h2><table><tr><th>shot</th><th>ray p50</th><th>ray p95</th><th>renderer p50</th><th>renderer p95</th><th>parity</th><th>signature</th></tr>')
for s in shots.values():
    P.append(f"<tr><td>{esc(s['shot'])}</td><td>{s.get('rayP50', '-')}</td><td>{s.get('rayP95', '-')}</td><td>{s.get('p50', '-')}</td>"
             f"<td>{s.get('p95', '-')}</td><td>{s.get('parity', '')}</td><td>{s.get('signature', '')}</td></tr>")
P.append('</table>')
(out / 'index.html').write_text('\n'.join(P), encoding='utf-8')
print(json.dumps(summary, indent=1))
