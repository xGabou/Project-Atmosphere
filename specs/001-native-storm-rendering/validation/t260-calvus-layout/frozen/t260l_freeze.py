"""T260.3 preservation: T260.2 B (code + evidence) frozen as the layout baseline; T260.1 and the earlier freezes chained.
Usage: python -I t260l_freeze.py snapshot | verify"""
import hashlib, json, os, subprocess, sys
root = 'C:/Users/matga/Downloads/Project-Atmosphere/'
V = root + 'specs/001-native-storm-rendering/validation/'
M = root + 'src/main/java/net/Gabou/projectatmosphere/clouds/morphology/'
here = os.path.dirname(os.path.abspath(__file__))
targets = {'CalvusSystemB.java (T260.2 B geometry)': M + 'CalvusSystemB.java',
           't260-calvus-refinement (T260.2 evidence)': V + 't260-calvus-refinement',
           't260-calvus-refinement.md': V + 't260-calvus-refinement.md'}
def digest(p):
    h = hashlib.sha256(); n = 0
    if os.path.isfile(p): return dict(files=1, sha256=hashlib.sha256(open(p, 'rb').read()).hexdigest())
    for dp, dn, fn in sorted(os.walk(p)):
        dn.sort()
        if '__pycache__' in dp: continue
        for f in sorted(fn):
            q = os.path.join(dp, f); h.update(os.path.relpath(q, p).replace(os.sep, '/').encode()); h.update(open(q, 'rb').read()); n += 1
    return dict(files=n, sha256=h.hexdigest())
f = os.path.join(here, 't260l-start-freeze.json')
if sys.argv[1] == 'snapshot':
    if os.path.exists(f): sys.exit('exists')
    json.dump({'note': 'T260.3 start (15 Oct 2026): T260.2 candidate B frozen as the layout baseline. B volume signatures (full/iso, native log): '
               '20261015 f33d2df336b2424d/d1a0b9ee367cc99c, 20262741 9e29f4515c82faaf/989cec96133ee606, 20264467 113d63c1a7fb1bf/e1e7ad1fb0143fb',
               'targets': {k: digest(v) for k, v in targets.items()}}, open(f, 'w'), indent=1)
    print('snapshot ok')
else:
    ref = json.load(open(f)); ok = True
    for k, v in ref['targets'].items():
        good = digest(targets[k]) == v; ok &= good; print(k, 'unchanged' if good else 'CHANGED')
    r = subprocess.run([sys.executable, '-I', V + 't260-calvus-refinement/frozen/t260r_freeze.py', 'verify'], capture_output=True, text=True)
    print('T260.2 freeze chain:', r.stdout.strip().splitlines()[-1] if r.stdout else r.stderr); ok &= r.returncode == 0
    print('PASS' if ok else 'FAIL'); sys.exit(0 if ok else 1)
