# usage: t241_gsheet.py <runlog> <out.png> [types] [cols]
import sys, os
from PIL import Image, ImageDraw

log, out = sys.argv[1], sys.argv[2]
D = None
types = []
for line in open(log, encoding='utf-8', errors='replace'):
    k = line.find('file=')
    if k >= 0 and 'T241G_' in line[k:]:
        path = line[k + 5:].split()[0].replace(chr(92), '/')
        D = os.path.dirname(path)
    j = line.find('T241G_SPAWN type=')
    if j >= 0:
        types.append(line[j + 17:].split()[0])
if len(sys.argv) > 3 and sys.argv[3]:
    types = sys.argv[3].split(',')
cols = sys.argv[4].split(',') if len(sys.argv) > 4 else ['SIDE_N', 'SIDE_P', 'UNDER_N', 'UNDER_P', 'ABOVE_N', 'ABOVE_P']
W, H = 320, 180
sheet = Image.new('RGB', (len(cols) * (W + 4) + 150, len(types) * (H + 4)), (255, 255, 255))
dr = ImageDraw.Draw(sheet)
for r, t in enumerate(types):
    dr.text((4, r * (H + 4) + H // 2), t, fill=(0, 0, 0))
    for c, col in enumerate(cols):
        f = os.path.join(D, f'T241G_{t}_{col}.png')
        if os.path.exists(f):
            im = Image.open(f).convert('RGB')
            im.thumbnail((W, H))
            sheet.paste(im, (150 + c * (W + 4), r * (H + 4)))
sheet.save(out)
print(D, types)
