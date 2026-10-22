"""T260.3 gallery: native T260.2 B (frozen baseline, "b_" shots) vs T260.3 layout C at identical cameras, six seeds;
per-seed contact sheets, before/after pairs (native frames copied unchanged), CPU evidence (component IDs, top-down
layout), T259 reference comparison, layout metrics, crown study and native timing.
Usage: python -I t260l_gallery.py <native dir[,dir2]> <cpu root with B/ C/> <native json[,json2]> <analysis dir>
       [candidate shot prefix (default none; "w_" = broad alternative C26)] [output folder (default gallery)] [candidate label]"""
import html, json, shutil, sys
from pathlib import Path
from PIL import Image, ImageDraw

here = Path(__file__).parent
natives, cpu, native_jsons, ana = [Path(x) for x in sys.argv[1].split(',')], Path(sys.argv[2]), [Path(x) for x in sys.argv[3].split(',')], Path(sys.argv[4])
CP = sys.argv[5] if len(sys.argv) > 5 else ''
out = here / (sys.argv[6] if len(sys.argv) > 6 else 'gallery')
for d in ('native', 'pairs', 'cpu'): (out / d).mkdir(parents=True, exist_ok=True)
SEEDS = ['20261015', '20262741', '20264467', '20265193', '20266607', '20268011']
VIEWS = ['side', 'opposite_side', 'front', 'back', 'above', 'below', 'low_angle', 'close', 'far', 'upwind', 'downwind', 'crosswind_left', 'crosswind_right']
ISO = ['iso_' + v for v in ['side', 'opposite_side', 'front', 'back', 'above', 'low_angle']]
Z4 = ['z4_side', 'z4_front', 'z4_above']
LB, LC = 'T260.2 B (baseline)', sys.argv[7] if len(sys.argv) > 7 else 'T260.3 layout'
for native in natives:
    for f in native.glob('T260_*.png'): shutil.copyfile(f, out / 'native' / f.name)
for v in ('B', 'C'):
    for seed in SEEDS:
        src = cpu / v / seed
        if src.exists():
            dst = out / 'cpu' / v / seed; dst.mkdir(parents=True, exist_ok=True)
            for f in src.glob('*.png'): shutil.copyfile(f, dst / f.name)
    shutil.copyfile(cpu / v / 't260-gates.json', out / f'{v}-t260-gates.json')
for f in ana.glob('*'):
    if f.is_file(): shutil.copyfile(f, out / f.name)
by = {}
for nj in native_jsons:
    for s in json.loads(nj.read_text(encoding='utf-8'))['shots']: by[s['shot']] = s


def name(seed, view, b): return f"T260_calvus_system__{seed}__{'b_' if b else CP}{view}__raw.png"


def pair(seed, view):
    pb, pc = out / 'native' / name(seed, view, True), out / 'native' / name(seed, view, False)
    if not (pb.exists() and pc.exists()): return None
    b, c = Image.open(pb).convert('RGB'), Image.open(pc).convert('RGB')
    im = Image.new('RGB', (b.width + c.width + 6, b.height + 24), (16, 21, 29))
    im.paste(b, (0, 24)); im.paste(c, (b.width + 6, 24))
    d = ImageDraw.Draw(im)
    sb, sc = by.get(f'T260_calvus_system__{seed}__b_{view}', {}), by.get(f'T260_calvus_system__{seed}__{CP}{view}', {})
    d.text((6, 5), f"{LB}  {seed} {view}  ray p95 {sb.get('gpuP95Ms', float('nan')):.2f} ms", fill=(255, 190, 120))
    d.text((b.width + 12, 5), f"{LC}  {seed} {view}  ray p95 {sc.get('gpuP95Ms', float('nan')):.2f} ms", fill=(140, 230, 140))
    n = f'pair-{seed}-{view}.jpg'
    im.save(out / 'pairs' / n, quality=90)
    return n


def sheet(seed, views, tag):
    tiles = []
    for v in views:
        for b in (True, False):
            p = out / 'native' / name(seed, v, b)
            if p.exists(): tiles.append((('B ' if b else 'C ') + v, Image.open(p).convert('RGB')))
    if not tiles: return None
    tw, th, cols = 360, 203, 6
    im = Image.new('RGB', (cols * tw, ((len(tiles) + cols - 1) // cols) * (th + 16)), (16, 21, 29)); d = ImageDraw.Draw(im)
    for i, (lab, t) in enumerate(tiles):
        x, y = (i % cols) * tw, (i // cols) * (th + 16)
        im.paste(t.resize((tw - 4, th)), (x, y + 16)); d.text((x + 4, y + 2), lab, fill=(255, 190, 120) if lab.startswith('B') else (140, 230, 140))
    n = f'sheet-{seed}-{tag}.jpg'; im.save(out / n, quality=88); return n


def fmt(s): return '%.3f [%.2f-%.2f] n=%d' % (s['mean'], s['min'], s['max'], s['n']) if s else '-'


esc = html.escape
css = ("<style>body{font-family:Segoe UI,sans-serif;background:#10151d;color:#dde;margin:16px;max-width:1800px}a{color:#8cf}"
       "table{border-collapse:collapse;margin:8px 0}td,th{border:1px solid #334;padding:3px 7px;font-size:13px}img{max-width:100%}"
       ".row{display:flex;gap:8px;flex-wrap:wrap}.row figure{margin:0}.row img{height:230px;width:auto}figcaption{font-size:12px;color:#aab}"
       ".note{color:#aab;font-size:13px}h2{margin-top:30px}</style>")
P = [f'<!doctype html><meta charset=utf-8><title>T260.3 calvus system layout</title>{css}',
     f'<h1>T260.3 &mdash; calvus system layout: {LB} vs {LC}</h1>',
     "<p class=note>Native flat-light frames. B and C of a seed are rendered at IDENTICAL cameras (framed by the union of both systems). "
     "Sheets: B (orange label) then C (green label) per view. Whole-system views: photographic lens (options FOV 45); close/below: player 121&deg; lens; "
     "z4: native 4&times; crown close-up. Seeds 20265193, 20266607 and 20268011 are new in T260.3. "
     "Report: <a href='../../t260-calvus-system-layout.md'>t260-calvus-system-layout.md</a>.</p>"]
for seed in SEEDS:
    P.append(f'<h2>Seed {seed}</h2>')
    for tag, views in (('system', VIEWS), ('primary-and-crown', ISO + Z4)):
        n = sheet(seed, views, tag)
        if n: P.append(f"<p><a href='{n}'><img src='{n}'></a></p>")
    P.append('<h3>Before / after pairs (native pixels)</h3><div class=row>')
    for v in VIEWS + ISO + Z4:
        n = pair(seed, v)
        if n: P.append(f"<figure><a href='pairs/{n}'><img src='pairs/{n}'></a><figcaption>{esc(v)}</figcaption></figure>")
    P.append('</div><h3>CPU evidence (same cameras): top-down layout and component identification</h3><div class=row>')
    for v in ('B', 'C'):
        for f in ('top_down_layout.png', 'components__side.png', 'components__front.png', 'components__above.png', 'components__low_angle.png'):
            P.append(f"<figure><a href='cpu/{v}/{seed}/{f}'><img src='cpu/{v}/{seed}/{f}'></a><figcaption>{v} {esc(f)}</figcaption></figure>")
    P.append('</div>')
    for v in ('B', 'C'):
        g = next((x for x in json.loads((out / f'{v}-t260-gates.json').read_text(encoding='utf-8')) if str(x['seed']) == seed), None)
        if g:
            P.append(f"<p><b>{v}</b>: gate A {'PASS' if g['gateA_cell']['pass'] else 'FAIL'} {esc(json.dumps(g['gateA_cell']['checks']))}<br>"
                     f"gate B {'PASS' if g['gateB_system']['pass'] else 'FAIL'} {esc(json.dumps(g['gateB_system']['checks']))}<br>"
                     f"connectivity {esc(json.dumps(g['gateB_system']['connectivity']))}; lower mass {esc(json.dumps(g['gateB_system']['lowerMass']))}</p>")
P.append('<h2>T259 photographic references vs B and C</h2>')
for v in ('B', 'C'):
    if (out / f'{v}-t260-vs-t259-silhouettes.png').exists(): P.append(f"<p><b>{v}</b><br><img src='{v}-t260-vs-t259-silhouettes.png'></p>")
lm = json.loads((out / 't260l-layout-metrics.json').read_text(encoding='utf-8'))
keys = ['aspect', 'summit_pos', 'shoulder', 'w50', 'w75', 'base_gap', 'crown_summits', 'notches', 'convexity_deficit']
P.append('<h3>System-layout metrics (connected system, identical code on photographs and models)</h3><table><tr><th>metric</th>'
         + ''.join(f'<th>{esc(k)}</th>' for k in lm) + '</tr>')
for k in keys:
    P.append(f'<tr><td>{k}</td>' + ''.join(f"<td>{fmt(r['summary'].get(k))}</td>" for r in lm.values()) + '</tr>')
P.append('</table>')
P.append('<h2>Crown study (isolated primary)</h2>')
cs = json.loads((out / 't260l-crown-study.json').read_text(encoding='utf-8'))
P.append('<table><tr><th>variant</th><th>cross-seed nearest crown distance</th><th>min</th><th>within-seed spread</th></tr>'
         + ''.join(f"<tr><td>{esc(k)}</td><td>{v['crossSeedNearest']}</td><td>{v['crossSeedNearestMin']}</td><td>{v['withinSeedSpread']}</td></tr>" for k, v in cs.items()) + '</table>')
for k in cs: P.append(f"<p><img src='crowns-{k}.png'></p>")
P.append('<h2>Native timing / parity (all shots)</h2><table><tr><th>shot</th><th>ray p50</th><th>ray p95</th><th>renderer p50</th><th>renderer p95</th>'
         '<th>parity</th><th>upload mismatches</th><th>texture MB</th><th>bricks</th></tr>')
for s in by.values():
    P.append(f"<tr><td>{esc(s['shot'])}</td><td>{s.get('gpuMedianMs', '-')}</td><td>{s.get('gpuP95Ms', '-')}</td><td>{s.get('pipelineMedianMs', '-')}</td><td>{s.get('pipelineP95Ms', '-')}</td>"
             f"<td>{s.get('parityMaxAbs', '')}</td><td>{s.get('uploadMismatched', '')}</td><td>{s.get('textureBytes', 0) / 1048576:.1f}</td><td>{s.get('field', {}).get('bricks', '')}</td></tr>")
P.append('</table>')
(out / 'index.html').write_text('\n'.join(P), encoding='utf-8')
print('gallery', out / 'index.html')
