"""Pixel-scale grain inside the cloud: RMS of (L - gaussian(L, 1.5 px)) over the eroded cloud mask / mean L."""
import sys, json, os
import numpy as np
from PIL import Image
from scipy import ndimage as ndi
for path in sys.argv[1:]:
    a = np.asarray(Image.open(path).convert('RGB')).astype(np.float32) / 255.0
    mx, mn = a.max(-1), a.min(-1)
    sat = (mx - mn) / np.maximum(mx, 1e-4)
    L = a @ np.array([0.2126, 0.7152, 0.0722], np.float32)
    m = ndi.binary_erosion((sat < 0.24) & (mx > 0.42), iterations=6)
    hp = L - ndi.gaussian_filter(L, 1.5)
    g = float(np.sqrt((hp[m] ** 2).mean()) / max(L[m].mean(), 1e-3)) if m.any() else float('nan')
    print(json.dumps({'file': os.path.basename(path), 'grain': round(g, 4), 'cloudPx': int(m.sum())}))
