# Lighting metrics on the rca session: cloud mask = pixels where m20 and m20_enc differ (only cloud pixels change).
import sys, numpy as np
from PIL import Image
S = "C:/Users/matga/Downloads/Project-Atmosphere/run/screenshots/cloudpreview/rca_s2609101_t18d2_"
def L(n):
    a = np.asarray(Image.open(S + n + ".png").convert("RGB"), dtype=np.float32)[40:]
    return a
for v in ["eside", "cthree", "lunder", "close", "ground800"]:
    try:
        base = L(v + "_m20"); enc = L(v + "_m20_enc")
    except FileNotFoundError:
        continue
    mask = np.abs(base - enc).max(axis=2) > 4
    print(f"== {v}: cloud px {mask.sum()}")
    for arm in ["m20", "m20_enc", "m42", "m42_enc", "m44", "m44_enc", "m20_ext647", "m42_ext647", "m20_ext647_enc", "m20_qhigh"]:
        try:
            a = L(v + "_" + arm)
        except FileNotFoundError:
            continue
        y = (0.2126 * a[..., 0] + 0.7152 * a[..., 1] + 0.0722 * a[..., 2])[mask]
        # local structure: mean abs Laplacian of luma inside the mask (fine relief readability)
        Y = 0.2126 * a[..., 0] + 0.7152 * a[..., 1] + 0.0722 * a[..., 2]
        lap = np.abs(4 * Y[1:-1, 1:-1] - Y[:-2, 1:-1] - Y[2:, 1:-1] - Y[1:-1, :-2] - Y[1:-1, 2:])
        rel = lap[mask[1:-1, 1:-1]].mean()
        print(f"  {arm:16s} mean {y.mean():6.1f}  p10 {np.percentile(y,10):6.1f}  p50 {np.percentile(y,50):6.1f}  p90 {np.percentile(y,90):6.1f}  max {y.max():6.1f}  lap {rel:5.2f}")
