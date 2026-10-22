"""T260.2 preservation: T260.1 evidence unchanged + the T260 start freeze (T255-T259, plumbing insertion-only).
Usage: python -I t260r_freeze.py snapshot | verify"""
import hashlib, json, os, subprocess, sys
root = 'C:/Users/matga/Downloads/Project-Atmosphere/'
V = root + 'specs/001-native-storm-rendering/validation/'
here = os.path.dirname(os.path.abspath(__file__))
targets = {'t260-calvus-system': V + 't260-calvus-system', 't260-calvus-system-prototype.md': V + 't260-calvus-system-prototype.md',
           't260-convective-system-architecture.md': V + 't260-convective-system-architecture.md'}
def digest(p):
    h = hashlib.sha256(); n = 0
    if os.path.isfile(p): return dict(files=1, sha256=hashlib.sha256(open(p, 'rb').read()).hexdigest())
    for dp, dn, fn in sorted(os.walk(p)):
        dn.sort()
        if '__pycache__' in dp: continue
        for f in sorted(fn):
            q = os.path.join(dp, f); h.update(os.path.relpath(q, p).replace(os.sep, '/').encode()); h.update(open(q, 'rb').read()); n += 1
    return dict(files=n, sha256=h.hexdigest())
f = os.path.join(here, 't260r-start-freeze.json')
if sys.argv[1] == 'snapshot':
    if os.path.exists(f): sys.exit('exists')
    json.dump({'note': 'T260.2 start (15 Oct 2026): T260.1 evidence frozen as the A baseline. A geometry signatures (full/iso): '
               '20261015 e10b14b8a34471a9/4e0fbcdd2c008d9f, 20262741 cf2fe91aafd006bc/234877faf56ab21c, 20264467 9a69d18c48c1c021/709547fd5e8fc7f2',
               'targets': {k: digest(v) for k, v in targets.items()}}, open(f, 'w'), indent=1)
    print('snapshot ok')
else:
    ref = json.load(open(f)); ok = True
    for k, v in ref['targets'].items():
        good = digest(targets[k]) == v; ok &= good; print(k, 'unchanged' if good else 'CHANGED')
    r = subprocess.run([sys.executable, '-I', V + 't260-calvus-system/frozen/t260_freeze.py', 'verify'], capture_output=True, text=True)
    print(r.stdout.strip().splitlines()[-1] if r.stdout else r.stderr); ok &= r.returncode == 0
    print('PASS' if ok else 'FAIL'); sys.exit(0 if ok else 1)
