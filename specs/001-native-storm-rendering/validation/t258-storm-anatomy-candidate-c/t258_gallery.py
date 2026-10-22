"""T258 candidate C gallery: B v3 vs C0 (core) / C1 (+ major growth) / C2 (+ secondary) / C3 (complete body and canopy).

Usage: python -I t258_gallery.py <capture folder> <capture log> <diagnostic dir written by stormAnatomyCDiagnostics> <gallery dir>
Copies every native frame unchanged, places the per-view comparison panels and native 4x close pairs, aggregates the
metrics and writes index/type/seed pages, contact sheets, summary.json and gallery-audit.json.
Evidence for visual review only: it never accepts candidate C.
"""
import json, re, shutil, sys, html
from pathlib import Path
from statistics import mean, median
from PIL import Image, ImageDraw, ImageFont

TYPES = ["cumulonimbus_calvus", "cumulonimbus_capillatus", "supercell"]
SEEDS = ["20261008", "20268927", "20276846"]
VIEWS = ["side", "opposite_side", "front", "back", "above", "below", "low_angle", "far", "close", "upwind", "downwind",
         "crosswind_left", "crosswind_right", "tower", "transition", "canopy_anvil", "updraft_oblique", "portrait", "portrait_front"]
ZOOM = ["side", "portrait_front", "above", "transition"]
ARMS = [("b3_", "B v3"), ("c0_", "C0 core"), ("c1_", "C1 + major"), ("c2_", "C2 + secondary"), ("", "C3 complete")]
SIDE = {"side", "opposite_side", "close", "far", "crosswind_left", "crosswind_right", "portrait"}


def font(n):
    try: return ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", n)
    except OSError: return ImageFont.load_default()


def parse_log(path):
    gpu, parity, traversal, framing, poses, lens, rejected, fields = {}, {}, [], {}, {}, {}, [], {}
    for line in Path(path).read_text(errors="replace").splitlines():
        m = re.search(r"T258_GPU shot=(\S+) samples=(\d+) .*medianMs=([\d.]+) p95Ms=([\d.]+) maxMs=([\d.]+)", line)
        if m: gpu[m[1]] = dict(samples=int(m[2]), p50=float(m[3]), p95=float(m[4]), max=float(m[5]))
        m = re.search(r"T258_PARITY shot=(\S+) samples=(\d+) occupied=(\d+) maxAbsError=(\S+) .*pass=(\w+)", line)
        if m: parity[m[1]] = dict(samples=int(m[2]), occupied=int(m[3]), max_abs_error=float(m[4]), passed=m[5] == "true")
        m = re.search(r"T258_TRAVERSAL_PAIR .*maxAbsRGBA=(\S+) .*pass=(\w+)", line)
        if m: traversal.append(dict(max_abs=float(m[1]), passed=m[2] == "true"))
        m = re.search(r"T258_FRAMING shot=(\S+) occupiedEdgePixels=(\d+)", line)
        if m: framing[m[1]] = int(m[2])
        m = re.search(r"T258_POSE shot=(\S+) version=(\d+) signature=(\S+) .*camera=\(([^)]+)\) aim=\(([^)]+)\)", line)
        if m: poses[m[1]] = dict(version=int(m[2]), signature=m[3], camera=m[4], aim=m[5])
        m = re.search(r"T258_LENS shot=(\S+) optionFov=(\S+) shotFov=(\S+) fovModifier=(\S+) effectiveFov=(\S+) zoom=(\S+)", line)
        if m: lens[m[1]] = dict(option=float(m[2]), shot=float(m[3]), modifier=float(m[4]), effective=float(m[5]), zoom=float(m[6]))
        m = re.search(r"T258_CAPTURE_REJECTED shot=(\S+) reason=(.*)", line)
        if m: rejected.append(dict(shot=m[1], reason=m[2]))
        m = re.search(r"T258_FIELD shot=(\S+) candidate=C level=(\S+) voxel=(\S+) dims=(\[[^\]]+\]) .*texture=(\d+)x(\d+)", line)
        if m: fields[m[1]] = dict(level=m[2], voxel=float(m[3]), dims=m[4], texture=f"{m[5]}x{m[6]}")
    return gpu, parity, traversal, framing, poses, lens, rejected, fields


def avg(vals):
    vals = [v for v in vals if v is not None]
    return round(mean(vals), 4) if vals else None


def shot(t, s, pre, v): return f"T258_{t}__{s}__{pre}{v}"


def main():
    src, log, diag, out = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3]), Path(sys.argv[4])
    out.mkdir(parents=True, exist_ok=True)
    gpu, parity, traversal, framing, poses, lens, rejected, fields = parse_log(log)
    rows = [json.loads(l) for l in (diag / "t258-diagnostic-metrics.jsonl").read_text().splitlines() if l.strip()]
    geo = {(r["type"], str(r["seed"])): r for r in rows if r["kind"] == "geometry"}
    views = {(r["type"], str(r["seed"]), r["view"]): r for r in rows if r["kind"] == "view"}
    missing, copied = [], 0
    expected = [shot("supercell", "20261008", "", "attribution_intervals"), shot("supercell", "20261008", "", "attribution_exact")]
    for t in TYPES:
        for s in SEEDS:
            d = out / t / s; d.mkdir(parents=True, exist_ok=True)
            names = [shot(t, s, p, v) for v in VIEWS for p, _ in ARMS] + [shot(t, s, p, "z4_" + v) for v in ZOOM for p in ("b3_", "")]
            expected += names
            for n in names:
                raw, screen = src / f"{n}__raw.png", src / f"{n}.png"
                if not raw.is_file(): missing.append(n); continue
                local = n.split("__", 2)[2]
                shutil.copyfile(raw, d / f"{local}__raw.png"); copied += 1
                if screen.is_file(): shutil.copyfile(screen, d / f"{local}.png")
            for v in VIEWS:
                c = diag / f"cmp_{t}__{s}__{v}.png"
                if c.is_file(): shutil.copyfile(c, d / f"cmp_{v}.png")
            for v in ZOOM:
                z = diag / f"zoom4_{t}__{s}__{v}.png"
                if z.is_file(): shutil.copyfile(z, d / f"zoom4_{v}.png")
    # Identical camera for all five arms of every view.
    camera_mismatch = []
    for t in TYPES:
        for s in SEEDS:
            for v in VIEWS:
                ref = poses.get(shot(t, s, "", v))
                for p, _ in ARMS:
                    q = poses.get(shot(t, s, p, v))
                    if ref and q and (q["camera"] != ref["camera"] or q["aim"] != ref["aim"]): camera_mismatch.append(shot(t, s, p, v))
    # B v3 arms must be the frozen B v3 geometry.
    base = json.loads(Path(__file__).parent.parent.joinpath("t257-convective-growth-field", "t257-baselines.json").read_text())["baselines"]["B-v3"]["geometry_fingerprints"]
    b_mismatch = [k for k, p in poses.items() if "__b3_" in k and base.get("/".join(re.match(r"T258_(.+?)__(\d+)__", k).groups()), {}).get("signature") != p["signature"]]
    perf = {}
    for t in TYPES:
        for arm, label in ARMS:
            vals = [gpu[shot(t, s, arm, v)]["p95"] for s in SEEDS for v in VIEWS if shot(t, s, arm, v) in gpu]
            perf.setdefault(t, {})[label] = dict(n=len(vals), p95_max=max(vals) if vals else None, p95_median=round(median(vals), 3) if vals else None,
                                                over_10ms=sum(v > 10 for v in vals))
        for arm, label in (("b3_z4_", "B v3 4x"), ("z4_", "C3 4x")):
            vals = [gpu[shot(t, s, "", arm + v)]["p95"] for s in SEEDS for v in ZOOM if shot(t, s, "", arm + v) in gpu]
            perf[t][label] = dict(n=len(vals), p95_max=max(vals) if vals else None, p95_median=round(median(vals), 3) if vals else None, over_10ms=sum(v > 10 for v in vals))
    summary = {}
    for t in TYPES:
        vs = [r for (tt, s, v), r in views.items() if tt == t]
        side = [r for r in vs if r["view"] in SIDE]
        plan = [r for r in vs if r["view"] in ("above", "below")]
        gs = [g for (tt, s), g in geo.items() if tt == t]
        summary[t] = dict(
            views=len(vs),
            iou_cpu_native_B=avg([r["iou_cpu_native_B"] for r in vs]), iou_cpu_native_C=avg([r["iou_cpu_native_C"] for r in vs]),
            silhouette_height_px_B=avg([r["size_B"][1] for r in vs]), silhouette_height_px_C=avg([r["size_C"][1] for r in vs]),
            disp_core_to_complete_rel_B=avg([r["disp_B_rel"] for r in vs]), disp_core_to_complete_rel_C=avg([r["disp_C0_C3_rel"] for r in vs]),
            disp_core_to_major_rel_C=avg([r["disp_C0_C1_rel"] for r in vs]),
            disp_px_C0_C1=avg([r["disp_C0_C1"][0] for r in vs]), disp_px_C1_C2=avg([r["disp_C1_C2"][0] for r in vs]), disp_px_C2_C3=avg([r["disp_C2_C3"][0] for r in vs]),
            outline_lobes_B=avg([r["valleys_B"]["lobes"] for r in vs]), outline_lobes_C=avg([r["valleys_C"]["lobes"] for r in vs]),
            outline_lobes_C0=avg([r["valleys_C0"]["lobes"] for r in vs]), outline_lobes_C1=avg([r["valleys_C1"]["lobes"] for r in vs]),
            valley_depth_top3_rel_B=avg([r["valleys_B"]["mean_top3_rel"] for r in vs]), valley_depth_top3_rel_C=avg([r["valleys_C"]["mean_top3_rel"] for r in vs]),
            deepest_valley_rel_B=avg([r["valleys_B"]["deepest_rel"] for r in vs]), deepest_valley_rel_C=avg([r["valleys_C"]["deepest_rel"] for r in vs]),
            convexity_deficit_B=avg([r["convexity_deficit_B"] for r in vs]), convexity_deficit_C=avg([r["convexity_deficit_C"] for r in vs]),
            plan_convexity_deficit_B=avg([r["convexity_deficit_B"] for r in plan]), plan_convexity_deficit_C=avg([r["convexity_deficit_C"] for r in plan]),
            plan_polar_residual_B=avg([r.get("polar_residual_B") for r in plan]), plan_polar_residual_C=avg([r.get("polar_residual_C") for r in plan]),
            anvil_top_line_rms_B=avg([r["anvil_B"][1] for r in side if "anvil_B" in r]), anvil_top_line_rms_C=avg([r["anvil_C"][1] for r in side if "anvil_C" in r]),
            anvil_bottom_line_rms_B=avg([r["anvil_B"][2] for r in side if "anvil_B" in r]), anvil_bottom_line_rms_C=avg([r["anvil_C"][2] for r in side if "anvil_C" in r]),
            anvil_thickness_cv_B=avg([r["anvil_B"][3] for r in side if "anvil_B" in r]), anvil_thickness_cv_C=avg([r["anvil_C"][3] for r in side if "anvil_C" in r]),
            anvil_thickness_over_length_B=avg([r["anvil_B"][0] for r in side if "anvil_B" in r]), anvil_thickness_over_length_C=avg([r["anvil_C"][0] for r in side if "anvil_C" in r]),
            projected_components_B=avg([r["components_B"][0] for r in vs]), projected_components_C=avg([r["components_C"][0] for r in vs]),
            major_groups=avg([g["major_groups"] for g in gs]), major_height_spread=avg([g["major_height_spread"] for g in gs]),
            major_surface_buried_in_other_majors=avg([g["major_surface_buried_in_other_majors"] for g in gs]),
            upper_body_visible_B=avg([g["upper_body_visible_B"] for g in gs]), upper_body_visible_C=avg([g["upper_body_visible_C"] for g in gs]),
            components_C0_C3=[g["components_C0_C3"] for g in gs],
            canopy_core_vs_full=[g["canopy"] for g in gs] if gs and gs[0]["canopy"] else None,
            voxel=[g["voxel"] for g in gs], texture=[g["texture"] for g in gs])
    zoom_values = sorted({round(l["zoom"], 3) for k, l in lens.items() if "__z4_" in k or "__b3_z4_" in k})
    eff = sorted({round(l["effective"], 2) for k, l in lens.items() if "z4_" not in k})
    framing_bad = {k: v for k, v in framing.items() if v and "z4_" not in k}
    audit = dict(expected_arms=len(expected), captured_raw=copied, missing=missing, rejected=rejected,
                 parity=dict(arms=len(parity), passed=sum(p["passed"] for p in parity.values()), max_error=max([p["max_abs_error"] for p in parity.values()], default=None),
                             failures=[k for k, p in parity.items() if not p["passed"]]),
                 traversal=traversal, camera_mismatch=camera_mismatch, b_v3_signature_mismatch=b_mismatch,
                 framing_edge_occupied_non_zoom=framing_bad, effective_fov=eff, zoom=zoom_values,
                 c_fields=dict(arms=len(fields), voxel_range=[min([f["voxel"] for f in fields.values()], default=None), max([f["voxel"] for f in fields.values()], default=None)]),
                 performance=perf)
    (out / "summary.json").write_text(json.dumps(summary, indent=1))
    (out / "gallery-audit.json").write_text(json.dumps(audit, indent=1))
    sheets(out, views)
    pages(out, summary, audit, views, geo)
    print(json.dumps(dict(copied=copied, missing=len(missing), parity=audit["parity"], traversal=traversal, camera_mismatch=len(camera_mismatch),
                          b_mismatch=len(b_mismatch), framing_bad=len(framing_bad), eff=eff, zoom=zoom_values), indent=1))


def sheets(out, views):
    """Per type: rows = seed x view, B v3 | C3 crops from the comparison panels; plus a C0..C3 layer sheet for side views."""
    f = font(15)
    for t in TYPES:
        tiles = []
        for s in SEEDS:
            for v in VIEWS:
                p = out / t / s / f"cmp_{v}.png"
                if p.is_file(): tiles.append((s, v, Image.open(p)))
        if not tiles: continue
        cw = 360
        cols = 3
        cells = []
        for s, v, im in tiles:
            w5 = im.width // 5
            pair = Image.new("RGB", (w5 * 2, im.height))
            pair.paste(im.crop((0, 0, w5, im.height)), (0, 0)); pair.paste(im.crop((4 * w5, 0, 5 * w5, im.height)), (w5, 0))
            k = cw / pair.width
            pair = pair.resize((cw, max(1, int(pair.height * k))))
            cells.append((s, v, pair))
        rowh = max(c[2].height for c in cells) + 22
        rows = (len(cells) + cols - 1) // cols
        sheet = Image.new("RGB", (cols * (cw + 6), rows * rowh), (16, 21, 29))
        d = ImageDraw.Draw(sheet)
        for i, (s, v, im) in enumerate(cells):
            x, y = (i % cols) * (cw + 6), (i // cols) * rowh
            sheet.paste(im, (x, y + 20)); d.text((x + 4, y + 2), f"{s} {v}   left B v3 | right C3", fill=(220, 220, 220), font=f)
        sheet.save(out / f"contact-{t}-b3-vs-c3.jpg", quality=90)
        layer = []
        for s in SEEDS:
            for v in ("side", "front", "above", "portrait_front"):
                p = out / t / s / f"cmp_{v}.png"
                if p.is_file(): layer.append((s, v, Image.open(p)))
        if layer:
            w = 1500
            ims = [(s, v, im.resize((w, max(1, int(im.height * w / im.width))))) for s, v, im in layer]
            sheet = Image.new("RGB", (w, sum(im.height + 22 for _, _, im in ims)), (16, 21, 29))
            d = ImageDraw.Draw(sheet); y = 0
            for s, v, im in ims:
                d.text((4, y + 2), f"{s} {v}:  B v3 | C0 core | C1 + major | C2 + secondary | C3 complete (cyan = C0 outline)", fill=(220, 220, 220), font=f)
                sheet.paste(im, (0, y + 20)); y += im.height + 22
            sheet.save(out / f"contact-{t}-layers.jpg", quality=90)


def pages(out, summary, audit, views, geo):
    esc = html.escape
    css = "<style>body{font-family:Segoe UI,sans-serif;background:#10151d;color:#dde;margin:16px}a{color:#8cf}table{border-collapse:collapse;margin:8px 0}td,th{border:1px solid #334;padding:3px 7px;font-size:13px}img{max-width:100%}h2{margin-top:28px}.note{color:#aab;font-size:13px}</style>"
    rows = []
    keys = [k for k in next(iter(summary.values())).keys() if not isinstance(next(iter(summary.values()))[k], list) and k != "canopy_core_vs_full"]
    for k in keys:
        rows.append("<tr><td>" + esc(k) + "</td>" + "".join(f"<td>{esc(str(summary[t].get(k)))}</td>" for t in TYPES) + "</tr>")
    perf_rows = []
    for t in TYPES:
        for label, p in audit["performance"][t].items():
            perf_rows.append(f"<tr><td>{esc(t)}</td><td>{esc(label)}</td><td>{p['n']}</td><td>{p['p95_median']}</td><td>{p['p95_max']}</td><td>{p['over_10ms']}</td></tr>")
    links = "".join(f"<li>{esc(t)}: " + " ".join(f"<a href='{t}/{s}/index.html'>{s}</a>" for s in SEEDS)
                    + f" &middot; <a href='contact-{t}-b3-vs-c3.jpg'>B v3 vs C3 sheet</a> &middot; <a href='contact-{t}-layers.jpg'>layer sheet</a></li>" for t in TYPES)
    idx = f"""<!doctype html><meta charset=utf-8><title>T258 candidate C</title>{css}
<h1>T258 storm anatomy candidate C &mdash; native flat-light gallery</h1>
<p class=note>EXPERIMENTAL. Candidate C is not accepted; this gallery is for visual review. Every arm of a view shares one camera
(fit containing B v3 and C3 with the effective lens {esc(str(audit['effective_fov']))} degrees). Panels: B v3 | C0 supporting core | C1 + major growth |
C2 + secondary growth | C3 complete body and canopy (cyan = C0 outline over C3). Report: <a href='../t258-storm-anatomy-candidate-c.md'>t258-storm-anatomy-candidate-c.md</a>.</p>
<h2>Types and seeds</h2><ul>{links}</ul>
<h2>Validation</h2><pre>{esc(json.dumps({k: audit[k] for k in ('expected_arms', 'captured_raw', 'parity', 'traversal', 'zoom', 'effective_fov')}, indent=1))}
missing={len(audit['missing'])} rejected={len(audit['rejected'])} camera_mismatch={len(audit['camera_mismatch'])} b_v3_signature_mismatch={len(audit['b_v3_signature_mismatch'])} framing_edge_non_zoom={len(audit['framing_edge_occupied_non_zoom'])}</pre>
<h2>Ray-march p95 (ms, GL timestamp, ray scope)</h2><table><tr><th>type</th><th>arm</th><th>views</th><th>median p95</th><th>max p95</th><th>&gt;10 ms</th></tr>{''.join(perf_rows)}</table>
<h2>Metrics (means over 3 seeds x 19 views unless named plan/anvil)</h2><table><tr><th>metric</th>{''.join(f'<th>{esc(t)}</th>' for t in TYPES)}</tr>{''.join(rows)}</table>
"""
    (out / "index.html").write_text(idx, encoding="utf-8")
    for t in TYPES:
        for s in SEEDS:
            g = geo.get((t, s), {})
            parts = [f"<!doctype html><meta charset=utf-8><title>T258 {esc(t)} {s}</title>{css}<p><a href='../../index.html'>index</a></p>",
                     f"<h1>{esc(t)} seed {s}</h1><pre class=note>{esc(json.dumps(g, indent=1))}</pre>"]
            for v in VIEWS:
                r = views.get((t, s, v))
                parts.append(f"<h2>{esc(v)}</h2><p class=note>B v3 | C0 | C1 | C2 | C3. Raw: " + " ".join(
                    f"<a href='{p}{v}__raw.png'>{esc(lab)}</a>" for p, lab in ARMS) + "</p>")
                parts.append(f"<img src='cmp_{v}.png'>")
                if r:
                    parts.append("<p class=note>" + esc(f"lobes B {r['valleys_B']['lobes']} / C {r['valleys_C']['lobes']}; top-3 valley depth B {r['valleys_B']['mean_top3_rel']:.3f} / C {r['valleys_C']['mean_top3_rel']:.3f} of height; "
                                                       f"core-to-complete shift B {r['disp_B_rel']:.3f} / C {r['disp_C0_C3_rel']:.3f} of width; IoU cpu/native B {r['iou_cpu_native_B']:.3f} C {r['iou_cpu_native_C']:.3f}") + "</p>")
            parts.append("<h2>Native 4x close views (left B v3, right C3; same camera and aim)</h2>")
            for v in ZOOM:
                parts.append(f"<h3>{esc(v)}</h3><img src='zoom4_{v}.png'>")
            (out / t / s / "index.html").write_text("\n".join(parts), encoding="utf-8")


if __name__ == "__main__":
    main()
