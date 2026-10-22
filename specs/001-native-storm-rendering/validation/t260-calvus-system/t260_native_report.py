"""T260 native evidence extractor: per-shot GPU/pipeline timings, parity, upload verify, traversal pairs, bake/field
stats and update cost from a runClient log. Usage: python -I t260_native_report.py <runClient out log> <out json>"""
import json, re, sys

log = open(sys.argv[1], encoding='utf-8', errors='replace').read().splitlines()
shots = {}
def shot(name): return shots.setdefault(name, {'shot': name})
kv = re.compile(r'(\w+)=(\{[^}]*\}|\[[^\]]*\]|Bounds\[[^\]]*\]|\S+)')
other = {'bakes': [], 'traversal': [], 'updates': [], 'rejected': []}
for line in log:
    m = re.search(r'(T260_\w+|T258_UPLOAD_VERIFY) (.*)$', line)
    if not m: continue
    tag, rest = m.group(1), m.group(2)
    d = dict(kv.findall(rest))
    if tag == 'T260_BAKE': other['bakes'].append(d); continue
    if tag == 'T260_TRAVERSAL_PAIR': other['traversal'].append(d); continue
    if tag == 'T260_CAPTURE_REJECTED': other['rejected'].append(rest); continue
    name = d.get('shot')
    if not name: continue
    s = shot(name)
    if tag == 'T260_GPU': s.update(gpuMedianMs=float(d['medianMs']), gpuP95Ms=float(d['p95Ms']), gpuMaxMs=float(d['maxMs']), gpuSamples=int(d['samples']), target=d['target'], program=d['program'])
    elif tag == 'T260_PIPELINE': s.update(pipelineMedianMs=float(d['medianMs']), pipelineP95Ms=float(d['p95Ms']), pipelineSamples=int(d['samples']))
    elif tag == 'T260_PARITY': s.update(parityMaxAbs=float(d['maxAbsError']), parityPass=d['pass'] == 'true', paritySamples=int(d['samples']), parityOccupied=int(d['occupied']))
    elif tag == 'T258_UPLOAD_VERIFY': s.update(uploadMismatched=int(d['mismatched']), uploadFloats=int(d['floats']))
    elif tag == 'T260_FIELD': s.update(field={k: d[k] for k in ('recipe', 'components', 'bubbles', 'voxel', 'feather', 'grid', 'empty', 'full', 'bricks', 'capacity', 'boxes', 'texture') if k in d})
    elif tag == 'T260_POSE': s.update(textureBytes=int(d['textureBytes']))
    elif tag == 'T260_FRAMING': s.update(occupiedEdgePixels=int(d['occupiedEdgePixels']))
    elif tag == 'T260_CAMERA_AUTHORITY': s.update(cameraAuthorityMaxAbs=float(d['maxAbsError']))
    elif tag == 'T260_UPDATE': s.update(update=d); other['updates'].append(d)
out = {'shots': list(shots.values()), **other}
json.dump(out, open(sys.argv[2], 'w'), indent=1)
print(f"{'shot':58s} {'gpu p50':>8s} {'gpu p95':>8s} {'pipe p50':>8s} {'pipe p95':>8s} {'parity':>9s} {'tex MB':>7s} bricks")
for s in shots.values():
    print(f"{s['shot'][:58]:58s} {s.get('gpuMedianMs', -1):8.3f} {s.get('gpuP95Ms', -1):8.3f} {s.get('pipelineMedianMs', -1):8.3f} {s.get('pipelineP95Ms', -1):8.3f} "
          f"{s.get('parityMaxAbs', float('nan')):9.2e} {s.get('textureBytes', 0) / 1048576:7.1f} {s.get('field', {}).get('bricks', '-')}")
for t in other['traversal']: print('traversal', t)
for u in other['updates']: print('update', u)
for r in other['rejected']: print('REJECTED', r)
