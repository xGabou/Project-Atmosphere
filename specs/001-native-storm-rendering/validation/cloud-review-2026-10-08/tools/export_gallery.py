"""Export this review's fresh framebuffer captures and evidence metadata.

May be run during capture for partial contact sheets; --complete verifies every view.
"""
from pathlib import Path
import hashlib
import json
import re
import shutil
import subprocess
import sys
from PIL import Image, ImageDraw, ImageFont, ImageOps

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parents[3]
TYPES = ['vapor_cluster', 'cumulus_humilis', 'cumulus_mediocris', 'cumulus_congestus',
         'cumulonimbus_calvus', 'cumulonimbus_capillatus', 'supercell',
         'stratus_nebulosus', 'stratocumulus', 'nimbostratus', 'cirrus']
VIEWS = ['side', 'opposite_side', 'front', 'above', 'below', 'low_angle', 'far']

def expected(kind):
    extra = ['anvil'] if kind.startswith('cumulonimbus') or kind == 'supercell' else []
    if kind in ['stratus_nebulosus', 'stratocumulus', 'nimbostratus', 'cirrus']:
        extra += ['sheet_extent']
    return VIEWS + extra

def font(size):
    return ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf', size)

def sheet(pictures, dest, title, cols=3):
    # Layout only: retain whole images and aspect ratios, no crops/colour changes.
    cw, ch = (700, 440) if cols == 2 else (530, 348)
    out = Image.new('RGB', (cols * cw, 65 + ch * ((len(pictures) + cols - 1) // cols)), '#101820')
    draw = ImageDraw.Draw(out)
    draw.text((18, 15), title, fill='white', font=font(23))
    for i, (name, path) in enumerate(pictures):
        x, y = i % cols * cw, 65 + i // cols * ch
        with Image.open(path) as src:
            preview = ImageOps.contain(src.convert('RGB'), (cw - 20, ch - 55))
        out.paste(preview, (x + (cw - preview.width) // 2, y + 38 + (ch - 55 - preview.height) // 2))
        draw.text((x + 12, y + 5), name, fill='#b9d6e8', font=font(18))
    out.save(dest)

def main():
    log = (ROOT / 'capture.log').read_text(encoding='utf-8', errors='replace')
    poses, files, controls, spawns, acknowledgements = {}, {}, {}, {}, {}
    for line in log.splitlines():
        m = re.search(r'REVIEW_ACK shot=REVIEW_(\S+)__(\S+) position=\(([^)]+)\) yaw=(\S+) pitch=(\S+) error=(\S+)', line)
        if m:
            acknowledgements[(m.group(1), m.group(2))] = dict(position=[float(x) for x in m.group(3).split(',')], yaw=float(m.group(4)), pitch=float(m.group(5)), error=float(m.group(6)))
        m = re.search(r'REVIEW_POSE type=(\S+) view=(\S+) region=(\S+) bounds=\(([^)]+)\) camera=\(([^)]+)\) aim=\(([^)]+)\) clusters=(\d+)', line)
        if m:
            kind, view, region, bounds, cam, aim, clusters = m.groups()
            values = [float(x) for x in bounds.split(',')]
            width, depth, height = values[1]-values[0], values[5]-values[4], values[3]-values[2]
            poses[(kind, view)] = dict(region_id=region, bounds=values, camera=[float(x) for x in cam.split(',')],
                aim=[float(x) for x in aim.split(',')], clusters=int(clusters), width_blocks=width, depth_blocks=depth,
                height_blocks=height, height_to_max_span=height/max(width, depth), base_y=values[2], top_y=values[3])
        m = re.search(r'T098_CAPTURE shot=REVIEW_(\S+)__(\S+) .*?file=(.*\.png)', line)
        if m and m.group(2) != 'SPAWN':
            files[(m.group(1), m.group(2))] = Path(m.group(3))
        m = re.search(r'T098_CAPTURE_CONTROLS shot=REVIEW_(\S+)__(\S+) (.*)', line)
        if m:
            controls[(m.group(1), m.group(2))] = m.group(3)
        m = re.search(r'REVIEW_SPAWN type=(\S+) region=(\S+) seed=(\d+)', line)
        if m:
            spawns[m.group(1)] = dict(region_id=m.group(2), seed=int(m.group(3)))
    missing, overview = [], []
    reviews = json.loads((ROOT / 'review-data.json').read_text(encoding='utf-8'))
    for kind in TYPES:
        folder = ROOT / kind
        folder.mkdir(exist_ok=True)
        meta = dict(cloud_type=kind, spawn=spawns.get(kind), views={})
        pictures = [('REAL: reference photograph', folder / 'reference.jpg')]
        for view in expected(kind):
            key = (kind, view)
            if key not in files or key not in poses or key not in acknowledgements:
                missing.append('/'.join(key))
                continue
            target = folder / (view + '.png')
            shutil.copyfile(files[key], target)
            with Image.open(target) as im:
                assert im.size == (1600, 900), (target, im.size)
                im.verify()
            entry = poses[key] | dict(source=str(files[key].relative_to(REPO)).replace('\\', '/'),
                controls=controls.get(key), acknowledged_camera=acknowledgements[key], sha256=hashlib.sha256(target.read_bytes()).hexdigest())
            assert entry['acknowledged_camera']['error'] <= .1
            assert 'leanFinal=true' in entry['controls'], entry['controls']
            meta['views'][view] = entry
            label = 'GAME: ' + view.replace('_', ' ')
            if 'Terrain-obstructed' in reviews[kind].get('view_notes', {}).get(view, ''):
                label += ' [OBSTRUCTED]'
            pictures.append((label, target))
        if meta['views']:
            ids = {x['region_id'] for x in meta['views'].values()}
            assert len(ids) == 1 and ids == {meta['spawn']['region_id']}, (kind, ids)
            (folder / 'views.json').write_text(json.dumps(meta, indent=2) + '\n', encoding='utf-8')
            sheet(pictures, folder / 'gallery.jpg', kind.replace('_', ' ').title() + ' | real reference + current renderer')
            show = 'below' if kind in ['cirrus', 'stratus_nebulosus', 'nimbostratus'] else 'side'
            if show in meta['views']:
                overview.extend([(kind + ': REAL', folder / 'reference.jpg'), (kind + ': GAME ' + show, folder / (show + '.png'))])
    if overview:
        sheet(overview, ROOT / 'overview.jpg', 'Cloud review 2026-10-08 | real (left), current renderer (right)', cols=2)
    sheet([(k.replace('_', ' ').title(), ROOT / k / 'reference.jpg') for k in TYPES],
          ROOT / 'references-gallery.jpg', 'Real cloud reference photographs | source and license in each folder')
    sources = [
        'src/main/java/net/Gabou/projectatmosphere/clouds/type/CloudTypeRegistry.java',
        'src/main/java/net/Gabou/projectatmosphere/clouds/type/CloudShapeProfile.java',
        'src/main/java/net/Gabou/projectatmosphere/clouds/simulation/CloudMorphologyGenerators.java',
        'src/main/java/net/Gabou/projectatmosphere/clouds/simulation/CloudGroupSpawner.java',
        'src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh',
        'build/resources/main/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume_final.fsh',
    ]
    provenance = dict(date='2026-10-08', head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=REPO,text=True).strip(),
        capture_log='capture.log', types=TYPES, missing=missing,
        captured_views=len(files), expected_views=sum(len(expected(k)) for k in TYPES),
        source_sha256={p:hashlib.sha256((REPO/p).read_bytes()).hexdigest() for p in sources if (REPO/p).exists()},
        settings=dict(renderer='production lean FINAL', resolution='1600x900', cloud_resolution_scale=.75,
            raymarch_quality='ULTRA', diagnostic_step_budget=0, lighting_mode=0, day_time=6000,
            motion_frozen=True, seed_formula='20261008 + 7919 * registry-order-index'),
        limitations=['One realization and one daylight setup per type; static evidence only.',
            'Bounds describe cluster envelopes, not segmented visible cloud or measured dimensions in the reference photograph.',
            'The stock T098 capture camera/density and fixture-loss messages refer to the remote baseline storm. Use REVIEW_POSE and REVIEW_SPAWN for each reviewed cloud. Nonstorm zero descriptors are expected.',
            'The working tree contains a pre-existing shader edit; source hashes identify the exact renderer reviewed.'])
    (ROOT / 'capture-provenance.json').write_text(json.dumps(provenance, indent=2) + '\n', encoding='utf-8')
    helper = REPO / 'src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/CloudReviewCapture.java'
    if helper.exists():
        shutil.copyfile(helper, ROOT / 'tools/CloudReviewCapture.java')
        diff = subprocess.check_output(['git','diff','--','src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormT098CaptureDriver.java'], cwd=REPO)
        (ROOT / 'tools/capture-driver.patch').write_bytes(diff)
    print('Captured:', len(files), 'Expected:', provenance['expected_views'], 'Missing:', len(missing))
    print('Remaining types:', ', '.join(dict.fromkeys(x.split('/')[0] for x in missing)))
    if '--complete' in sys.argv:
        assert not missing, missing
        assert 'T098_CAPTURE_COMPLETE' in log
        assert 'BUILD SUCCESSFUL' in log
        print('Complete gallery verified: 11 types, same instance per type, all production controls.')

if __name__ == '__main__':
    main()
