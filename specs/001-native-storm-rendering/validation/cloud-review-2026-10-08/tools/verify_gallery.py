"""Validate local navigation, photographs, evidence hashes and complete reviews."""
from pathlib import Path
from html.parser import HTMLParser
from urllib.parse import urlsplit, unquote
import hashlib
import json
from PIL import Image
from export_gallery import ROOT, TYPES, expected


class Links(HTMLParser):
    def __init__(self):
        super().__init__()
        self.targets = []

    def handle_starttag(self, tag, attrs):
        for name, value in attrs:
            if name in ('href', 'src') and value:
                self.targets.append(value)


def main():
    count = 0
    checked_links = 0
    for page in [ROOT / 'index.html'] + [ROOT / k / 'index.html' for k in TYPES]:
        parser = Links()
        parser.feed(page.read_text(encoding='utf-8'))
        for target in parser.targets:
            url = urlsplit(target)
            if url.scheme or not url.path:
                continue
            local = (page.parent / unquote(url.path)).resolve()
            assert local.is_relative_to(ROOT), (page, target)
            assert local.is_file(), (page, target)
            checked_links += 1
    for kind in TYPES:
        folder = ROOT / kind
        meta = json.loads((folder / 'views.json').read_text(encoding='utf-8'))
        assert set(meta['views']) == set(expected(kind))
        for view, evidence in meta['views'].items():
            path = folder / (view + '.png')
            assert hashlib.sha256(path.read_bytes()).hexdigest() == evidence['sha256']
            assert evidence['region_id'] == meta['spawn']['region_id']
            assert evidence['acknowledged_camera']['error'] <= .1
            assert 'leanFinal=true' in evidence['controls']
            with Image.open(path) as im:
                assert im.size == (1600, 900)
                im.verify()
            count += 1
        ref = json.loads((folder / 'reference-source.json').read_text(encoding='utf-8'))
        assert ref['source_page'].startswith('https://commons.wikimedia.org/')
        assert ref['author'] and ref['license']
        assert hashlib.sha1((folder / 'reference.jpg').read_bytes()).hexdigest() == ref['sha1']
        for path in [folder / 'reference.jpg', folder / 'gallery.jpg']:
            with Image.open(path) as im:
                im.verify()
        assert 'Pending inspection' not in (folder / 'review.md').read_text(encoding='utf-8')
    inventory = json.loads((ROOT / 'support-inventory.json').read_text(encoding='utf-8'))
    assert set(inventory['native_types']) == set(TYPES)
    assert len(inventory['shipped_noise_presets']) == 39
    provenance = json.loads((ROOT / 'capture-provenance.json').read_text(encoding='utf-8'))
    assert provenance['captured_views'] == count == 84
    assert not provenance['missing']
    result = dict(status='pass', native_types=len(TYPES), captured_views=count,
                  reference_photos=len(TYPES), html_pages=12, checked_local_links=checked_links,
                  noise_presets_inventoried=39,
                  checks=['Every expected view present and decodable at 1600x900',
                          'Image hashes match recorded evidence and reference metadata',
                          'Same seeded cloud region for every view in each folder',
                          'Production FINAL controls and camera acknowledgement',
                          'All static HTML local links and assets resolve',
                          'All per-type shape, lighting and size reviews completed'],
                  limitations=['No interactive-browser or JavaScript execution test performed.',
                               'Visual QA used reference/full-resolution images and contact sheets.',
                               'Terrain-obstructed views retained with explicit exclusions.'])
    (ROOT / 'validation-results.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
