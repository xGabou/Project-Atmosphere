"""T260.4 preservation: T260.3 C25 (accepted experimental baseline, code + evidence incl. rejected C26) frozen; renderer
sources copied as the pre-fix reference; T260.3 / T260.2 / T260.1 / T255-T259 freezes chained.
Usage: python -I t2604_freeze.py snapshot | verify"""
import hashlib, json, os, shutil, subprocess, sys
root = 'C:/Users/matga/Downloads/Project-Atmosphere/'
V = root + 'specs/001-native-storm-rendering/validation/'
M = root + 'src/main/java/net/Gabou/projectatmosphere/clouds/morphology/'
here = os.path.dirname(os.path.abspath(__file__))
targets = {'CalvusSystemC.java (T260.3 C25 / C26 geometry)': M + 'CalvusSystemC.java',
           'CalvusSystemB.java (T260.2 B geometry)': M + 'CalvusSystemB.java',
           't260-calvus-layout (T260.3 evidence, C26 diagnostics)': V + 't260-calvus-layout',
           't260-calvus-system-layout.md': V + 't260-calvus-system-layout.md'}
# Renderer files T260.4 may change: byte copies kept as the pre-fix reference (diffed in the report).
renderer = ['src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh',
            'src/main/resources/assets/projectatmosphere/shaders/include/cloud_convective_system_t260.glsl',
            'src/main/java/net/Gabou/projectatmosphere/clouds/morphology/SparseBrickVolume.java']
def digest(p):
    h = hashlib.sha256(); n = 0
    if os.path.isfile(p): return dict(files=1, sha256=hashlib.sha256(open(p, 'rb').read()).hexdigest())
    for dp, dn, fn in sorted(os.walk(p)):
        dn.sort()
        if '__pycache__' in dp: continue
        for f in sorted(fn):
            q = os.path.join(dp, f); h.update(os.path.relpath(q, p).replace(os.sep, '/').encode()); h.update(open(q, 'rb').read()); n += 1
    return dict(files=n, sha256=h.hexdigest())
f = os.path.join(here, 't2604-start-freeze.json')
if sys.argv[1] == 'snapshot':
    if os.path.exists(f): sys.exit('exists')
    os.makedirs(os.path.join(here, 'prefix-sources'), exist_ok=True)
    for p in renderer: shutil.copyfile(root + p, os.path.join(here, 'prefix-sources', os.path.basename(p)))
    json.dump({'note': 'T260.4 start (20 Oct 2026): C25 accepted as the experimental baseline (user decision); C26 rejected, '
               'kept with its diagnostics. Renderer sources copied to prefix-sources/ before any T260.4 edit.',
               'targets': {k: digest(v) for k, v in targets.items()},
               'prefix': {p: digest(root + p) for p in renderer}}, open(f, 'w'), indent=1)
    print('snapshot ok')
else:
    ref = json.load(open(f)); ok = True
    for k, v in ref['targets'].items():
        good = digest(targets[k]) == v; ok &= good; print(k, 'unchanged' if good else 'CHANGED')
    for p, v in ref['prefix'].items():
        good = digest(os.path.join(here, 'prefix-sources', os.path.basename(p))) == v; ok &= good
        print('pre-fix copy', os.path.basename(p), 'intact' if good else 'CHANGED', '| live file', 'unchanged' if digest(root + p) == v else 'modified (T260.4 renderer fix)')
    r = subprocess.run([sys.executable, '-I', V + 't260-calvus-layout/frozen/t260l_freeze.py', 'verify'], capture_output=True, text=True)
    print('T260.3 freeze chain:', r.stdout.strip().splitlines()[-1] if r.stdout else r.stderr); ok &= r.returncode == 0
    print('PASS' if ok else 'FAIL'); sys.exit(0 if ok else 1)
