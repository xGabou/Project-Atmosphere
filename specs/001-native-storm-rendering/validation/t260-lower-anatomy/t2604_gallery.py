"""T260.4 gallery: native frames of every seed at identical cameras (framed by C25 and D).
Rows per view: C25 under the T260.3 renderer (before), C25 under the fixed renderer, D under the fixed renderer (after).
Difference maps: renderer only (C25 legacy vs C25 fixed, RGB and alpha) and geometry only (C25 fixed vs D, alpha).
Usage: python -I t2604_gallery.py <native dir> <native json> <analysis dir> [out dir]"""
import html, json, shutil, sys
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw

here = Path(__file__).parent
native, njson, ana = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
out = here / (sys.argv[4] if len(sys.argv) > 4 else 'gallery')
for d in ('native', 'pairs', 'diff'): (out / d).mkdir(parents=True, exist_ok=True)
SEEDS = ['20261015', '20262741', '20264467', '20265193', '20266607', '20268011']
VIEWS = ['side', 'opposite_side', 'front', 'back', 'above', 'below', 'low_angle', 'close', 'crosswind_left', 'crosswind_right']
ARMS = [('t2604_c25_legacy', 'C25 / T260.3 renderer (before)', (255, 190, 120)),
        ('t2604_c25', 'C25 / fixed renderer', (150, 200, 255)),
        ('t2604_d', 'D / fixed renderer (after)', (140, 230, 140))]
for f in native.glob('T260_t2604_*.png'): shutil.copyfile(f, out / 'native' / f.name)
by = {s['shot']: s for s in json.loads(njson.read_text(encoding='utf-8'))['shots']} if njson.exists() else {}


def raw(arm, seed, view):
    p = out / 'native' / f'T260_{arm}__{seed}__{view}__raw.png'
    return Image.open(p).convert('RGBA') if p.exists() else None


def on_sky(im):
    """Native cloud target composited over the dark gallery background (premultiplied RGB as stored)."""
    bg = Image.new('RGB', im.size, (16, 21, 29))
    rgb = im.convert('RGB')
    a = im.split()[3]
    inv = a.point(lambda v: 255 - v)
    return ImageChops.add(ImageChops.multiply(bg, Image.merge('RGB', (inv, inv, inv))), rgb)


def amplified(a, b, gain, alpha_only=False):
    """|a - b| x gain, RGB channels (or alpha only) as a grey map."""
    if alpha_only:
        d = ImageChops.difference(a.split()[3], b.split()[3])
    else:
        d = ImageChops.difference(a.convert('RGB'), b.convert('RGB'))
        r, g, bl = d.split()
        d = ImageChops.lighter(ImageChops.lighter(r, g), bl)
    return d.point(lambda v: min(255, v * gain)), max(d.getextrema()[1], 0)


stats = {}
for seed in SEEDS:
    for view in VIEWS:
        L, F, D = raw('t2604_c25_legacy', seed, view), raw('t2604_c25', seed, view), raw('t2604_d', seed, view)
        if not (L and F and D): continue
        rmap, rmax = amplified(L, F, 8)
        ramap, ramax = amplified(L, F, 8, True)
        gmap, gmax = amplified(F, D, 2, True)
        stats[f'{seed}/{view}'] = dict(rendererMaxRGB=rmax, rendererMaxAlpha=ramax, geometryMaxAlpha=gmax)
        w, h = L.size
        im = Image.new('RGB', (w * 3 + 12, h * 2 + 48), (16, 21, 29))
        for k, (arm, lab, col) in enumerate(ARMS):
            im.paste(on_sky([L, F, D][k]), (k * (w + 6), 24))
            s = by.get(f'T260_{arm}__{seed}__{view}', {})
            ImageDraw.Draw(im).text((k * (w + 6) + 6, 5), f"{lab}  {seed} {view}  renderer p95 {s.get('pipelineP95Ms', float('nan')):.2f} ms", fill=col)
        im.paste(Image.merge('RGB', (rmap, rmap, rmap)), (0, h + 48))
        im.paste(Image.merge('RGB', (ramap, ramap, ramap)), (w + 6, h + 48))
        im.paste(Image.merge('RGB', (gmap, gmap, gmap)), (2 * (w + 6), h + 48))
        d = ImageDraw.Draw(im)
        d.text((6, h + 29), f'renderer change RGB x8 (C25 T260.3 vs fixed), max {rmax}/255', fill=(220, 220, 220))
        d.text((w + 12, h + 29), f'renderer change ALPHA x8, max {ramax}/255', fill=(220, 220, 220))
        d.text((2 * (w + 6) + 6, h + 29), f'geometry change ALPHA x2 (C25 vs D, fixed renderer), max {gmax}/255', fill=(220, 220, 220))
        im.save(out / 'pairs' / f'pair-{seed}-{view}.jpg', quality=90)
    tiles = []
    for view in VIEWS:
        for arm, lab, col in ARMS:
            x = raw(arm, seed, view)
            if x: tiles.append((lab.split(' (')[0] + ' ' + view, on_sky(x), col))
    if tiles:
        tw, th, cols = 360, 203, 6
        sheet = Image.new('RGB', (cols * tw, ((len(tiles) + cols - 1) // cols) * (th + 16)), (16, 21, 29)); d = ImageDraw.Draw(sheet)
        for i, (lab, t, col) in enumerate(tiles):
            x, y = (i % cols) * tw, (i // cols) * (th + 16)
            sheet.paste(t.resize((tw - 4, th)), (x, y + 16)); d.text((x + 4, y + 2), lab, fill=col)
        sheet.save(out / f'sheet-{seed}.jpg', quality=88)
(out / 'native-diff-stats.json').write_text(json.dumps(stats, indent=1), encoding='utf-8')
for f in ana.glob('*.json'): shutil.copyfile(f, out / f.name)
for f in ana.glob('*__plan.png'): shutil.copyfile(f, out / f.name)

esc = html.escape
css = ("<style>body{font-family:Segoe UI,sans-serif;background:#10151d;color:#dde;margin:16px;max-width:1900px}a{color:#8cf}"
       "table{border-collapse:collapse;margin:8px 0}td,th{border:1px solid #334;padding:3px 7px;font-size:13px}img{max-width:100%}"
       ".row{display:flex;gap:8px;flex-wrap:wrap}.row figure{margin:0}.row img{height:260px;width:auto}figcaption{font-size:12px;color:#aab}"
       ".note{color:#aab;font-size:13px}</style>")
P = [f'<!doctype html><meta charset=utf-8><title>T260.4 lower anatomy</title>{css}',
     '<h1>T260.4 &mdash; lower-cloud anatomy and artefact attribution</h1>',
     "<p class=note>Native flat-light frames; all three arms of a seed share identical cameras (framed by C25 and D). Orange: C25 under the "
     "T260.3 renderer (before). Blue: C25 under the fixed renderer (isolates the renderer fix). Green: D under the fixed renderer (after). "
     "Pairs add three difference maps: renderer change in colour and in alpha (C25 both renderers) and geometry change in alpha (C25 vs D). "
     "Report: <a href='../../t260-lower-cloud-anatomy.md'>t260-lower-cloud-anatomy.md</a>.</p>"]
for seed in SEEDS:
    P.append(f'<h2>Seed {seed}</h2>')
    if (out / f'sheet-{seed}.jpg').exists(): P.append(f"<p><a href='sheet-{seed}.jpg'><img src='sheet-{seed}.jpg'></a></p>")
    P.append('<div class=row>')
    for v in VIEWS:
        n = f'pairs/pair-{seed}-{v}.jpg'
        if (out / n).exists(): P.append(f"<figure><a href='{n}'><img src='{n}'></a><figcaption>{esc(v)}</figcaption></figure>")
    P.append('</div><div class=row>')
    for v in ('C', 'D'):
        n = f'{v}_{seed}__plan.png'
        if (out / n).exists(): P.append(f"<figure><img src='{n}' style='height:300px'><figcaption>{v} planform: grey turret columns, blue exposed lower mass (brighter = farther past the turrets), teal detached</figcaption></figure>")
    P.append('</div>')
P.append('<h2>Native timing / parity / traversal</h2><table><tr><th>shot</th><th>ray p95</th><th>renderer p50</th><th>renderer p95</th><th>parity</th><th>upload mismatches</th><th>texture MB</th></tr>')
for s in by.values():
    P.append(f"<tr><td>{esc(s['shot'])}</td><td>{s.get('gpuP95Ms', '-')}</td><td>{s.get('pipelineMedianMs', '-')}</td><td>{s.get('pipelineP95Ms', '-')}</td>"
             f"<td>{s.get('parityMaxAbs', '')}</td><td>{s.get('uploadMismatched', '')}</td><td>{s.get('textureBytes', 0) / 1048576:.1f}</td></tr>")
P.append('</table>')
(out / 'index.html').write_text('\n'.join(P), encoding='utf-8')
print('gallery', out / 'index.html', len(stats), 'pairs')
