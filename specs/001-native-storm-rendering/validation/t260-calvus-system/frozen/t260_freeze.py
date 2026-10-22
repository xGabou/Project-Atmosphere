"""T260 start snapshot and preservation check.
Usage: python -I t260_freeze.py snapshot   (once, before any T260 edit)
       python -I t260_freeze.py verify      (galleries/baselines unchanged, plumbing = frozen copy + inserted lines only)"""
import difflib, hashlib, json, os, shutil, sys
root = 'C:/Users/matga/Downloads/Project-Atmosphere/'
V = root + 'specs/001-native-storm-rendering/validation/'
here = os.path.dirname(os.path.abspath(__file__))
# Shared plumbing T260 may touch: insertion-only against these byte copies.
plumb = ['src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh',
         'src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/CoreCostDiagnosticProgram.java',
         'src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/ExperimentalStormMorphologyState.java',
         'src/main/java/net/Gabou/projectatmosphere/client/render/shader/VolumetricCloudShaders.java',
         'build.gradle']
# Capture harness (experimental only; T258 rewrote it too): small modifications allowed, every changed line listed.
harness = ['src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormMorphologyCapture.java']
# Frozen experimental geometry (T255-T258): must stay byte-identical.
strict = ['src/main/java/net/Gabou/projectatmosphere/clouds/morphology/' + f for f in
          ['StormAnatomyCandidateC.java', 'ConvectiveGrowthFieldPrototype.java', 'ConvectiveGrowthFieldA.java',
           'GrowthDefinedStormPrototype.java', 'StormMorphologyPrototype.java', 'StormGeometryProgram.java']] + \
         ['src/main/resources/assets/projectatmosphere/shaders/include/' + f for f in
          ['cloud_storm_anatomy_c.glsl', 'cloud_convective_growth_field.glsl', 'cloud_convective_growth_field_a.glsl',
           'cloud_growth_defined_storm.glsl', 'cloud_storm_morphology_prototype.glsl']] + \
         ['src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormAnatomyCapturePlan.java',
          'src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormMorphologyCapturePlan.java',
          'PAchangelog.md']
galleries = [V + g for g in ['t255-storm-prototype', 't256-storm-prototype', 't257-convective-growth-field/native',
             't257-convective-growth-field/diagnostics', 't258-storm-anatomy-candidate-c/frozen', 't259-reference-morphology']] + \
            [root + 'run/screenshots/t098/' + d for d in ['9670f282', '4a4a1373', '7c72205c', '99e36c6f', '11612186', '3682ab0f']]
files = [V + f for f in ['t255-storm-prototype.zip', 't256-storm-prototype.zip', 't259-reference-morphology.md',
         't257-convective-growth-field.md', 't257-convective-growth-field/t257-baselines.json']]

def sha(p): return hashlib.sha256(open(p, 'rb').read()).hexdigest()
def tree(g):
    h = hashlib.sha256(); n = 0
    for dp, dn, fn in sorted(os.walk(g)):
        dn.sort()
        for f in sorted(fn):
            if f.endswith('.pyc'): continue
            q = os.path.join(dp, f); h.update(os.path.relpath(q, g).replace(os.sep, '/').encode()); h.update(open(q, 'rb').read()); n += 1
    return dict(files=n, sha256=h.hexdigest())
def key(p): return p.replace(root, '')

def insertion_only(frozen, current):
    a = open(frozen, encoding='utf-8', newline='').read().splitlines(); b = open(current, encoding='utf-8', newline='').read().splitlines()
    sm = difflib.SequenceMatcher(None, a, b, autojunk=False)
    bad = [op for op in sm.get_opcodes() if op[0] in ('replace', 'delete')]
    return not bad, sum(op[4] - op[3] for op in sm.get_opcodes() if op[0] == 'insert')

if sys.argv[1] == 'snapshot':
    if os.path.exists(os.path.join(here, 't260-start-freeze.json')): sys.exit('snapshot exists; refusing to overwrite')
    out = {'note': 'T260 start snapshot (15 Oct 2026), taken before any T260 edit.', 'plumbing': {}, 'strict': {}, 'galleries': {}, 'files': {}}
    os.makedirs(os.path.join(here, 'sources'), exist_ok=True)
    for p in plumb + harness:
        out['plumbing'][p] = sha(root + p); shutil.copyfile(root + p, os.path.join(here, 'sources', os.path.basename(p)))
    for p in strict: out['strict'][p] = sha(root + p)
    for g in galleries: out['galleries'][key(g)] = tree(g)
    for f in files: out['files'][key(f)] = sha(f)
    json.dump(out, open(os.path.join(here, 't260-start-freeze.json'), 'w'), indent=1)
    print('snapshot', len(plumb), 'plumbing,', len(strict), 'strict,', len(galleries), 'galleries,', len(files), 'files')
else:
    ref = json.load(open(os.path.join(here, 't260-start-freeze.json'))); ok = True
    for p, h in ref['plumbing'].items():
        frozen = os.path.join(here, 'sources', os.path.basename(p))
        good = sha(frozen) == h; ins = 0
        if p in harness:
            a = open(frozen, encoding='utf-8').read().splitlines(); b = open(root + p, encoding='utf-8').read().splitlines()
            changed = [l for l in difflib.unified_diff(a, b, lineterm='', n=0) if l.startswith('-') and not l.startswith('---')]
            ok &= good; print('harness', p.split('/')[-1], f'{len(changed)} modified/removed lines:'); [print('   ', l.strip()[:150]) for l in changed]
            continue
        if good: good, ins = insertion_only(frozen, root + p)
        ok &= good; print('plumbing', p.split('/')[-1], f'insertion-only (+{ins} lines)' if good else 'MODIFIED BEYOND INSERTIONS')
    for p, h in ref['strict'].items():
        good = sha(root + p) == h; ok &= good; print('strict', p.split('/')[-1], 'unchanged' if good else 'CHANGED')
    for g, v in ref['galleries'].items():
        cur = tree(root + g); good = cur == v; ok &= good; print('gallery', g, cur['files'], 'unchanged' if good else 'CHANGED')
    for f, h in ref['files'].items():
        good = sha(root + f) == h; ok &= good; print('file', f, 'unchanged' if good else 'CHANGED')
    print('PASS' if ok else 'FAIL'); sys.exit(0 if ok else 1)
