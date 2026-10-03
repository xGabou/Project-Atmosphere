# Write cloud-shape-gallery/README.md from the per-family views.json files.
import json, os, subprocess

ROOT = r'C:\Users\matga\Downloads\Project-Atmosphere'
GAL = os.path.join(ROOT, r'specs\001-native-storm-rendering\validation\cloud-shape-gallery')
head = subprocess.run(['git', '-C', ROOT, 'rev-parse', '--short', 'HEAD'], capture_output=True, text=True).stdout.strip()

# Review findings (visual inspection of every image after capture).
OBSERVED = {
    'cirrus': 'thin high layer: side / opposite_side / front / back / close / far / edge_profile / top_profile see it '
              'edge-on at the horizon and are nearly empty (near-identical to each other); below, upwind, downwind, '
              'underside and above show the streaks.',
    'stratus_nebulosus': 'thin deck: the level side views (side, opposite_side, back, far, edge_profile, top_profile) '
                         'show the same thin line; above, below, underside, low_angle carry the shape.',
    'cumulus_humilis': 'terrain (tree-covered hill) in the lower foreground of side-level views; the cloud itself is '
                       'unobstructed. below and low_angle both look up at a small cloud and are similar.',
    'cumulus_mediocris': 'terrain in the lower foreground of front / back / close / lower_body / tower / transition; '
                         'cloud unobstructed. front and back are near-identical at low resolution (compact cloud).',
    'cumulus_congestus': 'terrain in the lower foreground of low_angle / lower_body; cloud unobstructed.',
    'stratocumulus': 'terrain in the lower foreground of low_angle / close / edge_profile; cloud unobstructed.',
    'nimbostratus': 'terrain in the foreground of low_angle / below / underside (below views look up from just above '
                    'terrain); deck unobstructed. far and edge_profile are both distant level views.',
    'cumulonimbus': 'terrain in the lower foreground of below / low_angle / lower_body; storm unobstructed. far is '
                    'deliberately small.',
    'supercell': 'terrain in the lower foreground of below / low_angle / lower_body; storm unobstructed. far is '
                 'deliberately small.',
}

lines = ['# Cloud-shape reference gallery', '',
         'Production renderer only (lean FINAL): T243 shapes, T244 lighting, T226 rain / lower-trunk lighting; '
         f'lighting mode 0, no experiment switches, no debug morphology, no legacy lobe union. Source commit `{head}`.',
         '',
         'One folder per cloud family, one image per camera view (1600x900). Every image inside a folder is the SAME '
         'cloud realization: the cloud is spawned once (just in time, motion frozen) and the camera is moved around '
         'it. Each folder has a `views.json` with the cloud type, spawn region id and world position, cloud bounds, '
         'shear axis, and for every view the camera position, aim point, yaw / pitch and terrain height.',
         '',
         '## How the views are defined',
         '',
         '* `d` = max(0.55 x extent + 0.8 x height, extent + 25) + 10 blocks from the cloud centre (`extent` = half the '
         'horizontal span of the region\'s clusters).',
         '* front / back / upwind / downwind follow the cloud\'s own shear axis (the height-weighted offset of its '
         'clusters - the upper mass leans downwind); front = from upwind, back = from downwind; side / '
         'opposite_side / crosswind_* are perpendicular to it. upwind / downwind / crosswind_* are elevated '
         '(+0.35 d).',
         '* above: top + 0.6 d. below / underside: between terrain and the cloud base (never under terrain, never '
         'above the base); storms put the below camera under the anvil overhang, outside the trunk.',
         '* low_angle: 40 blocks above terrain, looking up. far: 2 d. close: max(0.6 d, extent + 20).',
         '* Layer extras: sheet_extent (high oblique), underside, edge_profile (level, 1.4 d), top_profile (grazing '
         'the top). Convective extras: lower_body, tower, transition (level at 15 / 45 / 75 % of the height); storms '
         'add anvil.',
         '* Every camera except below / underside is kept at least 15 blocks above the terrain heightmap.',
         '* yaw / pitch use the Minecraft convention (negative pitch looks up, positive looks down).',
         '',
         '## Families', '']
for fam in sorted(os.listdir(GAL)):
    d = os.path.join(GAL, fam)
    if not os.path.isdir(d) or fam == 'tools':
        continue
    meta = json.load(open(os.path.join(d, 'views.json'), encoding='utf-8'))
    views = meta['views']
    any_view = next(iter(views.values()))
    sp = meta.get('spawn') or {}
    lines += [f'### {fam}', '',
              f'* cloud type `{meta["cloud_type"]}`, region `{sp.get("region", "?")}`, spawned at '
              f'x {sp.get("at", ["?", "?"])[0]:.0f}, z {sp.get("at", ["?", "?"])[1]:.0f}; centre '
              f'({any_view["centre"][0]:.0f}, {any_view["centre"][1]:.0f}), extent {any_view["ext"]:.0f}, '
              f'base {any_view["base"]:.0f}, top {any_view["top"]:.0f}, downwind '
              f'({any_view["downwind"][0]:.2f}, {any_view["downwind"][1]:.2f})',
              f'* views ({len(views)}): ' + ', '.join(f'`{v}.png`' for v in views),
              f'* observations: {OBSERVED.get(fam, "-")}', '',
              '| view | camera (x, y, z) | yaw / pitch | note |', '|---|---|---|---|']
    for v, e in views.items():
        c = e.get('camera', [0, 0, 0])
        lines.append(f'| {v} | ({c[0]:.0f}, {c[1]:.0f}, {c[2]:.0f}) | {e.get("yaw", 0):.1f} / {e.get("pitch", 0):.1f} | '
                     f'{e.get("note") or ""} |')
    lines.append('')
lines += ['## Capture status', '',
          'All expected views exist for every family (see `cloud_gallery_export.py` status). No camera was placed '
          'inside terrain; no below-cloud camera was above the cloud base. The first supercell capture saved a stale '
          'frame for `above` (identical to `back`); the whole supercell family was re-captured in its own session so '
          'the folder stays one realization.', '',
          '## Reproducing', '',
          'Harness (run from the repo root, client closed): restore `StormT098CaptureDriver.java` / '
          '`StormLobeDescriptor.java`, run `tools/t241_capture_repo.py \'[]\'` then `tools/cloud_gallery.py '
          '\'<json family list>\'`, launch `runClient`, then `tools/cloud_gallery_export.py <runClient log>` and '
          '`tools/cloud_gallery_readme.py`. The capture patches are temporary and must be reverted after the run.', '']
open(os.path.join(GAL, 'README.md'), 'w', encoding='utf-8').write('\n'.join(lines))
print('README written', len(lines), 'lines')
