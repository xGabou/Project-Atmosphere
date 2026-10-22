"""T259: normalised, view-matched silhouette comparison of photographic references vs B v3 vs rejected candidate C.

Usage: python -I t259_compare.py <B v3 gallery dir (t257 native/ab-v3)> <T258 capture folder>
Writes t259-metrics.json (every silhouette), t259-summary.json (per category statistics) and figures/.
Model views are the crosswind/side family that matches ground photographs: side, opposite_side, low_angle,
portrait, crosswind_left, crosswind_right. Every silhouette is normalised to height 1 and oriented with its
anvil/top mass to the right. Truncated references (storm leaves the frame) are excluded from width metrics.
"""
import json, sys, statistics as st
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont
sys.path.insert(0, str(Path(__file__).parent))
import t259_silhouette as sil

here = Path(__file__).parent
CATS = {"calvus": ("Cumulonimbus_calvus", "cumulonimbus_calvus"), "capillatus": ("Cumulonimbus_capillatus", "cumulonimbus_capillatus"),
        "supercell": ("Supercells", "supercell")}
SEEDS = ["20261008", "20268927", "20276846"]
VIEWS = ["side", "opposite_side", "low_angle", "portrait", "crosswind_left", "crosswind_right"]
WIDTH_KEYS = {"aspect", "base_width", "max_width", "anvil_overhang_downwind", "anvil_overhang_upwind", "waist_width"}
KEYS = ["aspect", "base_width", "waist_width", "waist_height", "max_width", "max_width_height", "top_flat_fraction", "crown_summits",
        "anvil_depth", "anvil_overhang_downwind", "anvil_overhang_upwind", "anvil_thickness_mean", "anvil_thickness_cv", "lean",
        "notches", "deepest_notch", "convexity_deficit"]


def font(n):
    try: return ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", n)
    except OSError: return ImageFont.load_default()


def main():
    bdir, cdir = Path(sys.argv[1]), Path(sys.argv[2])
    ann = json.loads((here / "t259-reference-annotations.json").read_text(encoding="utf-8"))["references"]
    rows, shapes = [], {}
    for cat, (refprefix, mtype) in CATS.items():
        for name, cfg in ann.items():
            if not name.startswith(refprefix): continue
            m = np.asarray(Image.open(here / "masks" / f"{name}.png")) > 127
            r = dict(source="reference", category=cat, name=name, truncated=cfg.get("truncated"), view=cfg.get("view"), **sil.metrics(m))
            rows.append(r); shapes.setdefault((cat, "reference"), []).append((name, sil.normalise(m)))
        for seed in SEEDS:
            for v in VIEWS:
                for model, f in (("B v3", bdir / mtype / seed / f"{v}__raw.png"), ("C (rejected)", cdir / f"T258_{mtype}__{seed}__{v}__raw.png")):
                    if not f.is_file(): continue
                    m = sil.model_mask(f)
                    if not m.any(): continue
                    r = dict(source=model, category=cat, name=f"{mtype}/{seed}/{v}", truncated="none", view=v, **sil.metrics(m))
                    rows.append(r)
                    if v in ("side", "low_angle"): shapes.setdefault((cat, model), []).append((f"{seed}/{v}", sil.normalise(m)))
    (here / "t259-metrics.json").write_text(json.dumps(rows, indent=1), encoding="utf-8")
    summary = {}
    for cat in CATS:
        summary[cat] = {}
        for src in ("reference", "B v3", "C (rejected)"):
            rs = [r for r in rows if r["category"] == cat and r["source"] == src]
            s = dict(n=len(rs))
            for k in KEYS:
                vals = [r[k] for r in rs if r.get(k) is not None and not (k in WIDTH_KEYS and r["truncated"] not in ("none", None))]
                if vals: s[k] = dict(mean=round(st.mean(vals), 4), sd=round(st.pstdev(vals), 4), min=round(min(vals), 4), max=round(max(vals), 4), n=len(vals))
            prof = [r["width_profile"] for r in rs if r["truncated"] in ("none", None)]
            if prof: s["width_profile_mean"] = [round(float(x), 4) for x in np.mean(np.array(prof), 0)]
            summary[cat][src] = s
    (here / "t259-summary.json").write_text(json.dumps(summary, indent=1), encoding="utf-8")
    figures(shapes, summary)
    for cat in CATS:
        print(cat)
        for k in ("aspect", "base_width", "waist_width", "max_width", "max_width_height", "top_flat_fraction", "crown_summits", "anvil_depth",
                  "anvil_overhang_downwind", "anvil_thickness_mean", "lean", "notches", "deepest_notch", "convexity_deficit"):
            print(f"  {k:26s}", "  ".join(f"{src[:6]:6s} {summary[cat][src].get(k, {}).get('mean', '-')!s:>7}" for src in ("reference", "B v3", "C (rejected)")))


def figures(shapes, summary):
    out = here / "figures"; out.mkdir(exist_ok=True)
    f = font(16); fs = font(13)
    for cat in CATS:
        cols = [("reference", "Photographic references (silhouette masks)"), ("B v3", "B v3 (native side / low-angle)"), ("C (rejected)", "Rejected C (native side / low-angle)")]
        H = 120
        rowsimg = []
        for src, title in cols:
            items = shapes.get((cat, src), [])
            tiles = []
            for name, m in items:
                im = Image.fromarray((m * 255).astype(np.uint8)).resize((max(1, int(m.shape[1] * H / m.shape[0])), H))
                tiles.append((name, im))
            rowsimg.append((title, tiles))
        W = 1800
        lines = []
        for title, tiles in rowsimg:
            x, line, cur = 0, [], []
            for name, im in tiles:
                if x + im.width + 12 > W: line.append(cur); cur, x = [], 0
                cur.append((name, im)); x += im.width + 12
            if cur: line.append(cur)
            lines.append((title, line))
        total_h = sum(30 + len(l) * (H + 22) for _, l in lines) + 10
        sheet = Image.new("RGB", (W, total_h), (16, 21, 29)); d = ImageDraw.Draw(sheet); y = 6
        for title, line in lines:
            d.text((8, y), title, fill=(255, 220, 120), font=f); y += 26
            for cur in line:
                x = 8
                for name, im in cur:
                    sheet.paste(Image.merge("RGB", [im, im, im]), (x, y + 16)); d.text((x, y), name.replace("Cumulonimbus_", "").replace("Supercells_", "sc_"), fill=(140, 200, 255), font=fs)
                    x += im.width + 12
                y += H + 22
        sheet.save(out / f"silhouettes-{cat}.png")
        # Width profile chart: width / height at 19 heights (base at the bottom), references thin, means thick.
        cw, ch = 900, 520
        img = Image.new("RGB", (cw, ch), (250, 250, 250)); d = ImageDraw.Draw(img)
        x0, y0, x1, y1 = 70, 20, cw - 20, ch - 50
        maxw = 3.0
        for g in range(0, 7):
            gx = x0 + (x1 - x0) * g * .5 / maxw; d.line([(gx, y0), (gx, y1)], fill=(220, 220, 220)); d.text((gx - 8, y1 + 6), f"{g * .5:.1f}", fill=(60, 60, 60), font=fs)
        d.text((x0 + 200, ch - 22), "silhouette width / silhouette height", fill=(30, 30, 30), font=fs)
        d.text((4, y0), "top", fill=(30, 30, 30), font=fs); d.text((4, y1 - 14), "base", fill=(30, 30, 30), font=fs)
        hs = np.linspace(.05, .95, 19)
        def curve(p, col, wdt):
            pts = [(x0 + (x1 - x0) * min(v, maxw) / maxw, y1 - (y1 - y0) * h) for v, h in zip(p, hs)]
            d.line(pts, fill=col, width=wdt)
        import json as _j
        rs = _j.loads((here / "t259-metrics.json").read_text(encoding="utf-8"))
        for r in rs:
            if r["category"] != cat or r["truncated"] not in ("none", None): continue
            col = {"reference": (150, 150, 150), "B v3": (255, 170, 170), "C (rejected)": (170, 200, 255)}[r["source"]]
            curve(r["width_profile"], col, 1)
        for src, col in (("reference", (20, 20, 20)), ("B v3", (210, 30, 30)), ("C (rejected)", (30, 80, 220))):
            p = summary[cat][src].get("width_profile_mean")
            if p: curve(p, col, 4)
        d.text((x1 - 330, y0 + 4), "black: references (mean)  red: B v3  blue: rejected C", fill=(30, 30, 30), font=fs)
        img.save(out / f"width-profile-{cat}.png")


if __name__ == "__main__":
    main()
