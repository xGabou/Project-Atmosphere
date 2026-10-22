"""Build offline HTML and Markdown reviews from inspected evidence and metadata."""
from pathlib import Path
import html
import json
import re
from export_gallery import ROOT, REPO, TYPES, expected

CSS = '''
:root{color-scheme:dark;--bg:#101820;--panel:#19252f;--ink:#edf3f8;--muted:#b7c8d5;--accent:#8fcee8}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font:16px/1.6 system-ui,sans-serif}
main{max-width:1500px;margin:auto;padding:32px}a{color:var(--accent)}h1{font-size:clamp(28px,4vw,48px);line-height:1.15}
h2{font-size:26px}h3{font-size:19px}p{max-width:1050px}small,.muted{color:var(--muted)}
.compare,.cards{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:20px}
figure{margin:0;background:var(--panel);padding:14px;border-radius:8px}figure img{width:100%;height:380px;object-fit:contain;background:#080f15}
figcaption{font-size:14px;padding-top:8px}.view-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px}.view-grid img{height:220px}
.cards article{background:var(--panel);padding:22px;border-radius:8px}.cards img{width:49%;height:190px;object-fit:contain;background:#080f15}
select,button,input{font:inherit;background:#243744;color:white;border:1px solid #73899a;border-radius:4px;padding:7px}
.viewer{height:380px;overflow:hidden;background:#080f15}.viewer img{transform-origin:center;transition:transform .15s}
table{width:100%;border-collapse:collapse;margin:20px 0}th,td{text-align:left;border-bottom:1px solid #42545f;padding:12px;vertical-align:top}
th{color:var(--accent)}.tag{border:1px solid #668597;border-radius:20px;padding:3px 10px;display:inline-block}
.note{background:#20323e;padding:18px;border-left:4px solid var(--accent)}nav{display:flex;gap:18px;flex-wrap:wrap}code{overflow-wrap:anywhere}
@media(max-width:800px){main{padding:18px}.compare,.cards{grid-template-columns:1fr}.view-grid{grid-template-columns:repeat(2,minmax(0,1fr))}figure img,.viewer{height:260px}}
@media(max-width:480px){.view-grid{grid-template-columns:1fr}td,th{padding:7px}}
'''
E = html.escape

def doc(title, body, script=''):
    return f'<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>{E(title)}</title><style>{CSS}</style></head><body><main>{body}</main>{script}</body></html>\n'

def credit(ref, image='reference.jpg'):
    return f'<a href="{E(ref["source_page"])}">{E(ref["file_title"])}</a> · {E(ref["author"])} · <a href="{E(ref["license_url"] or ref["source_page"])}">{E(ref["license"])}</a><br><small>{E(ref["alterations"])}</small>'

def main():
    reviews = json.loads((ROOT/'review-data.json').read_text(encoding='utf-8'))
    provenance = json.loads((ROOT/'capture-provenance.json').read_text(encoding='utf-8'))
    assert not provenance['missing']
    registry = (REPO/'src/main/java/net/Gabou/projectatmosphere/clouds/type/CloudTypeRegistry.java').read_text(encoding='utf-8')
    native = ['vapor_cluster'] + re.findall(r'register\(new CloudTypeDefinition\(\s*"([a-z_]+)"', registry)
    assert set(native) == set(TYPES), (native, TYPES)
    legacy = []
    schema_keys = {'family','visual','material','shape','spawn','evolution'}
    for path in sorted((REPO/'src/main/resources/data').glob('*/cloud_types/*.json')):
        data = json.loads(path.read_text(encoding='utf-8'))
        legacy.append(dict(id=path.stem, namespace=path.parts[-3], schema='native' if schema_keys.intersection(data) else 'Simple Clouds noise preset', source=str(path.relative_to(REPO)).replace('\\','/'), weather=data.get('weather_type')))
    assert not any(p['schema']=='native' for p in legacy), 'Additional native datapack definitions need review'
    inventory = dict(native_types=TYPES, shipped_noise_presets=legacy,
        other_renderers=['Hurricane cloud volumes: separate HurricaneCloudVolume/SimpleCloudsHurricaneRenderer path; not a registered native cloud type.',
                         'Tornado funnel: separate effect renderer, not a CloudTypeRegistry cloud type.'],
        scope='All 11 current native CloudTypeRegistry types. Optional Simple Clouds presets and separate hurricane rendering are inventoried, not presented as native types or falsely graded from native screenshots.')
    (ROOT/'support-inventory.json').write_text(json.dumps(inventory,indent=2)+'\n',encoding='utf-8')
    cards, table, readme_table, credits = [], [], [], []
    for kind in TYPES:
        folder = ROOT/kind
        data = reviews[kind]
        assert all('Pending' not in data[k] for k in ['shape','lighting','size','priority','next']), kind
        ref = json.loads((folder/'reference-source.json').read_text(encoding='utf-8'))
        meta = json.loads((folder/'views.json').read_text(encoding='utf-8'))
        assert set(meta['views']) == set(expected(kind))
        dims = meta['views']['side']
        default = 'below' if kind in ['cirrus','stratus_nebulosus','nimbostratus'] else 'side'
        dimension = f'{dims["width_blocks"]:.1f} × {dims["depth_blocks"]:.1f} × {dims["height_blocks"]:.1f} blocks (X × Z × height)'
        ratio = f'{dims["height_to_max_span"]:.3f}'
        options = ''.join(f'<option value="{v}.png" {"selected" if v==default else ""}>{E(v.replace("_"," "))}</option>' for v in expected(kind))
        findings = ''.join(f'<tr><th>{label}</th><td>{E(data[key])}</td></tr>' for key,label in [('shape','Shape'),('lighting','Lighting'),('size','Overall size'),('next','Suggested next change')])
        figures = ''.join(f'<figure><a href="{v}.png"><img loading="lazy" src="{v}.png" alt="{E(data["name"])} {E(v.replace("_"," "))} production capture"></a><figcaption>{E(v.replace("_"," "))} · <a href="{v}.png">full resolution</a><br>{E(data.get("view_notes",{}).get(v,""))}</figcaption></figure>' for v in expected(kind))
        context_figure = ''
        if kind == 'cumulonimbus_capillatus':
            context_ref = json.loads((folder/'reference-context-source.json').read_text(encoding='utf-8'))
            context_figure = f'<h2>Additional lighting context</h2><figure><a href="reference-context.jpg"><img loading="lazy" src="reference-context.jpg" alt="Warm-lit storm photograph, additional lighting context only"></a><figcaption>{credit(context_ref)}<br>Not the primary shape reference: source descriptions disagree about development stage.</figcaption></figure>'
        body = f'''<nav><a href="../index.html">All cloud types</a><a href="review.md">Written review</a><a href="views.json">Camera and bounds metadata</a><a href="gallery.jpg">Contact sheet</a></nav>
<h1>{E(data['name'])}</h1><p class="tag">Review priority: {E(data['priority'])}</p>
<p>{E(data['standard'])} <a href="{E(data['standard_url'])}">Meteorological reference</a>.</p>
<div class="compare"><figure><a href="reference.jpg"><img src="reference.jpg" alt="Real {E(data['name'])} reference photograph"></a><figcaption>REAL PHOTOGRAPH<br>{credit(ref)}</figcaption></figure>
<figure><div class="viewer"><a id="game-link" href="{default}.png"><img id="game-image" src="{default}.png" alt="Current production {E(data['name'])} capture"></a></div><figcaption><label for="camera-view">CURRENT RENDERER · camera view </label><select id="camera-view">{options}</select><br><label for="magnification">View magnification </label><input id="magnification" type="range" min="1" max="4" value="1" step="0.5"><output id="zoom-label" for="magnification">1×</output><br>1600 × 900 · ULTRA · fixed noon · one seeded instance</figcaption></figure></div>
<p class="note">{E(data['photo_context'])} Solar angle, exposure and camera distance differ. Compare outline, internal shading and proportions; the photograph is not a physical size calibration.</p>
<h2>Shape, lighting and size review</h2><table>{findings}</table>
<h2>Measured game envelope</h2><p>{dimension}. Height / maximum horizontal span: <strong>{ratio}</strong>. Base Y {dims['base_y']:.1f}; top Y {dims['top_y']:.1f}; {dims['clusters']} clusters.</p>
<p class="muted">Bounds enclose the region's cluster members; they are not the exact visible silhouette. Small state evolution between views is recorded in views.json. Cameras were confirmed within 0.1 block and 0.1 degree before settling. Below cameras use player feet; the rendered eye is about 1.62 blocks higher.</p>
<h2>Every captured view</h2><div class="view-grid">{figures}</div>
<p><code>{E(meta['spawn']['region_id'])}</code> · seed {meta['spawn']['seed']} · <a href="reference-source.json">Photo attribution metadata</a></p>{context_figure}'''
        script = '''<script>document.getElementById('camera-view').addEventListener('change',e=>{const img=document.getElementById('game-image');img.src=e.target.value;img.alt='Current production cloud: '+e.target.selectedOptions[0].text;document.getElementById('game-link').href=e.target.value;});document.getElementById('magnification').addEventListener('input',e=>{document.getElementById('game-image').style.transform='scale('+e.target.value+')';document.getElementById('zoom-label').textContent=e.target.value+'×';});</script>'''
        (folder/'index.html').write_text(doc(data['name']+' | Cloud review',body,script),encoding='utf-8')
        md = f'''# {data['name']} — cloud review, 2026-10-08

Priority: **{data['priority']}**. [Open comparison gallery](index.html) · [All views contact sheet](gallery.jpg).

## Real reference

![Real reference](reference.jpg)

{data['standard']} [Meteorological reference]({data['standard_url']}).

Photo: [{ref['file_title']}]({ref['source_page']}) by {ref['author']}; [{ref['license']}]({ref['license_url'] or ref['source_page']}). Retrieved 2026-10-08. {ref['alterations']}

{data['photo_context']}

## Comparison

| Aspect | Observation |
| --- | --- |
| Shape | {data['shape']} |
| Lighting | {data['lighting']} |
| Overall size | {data['size']} |

## Measured game size

{dimension}; height / maximum span **{ratio}**; base Y {dims['base_y']:.1f}, top Y {dims['top_y']:.1f}. {dims['clusters']} clusters in region `{meta['spawn']['region_id']}`, seed `{meta['spawn']['seed']}`.

These are cluster-envelope bounds, not a segmentation of the visible cloud. Camera distance and photo scale are not calibrated. Game blocks are reported as blocks; the reference photograph's physical width and height are unknown. Relative cloud proportions can still be judged.

## Suggested next change

{data['next']}

## Captures

All screenshots are new production lean FINAL captures from this review, 1600×900, ULTRA, scale 0.75, lighting mode 0, diagnostic step budget 0, frozen daylight at 6000 and frozen movement. Camera acknowledgement precedes at least 80 ticks and 24 presented frames of settling. [Metadata](views.json) records each acknowledged pose, bounds, controls and screenshot hash.

'''
        md += '\n'.join(f'- [{v.replace("_"," ")}](./{v}.png) '+data.get('view_notes',{}).get(v,'') for v in expected(kind))+'\n'
        (folder/'review.md').write_text(md,encoding='utf-8')
        cards.append(f'<article data-name="{kind}"><h2><a href="{kind}/index.html">{E(data["name"])}</a></h2><a href="{kind}/index.html"><img loading="lazy" src="{kind}/reference.jpg" alt="{E(data["name"])} real photograph"><img loading="lazy" src="{kind}/{default}.png" alt="{E(data["name"])} current renderer"></a><p><small>REAL (left) · CURRENT RENDERER (right)</small></p><p>{E(data["shape"])}</p><p class="tag">{E(data["priority"])}</p><p>{dimension}</p><p><small>{credit(ref)}</small></p></article>')
        table.append(f'<tr><td><a href="{kind}/index.html">{E(data["name"])}</a></td><td>{dims["width_blocks"]:.1f} × {dims["depth_blocks"]:.1f} × {dims["height_blocks"]:.1f}</td><td>{ratio}</td><td>{E(data["priority"])}</td></tr>')
        readme_table.append(f'| [{data["name"]}]({kind}/review.md) | {dims["width_blocks"]:.1f} × {dims["depth_blocks"]:.1f} × {dims["height_blocks"]:.1f} | {ratio} | {data["priority"]} |')
        credits.append(f'- **{data["name"]}**: [{ref["file_title"]}]({ref["source_page"]}) — {ref["author"]}; [{ref["license"]}]({ref["license_url"] or ref["source_page"]}). Local: `{kind}/reference.jpg`. {ref["alterations"]}')
    overview_body = f'''<nav><a href="README.md">Full written review</a><a href="overview.jpg">Comparison contact sheet</a><a href="support-inventory.json">Supported-type inventory</a><a href="PHOTO-CREDITS.md">Photo credits</a></nav>
<h1>Clouds, compared with reality.</h1><p>Project Atmosphere · 2026-10-08 · all 11 native cloud types · 84 freshly captured views (obstructed views labelled) · real internet photographs.</p>
<p class="note">{E(inventory['scope'])} Photographs and game captures have different sun angles, framing and exposure. Size figures are measured game envelopes; physical dimensions of photographed clouds are unknown.</p>
<label for="filter">Find a cloud type </label><input id="filter" type="search" placeholder="Cumulus, cirrus, supercell…">
<div class="cards">{''.join(cards)}</div><h2>Size relationships</h2><p>One seeded example per type. X × Z × height, in blocks; ratio = height / maximum horizontal span. Cameras frame each cloud individually, so thumbnail size is not world size.</p>
<table><thead><tr><th>Type</th><th>Envelope (blocks)</th><th>H / span</th><th>Review priority</th></tr></thead><tbody>{''.join(table)}</tbody></table>
<h2>How the review was made</h2><p>Original framebuffer screenshots, same cloud instance per folder, confirmed camera teleports, fixed noon and frozen movement. Each folder includes full-resolution views, a photograph with source and license, measured bounds, and shape/lighting/size findings. Capture provenance identifies the exact renderer sources, including the shader edit already present in the working tree.</p>
<p><a href="capture-provenance.json">Capture provenance</a> · <a href="capture.log">Full capture log</a></p>'''
    script = '''<script>document.getElementById('filter').addEventListener('input',e=>{const q=e.target.value.toLowerCase().replaceAll(' ','_');document.querySelectorAll('[data-name]').forEach(c=>c.hidden=!c.dataset.name.includes(q));});</script>'''
    (ROOT/'index.html').write_text(doc('Project Atmosphere | Cloud review 2026-10-08',overview_body,script),encoding='utf-8')
    readme = '''# Current native clouds — real-world comparison, 2026-10-08

[Open the offline comparison gallery](index.html) · [Overview contact sheet](overview.jpg) · [Photo credits](PHOTO-CREDITS.md).

All 11 native registry types have their own folders, real photographs, new screenshots from the current renderer, and a shape/lighting/overall-size review. Each folder contains `index.html`, `review.md`, `gallery.jpg`, the full-resolution named PNG views, `reference.jpg`, `reference-source.json` and `views.json`.

## Main findings

The most important findings from the inspected views:

- **Storm identity and continuity:** calvus, capillatus and supercell share very similar narrow columns, conspicuous horizontal waists and block-like lower masses. Their broad envelope dimensions are also similar. Differentiate their connected crown/canopy structure before fine lighting work.
- **Cumulus progression:** mediocris to congestus has useful relative growth, but the lobes often look assembled or horizontally stacked. Humilis needs a flatter shared base. Vapor and humilis are similar in scale, not a strict size increase.
- **Layer structure:** stratus has a thin envelope but a noticeably cellular underside; stratocumulus is a recognisable broken layer but lacks the reference's roll organisation. Nimbostratus has useful dense shading and thickness, yet remains an isolated compact deck rather than regional overcast.
- **Lighting:** shaded bases and bright exposed tops provide depth. Large smooth or uniformly dark regions, strong waist transitions, and exaggerated cellular contrast weaken the real-cloud impression. Sun-facing below views are not neutral underside-lighting tests.
- **Cirrus fibres:** high altitude and thin proportions are distinct, but the visible strands are short repetitive strips rather than long tapered feathers. This is a high-priority morphology issue.

Priorities are qualitative judgments about one realization, not numerical accuracy scores or statistical tests. The terrain-obstructed calvus low-angle frame is retained and explicitly excluded from cloud judgments.

## Gallery and size inventory

| Type | X × Z × height (blocks) | Height / max span | Priority |
| --- | --- | --- | --- |
'''+ '\n'.join(readme_table) + '''

## Comparison method and limits

Shape: outline, top/base profile, cloud-type cues, layer continuity and internal structure. Lighting: bright faces, darker bases, transmission, rim treatment and visible depth. Overall size: measured in-game width, depth and thickness, their ratios and ordering across types.

The photographs are real internet references, not generated images. Source pages, author, date and licenses are stored locally. Some are source-provided 1280px previews; others are original files. Gallery layouts preserve aspect ratio and the complete image without cropping or colour correction. Different solar angles/exposures are stated in each review; warm sunset references are not noon colour targets.

Camera distance and exact cloud geometry cannot be recovered from these photos. No exact photographed-cloud width, height or game-to-IRL scale factor is claimed. Bounds are the enclosing region-cluster envelope and may be larger than the visible cloud. The cameras reframe each type; thumbnail size cannot be used to infer real-world size. The WMO describes the cumulus sequence as tens to hundreds of metres for humilis, a few hundred metres to roughly 2 km for mediocris, and sometimes over 5 km for congestus; game dimensions are intentionally compressed. [WMO cumulus size reference](https://cloudatlas.wmo.int/en/observation-of-clouds-from-aircraft-descriptions-cumulus.html).

Only one deterministic realization per type and one daylight setup were reviewed. This does not establish behaviour across seeds, lifecycle stages, weather, night lighting or animation. Vapor cluster is a simulation state; its photograph is explicitly a cumulus-fractus proxy. A static supercell photo does not prove rotation in the renderer.

## Current renderer evidence

Captured from the working tree at the commit in `capture-provenance.json`, with the pre-existing shader edit included. Per-file SHA-256 hashes identify the precise shader and morphology sources. Production lean FINAL, lighting mode 0, diagnostic step budget 0, ULTRA, fixed resolution scale 0.75, main framebuffer 1600×900, pinned step scale 1.0, normal temporal history. Daylight is frozen at game time 6000 and cloud movement is frozen. Each cloud is spawned once with seed `20261008 + 7919 × type index` in a separate isolated area. Some non-position cloud state still evolves slightly; each pose records its own bounds.

The camera must be acknowledged within 0.1 block and 0.1 degree before the settle window starts. Then at least 80 ticks and 24 presented frames elapse before capture. A first exploratory pass exposed delayed teleport processing during chunk generation and was discarded; none of its frames are included here. Side = +Z, opposite side = −Z, front = −X; these are world-axis views, not meteorological upwind/downwind claims. Terrain height is sampled for below and low-angle views, but distant chunks can finish loading later. Any terrain-obstructed frames are explicitly labelled in the per-type review and excluded from cloud judgments. Player feet positions in metadata differ from the rendered eye by approximately 1.62 blocks.

The inherited `T098_CAPTURE` camera/density and `T098_CAPTURE_FIXTURE_LOST` lines refer to the remote baseline storm used to bootstrap the harness. For reviewed clouds, use `REVIEW_SPAWN`, `REVIEW_POSE`, `REVIEW_ACK` and per-folder `views.json`. Zero storm descriptors for a nonstorm cloud is expected. A failure to load the reviewed cloud is assessed from the screenshots and that cloud's sync/render evidence, not the unrelated baseline fixture count.

## What “supported types” means here

The gallery covers every current built-in `CloudTypeRegistry` ID. An automated inventory also scans shipped datapack JSON for native-schema overrides. No extra native-schema types are shipped at review time. `CloudFamily.ALTOCUMULUS`, `CloudMorphologyFamily.TOWER` and `SPIRAL_STORM` enum capabilities are not additional registered cloud types.

There are 39 shipped noise-preset JSON assets: 38 under `data/simpleclouds/cloud_types` and one `data/projectatmosphere/cloud_types/hurricane.json`. They use the Simple Clouds schema, are ignored by the native type reload listener, and are fully listed in [support-inventory.json](support-inventory.json). They are optional compatibility presets rather than additional native cloud implementations. Hurricane cloud volumes and tornado funnels have separate render paths; they are not graded with native cloud screenshots. The capture client ran with Simple Clouds disabled.

## Rebuild artifacts

`tools/download_references.py` downloads the selected real photographs and their attribution metadata; `tools/export_gallery.py --complete` validates/exports this run; `tools/render_report.py` regenerates the HTML and Markdown from `review-data.json`; `tools/verify_gallery.py` checks the exported evidence and local links. [Validation results](validation-results.json). Python requires Pillow. The temporary Java capture harness and its driver patch are retained under `tools` for provenance and future capture reproduction; they are not part of the production renderer.
'''
    (ROOT/'README.md').write_text(readme,encoding='utf-8')
    context = json.loads((ROOT/'cumulonimbus_capillatus/reference-context-source.json').read_text(encoding='utf-8'))
    credits.append(f'- **Capillatus additional lighting context**: [{context["file_title"]}]({context["source_page"]}) — {context["author"]}; [{context["license"]}]({context["license_url"]}). Local: `cumulonimbus_capillatus/reference-context.jpg`. Its English/French descriptions disagree about development stage; it is not the primary shape reference.')
    (ROOT/'PHOTO-CREDITS.md').write_text('# Photograph credits\n\nPhotograph rights remain with the credited authors under the linked licenses. Sources and licenses were retrieved 2026-10-08. Proportional preview resizing and gallery arrangement only; no colour changes or crops. The gallery is a collection of separately attributed photographs and game screenshots; the image licenses do not change the project source license.\n\n'+'\n\n'.join(credits)+'\n',encoding='utf-8')
    print('Built 11 offline HTML galleries, 11 written reviews, root index, inventory and photo credits.')

if __name__ == '__main__':
    main()
