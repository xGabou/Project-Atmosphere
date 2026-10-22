"""T259: build reference silhouette masks and overlays from the annotated photographs.
Usage: python -I t259_masks.py <selected photo dir (untrusted downloads)>
Copies each annotated photo to references/, writes masks/<name>.png and overlays/<name>.jpg (cloud tinted, outline red,
crop box yellow, horizon cyan) so every mask can be checked by eye."""
import json, shutil, sys
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
sys.path.insert(0, str(Path(__file__).parent))
import t259_silhouette as sil

here = Path(__file__).parent
src = Path(sys.argv[1])
ann = json.loads((here / "t259-reference-annotations.json").read_text(encoding="utf-8"))["references"]
only = sys.argv[2] if len(sys.argv) > 2 else None
for d in ("references", "masks", "overlays"): (here / d).mkdir(exist_ok=True)
for name, cfg in ann.items():
    if only and only not in name: continue
    img = Image.open(src / f"{name}.jpg").convert("RGB")
    shutil.copyfile(src / f"{name}.jpg", here / "references" / f"{name}.jpg")
    m = sil.photo_mask(img, cfg)
    Image.fromarray(m.astype(np.uint8) * 255).save(here / "masks" / f"{name}.png")
    a = np.asarray(img).astype(np.float32)
    tint = a.copy(); tint[m] = .55 * a[m] + .45 * np.array([255, 120, 0])
    edge = m & ~(np.roll(m, 1, 0) & np.roll(m, -1, 0) & np.roll(m, 1, 1) & np.roll(m, -1, 1))
    tint[edge] = [255, 0, 0]
    o = Image.fromarray(tint.astype(np.uint8)); dr = ImageDraw.Draw(o); w, h = o.size
    x0, y0, x1, y1 = cfg["crop"]; dr.rectangle([x0 * w, y0 * h, x1 * w - 1, y1 * h - 1], outline=(255, 255, 0), width=2)
    dr.line([(0, cfg.get("horizon", 1) * h), (w, cfg.get("horizon", 1) * h)], fill=(0, 255, 255), width=2)
    o.save(here / "overlays" / f"{name}.jpg", quality=88)
    print(name, int(m.sum()))
