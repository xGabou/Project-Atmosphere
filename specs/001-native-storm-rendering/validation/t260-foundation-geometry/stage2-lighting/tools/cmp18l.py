import sys, os
from PIL import Image, ImageChops
src = sys.argv[1]
for f in sorted(os.listdir(src)):
    for tag in ("_exact", "_repeat"):
        if f.endswith(tag + ".png"):
            a = Image.open(os.path.join(src, f)).convert("RGB"); b = Image.open(os.path.join(src, f.replace(tag, ""))).convert("RGB")
            w, h = a.size
            d = ImageChops.difference(a.crop((0, 40, w, h)), b.crop((0, 40, w, h)))
            px = sum(1 for p in d.getdata() if p != (0, 0, 0))
            print(f"{f:55s} vs base: differing pixels below HUD = {px}  maxdiff = {max(d.getextrema(), key=lambda e: e[1])[1]}")
