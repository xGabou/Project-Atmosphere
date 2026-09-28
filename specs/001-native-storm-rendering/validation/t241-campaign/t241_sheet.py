# usage: t241_sheet.py <runlog> <out.png> ARM1,ARM2,... [poses]
import sys, os
import numpy as np
from PIL import Image

log, out, arms = sys.argv[1], sys.argv[2], sys.argv[3].split(',')
poses = sys.argv[4].split(',') if len(sys.argv) > 4 else ['SIDE', 'LOWSIDE', 'FAR', 'LATERAL', 'ABOVE', 'TOWERLOW']
D = None
for line in open(log, encoding='utf-8', errors='replace'):
    k = line.find('file=')
    if k >= 0 and 'T241_' in line[k:]:
        path = line[k + 5:].split()[0].replace(chr(92), '/')
        D = os.path.dirname(path)
        break
tiles = []
W, H = 330, 250
for p in poses:
    for a in arms:
        f = os.path.join(D, f'T241_{a}_{p}.png')
        if not os.path.exists(f):
            tiles.append(None)
            continue
        im = Image.open(f).convert('RGB')
        m = np.asarray(im).astype(int)
        r, g, b = m[..., 0], m[..., 1], m[..., 2]
        cl = ((b < 235) & (abs(g - r) < 45) & (r > 60)) | ((r > 200) & (g < 80)) | ((g > 150) & (r < 150) & (b < 170))
        if p in ('LOWSIDE', 'TOWERLOW'):
            cl[int(m.shape[0] * 0.55):, :] = False
        ys, xs = np.nonzero(cl)
        if len(xs) > 50 and p != 'ABOVE':
            pad = 40
            im = im.crop((max(0, xs.min() - pad), max(0, ys.min() - pad),
                          min(m.shape[1], xs.max() + pad), min(m.shape[0], ys.max() + pad)))
        im.thumbnail((W, H))
        tiles.append(im)
sheet = Image.new('RGB', (len(arms) * (W + 5), len(poses) * (H + 5)), (255, 255, 255))
for k, t in enumerate(tiles):
    if t is not None:
        sheet.paste(t, ((k % len(arms)) * (W + 5), (k // len(arms)) * (H + 5)))
sheet.save(out)
print(D)
