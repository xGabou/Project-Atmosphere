"""T260.2 before/after gallery: native A (frozen T260.1) vs B (refinement) at identical cameras, CPU evidence for both,
T259 comparison and metrics. Native frames are copied unchanged; pairs are labelled side-by-side composites.
Usage: python -I t260r_gallery.py <native dir[,dir2]> <cpu root with A/ B/ compare-A/ compare-B/> <native json[,json2]>"""
import html, json, shutil, sys
from pathlib import Path
from PIL import Image, ImageDraw

here = Path(__file__).parent
# Several native dirs / jsons may be given (comma separated); later ones override (e.g. a recaptured view).
natives, cpu, native_jsons = [Path(x) for x in sys.argv[1].split(',')], Path(sys.argv[2]), [Path(x) for x in sys.argv[3].split(',')]
out = here / 'gallery'
for d in ('native', 'pairs', 'cpu'): (out / d).mkdir(parents=True, exist_ok=True)
SEEDS = ['20261015', '20262741', '20264467']
VIEWS = ['side', 'opposite_side', 'front', 'back', 'above', 'below', 'low_angle', 'close', 'far', 'upwind', 'downwind', 'crosswind_left', 'crosswind_right']
ISO = ['iso_' + v for v in ['side', 'opposite_side', 'front', 'back', 'above', 'low_angle']]
Z4 = ['z4_side', 'z4_front', 'z4_above']
for native in natives:
    for f in native.glob('T260_*.png'): shutil.copyfile(f, out / 'native' / f.name)
for v in ('A', 'B'):
    for seed in SEEDS:
        src = cpu / v / seed
        if src.exists():
            dst = out / 'cpu' / v / seed; dst.mkdir(parents=True, exist_ok=True)
            for f in src.glob('*.png'): shutil.copyfile(f, dst / f.name)
    for f in (cpu / f'compare-{v}').glob('*'): shutil.copyfile(f, out / f'{v}-{f.name}')
    shutil.copyfile(cpu / v / 't260-gates.json', out / f'{v}-t260-gates.json')
by = {}
for nj in native_jsons:
    for s in json.loads(nj.read_text(encoding='utf-8'))['shots']: by[s['shot']] = s
nat = {'shots': list(by.values())}

def name(seed, view, a): return f"T260_calvus_system__{seed}__{'a_' if a else ''}{view}__raw.png"

def pair(seed, view):
    pa, pb = out / 'native' / name(seed, view, True), out / 'native' / name(seed, view, False)
    if not (pa.exists() and pb.exists()): return None
    a, b = Image.open(pa).convert('RGB'), Image.open(pb).convert('RGB')
    im = Image.new('RGB', (a.width + b.width + 6, a.height + 24), (16, 21, 29))
    im.paste(a, (0, 24)); im.paste(b, (a.width + 6, 24))
    d = ImageDraw.Draw(im)
    sa, sb = by.get(f'T260_calvus_system__{seed}__a_{view}', {}), by.get(f'T260_calvus_system__{seed}__{view}', {})
    d.text((6, 5), f"A (T260.1)  {seed} {view}  ray p95 {sa.get('gpuP95Ms', float('nan')):.2f} ms", fill=(255, 190, 120))
    d.text((a.width + 12, 5), f"B (T260.2)  {seed} {view}  ray p95 {sb.get('gpuP95Ms', float('nan')):.2f} ms", fill=(140, 230, 140))
    n = f'pair-{seed}-{view}.jpg'
    im.save(out / 'pairs' / n, quality=90)
    return n

def sheet(seed, views, tag):
    tiles = []
    for v in views:
        for a in (True, False):
            p = out / 'native' / name(seed, v, a)
            if p.exists(): tiles.append((('A ' if a else 'B ') + v, Image.open(p).convert('RGB')))
    if not tiles: return None
    tw, th, cols = 360, 203, 6
    im = Image.new('RGB', (cols * tw, ((len(tiles) + cols - 1) // cols) * (th + 16)), (16, 21, 29)); d = ImageDraw.Draw(im)
    for i, (lab, t) in enumerate(tiles):
        x, y = (i % cols) * tw, (i // cols) * (th + 16)
        im.paste(t.resize((tw - 4, th)), (x, y + 16)); d.text((x + 4, y + 2), lab, fill=(255, 190, 120) if lab.startswith('A') else (140, 230, 140))
    n = f'sheet-{seed}-{tag}.jpg'; im.save(out / n, quality=88); return n

esc = html.escape
css = ("<style>body{font-family:Segoe UI,sans-serif;background:#10151d;color:#dde;margin:16px;max-width:1800px}a{color:#8cf}"
       "table{border-collapse:collapse;margin:8px 0}td,th{border:1px solid #334;padding:3px 7px;font-size:13px}img{max-width:100%}"
       ".row{display:flex;gap:8px;flex-wrap:wrap}.row figure{margin:0}.row img{height:230px;width:auto}figcaption{font-size:12px;color:#aab}"
       ".note{color:#aab;font-size:13px}h2{margin-top:30px}</style>")
P = [f'<!doctype html><meta charset=utf-8><title>T260.2 calvus refinement</title>{css}',
     '<h1>T260.2 &mdash; calvus morphology refinement: A (T260.1, frozen) vs B (refinement)</h1>',
     "<p class=note>Native flat-light frames. A and B of a seed are rendered at IDENTICAL cameras (framed by the union of both systems). "
     "Whole-system views: photographic lens (options FOV 45); close/below: player 121&deg; lens; z4: native 4&times; crown close-up (aim fixed in T260.2). "
     "Report: <a href='../../t260-calvus-refinement.md'>t260-calvus-refinement.md</a>.</p>"]
for seed in SEEDS:
    P.append(f'<h2>Seed {seed}</h2>')
    for tag, views in (('system', VIEWS), ('primary-and-crown', ISO + Z4)):
        n = sheet(seed, views, tag)
        if n: P.append(f"<p><a href='{n}'><img src='{n}'></a></p>")
    P.append('<h3>Before / after pairs (native pixels)</h3><div class=row>')
    for v in VIEWS + ISO + Z4:
        n = pair(seed, v)
        if n: P.append(f"<figure><a href='pairs/{n}'><img src='pairs/{n}'></a><figcaption>{esc(v)}</figcaption></figure>")
    P.append('</div><h3>CPU evidence (same camera plan): component identification, top-down layout, cross-sections</h3><div class=row>')
    for v in ('A', 'B'):
        for f in ('top_down_layout.png', 'components__side.png', 'components__front.png', 'components__above.png', 'primary_cross_sections.png'):
            P.append(f"<figure><a href='cpu/{v}/{seed}/{f}'><img src='cpu/{v}/{seed}/{f}'></a><figcaption>{v} {esc(f)}</figcaption></figure>")
    P.append('</div>')
    for v in ('A', 'B'):
        g = next((x for x in json.loads((out / f'{v}-t260-gates.json').read_text(encoding='utf-8')) if str(x['seed']) == seed), None)
        if g: P.append(f"<p><b>{v}</b>: gate A {'PASS' if g['gateA_cell']['pass'] else 'FAIL'} {esc(json.dumps(g['gateA_cell']['checks']))}<br>"
                       f"gate B {'PASS' if g['gateB_system']['pass'] else 'FAIL'} {esc(json.dumps(g['gateB_system']['checks']))}</p>")
P.append("<h2>T259 photographic references vs A and B (height-normalised silhouettes)</h2>")
for v in ('A', 'B'): P.append(f"<p><b>{v}</b><br><img src='{v}-t260-vs-t259-silhouettes.png'></p>")
sa = json.loads((out / 'A-t260-vs-t259.json').read_text(encoding='utf-8'))['summary']
sb = json.loads((out / 'B-t260-vs-t259.json').read_text(encoding='utf-8'))['summary']
P.append('<table><tr><th>metric</th><th>T259 refs</th><th>A system</th><th>B system</th><th>A primary</th><th>B primary</th></tr>')
for k in ['aspect', 'max_width_height', 'crown_summits', 'notches', 'deepest_notch', 'top_flat_fraction', 'lean', 'convexity_deficit']:
    g = lambda d, s: (d[s].get(k) or {}).get('mean', '-')
    P.append(f"<tr><td>{k}</td><td>{g(sa, 'T259 reference')}</td><td>{g(sa, 'T260 system')}</td><td>{g(sb, 'T260 system')}</td><td>{g(sa, 'T260 primary (isolated)')}</td><td>{g(sb, 'T260 primary (isolated)')}</td></tr>")
P.append('</table><h2>Native timing / parity (all shots)</h2><table><tr><th>shot</th><th>ray p50</th><th>ray p95</th><th>renderer p50</th><th>renderer p95</th><th>parity</th><th>upload mismatches</th><th>texture MB</th><th>bricks</th></tr>')
for s in nat['shots']:
    P.append(f"<tr><td>{esc(s['shot'])}</td><td>{s.get('gpuMedianMs', '-')}</td><td>{s.get('gpuP95Ms', '-')}</td><td>{s.get('pipelineMedianMs', '-')}</td><td>{s.get('pipelineP95Ms', '-')}</td>"
             f"<td>{s.get('parityMaxAbs', '')}</td><td>{s.get('uploadMismatched', '')}</td><td>{s.get('textureBytes', 0) / 1048576:.1f}</td><td>{s.get('field', {}).get('bricks', '')}</td></tr>")
P.append('</table>')
(out / 'index.html').write_text('\n'.join(P), encoding='utf-8')
print('gallery', out / 'index.html')
