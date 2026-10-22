"""T260.5 preservation: candidate D geometry (accepted experimental calvus baseline) and every input that defines the
density field are frozen; T260.4 evidence chained. Lighting sources T260.5 may change are byte-copied as the pre-T260.5
reference. Usage: python -I t2605_freeze.py snapshot | verify"""
import hashlib, json, os, shutil, subprocess, sys
root = 'C:/Users/matga/Downloads/Project-Atmosphere/'
V = root + 'specs/001-native-storm-rendering/validation/'
M = root + 'src/main/java/net/Gabou/projectatmosphere/clouds/morphology/'
here = os.path.dirname(os.path.abspath(__file__))
# Density-defining sources: must be byte-identical at the end of T260.5 (no morphology change).
targets = {'CalvusSystemD.java (D geometry)': M + 'CalvusSystemD.java',
           'CalvusSystemC.java (C25 / C26)': M + 'CalvusSystemC.java',
           'ConvectiveSystemT260.java (recipe -> field)': M + 'ConvectiveSystemT260.java',
           'SparseBrickVolume.java (bake + CPU density mirror)': M + 'SparseBrickVolume.java',
           'cloud_convective_system_t260.glsl (GPU density)': root + 'src/main/resources/assets/projectatmosphere/shaders/include/cloud_convective_system_t260.glsl',
           't260-lower-anatomy (T260.4 evidence)': V + 't260-lower-anatomy',
           't260-lower-cloud-anatomy.md': V + 't260-lower-cloud-anatomy.md'}
# Lighting / harness files T260.5 may change: pre-T260.5 copies.
renderer = ['src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh',
            'src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/ExperimentalStormMorphologyState.java',
            'src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/ConvectiveSystemCapture.java',
            'src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormMorphologyCapture.java']
# D's bake signatures (T260.4 native == CPU); T260.5 shots must carry exactly these.
D_SIGNATURES = {'20261015': '66156224c1eb8041', '20262741': '822dab19c217915c', '20264467': '13dc7143e2a1a61e',
                '20265193': 'a4f261574e77231e', '20266607': '4420e7d66ea4154f', '20268011': '5aa5083238fba0bb'}
def digest(p):
    h = hashlib.sha256(); n = 0
    if os.path.isfile(p): return dict(files=1, sha256=hashlib.sha256(open(p, 'rb').read()).hexdigest())
    for dp, dn, fn in sorted(os.walk(p)):
        dn.sort()
        if '__pycache__' in dp: continue
        for f in sorted(fn):
            q = os.path.join(dp, f); h.update(os.path.relpath(q, p).replace(os.sep, '/').encode()); h.update(open(q, 'rb').read()); n += 1
    return dict(files=n, sha256=h.hexdigest())
f = os.path.join(here, 't2605-start-freeze.json')
if sys.argv[1] == 'snapshot':
    if os.path.exists(f): sys.exit('exists')
    os.makedirs(os.path.join(here, 'prefix-sources'), exist_ok=True)
    for p in renderer: shutil.copyfile(root + p, os.path.join(here, 'prefix-sources', os.path.basename(p)))
    json.dump({'note': 'T260.5 start (20 Oct 2026): candidate D accepted as the experimental calvus baseline (user decision, not '
               'production). Geometry frozen; lighting/harness sources copied to prefix-sources/ before any T260.5 edit.',
               'dSignatures': D_SIGNATURES,
               'targets': {k: digest(v) for k, v in targets.items()},
               'prefix': {p: digest(root + p) for p in renderer}}, open(f, 'w'), indent=1)
    print('snapshot ok')
else:
    ref = json.load(open(f)); ok = True
    for k, v in ref['targets'].items():
        good = digest(targets[k]) == v; ok &= good; print(k, 'unchanged' if good else 'CHANGED')
    for p, v in ref['prefix'].items():
        good = digest(os.path.join(here, 'prefix-sources', os.path.basename(p))) == v; ok &= good
        print('pre-T260.5 copy', os.path.basename(p), 'intact' if good else 'CHANGED', '| live file', 'unchanged' if digest(root + p) == v else 'modified (T260.5)')
    r = subprocess.run([sys.executable, '-I', V + 't260-lower-anatomy/frozen/t2604_freeze.py', 'verify'], capture_output=True, text=True)
    print('T260.4 freeze chain:', r.stdout.strip().splitlines()[-1] if r.stdout else r.stderr); ok &= r.returncode == 0
    print('PASS' if ok else 'FAIL'); sys.exit(0 if ok else 1)
