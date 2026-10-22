"""T260 gallery exporter: native frames (unchanged copies) + CPU evidence (component identification, top-down layout,
high-resolution silhouettes, primary cross-sections, T259 comparison) + metrics into one navigable index.html.
Usage: python -I t260_gallery.py <native capture dir (run/screenshots/t098/<uuid>)> <cpu gallery dir> <compare dir> <native json>"""
import html, json, shutil, sys
from pathlib import Path
from PIL import Image, ImageDraw

here = Path(__file__).parent
native, cpu, cmp_dir, native_json = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3]), Path(sys.argv[4])
out = here / 'gallery'
(out / 'native').mkdir(parents=True, exist_ok=True)
(out / 'cpu').mkdir(parents=True, exist_ok=True)
SEEDS = ['20261015', '20262741', '20264467']
VIEWS = ['side', 'opposite_side', 'front', 'back', 'above', 'below', 'low_angle', 'close', 'far', 'upwind', 'downwind', 'crosswind_left', 'crosswind_right']
ISO = ['side', 'opposite_side', 'front', 'back', 'above', 'low_angle']
copied = 0
for f in sorted(native.glob('T260_*__raw.png')):
    shutil.copyfile(f, out / 'native' / f.name); copied += 1
for f in sorted(native.glob('T260_*.png')):
    if not f.name.endswith('__raw.png'):
        shutil.copyfile(f, out / 'native' / f.name); copied += 1
for seed in SEEDS:
    d = cpu / seed
    if not d.exists(): continue
    (out / 'cpu' / seed).mkdir(exist_ok=True)
    for f in d.glob('*.png'): shutil.copyfile(f, out / 'cpu' / seed / f.name)
for f in cmp_dir.glob('*'): shutil.copyfile(f, out / f.name)
nat = json.loads(native_json.read_text(encoding='utf-8'))
by = {s['shot']: s for s in nat['shots']}
gates = json.loads((cpu / 't260-gates.json').read_text(encoding='utf-8'))

def raw(seed, view): return f'native/T260_calvus_system__{seed}__{view}__raw.png'

def sheet(seed):
    """Contact sheet of the 13 native views + isolated primary for one seed (native pixels, labelled)."""
    tiles = []
    for v in VIEWS + ['iso_' + x for x in ISO] + ['z4_side', 'z4_front', 'z4_above']:
        p = out / raw(seed, v)
        if p.exists(): tiles.append((v, Image.open(p).convert('RGB')))
    if not tiles: return None
    tw, th = 400, 225
    cols = 5
    im = Image.new('RGB', (cols * tw, ((len(tiles) + cols - 1) // cols) * (th + 18)), (16, 21, 29))
    d = ImageDraw.Draw(im)
    for i, (v, t) in enumerate(tiles):
        x, y = (i % cols) * tw, (i // cols) * (th + 18)
        im.paste(t.resize((tw, th)), (x, y + 18)); d.text((x + 4, y + 3), v, fill=(255, 220, 120))
    name = f'native-sheet-{seed}.png'
    im.save(out / name)
    return name

esc = html.escape
css = ("<style>body{font-family:Segoe UI,sans-serif;background:#10151d;color:#dde;margin:16px;max-width:1700px}a{color:#8cf}"
       "table{border-collapse:collapse;margin:8px 0}td,th{border:1px solid #334;padding:3px 7px;font-size:13px}"
       "img{max-width:100%}.row{display:flex;gap:8px;flex-wrap:wrap}.row figure{margin:0}.row img{height:240px;width:auto}"
       "figcaption{font-size:12px;color:#aab}.note{color:#aab;font-size:13px}h2{margin-top:32px}</style>")
P = [f'<!doctype html><meta charset=utf-8><title>T260 calvus system</title>{css}',
     '<h1>T260 &mdash; first complete 3D convective system prototype (cumulonimbus calvus)</h1>',
     "<p class=note>Experimental fixture only. Flat light, no rain, no temporal reconstruction. Every camera of a seed renders ONE frozen baked volume. "
     "Whole-system views use a photographic lens (options FOV 45 &times; 1.1) fitted to the complete system; <b>close</b> and <b>below</b> use the player's own 121&deg; lens; "
     "z4 = native 4&times; close-up of the primary crown. Report: <a href='../../t260-calvus-system-prototype.md'>t260-calvus-system-prototype.md</a>.</p>"]
for seed in SEEDS:
    P.append(f'<h2>Seed {seed}</h2>')
    sh = sheet(seed)
    if sh: P.append(f"<p><img src='{sh}'></p>")
    P.append("<h3>Complete system &mdash; native frames</h3><div class=row>")
    for v in VIEWS:
        s = by.get(f'T260_calvus_system__{seed}__{v}', {})
        cap = f"{v} &middot; ray p95 {s.get('gpuP95Ms', float('nan')):.2f} ms &middot; renderer p95 {s.get('pipelineP95Ms', float('nan')):.2f} ms"
        P.append(f"<figure><a href='{raw(seed, v)}'><img src='{raw(seed, v)}'></a><figcaption>{cap}</figcaption></figure>")
    P.append("</div><h3>Isolated primary tower &mdash; same cameras</h3><div class=row>")
    for v in ISO: P.append(f"<figure><a href='{raw(seed, 'iso_' + v)}'><img src='{raw(seed, 'iso_' + v)}'></a><figcaption>iso {v}</figcaption></figure>")
    P.append("</div><h3>Native 4&times; close-ups of the primary crown</h3><div class=row>")
    for v in ('side', 'front', 'above'): P.append(f"<figure><a href='{raw(seed, 'z4_' + v)}'><img src='{raw(seed, 'z4_' + v)}'></a><figcaption>z4 {v}</figcaption></figure>")
    P.append("</div><h3>Component identification, top-down layout, cross-sections (CPU, same camera plan)</h3><div class=row>")
    for f in ['top_down_layout.png', 'components__side.png', 'components__front.png', 'components__above.png', 'components__low_angle.png', 'primary_cross_sections.png']:
        P.append(f"<figure><a href='cpu/{seed}/{f}'><img src='cpu/{seed}/{f}'></a><figcaption>{esc(f)}</figcaption></figure>")
    P.append("</div><h3>High-resolution silhouettes (2400&times;1350, CPU)</h3><div class=row>")
    for v in ['side', 'front', 'low_angle', 'crosswind_left']:
        for k in ('', 'iso_'):
            P.append(f"<figure><a href='cpu/{seed}/hires__{k}{v}.png'><img src='cpu/{seed}/hires__{k}{v}.png'></a><figcaption>{k}{v}</figcaption></figure>")
    P.append('</div>')
    g = next((x for x in gates if str(x['seed']) == seed), None)
    if g:
        P.append(f"<p><b>Gate A (individual cell): {'PASS' if g['gateA_cell']['pass'] else 'FAIL'}</b> &mdash; {esc(json.dumps(g['gateA_cell']['checks']))}</p>")
        P.append(f"<p><b>Gate B (complete system): {'PASS' if g['gateB_system']['pass'] else 'FAIL'}</b> &mdash; {esc(json.dumps(g['gateB_system']['checks']))}</p>")
P.append("<h2>Comparison with the T259 photographic references</h2><p><img src='t260-vs-t259-silhouettes.png'></p>")
summ = json.loads((out / 't260-vs-t259.json').read_text(encoding='utf-8'))['summary']
P.append('<table><tr><th>metric (crosswind/side views, normalised)</th>' + ''.join(f'<th>{esc(k)}</th>' for k in summ) + '</tr>')
for k in ['aspect', 'max_width_height', 'crown_summits', 'notches', 'deepest_notch', 'top_flat_fraction', 'lean', 'convexity_deficit']:
    P.append(f'<tr><td>{k}</td>' + ''.join(f"<td>{(summ[s].get(k) or {}).get('mean', '-')}</td>" for s in summ) + '</tr>')
P.append('</table>')
P.append('<h2>Native timing, parity and upload checks (all shots)</h2><table><tr><th>shot</th><th>ray p50</th><th>ray p95</th><th>renderer p50</th><th>renderer p95</th><th>parity max |err|</th><th>upload mismatches</th><th>texture MB</th></tr>')
for s in nat['shots']:
    P.append(f"<tr><td>{esc(s['shot'])}</td><td>{s.get('gpuMedianMs', '-')}</td><td>{s.get('gpuP95Ms', '-')}</td><td>{s.get('pipelineMedianMs', '-')}</td><td>{s.get('pipelineP95Ms', '-')}</td>"
             f"<td>{s.get('parityMaxAbs', '')}</td><td>{s.get('uploadMismatched', '')}</td><td>{s.get('textureBytes', 0) / 1048576:.1f}</td></tr>")
P.append('</table>')
(out / 'index.html').write_text('\n'.join(P), encoding='utf-8')
print('gallery', out / 'index.html', 'native files', copied)
