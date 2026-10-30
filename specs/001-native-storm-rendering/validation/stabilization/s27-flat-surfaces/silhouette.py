"""Silhouette bumpiness at cloud-relative scales (same code for photographs and renders).

mask: largest low-saturation bright component, holes filled. H: mask height. For r = k% of H, bumps(r) = share of the
mask area removed by a morphological opening with a disk of radius r - the area that sits in protrusions narrower
than ~2r. A pile of a few big smooth balls loses almost nothing at small r; a cauliflower outline loses a lot.
"""
import sys, json, os
import numpy as np
from PIL import Image
from scipy import ndimage as ndi


def disk(r):
    y, x = np.mgrid[-r:r + 1, -r:r + 1]
    return x * x + y * y <= r * r


def mask_of(path, max_side=900):
    im = Image.open(path).convert('RGB')
    s = max_side / max(im.size)
    if s < 1:
        im = im.resize((int(im.size[0] * s), int(im.size[1] * s)), Image.LANCZOS)
    a = np.asarray(im).astype(np.float32) / 255.0
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    mx, mn = a.max(-1), a.min(-1)
    sat = (mx - mn) / np.maximum(mx, 1e-4)
    sky = (b > r + 0.06) & (sat > 0.22)
    cloud = (sat < 0.24) & (mx > 0.42) & ~sky
    lab, n = ndi.label(cloud)
    if n == 0:
        return None
    sizes = ndi.sum(cloud, lab, range(1, n + 1))
    return ndi.binary_fill_holes(lab == (1 + int(np.argmax(sizes))))


for path in sys.argv[1:]:
    m = mask_of(path)
    if m is None:
        print(json.dumps({'file': os.path.basename(path), 'error': 'no mask'}))
        continue
    ys, xs = np.nonzero(m)
    H = ys.max() - ys.min() + 1
    area = m.sum()
    out = {'file': path.replace('\\', '/').split('/')[-2] + '/' + os.path.basename(path), 'H': int(H)}
    for k in (1, 2, 4, 8):
        rad = max(1, int(round(k / 100.0 * H)))
        opened = ndi.binary_opening(m, structure=disk(rad))
        out[f'bumps{k}'] = round(float((m & ~opened).sum()) / area, 4)
    print(json.dumps(out))
