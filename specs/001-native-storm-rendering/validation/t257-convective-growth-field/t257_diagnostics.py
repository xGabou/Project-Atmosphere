"""T257 B v3 structural-attribution gallery (D0 core / D1 + mesoscale heads / D2 complete / D3 exact-field diagnostics).

Usage: python -I t257_diagnostics.py <capture folder> <capture log> <diagnostic dir written by convectiveGrowthFieldDiagnostics>
Copies the native D0/D1/D2 frames next to the D3 panels, aggregates metrics and writes index/type/seed pages,
contact sheets, summary.json and gallery-audit.json. Diagnostic evidence only: no acceptance, no geometry changes.
"""
import json, math, re, shutil, sys, html, hashlib
from pathlib import Path
from statistics import mean
from PIL import Image, ImageDraw, ImageFont

TYPES = ["cumulonimbus_calvus", "cumulonimbus_capillatus", "supercell"]
SEEDS = ["20261008", "20268927", "20276846"]
VIEWS = ["side", "opposite_side", "front", "back", "above", "below", "low_angle", "far", "close", "upwind", "downwind",
         "crosswind_left", "crosswind_right", "tower", "transition", "canopy_anvil", "updraft_oblique", "portrait", "portrait_front"]
SIDE = {"side", "opposite_side", "close", "far", "crosswind_left", "crosswind_right", "portrait"}
LEVELS = [("d0_", "D0 core"), ("d1_", "D1 + mesoscale"), ("", "D2 complete")]
FOCAL = 337.5 / math.tan(math.radians(110 * 1.1 / 2))  # native px per radian at the calibrated lens


def font(n):
    try: return ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", n)
    except OSError: return ImageFont.load_default()


def parse_log(path):
    gpu, parity, traversal, framing, poses = {}, {}, [], {}, {}
    for line in Path(path).read_text(errors="replace").splitlines():
        m = re.search(r"T257_GPU shot=(\S+) samples=(\d+) .*medianMs=([\d.]+) p95Ms=([\d.]+)", line)
        if m: gpu[m[1]] = dict(samples=int(m[2]), p50=float(m[3]), p95=float(m[4]))
        m = re.search(r"T257_PARITY shot=(\S+) .*maxAbsError=(\S+) .*pass=(\w+)", line)
        if m: parity[m[1]] = dict(max_abs_error=float(m[2]), passed=m[3] == "true")
        m = re.search(r"T257_TRAVERSAL_PAIR .*maxAbsRGBA=(\S+) .*pass=(\w+)", line)
        if m: traversal.append(dict(max_abs=float(m[1]), passed=m[2] == "true"))
        m = re.search(r"T257_FRAMING shot=(\S+) occupiedEdgePixels=(\d+)", line)
        if m: framing[m[1]] = int(m[2])
        m = re.search(r"T257_POSE shot=(\S+) version=(\d+) signature=(\S+)", line)
        if m: poses[m[1]] = dict(version=int(m[2]), signature=m[3])
    return gpu, parity, traversal, framing, poses


def avg(vals):
    vals = [v for v in vals if v is not None]
    return round(mean(vals), 4) if vals else None


def main():
    src, log, out = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
    gpu, parity, traversal, framing, poses = parse_log(log)
    rows = [json.loads(l) for l in (out / "t257-diagnostic-metrics.jsonl").read_text().splitlines() if l.strip()]
    geo = {(r["type"], str(r["seed"])): r for r in rows if r["kind"] == "geometry"}
    views = [r for r in rows if r["kind"] == "view"]
    missing = []
    for t in TYPES:
        for s in SEEDS:
            for v in VIEWS:
                for pre, _ in LEVELS:
                    shot = f"T257_{t}__{s}__{pre}{v}"
                    raw, screen = src / f"{shot}__raw.png", src / f"{shot}.png"
                    if not (raw.is_file() and screen.is_file()): missing.append(shot); continue
                    d = out / t / s; d.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(raw, d / f"{pre}{v}__raw.png"); shutil.copyfile(screen, d / f"{pre}{v}.png")
    # D2 geometry must be the B v3 baseline (identical signature per type/seed).
    baseline = json.loads((Path(__file__).with_name("t257-baselines.json")).read_text())["baselines"]["B-v3"]["geometry_fingerprints"]
    signature_mismatch = [k for k, p in poses.items() if re.search(r"__(?!d0_|d1_|attribution)[a-z_]+$", k)
                          and (lambda t, s: baseline.get(f"{t}/{s}", {}).get("signature"))(*re.match(r"T257_(.+?)__(\d+)__", k).groups()) != p["signature"]]
    # Per-view projected feature sizes.
    for r in views:
        g = geo[(r["type"], str(r["seed"]))]
        k = FOCAL / r["cameraDistance"]
        r["projected"] = dict(headDiameterPx=round(2 * g["cellRadius"] * k, 1), headAmplitudePx=round(g["cellAmpMax"] * k, 1),
                              octave2AmplitudePx=round(g["octave2Amp"] * k, 2), turretAmplitudePx=round(g["sideTurretAmpMax"] * k, 1),
                              stormHeightPx=r["heightPx"], galleryHeadAmplitudePx=round(g["cellAmpMax"] * k * r["galleryScale"], 1))
        for pre, _ in LEVELS:
            q = gpu.get(f"T257_{r['type']}__{r['seed']}__{pre}{r['view']}")
            r.setdefault("gpu_p95", {})[pre or "d2_"] = q["p95"] if q else None
    summary = {}
    for t in TYPES:
        vs = [r for r in views if r["type"] == t]
        side = [r for r in vs if r["view"] in SIDE]
        plan = [r for r in vs if "canopyEdgeVariation" in r]
        anv = [r for r in vs if "anvil" in r]
        lv = lambda key, rs, i: avg([r[key][i] for r in rs])
        summary[t] = dict(
            views=len(vs), storm_height_px=avg([r["heightPx"] for r in vs]), storm_width_px=avg([r["widthPx"] for r in vs]),
            gallery_scale=avg([r["galleryScale"] for r in vs]), feather_px=avg([r["featherPx"] for r in vs]),
            cpu_native_iou_min=min(r["cpuNativeIoU"] for r in vs) if vs else None,
            silhouette_displacement_px=dict(core_to_meso=avg([r["disp01"][0] for r in vs]), core_to_full=avg([r["disp02"][0] for r in vs]),
                                            meso_to_full=avg([r["disp12"][0] for r in vs]), core_to_full_p95=avg([r["disp02"][1] for r in vs])),
            silhouette_displacement_over_width=avg([r["disp02MeanOverWidth"] for r in vs]), xor_over_core_area=avg([r["xor02OverArea0"] for r in vs]),
            visible_heads=dict(d1=avg([r["heads"]["d1"] for r in vs]), d2=avg([r["heads"]["d2"] for r in vs]), d2_gallery=avg([r["heads"]["d2Gallery"] for r in vs])),
            head_prominence_over_row_width=avg([r["headProminenceOverRowWidth"] for r in vs]),
            crown_irregularity_side=[lv("crownIrregularity", side, i) for i in range(3)],
            height_variation_side=[lv("heightVariation", side, i) for i in range(3)],
            canopy_edge_variation_plan=[lv("canopyEdgeVariation", plan, i) for i in range(3)] if plan else None,
            plan_outline_shift=dict(core_to_meso_px=avg([r["disp01"][0] for r in plan]), core_to_full_px=avg([r["disp02"][0] for r in plan]),
                                    core_to_full_p95_px=avg([r["disp02"][1] for r in plan]), core_to_full_over_width=avg([r["disp02MeanOverWidth"] for r in plan])) if plan else None,
            anvil_side={k: [avg([r["anvil"][d][j] for r in anv]) for d in ("d0", "d1", "d2")] for j, k in
                        enumerate(("thickness_over_length", "top_rms_over_length", "bottom_rms_over_length", "thickness_cv"))} if anv else None,
            d3=dict(crease_on_silhouette=avg([r["d3"]["creaseOnSilhouette"] for r in vs]), occluding_on_silhouette=avg([r["d3"]["occludingOnSilhouette"] for r in vs]),
                    relief_on_silhouette=avg([r["d3"]["reliefOnSilhouette"] for r in vs]), mean_relief_px=avg([r["d3"]["meanReliefPx"] for r in vs])),
            projected=dict(head_diameter_px=avg([r["projected"]["headDiameterPx"] for r in vs]), head_amplitude_px=avg([r["projected"]["headAmplitudePx"] for r in vs]),
                           octave2_amplitude_px=avg([r["projected"]["octave2AmplitudePx"] for r in vs]),
                           gallery_head_amplitude_px=avg([r["projected"]["galleryHeadAmplitudePx"] for r in vs])),
            gpu_p95_max={k: max([r["gpu_p95"][k] for r in vs if r["gpu_p95"].get(k)] or [None]) for k in ("d0_", "d1_", "d2_")},
            gpu_p95_mean={k: avg([r["gpu_p95"][k] for r in vs]) for k in ("d0_", "d1_", "d2_")},
            geometry={k: avg([geo[(t, s)][k] for s in SEEDS if (t, s) in geo]) for k in
                      ("H", "feather", "cellAmpOverBodyWidth", "cellAmpOverRadius", "columnValleyRatio", "columnValleyRatioMeso", "columnCoverage",
                       "crownValleyRatio", "crownCoverage", "buriedColumnArea", "buriedColumnRelief", "buriedUpperColumnArea", "creaseDepthOverFeather")})
    audit = dict(views=len(views), expected_views=171, missing_native_frames=missing, signature_mismatch_vs_B_v3=signature_mismatch,
                 parity={k: v for k, v in parity.items()}, parity_all_pass=bool(parity) and all(v["passed"] for v in parity.values()),
                 traversal=traversal, framing_failures=sorted(k for k, v in framing.items() if v != 0),
                 geometry_identity="D2 = B v3: per-shot header signatures equal t257-baselines.json B-v3; D3 rays the same FULL program (fingerprint-identical)")
    (out / "summary.json").write_text(json.dumps(dict(summary=summary, views=views, geometry=list(geo.values())), indent=1))
    (out / "gallery-audit.json").write_text(json.dumps(audit, indent=1))

    # Contact sheets of the 5-tile panels (D0 | D1 | D2 native | D3 | relief), pixelated upscale of native pixels.
    def sheet(sel, name, scale=2):
        tiles = []
        for r in sel:
            with Image.open(out / r["panel"]) as im: tiles.append((r, im.convert("RGB")))
        if not tiles: return
        wmax = max(im.width for _, im in tiles) * scale
        hh = [im.height * scale + 22 for _, im in tiles]
        canvas = Image.new("RGB", (wmax, sum(hh)), (16, 21, 29)); dr = ImageDraw.Draw(canvas); y = 0
        for (r, im), h in zip(tiles, hh):
            dr.text((6, y + 2), f"{r['type'].removeprefix('cumulonimbus_')} | {r['seed']} | {r['view']} | D0 core | D1 +meso | D2 complete | D3 exact-field creases | head relief", fill=(220, 235, 250), font=font(13))
            canvas.paste(im.resize((im.width * scale, im.height * scale), Image.NEAREST), (0, y + 22)); y += h
        canvas.save(out / name, quality=92)
    pick = lambda t=None, s=None, vv=None: [r for r in views if (t is None or r["type"] == t) and (s is None or str(r["seed"]) == s) and (vv is None or r["view"] in vv)]
    order = lambda rs: sorted(rs, key=lambda r: (TYPES.index(r["type"]), str(r["seed"]), VIEWS.index(r["view"])))
    sheet(order(pick(s="20261008", vv=("side", "above", "portrait_front", "transition"))), "contact-diagnostic-overall.jpg")
    for t in TYPES:
        sheet(order(pick(t=t, vv=("side", "above", "portrait_front", "transition"))), f"{t}/contact-diagnostic.jpg")

    style = ("<style>body{background:#10151d;color:#dee8f4;font:15px system-ui;margin:20px}a{color:#83c2ff}"
             "table{border-collapse:collapse;margin:10px 0}td,th{border:1px solid #344155;padding:3px 7px;font-size:13px}"
             "img.px{image-rendering:pixelated;width:100%;max-width:1600px}figure{margin:16px 0}</style>")
    notice = ("<p><b>Diagnostic only.</b> B v3 = EXPERIMENTAL BASELINE, not accepted morphology, not production. D0/D1/D2 are native "
              "cloud-target frames (flat neutral light, micro/history off) at identical seed and camera; D3 is the SAME complete "
              "geometry ray-cast exactly on the CPU at the native camera/lens/resolution (CPU/native silhouette IoU recorded) showing "
              "headlight-shaded surface, creases (red), interior occluding contours (magenta), core outline (cyan) and D2 outline (white). "
              "Panels are cropped native pixels upscaled without filtering.</p>")
    def page(title, body): return f"<!doctype html><meta charset='utf-8'><title>{html.escape(title)}</title>{style}<h1>{html.escape(title)}</h1>{notice}{body}"
    def table(head, rows): return "<table><tr>" + "".join(f"<th>{h}</th>" for h in head) + "</tr>" + "".join("<tr>" + "".join(f"<td>{c}</td>" for c in r) + "</tr>" for r in rows) + "</table>"
    fmt = lambda v: "n/a" if v is None else (f"{v:.3f}" if isinstance(v, float) else str(v))
    S = summary
    body = (f"<p>{len(views)}/171 views analysed; missing native frames: {len(missing)}; parity all pass: {audit['parity_all_pass']}; "
            f"traversal pass: {any(t['passed'] for t in traversal)}; framing failures: {len(audit['framing_failures'])}; "
            f"D2 signature mismatches vs B-v3: {len(signature_mismatch)}.</p>"
            "<p><a href='../../../t257-convective-growth-field.md'>Written attribution (report §10)</a> | <a href='summary.json'>summary.json</a> | "
            "<a href='gallery-audit.json'>audit</a> | <a href='contact-diagnostic-overall.jpg'>overall contact sheet</a></p>")
    body += "<h2>Scale at gallery framing</h2>" + table(["type", "storm height px", "storm width px", "feather px", "head diameter px", "head amplitude px",
        "octave-2 amplitude px", "head amplitude on contact sheet px", "CPU/native IoU (min)"],
        [[t, fmt(S[t]["storm_height_px"]), fmt(S[t]["storm_width_px"]), fmt(S[t]["feather_px"]), fmt(S[t]["projected"]["head_diameter_px"]),
          fmt(S[t]["projected"]["head_amplitude_px"]), fmt(S[t]["projected"]["octave2_amplitude_px"]), fmt(S[t]["projected"]["gallery_head_amplitude_px"]),
          fmt(S[t]["cpu_native_iou_min"])] for t in TYPES])
    body += "<h2>Silhouette contribution of the mesoscale features</h2>" + table(["type", "mean boundary shift D0&rarr;D1 px", "D0&rarr;D2 px", "D1&rarr;D2 px",
        "D0&rarr;D2 / width", "XOR / core area", "visible heads D1", "D2", "D2 at contact-sheet scale", "head prominence / row width"],
        [[t, fmt(S[t]["silhouette_displacement_px"]["core_to_meso"]), fmt(S[t]["silhouette_displacement_px"]["core_to_full"]), fmt(S[t]["silhouette_displacement_px"]["meso_to_full"]),
          fmt(S[t]["silhouette_displacement_over_width"]), fmt(S[t]["xor_over_core_area"]), fmt(S[t]["visible_heads"]["d1"]), fmt(S[t]["visible_heads"]["d2"]),
          fmt(S[t]["visible_heads"]["d2_gallery"]), fmt(S[t]["head_prominence_over_row_width"])] for t in TYPES])
    body += "<h2>Outline variation by level (D0 / D1 / D2)</h2>" + table(["type", "crown irregularity (side)", "edge variation with height (side)",
        "canopy edge variation (above/below)", "anvil thickness/length (side)", "anvil top RMS/length", "anvil bottom RMS/length", "anvil thickness CV"],
        [[t, " / ".join(map(fmt, S[t]["crown_irregularity_side"])), " / ".join(map(fmt, S[t]["height_variation_side"])),
          " / ".join(map(fmt, S[t]["canopy_edge_variation_plan"] or [None])),
          *([" / ".join(map(fmt, S[t]["anvil_side"][k])) for k in ("thickness_over_length", "top_rms_over_length", "bottom_rms_over_length", "thickness_cv")] if S[t]["anvil_side"] else ["n/a"] * 4)] for t in TYPES])
    body += "<h2>Plan-view (above/below) outline shift by the canopy edge features</h2>" + table(["type", "D0&rarr;D1 px", "D0&rarr;D2 px", "D0&rarr;D2 p95 px", "D0&rarr;D2 / width"],
        [[t, *[fmt(S[t]["plan_outline_shift"][k]) for k in ("core_to_meso_px", "core_to_full_px", "core_to_full_p95_px", "core_to_full_over_width")]] for t in TYPES])
    body += "<h2>D3: where the structure is, and whether flat light can show it</h2>" + table(["type", "crease px on outer silhouette", "occluding contours on outer silhouette",
        "head relief on outer silhouette", "mean head relief px (visible surface)"],
        [[t, fmt(S[t]["d3"]["crease_on_silhouette"]), fmt(S[t]["d3"]["occluding_on_silhouette"]), fmt(S[t]["d3"]["relief_on_silhouette"]), fmt(S[t]["d3"]["mean_relief_px"])] for t in TYPES])
    body += "<h2>Resolution-free geometry (mean of 3 seeds)</h2>" + table(["type", "H", "feather", "head amp / body width", "head amp / head radius",
        "column valley ratio D2 (D1)", "column coverage", "crown valley ratio", "crown coverage", "buried column area", "buried head relief", "buried upper column", "valley depth / feather"],
        [[t, *[fmt(S[t]["geometry"][k]) for k in ("H", "feather", "cellAmpOverBodyWidth", "cellAmpOverRadius")],
          f"{fmt(S[t]['geometry']['columnValleyRatio'])} ({fmt(S[t]['geometry']['columnValleyRatioMeso'])})",
          *[fmt(S[t]["geometry"][k]) for k in ("columnCoverage", "crownValleyRatio", "crownCoverage", "buriedColumnArea", "buriedColumnRelief", "buriedUpperColumnArea", "creaseDepthOverFeather")]] for t in TYPES])
    body += "<h2>Ray-march GPU p95 by level (ms, max / mean over 57 views)</h2>" + table(["type", "D0", "D1", "D2"],
        [[t, *[f"{fmt(S[t]['gpu_p95_max'][k])} / {fmt(S[t]['gpu_p95_mean'][k])}" for k in ("d0_", "d1_", "d2_")]] for t in TYPES])
    body += "<ul>" + "".join(f"<li><a href='{t}/index.html'>{t}</a> — <a href='{t}/contact-diagnostic.jpg'>contact sheet</a></li>" for t in TYPES) + "</ul>"
    (out / "index.html").write_text(page("T257 B v3 structural attribution — D0/D1/D2/D3", body), encoding="utf-8")
    for t in TYPES:
        (out / t / "index.html").write_text(page(t, "<a href='../index.html'>All</a><ul>" + "".join(f"<li><a href='{s}/index.html'>seed {s}</a></li>" for s in SEEDS)
                                                 + "</ul><img class='px' src='contact-diagnostic.jpg'>"), encoding="utf-8")
        for s in SEEDS:
            b = "<a href='../index.html'>Family</a> | <a href='../../index.html'>All</a>"
            for r in order(pick(t=t, s=s)):
                v = r["view"]
                b += (f"<figure id='{v}'><figcaption><b>{v}</b> — D0 core | D1 +meso | D2 complete | D3 exact-field | relief. Storm {r['heightPx']}×{r['widthPx']} px; "
                      f"shift D0→D2 {r['disp02'][0]:.1f} px mean / {r['disp02'][1]:.1f} p95; heads D1 {r['heads']['d1']} D2 {r['heads']['d2']}; IoU {r['cpuNativeIoU']:.3f}; "
                      f"p95 D0/D1/D2 {r['gpu_p95'].get('d0_')} / {r['gpu_p95'].get('d1_')} / {r['gpu_p95'].get('d2_')} ms</figcaption>"
                      f"<img class='px' loading='lazy' src='diag-{v}.png'>")
                if (out / t / s / f"zoom4_{v}.png").is_file():
                    b += f"<p>Resolution probe, 4× native pixel density, same camera: CORE iso-silhouette | FULL iso-silhouette | FULL headlight + creases</p><img class='px' loading='lazy' src='zoom4_{v}.png'>"
                b += (f"<p><a href='d0_{v}.png'>D0 frame</a> | <a href='d1_{v}.png'>D1 frame</a> | <a href='{v}.png'>D2 frame</a> | "
                      f"<a href='d0_{v}__raw.png'>D0 raw</a> | <a href='d1_{v}__raw.png'>D1 raw</a> | <a href='{v}__raw.png'>D2 raw</a> | <a href='d3_{v}.png'>D3 full</a></p></figure>")
            (out / t / s / "index.html").write_text(page(f"{t} — seed {s}", b), encoding="utf-8")
    print(json.dumps(dict(views=len(views), missing=len(missing), signature_mismatch=len(signature_mismatch), parity=audit["parity_all_pass"],
                          framing_failures=len(audit["framing_failures"])), indent=1))


if __name__ == "__main__":
    main()
