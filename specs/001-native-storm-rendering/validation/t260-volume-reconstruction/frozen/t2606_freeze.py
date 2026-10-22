"""T260.6 preservation: candidate D (frozen geometry), the T260.5 neutral lighting (bake, wrapper, shader block), the
sparse-brick renderer and every prior T260 evidence directory are frozen at the T260.6 start; harness/build files T260.6
may change are byte-copied first. Usage: python -I t2606_freeze.py snapshot | verify"""
import hashlib, json, os, shutil, subprocess, sys
root = 'C:/Users/matga/Downloads/Project-Atmosphere/'
V = root + 'specs/001-native-storm-rendering/validation/'
M = root + 'src/main/java/net/Gabou/projectatmosphere/clouds/morphology/'
R = root + 'src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/'
here = os.path.dirname(os.path.abspath(__file__))
# Must be byte-identical at the end of T260.6.
targets = {'CalvusSystemD.java (D geometry)': M + 'CalvusSystemD.java',
           'CalvusSystemC.java (C25 / C26)': M + 'CalvusSystemC.java',
           'ConvectiveSystemT260.java (recipe -> field)': M + 'ConvectiveSystemT260.java',
           'SparseBrickVolume.java (sparse-brick bake + CPU mirror)': M + 'SparseBrickVolume.java',
           'NeutralLightVolume.java (T260.5 light bake)': M + 'NeutralLightVolume.java',
           'cloud_convective_system_t260.glsl (GPU density)': root + 'src/main/resources/assets/projectatmosphere/shaders/include/cloud_convective_system_t260.glsl',
           'cloud_atmosphere_volume.fsh (T260.5 lighting + renderer)': root + 'src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh',
           't260-neutral-lighting (T260.5 evidence + galleries)': V + 't260-neutral-lighting',
           't260-neutral-lighting.md': V + 't260-neutral-lighting.md',
           't260-lower-anatomy (T260.4 evidence)': V + 't260-lower-anatomy'}
# Harness / plumbing T260.6 may change (insertions): pre-T260.6 copies.
prefix = ['src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/ConvectiveSystemCapture.java',
          'src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormMorphologyCapture.java',
          'src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/NeutralLitProgram.java',
          'src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/ExperimentalStormMorphologyState.java']
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
f = os.path.join(here, 't2606-start-freeze.json')
if sys.argv[1] == 'snapshot':
    if os.path.exists(f): sys.exit('exists')
    os.makedirs(os.path.join(here, 'prefix-sources'), exist_ok=True)
    for p in prefix: shutil.copyfile(root + p, os.path.join(here, 'prefix-sources', os.path.basename(p)))
    json.dump({'note': 'T260.6 start (20 Oct 2026): D frozen as the baseline, T260.5 neutral lighting frozen as the diagnostic '
               'reference, sparse-brick renderer frozen, prior evidence frozen; harness copies in prefix-sources/.',
               'dSignatures': D_SIGNATURES,
               'targets': {k: digest(v) for k, v in targets.items()},
               'prefix': {p: digest(root + p) for p in prefix}}, open(f, 'w'), indent=1)
    print('snapshot ok')
else:
    ref = json.load(open(f)); ok = True
    for k, v in ref['targets'].items():
        good = digest(targets[k]) == v; ok &= good; print(k, 'unchanged' if good else 'CHANGED')
    for p, v in ref['prefix'].items():
        good = digest(os.path.join(here, 'prefix-sources', os.path.basename(p))) == v; ok &= good
        print('pre-T260.6 copy', os.path.basename(p), 'intact' if good else 'CHANGED', '| live file', 'unchanged' if digest(root + p) == v else 'modified (T260.6)')
    r = subprocess.run([sys.executable, '-I', V + 't260-neutral-lighting/frozen/t2605_freeze.py', 'verify'], capture_output=True, text=True)
    print('T260.5 freeze chain:', r.stdout.strip().splitlines()[-1] if r.stdout else r.stderr); ok &= r.returncode == 0
    print('PASS' if ok else 'FAIL'); sys.exit(0 if ok else 1)
