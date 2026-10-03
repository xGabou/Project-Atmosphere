# Export a cloud-gallery capture run into specs/.../validation/cloud-shape-gallery/<family>/<view>.png
# with per-family views.json metadata and a root README.md index.
# usage: cloud_gallery_export.py <runClient-log> [--readme-only]
import json, os, re, shutil, sys

ROOT = r'C:\Users\matga\Downloads\Project-Atmosphere'
GAL = os.path.join(ROOT, r'specs\001-native-storm-rendering\validation\cloud-shape-gallery')
FOLDER = {'cumulonimbus_calvus': 'cumulonimbus'}
sys.path.insert(0, os.path.dirname(__file__))
import importlib.util
spec = importlib.util.spec_from_file_location('cg', os.path.join(os.path.dirname(__file__), 'cloud_gallery.py'))
src = open(os.path.join(os.path.dirname(__file__), 'cloud_gallery.py'), encoding='utf-8').read()
KIND = {}
exec(src[src.index('BASE = '):src.index('SHOTS = ')], {}, KIND)
KIND = KIND['KIND']

log = open(sys.argv[1], encoding='utf-8', errors='replace').read().splitlines()
pose, cam, files, spawn = {}, {}, {}, {}
lastPose = None
for line in log:
    m = re.search(r'CG_POSE type=(\S+) view=(\S+) region=(\S+) centre=\(([^,]+),([^)]+)\) ext=(\S+) base=(\S+) top=(\S+) '
                  r'shear=\(([^,]+),([^)]+)\) ground=(\S+) cam=\(([^,]+),([^,]+),([^)]+)\) aim=\(([^,]+),([^,]+),([^)]+)\) note=(.*)$', line)
    if m:
        g = m.groups()
        pose[(g[0], g[1])] = dict(type=g[0], view=g[1], region=g[2], centre=[float(g[3]), float(g[4])], ext=float(g[5]),
                                 base=float(g[6]), top=float(g[7]), downwind=[float(g[8]), float(g[9])], ground=float(g[10]),
                                 camera=[float(g[11]), float(g[12]), float(g[13])], aim=[float(g[14]), float(g[15]), float(g[16])],
                                 note=g[17].strip())
        continue
    m = re.search(r'CG_CAMERA shot=CG_(\S+?)__(\S+) pos=\(([^,]+),([^,]+),([^)]+)\) yaw=(\S+) pitch=(\S+)', line)
    if m:
        cam[(m.group(1), m.group(2))] = dict(yaw=float(m.group(6)), pitch=float(m.group(7)))
        continue
    m = re.search(r'CG_SPAWN type=(\S+) ok=(\S+) region=(\S+) at=\(([^,]+),([^)]+)\)', line)
    if m:
        spawn[m.group(1)] = dict(ok=m.group(2) == 'true', region=m.group(3), at=[float(m.group(4)), float(m.group(5))])
        continue
    m = re.search(r'T098_CAPTURE shot=CG_(\S+?)__(\S+) .*?file=(\S+\.png)', line)
    if m and m.group(2) != 'SPAWN':
        files[(m.group(1), m.group(2))] = m.group(3)

os.makedirs(GAL, exist_ok=True)
missing, notes = [], []
for t, views in KIND.items():
    if t not in spawn:
        continue  # family not captured in this log
    folder = os.path.join(GAL, FOLDER.get(t, t))
    os.makedirs(folder, exist_ok=True)
    meta = dict(cloud_type=t, spawn=spawn.get(t), renderer='production lean FINAL (T243 shapes, T244 lighting, T226 rain/lower-trunk lighting), lighting mode 0, no experiment switches', views={})
    for v in views:
        key = (t, v)
        f = files.get(key)
        if f and os.path.exists(f.replace('\\', '/')):
            shutil.copyfile(f, os.path.join(folder, v + '.png'))
            entry = dict(pose.get(key, {}))
            entry.update(cam.get(key, {}))
            entry['source'] = os.path.basename(f)
            meta['views'][v] = entry
            if entry.get('note'):
                notes.append((t, v, entry['note']))
        else:
            missing.append((t, v))
    open(os.path.join(folder, 'views.json'), 'w', encoding='utf-8').write(json.dumps(meta, indent=2))
print('missing', missing)
print('notes', notes)
json.dump(dict(missing=missing, notes=notes), open(os.path.join(os.path.dirname(__file__), 'cg_status.json'), 'w'))
