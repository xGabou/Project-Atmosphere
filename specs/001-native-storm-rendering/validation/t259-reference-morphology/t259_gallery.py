"""T259 photographic reference gallery: annotated anatomy, masks, comparisons, attribution.
Usage: python -I t259_gallery.py <selected photo dir (untrusted downloads, with selection.json)>
Writes annotated/<name>.jpg and index.html. Photos are Wikimedia Commons files under the licences listed in the page;
overlays, masks and annotations are adaptations under the same licence as their source photo."""
import html, json, re, shutil, sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

here = Path(__file__).parent
src = Path(sys.argv[1])


def font(n):
    try: return ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", n)
    except OSError: return ImageFont.load_default()


def strip(s): return re.sub(r"<[^>]+>", "", s or "").strip()


def annotate(name, anatomy, note):
    im = Image.open(here / "references" / f"{name}.jpg").convert("RGB")
    w, h = im.size
    d = ImageDraw.Draw(im); f = font(max(14, w // 55))
    for label, (x, y) in anatomy.items():
        px, py = x * w, y * h
        d.ellipse([px - 7, py - 7, px + 7, py + 7], outline=(255, 40, 40), width=3)
        tx = min(px + 12, w - 10 * len(label) - 10); ty = max(4, py - 22)
        text = label.replace("_", " ")
        bb = d.textbbox((tx, ty), text, font=f)
        d.rectangle([bb[0] - 3, bb[1] - 2, bb[2] + 3, bb[3] + 2], fill=(0, 0, 0))
        d.text((tx, ty), text, fill=(255, 230, 80), font=f)
    if note:
        bb = d.textbbox((8, h - 30), note, font=font(max(12, w // 75)))
        d.rectangle([bb[0] - 4, bb[1] - 3, bb[2] + 4, bb[3] + 3], fill=(0, 0, 0)); d.text((8, h - 30), note, fill=(200, 230, 255), font=font(max(12, w // 75)))
    (here / "annotated").mkdir(exist_ok=True)
    im.save(here / "annotated" / f"{name}.jpg", quality=88)


def main():
    ann = json.loads((here / "t259-reference-annotations.json").read_text(encoding="utf-8"))
    sel = {s["file"][:-4]: s for s in json.loads((src / "selection.json").read_text(encoding="utf-8"))}
    summary = json.loads((here / "t259-summary.json").read_text(encoding="utf-8"))
    for name, cfg in ann.get("anatomy_only", {}).items():
        if not (here / "references" / f"{name}.jpg").exists(): shutil.copyfile(src / f"{name}.jpg", here / "references" / f"{name}.jpg")
        annotate(name, cfg["anatomy"], cfg.get("note", ""))
    for name, cfg in ann["references"].items():
        annotate(name, cfg.get("anatomy", {}), f"view: {cfg.get('view')}; truncated: {cfg.get('truncated')}; base visible: {cfg.get('base_visible')}")
    esc = html.escape
    css = ("<style>body{font-family:Segoe UI,sans-serif;background:#10151d;color:#dde;margin:16px;max-width:1500px}a{color:#8cf}"
           "table{border-collapse:collapse;margin:8px 0}td,th{border:1px solid #334;padding:3px 7px;font-size:13px;vertical-align:top}"
           "img{max-width:100%}.row{display:flex;gap:8px;flex-wrap:wrap}.row img{height:220px;width:auto}.note{color:#aab;font-size:13px}h2{margin-top:30px}</style>")
    parts = [f"<!doctype html><meta charset=utf-8><title>T259 storm references</title>{css}",
             "<h1>T259 reference-driven storm morphology &mdash; photographic references</h1>",
             "<p class=note>Real storms from Wikimedia Commons (licences and authors below). For each: annotated anatomy, silhouette mask overlay (orange fill, red outline; yellow = storm crop, cyan = horizon/haze cut) and the binary mask used for the measurements. Report: <a href='../t259-reference-morphology.md'>t259-reference-morphology.md</a>.</p>"]
    cats = [("calvus", "Cumulonimbus_calvus", "Cumulonimbus calvus"), ("capillatus", "Cumulonimbus_capillatus", "Cumulonimbus capillatus"), ("supercell", "Supercells", "Supercell thunderstorms")]
    for key, prefix, title in cats:
        parts.append(f"<h2>{esc(title)}</h2>")
        parts.append(f"<p><img src='figures/silhouettes-{key}.png'></p><p><img src='figures/width-profile-{key}.png' style='max-width:900px'></p>")
        for name, cfg in ann["references"].items():
            if not name.startswith(prefix): continue
            parts.append(f"<h3>{esc(name)}</h3><div class=row><img src='annotated/{name}.jpg'><img src='overlays/{name}.jpg'><img src='masks/{name}.png' style='background:#000'></div>")
        for name, cfg in ann.get("anatomy_only", {}).items():
            if name.startswith(prefix):
                parts.append(f"<h3>{esc(name)} (anatomy only)</h3><div class=row><img src='annotated/{name}.jpg'></div><p class=note>{esc(cfg.get('note', ''))}</p>")
    # Measurement table.
    keys = ["aspect", "max_width_height", "top_flat_fraction", "crown_summits", "notches", "deepest_notch", "anvil_overhang_downwind", "anvil_overhang_upwind",
            "anvil_thickness_mean", "anvil_thickness_cv", "lean", "convexity_deficit"]
    parts.append("<h2>Measurements (normalised to silhouette height)</h2><p class=note>Mean (sd) per source; width metrics exclude truncated references. Silhouette height of a photograph is the VISIBLE depth (bases are often hidden), so absolute width/height ratios of references are upper bounds; see the report for the robust, height-independent comparisons.</p><table><tr><th>metric</th>")
    for key, _, title in cats: parts.append(f"<th colspan=3>{esc(title)}</th>")
    parts.append("</tr><tr><th></th>" + "<th>references</th><th>B v3</th><th>rejected C</th>" * 3 + "</tr>")
    for k in keys:
        parts.append(f"<tr><td>{esc(k)}</td>")
        for key, _, _ in cats:
            for s in ("reference", "B v3", "C (rejected)"):
                v = summary[key][s].get(k)
                parts.append(f"<td>{v['mean']} ({v['sd']}) n={v['n']}</td>" if v else "<td>-</td>")
        parts.append("</tr>")
    parts.append("</table>")
    # Attribution.
    parts.append("<h2>Sources and licences</h2><table><tr><th>file</th><th>Commons page</th><th>author</th><th>licence</th><th>date</th></tr>")
    for name, s in sorted(sel.items()):
        parts.append(f"<tr><td>{esc(name)}</td><td><a href='{esc(s['page'] or '')}'>{esc(s['title'])}</a></td><td>{esc(strip(s.get('artist')))[:120]}</td><td>{esc(s.get('license') or '')}</td><td>{esc(strip(s.get('date')))[:30]}</td></tr>")
    parts.append("</table><p class=note>Annotations, overlays and masks are adaptations of the listed photographs and are shared under the same licence as each source.</p>")
    (here / "index.html").write_text("\n".join(parts), encoding="utf-8")
    (here / "t259-sources.json").write_text(json.dumps(list(sel.values()), indent=1), encoding="utf-8")
    print("ok", len(sel))


if __name__ == "__main__":
    main()
