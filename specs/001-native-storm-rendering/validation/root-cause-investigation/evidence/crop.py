import sys
from PIL import Image
src, x, y, w, h, s, out = sys.argv[1], *map(int, sys.argv[2:7]), sys.argv[7]
im = Image.open(src).convert("RGB").crop((x, y, x+w, y+h))
im.resize((w*s, h*s), Image.NEAREST).save(out)
