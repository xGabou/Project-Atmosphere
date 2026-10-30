"""Side-by-side review sheets for a ProductionCloudCapture set (stabilisation log).

usage: python -I ab_gallery.py <screenshots/prodcapture/<set> dir> <out dir> <arm> [arm ...]

One PNG per cloud type: rows = view x time, columns = arms (labelled), each frame cropped to its central 80 %, plus an
index.html listing the sheets. Frames are named <type>__<view>__t<time>__<arm>.png.
"""
import sys, os, glob, collections, html
from PIL import Image, ImageDraw

src, out, arms = sys.argv[1], sys.argv[2], sys.argv[3:]
os.makedirs(out, exist_ok=True)
frames = collections.defaultdict(dict)
for p in glob.glob(os.path.join(src, '*.png')):
    parts = os.path.basename(p)[:-4].split('__')
    if len(parts) == 4:
        frames[parts[0]][(parts[1], parts[2], parts[3])] = p

W, H, LABEL = 640, 360, 22
index = []
for cloud in sorted(frames):
    rows = sorted({(v, t) for (v, t, a) in frames[cloud]}, key=lambda r: (r[0], -int(r[1][1:])))
    sheet = Image.new('RGB', (W * len(arms), (H + LABEL) * len(rows) + LABEL), (24, 26, 32))
    d = ImageDraw.Draw(sheet)
    for c, a in enumerate(arms):
        d.text((c * W + 8, 4), a, fill=(255, 220, 120))
    for r, (v, t) in enumerate(rows):
        y = LABEL + r * (H + LABEL)
        d.text((8, y + 4), f"{v}  {t}", fill=(220, 220, 220))
        for c, a in enumerate(arms):
            p = frames[cloud].get((v, t, a))
            if p is None:
                continue
            im = Image.open(p).convert('RGB')
            w, h = im.size
            im = im.crop((int(w * 0.1), int(h * 0.1), int(w * 0.9), int(h * 0.9))).resize((W, H))
            sheet.paste(im, (c * W, y + LABEL))
    name = f"{cloud}.png".replace("#", "_")  # "type#n" instances: "#" would start a URL fragment
    sheet.save(os.path.join(out, name))
    index.append(name)

with open(os.path.join(out, 'index.html'), 'w', encoding='utf-8') as f:
    f.write('<html><body style="background:#181a20;color:#ddd;font-family:sans-serif">')
    f.write(f'<h2>{html.escape(os.path.basename(src))}: {" vs ".join(map(html.escape, arms))}</h2>')
    for n in index:
        f.write(f'<h3>{html.escape(n[:-4])}</h3><img src="{n}" style="max-width:100%"><br>')
    f.write('</body></html>')
print(len(index), 'sheets ->', out)
