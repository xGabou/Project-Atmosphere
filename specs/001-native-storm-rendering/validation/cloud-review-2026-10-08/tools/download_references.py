"""Download Commons photographs and save exact attribution metadata.

Uses Windows curl's certificate store and supported 1280px previews.
Run: python tools/download_references.py
"""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import hashlib
import html
import json
import re
import subprocess
from urllib.parse import urlencode
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
FILES = {
    'vapor_cluster': 'Cumulus Fractus.jpg',
    'cumulus_humilis': 'Cumulus-humilis.jpg',
    'cumulus_mediocris': 'Cumulus mediocris 3.JPG',
    'cumulus_congestus': 'Cumulus congestus cloud.jpg',
    'cumulonimbus_calvus': 'Cumulonimbus calvus a4.JPG',
    'cumulonimbus_capillatus': 'Cumulonimbus capillatus incus.jpg',
    'supercell': 'Supercell 059.jpg',
    'stratus_nebulosus': 'Stratus nebulosus in Limburg, Panorama.jpg',
    'stratocumulus': 'Stratocumulus stratiformis undulatus perlucidus.jpg',
    'nimbostratus': 'Nimbostratus clouds.jpg',
    'cirrus': 'Cirrus-fibratus.jpg',
}

def plain(value):
    return html.unescape(re.sub('<[^>]*>', '', str(value)))

def download(item):
    kind, name = item
    folder = ROOT / kind
    folder.mkdir(parents=True, exist_ok=True)
    dest = folder / 'reference.jpg'
    metadata = folder / 'reference-source.json'
    if dest.exists() and metadata.exists():
        return json.loads(metadata.read_text(encoding='utf-8'))
    api = 'https://commons.wikimedia.org/w/api.php?' + urlencode({
        'action': 'query', 'format': 'json', 'prop': 'imageinfo',
        'iiprop': 'url|extmetadata|size|sha1|mime', 'titles': 'File:' + name,
        'iiurlwidth': 1280,
    })
    response = subprocess.check_output(['curl.exe', '-sS', '--fail', '--retry', '2',
                                        '--max-time', '40', api])
    payload = json.loads(response)
    page = next(iter(payload['query']['pages'].values()))
    info = page['imageinfo'][0]
    ext = {k: plain(v['value']) for k, v in info['extmetadata'].items()}
    assert info['mime'] == 'image/jpeg', info['mime']
    url = info.get('thumburl', info['url']).split('?')[0]
    subprocess.run(['curl.exe', '-sS', '--fail', '--retry', '2', '--max-time', '90',
                    '-o', str(dest), url], check=True)
    with Image.open(dest) as im:
        im.verify()
    sha1 = hashlib.sha1(dest.read_bytes()).hexdigest()
    if url == info['url'].split('?')[0]:
        assert sha1 == info['sha1'], (kind, sha1, info['sha1'])
    result = {
        'cloud_type': kind, 'file_title': page['title'], 'source_page': info['descriptionurl'],
        'download_url': url, 'retrieved': '2026-10-08', 'author': ext.get('Artist', ''),
        'license': ext.get('LicenseShortName', ''), 'license_url': ext.get('LicenseUrl', ''),
        'description': ext.get('ImageDescription', ''), 'photo_date': ext.get('DateTimeOriginal', ''),
        'width': info.get('thumbwidth', info['width']), 'height': info.get('thumbheight', info['height']), 'sha1': sha1,
        'original_sha1': info['sha1'],
        'alterations': 'Commons 1280px preview where available. Proportional resizing only; no cropping or colour changes in this gallery.',
        'match': 'Approximate visual proxy: cumulus fractus; vapor_cluster is a simulation state, not a WMO species.' if kind == 'vapor_cluster' else 'Photograph identified by its source as this cloud type; visual reference, not a calibrated measurement.',
        'commons_extmetadata': ext,
    }
    metadata.write_text(json.dumps(result, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    print(kind, info['width'], info['height'], result['license'], flush=True)
    return result

if __name__ == '__main__':
    with ThreadPoolExecutor(max_workers=2) as pool:
        references = list(pool.map(download, FILES.items()))
    (ROOT / 'references.json').write_text(json.dumps(references, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    print('Verified photographs:', len(references))
