"""T257 native gallery export + silhouette/GPU metrics (rejected candidate A vs candidate B, identical seed/pose).

Usage: python -I t257_gallery.py <capture folder> <capture log> <pass name>
Writes native/<pass>/{index.html, metrics.json, comparison.json, <type>/<seed>/*.png}.
Metrics are silhouette diagnostics on the actual cloud-target alpha, not realism acceptance.
"""
import json, re, shutil, sys, hashlib, argparse, html, subprocess
from html.parser import HTMLParser
from collections import deque
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont

TYPES = ["cumulonimbus_calvus", "cumulonimbus_capillatus", "supercell"]
SEEDS = ["20261008", "20268927", "20276846"]
VIEWS = ["side", "opposite_side", "front", "back", "above", "below", "low_angle", "far", "close", "upwind",
         "downwind", "crosswind_left", "crosswind_right", "tower", "transition", "canopy_anvil", "updraft_oblique",
         "portrait", "portrait_front"]
SIDE_VIEWS = {"side", "opposite_side", "close", "far", "crosswind_left", "crosswind_right", "portrait"}

def font(size):
    try:return ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',size)
    except OSError:return ImageFont.load_default()


def shift_min(m, k, axis):
    out = m.copy()
    for d in range(1, k + 1):
        a = np.roll(m, d, axis); b = np.roll(m, -d, axis)
        out &= a & b
    return out


def shift_max(m, k, axis):
    out = m.copy()
    for d in range(1, k + 1):
        out |= np.roll(m, d, axis) | np.roll(m, -d, axis)
    return out


def erode(m, k): return shift_min(shift_min(m, k, 0), k, 1)
def dilate(m, k): return shift_max(shift_max(m, k, 0), k, 1)


def components(mask):
    small = mask[::2, ::2]
    seen = np.zeros_like(small, bool)
    h, w = small.shape
    sizes = []
    ys, xs = np.nonzero(small)
    for y0, x0 in zip(ys, xs):
        if seen[y0, x0]:
            continue
        q = deque([(y0, x0)]); seen[y0, x0] = True; n = 0
        while q:
            y, x = q.popleft(); n += 1
            for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                a, b = y + dy, x + dx
                if 0 <= a < h and 0 <= b < w and small[a, b] and not seen[a, b]:
                    seen[a, b] = True; q.append((a, b))
        sizes.append(n)
    sizes.sort(reverse=True)
    significant = [s for s in sizes if s >= 8]
    return len(significant), (sizes[0] / max(1, sum(sizes))) if sizes else 0.0


def hull_area(mask):
    ys, xs = np.nonzero(mask)
    if len(xs) < 3:
        return 0.0
    pts = sorted(set(zip(xs.tolist(), ys.tolist())))
    # boundary-only speedup: keep per-row extremes
    rows = {}
    for x, y in pts:
        lo, hi = rows.get(y, (x, x)); rows[y] = (min(lo, x), max(hi, x))
    pts = sorted({(lo, y) for y, (lo, hi) in rows.items()} | {(hi, y) for y, (lo, hi) in rows.items()})
    def cross(o, a, b): return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
    lower, upper = [], []
    for p in pts:
        while len(lower) >= 2 and cross(lower[-2], lower[-1], p) <= 0: lower.pop()
        lower.append(p)
    for p in reversed(pts):
        while len(upper) >= 2 and cross(upper[-2], upper[-1], p) <= 0: upper.pop()
        upper.append(p)
    h = lower[:-1] + upper[:-1]
    return abs(sum(h[i][0] * h[(i + 1) % len(h)][1] - h[(i + 1) % len(h)][0] * h[i][1] for i in range(len(h)))) / 2.0


def perimeter(m):
    return int((m & ~np.roll(m, 1, 0)).sum() + (m & ~np.roll(m, -1, 0)).sum() + (m & ~np.roll(m, 1, 1)).sum() + (m & ~np.roll(m, -1, 1)).sum())


def metrics(raw_path, view):
    a = np.asarray(Image.open(raw_path).convert("RGBA"))[:, :, 3]
    mask = a > 2
    area = int(mask.sum())
    if area == 0:
        return {"occupied": 0}
    k = max(2, int(round(0.045 * np.sqrt(area))))
    opened = dilate(erode(mask, k), k)
    thin = float((mask & ~opened).sum()) / area
    smooth = erode(dilate(opened, k), k)
    detail = perimeter(mask) / max(1, perimeter(smooth)) - 1.0
    comps, largest = components(mask)
    ys, xs = np.nonzero(mask)
    out = {"occupied": area, "components": comps, "largest_fraction": round(largest, 4),
           "solidity": round(area / max(1.0, hull_area(mask)), 4), "thin_fraction": round(thin, 4),
           "outline_detail": round(detail, 4), "opening_px": k,
           "height_px": int(ys.max() - ys.min() + 1), "width_px": int(xs.max() - xs.min() + 1)}
    if view in SIDE_VIEWS:
        # Column-height profile: the downwind 35 % of the silhouette (canopy/upper spread) vs total height.
        cols = mask.sum(0)
        x0, x1 = xs.min(), xs.max()
        lo = int(x0 + 0.62 * (x1 - x0)); hi = int(x0 + 0.88 * (x1 - x0))
        seg = cols[lo:hi + 1]
        out["outer_column_height_ratio"] = round(float(np.median(seg)) / out["height_px"], 4) if len(seg) else 0.0
        out["min_column_ratio"] = round(float(cols[int(x0 + .05 * (x1 - x0)):int(x1 - .05 * (x1 - x0))].min()) / out["height_px"], 4)
    return out


def parse_log(path):
    gpu, pipe, rejected, parity, traversal, framing = {}, {}, {}, {}, [], {}
    for line in Path(path).read_text(errors="replace").splitlines():
        m = re.search(r"T257_GPU shot=(\S+) samples=(\d+) .*medianMs=([\d.]+) p95Ms=([\d.]+) maxMs=([\d.]+)", line)
        if m: gpu[m[1]] = dict(samples=int(m[2]), p50=float(m[3]), p95=float(m[4]), max=float(m[5]))
        m = re.search(r"T257_PIPELINE shot=(\S+) samples=(\d+) .*medianMs=([\d.]+) p95Ms=([\d.]+) maxMs=([\d.]+)", line)
        if m: pipe[m[1]] = dict(samples=int(m[2]), p50=float(m[3]), p95=float(m[4]), max=float(m[5]))
        m = re.search(r"T257_CAPTURE_REJECTED shot=(\S+) reason=(.*)", line)
        if m: rejected[m[1]] = m[2]
        m = re.search(r"T257_PARITY shot=(\S+) samples=(\d+) occupied=(\d+) maxAbsError=(\S+) .*pass=(\w+)", line)
        if m: parity[m[1]] = dict(samples=int(m[2]), occupied=int(m[3]), max_abs_error=float(m[4]), passed=m[5] == "true")
        m = re.search(r"T257_TRAVERSAL_PAIR .*channels=(\d+) maxAbsRGBA=(\S+) .*pass=(\w+)", line)
        if m: traversal.append(dict(channels=int(m[1]), max_abs=float(m[2]), passed=m[3] == "true"))
        m = re.search(r"T257_FRAMING shot=(\S+) occupiedEdgePixels=(\d+)", line)
        if m: framing[m[1]] = int(m[2])
    return gpu, pipe, rejected, parity, traversal, framing


def crop_box(paths):
    """Union bounding box of the cloud alpha across the given raw images, padded."""
    box = None
    for p in paths:
        a = np.asarray(Image.open(p).convert("RGBA"))[:, :, 3] > 2
        ys, xs = np.nonzero(a)
        if len(xs) == 0: continue
        b = [xs.min(), ys.min(), xs.max(), ys.max()]
        box = b if box is None else [min(box[0], b[0]), min(box[1], b[1]), max(box[2], b[2]), max(box[3], b[3])]
    return box


def contact_sheet(out, pairs):
    """Per view group: rows = type/seed, columns = A | B, identical crop per row (same framing)."""
    for group in (("side", "close", "portrait"), ("front", "back", "above", "below"), ("low_angle", "tower", "transition", "canopy_anvil")):
        rows = [p for p in pairs if p["view"] in group]
        rows.sort(key=lambda p: (TYPES.index(p["type"]), p["seed"], group.index(p["view"])))
        cell_w, cell_h = 420, 260
        sheet = Image.new("RGB", (cell_w * 2 + 8, (cell_h + 4) * len(rows)), (16, 21, 29))
        for r, p in enumerate(rows):
            ra, rb = out / p["a"]["file"], out / p["b"]["file"]
            box = crop_box([ra, rb])
            if box is None: continue
            pad = 12
            x0, y0, x1, y1 = box[0] - pad, box[1] - pad, box[2] + pad, box[3] + pad
            for c, (raw, rec) in enumerate(((ra, p["a"]), (rb, p["b"]))):
                shot = Image.open(str(raw).replace("__raw", "")).convert("RGB")
                sx, sy = shot.width / Image.open(raw).width, shot.height / Image.open(raw).height
                tile = shot.crop((int(x0 * sx), int(y0 * sy), int(x1 * sx), int(y1 * sy)))
                tile.thumbnail((cell_w, cell_h))
                sheet.paste(tile, (c * (cell_w + 8) + (cell_w - tile.width) // 2, r * (cell_h + 4) + (cell_h - tile.height) // 2))
        sheet.save(out / f"contact-{group[0]}.jpg", quality=90)


def legacy_main():
    src, log, name = Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3]
    here = Path(__file__).resolve().parent
    out = here / "native" / name
    out.mkdir(parents=True, exist_ok=True)
    gpu, pipe, rejected, parity, traversal, framing = parse_log(log)
    records, pairs = [], []
    for f in sorted(src.glob("T257_*.png")):
        m = re.match(r"T257_(cumulonimbus_calvus|cumulonimbus_capillatus|supercell)__(\d+)__(.+?)(__raw)?\.png$", f.name)
        if not m: continue
        t, seed, view, raw = m[1], m[2], m[3], bool(m[4])
        d = out / t / seed; d.mkdir(parents=True, exist_ok=True)
        target = d / (view + ("__raw" if raw else "") + ".png")
        shutil.copyfile(f, target)
        if raw:
            shot = f"T257_{t}__{seed}__{view}"
            model = "A" if view.startswith("a_") else "B"
            pose = view[2:] if view.startswith("a_") else view
            rec = dict(type=t, seed=seed, view=view, pose=pose, model=model, file=str(target.relative_to(out)).replace("\\", "/"),
                       sha256=hashlib.sha256(target.read_bytes()).hexdigest(), gpu=gpu.get(shot), pipeline=pipe.get(shot),
                       rejected=rejected.get(shot), framing_edge_pixels=framing.get(shot))
            rec["silhouette"] = metrics(target, pose)
            records.append(rec)
    by = {(r["type"], r["seed"], r["pose"], r["model"]): r for r in records}
    for t in TYPES:
        for seed in SEEDS:
            for v in VIEWS:
                a, b = by.get((t, seed, v, "A")), by.get((t, seed, v, "B"))
                if a and b: pairs.append(dict(type=t, seed=seed, view=v, a=a, b=b))
    (out / "metrics.json").write_text(json.dumps(dict(records=records, parity=parity, traversal=traversal,
                                                      rejected=rejected), indent=1))
    # Aggregate per type/model.
    summary = {}
    for t in TYPES:
        for model in ("A", "B"):
            rs = [r for r in records if r["type"] == t and r["model"] == model and r["pose"] in VIEWS]
            if not rs: continue
            pp = [r["pipeline"]["p95"] for r in rs if r["pipeline"]]
            rp = [r["gpu"]["p95"] for r in rs if r["gpu"]]
            def mean(key, sub=None):
                vals = [r["silhouette"].get(key) for r in rs if r["silhouette"].get(key) is not None and (sub is None or r["pose"] in sub)]
                return round(sum(vals) / len(vals), 4) if vals else None
            summary[f"{t}/{model}"] = dict(views=len(rs), pipeline_p95_max=max(pp) if pp else None,
                                           pipeline_p95_mean=round(sum(pp) / len(pp), 3) if pp else None,
                                           ray_p95_max=max(rp) if rp else None, views_over_10ms=sum(1 for x in pp if x > 10),
                                           components_mean=mean("components"), solidity_mean=mean("solidity"),
                                           thin_fraction_mean=mean("thin_fraction"), outline_detail_mean=mean("outline_detail"),
                                           outer_column_height_ratio_side=mean("outer_column_height_ratio", SIDE_VIEWS),
                                           min_column_ratio_side=mean("min_column_ratio", SIDE_VIEWS))
    (out / "comparison.json").write_text(json.dumps(dict(summary=summary, pairs=[dict(type=p["type"], seed=p["seed"], view=p["view"],
        a=dict(sil=p["a"]["silhouette"], pipeline=p["a"]["pipeline"], gpu=p["a"]["gpu"]),
        b=dict(sil=p["b"]["silhouette"], pipeline=p["b"]["pipeline"], gpu=p["b"]["gpu"])) for p in pairs]), indent=1))
    style = ("<style>:root{--bg:#10151d;--fg:#dee8f4;--line:#344155}body{background:var(--bg);color:var(--fg);font:15px system-ui;margin:16px}"
             "a{color:#83c2ff}img{width:100%;background:#000;display:block}table{border-collapse:collapse}td,th{border:1px solid var(--line);padding:4px 8px}"
             ".pair{display:grid;grid-template-columns:1fr 1fr;gap:6px;margin-bottom:14px}figure{margin:0;border:1px solid var(--line);padding:4px}"
             "figcaption{font-size:12px;padding:4px}h3{margin:18px 0 6px}</style>")
    html = [f"<!doctype html><meta charset='utf-8'><title>T257 A vs B gallery</title>{style}<h1>T257 rejected candidate A vs candidate B — {name}</h1>"
            "<p>Experimental native fixture, neutral flat radiance, micro off, history off, fixed 4-block march. Left: rejected candidate A. Right: candidate B. Identical seed, camera and framing, same session. Not production-approved; metrics are silhouette diagnostics, not realism acceptance. Candidate B awaits visual review.</p>",
            "<table><tr><th>type/model</th><th>views</th><th>whole-hook p95 max (ms)</th><th>mean p95</th><th>&gt;10 ms</th><th>components</th><th>solidity</th><th>thin fraction</th><th>outline detail</th><th>outer column ratio (side)</th></tr>"]
    for k, s in summary.items():
        html.append(f"<tr><td>{k}</td><td>{s['views']}</td><td>{s['pipeline_p95_max']}</td><td>{s['pipeline_p95_mean']}</td><td>{s['views_over_10ms']}</td><td>{s['components_mean']}</td><td>{s['solidity_mean']}</td><td>{s['thin_fraction_mean']}</td><td>{s['outline_detail_mean']}</td><td>{s['outer_column_height_ratio_side']}</td></tr>")
    html.append("</table>")
    for t in TYPES:
        html.append(f"<h2>{t}</h2>")
        for seed in SEEDS:
            html.append(f"<h3>seed {seed}</h3>")
            for p in [p for p in pairs if p["type"] == t and p["seed"] == seed]:
                cap = lambda r: (f"{r['model']} {p['view']} — whole-hook p95 {r['pipeline']['p95'] if r['pipeline'] else 'n/a'} ms, "
                                 f"thin {r['silhouette'].get('thin_fraction')}, solidity {r['silhouette'].get('solidity')}"
                                 + (f" — REJECTED {r['rejected']}" if r['rejected'] else ""))
                fa = p["a"]["file"].replace("__raw", ""); fb = p["b"]["file"].replace("__raw", "")
                html.append(f"<div class='pair'><figure><img loading='lazy' src='{fa}'><figcaption>{cap(p['a'])}</figcaption></figure>"
                            f"<figure><img loading='lazy' src='{fb}'><figcaption>{cap(p['b'])}</figcaption></figure></div>")
    controls = [r for r in records if r["view"].startswith("control_")]
    if controls:
        html.append("<h2>Candidate B control arms (supercell seed 20261008)</h2><div class='pair'>")
        for r in controls:
            html.append(f"<figure><img loading='lazy' src='{r['file'].replace('__raw', '')}'><figcaption>{r['view']}</figcaption></figure>")
        html.append("</div>")
    (out / "index.html").write_text("".join(html), encoding="utf-8")
    contact_sheet(out, pairs)
    print(json.dumps(summary, indent=1))
    print("pairs", len(pairs), "records", len(records), "rejected", len(rejected), "parity", parity, "traversal", traversal)


def expected_shots():
    shots = ["T257_supercell__20261008__attribution_intervals", "T257_supercell__20261008__attribution_exact"]
    for t in TYPES:
        for seed in SEEDS:
            for view in VIEWS:
                shots += [f"T257_{t}__{seed}__{view}", f"T257_{t}__{seed}__a_{view}"]
    return shots + [f"T257_supercell__20261008__control_{v}" for v in ("neutral", "tilt", "shear", "split", "narrowing", "wall")]


def evidence_sources(sources):
    """Latest complete arm wins; preserve every original source and its log."""
    records, parity, traversal = {}, {}, []
    for folder, log in sources:
        folder, log = Path(folder).resolve(), Path(log).resolve()
        gpu, pipe, rejected, par, trav, framing = parse_log(log)
        parity.update(par); traversal.extend(trav)
        poses, ack, captures, conditions, effective = {}, {}, {}, {}, {}
        for line in log.read_text(errors="replace").splitlines():
            m = re.search(r"T257_POSE shot=(\S+) version=(\d+) signature=(\S+) origin=\(([^)]+)\).*camera=\(([^)]+)\) aim=\(([^)]+)\)", line)
            if m:
                poses[m[1]] = dict(version=int(m[2]), signature=m[3], origin=[float(v) for v in m[4].split(',')],
                                  camera=[float(v) for v in m[5].split(',')], aim=[float(v) for v in m[6].split(',')])
            m = re.search(r"T257_ACK shot=(\S+).*yaw=([\d.-]+) pitch=([\d.-]+)", line)
            if m: ack[m[1]] = [float(m[2]), float(m[3])]
            m = re.search(r"T098_CAPTURE shot=(T257_\S+).*mainTarget=(\S+) cloudTarget=(\S+) effectiveResolutionScale=(\S+)", line)
            if m: captures[m[1]] = dict(main_target=m[2], cloud_target=m[3], resolution_scale=float(m[4]))
            m = re.search(r"T257_LENS shot=(\S+) fov=(\S+) fovEffectScale=(\S+)", line)
            if m: conditions[m[1]] = dict(fov=float(m[2]), fov_effect_scale=float(m[3]), evidence="native_lens_log")
            m = re.search(r"T257_EFFECTIVE shot=(\S+) (.+) packedVersion=\d+",line)
            if m: effective[m[1]] = m[2]
        for shot in expected_shots():
            raw, screen = folder / (shot + "__raw.png"), folder / (shot + ".png")
            if not (raw.is_file() and screen.is_file() and shot in captures): continue
            if shot not in poses or shot not in ack or shot not in framing or shot not in effective: continue
            records[shot] = dict(shot=shot, raw=raw, screen=screen, log=str(log), pose=poses[shot], ack=ack[shot],
                                 conditions=conditions.get(shot,dict(fov=110.0,fov_effect_scale=1.0,
                                     evidence="retained_ab_v1_fixed_options_fov_1.0; no per-arm lens log")),
                                 captured=captures[shot], effective=effective[shot],gpu=gpu.get(shot), pipeline=pipe.get(shot),
                                 framing_edge_pixels=framing[shot], rejected=rejected.get(shot))
    return records, parity, traversal


def pair_matches(a, b):
    return all(a['pose'][k] == b['pose'][k] for k in ('origin','camera','aim')) and a['ack'] == b['ack'] \
        and a['captured'] == b['captured'] and a['effective'] == b['effective'] \
        and all(a['conditions'][k] == b['conditions'][k] for k in ('fov','fov_effect_scale'))


def expected_parity_shots():
    return {f'T257_{t}__{seed}__side' for t in TYPES for seed in SEEDS} | {
        f'T257_supercell__20261008__control_{v}' for v in ('neutral','tilt','shear','split','narrowing','wall')
    } | {'T257_supercell__20261008__attribution_intervals'}


def usable(r):
    if not r or r['rejected'] or r['framing_edge_pixels'] != 0: return False
    with Image.open(r['raw']) as im:
        a = np.asarray(im.convert('RGBA'))[:,:,3]
    return bool((a > 2).any()) and not bool((a[0,:] > 2).any() or (a[-1,:] > 2).any() or (a[:,0] > 2).any() or (a[:,-1] > 2).any())


def freeze_manifest(sources, records):
    root = Path(__file__).resolve().parents[4]
    protected = {}
    for f in sorted((root/'src/main').rglob('*')):
        if not f.is_file() or f.suffix not in ('.java','.glsl','.fsh','.vsh','.json'): continue
        if f.name in ('StormMorphologyCapture.java','StormMorphologyCapturePlan.java'): continue
        protected[str(f.relative_to(root)).replace('\\','/')] = hashlib.sha256(f.read_bytes()).hexdigest()
    protected['PAchangelog.md'] = hashlib.sha256((root/'PAchangelog.md').read_bytes()).hexdigest()
    originals = {}
    for folder, _ in sources:
        for f in sorted(Path(folder).glob('T257_*.png')):
            originals[str(f.resolve())] = hashlib.sha256(f.read_bytes()).hexdigest()
    return dict(protected_files=protected,original_capture_files=originals,
                head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),
                preserved_capture_arms=len(records),note='No Candidate A/B geometry or production changes authorized')


def label_pair(a_path,b_path,label,output,crop=None):
    """Full pair or shared-window detail pair. Never alter individual/raw capture files."""
    with Image.open(a_path) as ia, Image.open(b_path) as ib:
        a,b=ia.convert('RGB'),ib.convert('RGB')
    if a.size != b.size: raise ValueError('Unequal A/B frame dimensions')
    if crop is not None:a,b=a.crop(crop),b.crop(crop)
    w,h=a.size; canvas=Image.new('RGB',(2*w+8,h+54),(16,21,29)); draw=ImageDraw.Draw(canvas)
    header_font=font(22 if crop is None else max(7,min(18,int(w/12))))
    draw.text((12,8),'A = REJECTED'+(' | '+label if crop is None else ''),fill=(255,180,160),font=header_font)
    draw.text((w+20,8),'B = EXPERIMENTAL'+(' | '+label if crop is None else ''),fill=(160,210,255),font=header_font)
    canvas.paste(a,(0,54));canvas.paste(b,(w+8,54));canvas.save(output)


def shared_detail_crop(a,b):
    """One identical native-screen crop for both arms; originals and full pairs retained."""
    with Image.open(a['raw']) as ia,Image.open(b['raw']) as ib:
        mask=(np.asarray(ia.convert('RGBA'))[:,:,3]>2)|(np.asarray(ib.convert('RGBA'))[:,:,3]>2)
    ys,xs=np.nonzero(mask)
    with Image.open(a['screen']) as screen:w,h=screen.size
    sx,sy=w/mask.shape[1],h/mask.shape[0]
    x0,x1=int(xs.min()*sx),int(np.ceil((xs.max()+1)*sx))
    y0,y1=int(ys.min()*sy),int(np.ceil((ys.max()+1)*sy))
    padding=max(24,int(max(x1-x0,y1-y0)*.12))
    return (max(0,x0-padding),max(0,y0-padding),min(w,x1+padding),min(h,y1+padding))


def make_sheet(out,pairs,filename):
    cols=3;tw,th=650,210; rows=(len(pairs)+cols-1)//cols
    sheet=Image.new('RGB',(cols*tw,max(1,rows)*th),(16,21,29))
    draw=ImageDraw.Draw(sheet)
    for i,p in enumerate(pairs):
        with Image.open(out/p['detail_file']) as im:
            tile=im.convert('RGB');tile.thumbnail((tw-8,th-32))
        x=(i%cols)*tw+(tw-tile.width)//2;y=(i//cols)*th+26
        sheet.paste(tile,(x,y))
        label=f'{p["type"].removeprefix("cumulonimbus_")} | {p["seed"]} | {p["view"]} | A rejected / B experimental'
        draw.text(((i%cols)*tw+6,(i//cols)*th+2),label,fill=(220,235,250),font=font(13))
    sheet.save(out/filename,quality=94)


class Links(HTMLParser):
    def __init__(self):super().__init__();self.targets=[];self.ids=set()
    def handle_starttag(self,tag,attrs):
        attrs=dict(attrs)
        if 'id' in attrs:self.ids.add(attrs['id'])
        for key in ('href','src'):
            if key in attrs:self.targets.append(attrs[key])


def verify_links(out):
    broken=[];pages=list(out.rglob('*.html'));checked=0
    for f in pages:
        parser=Links();parser.feed(f.read_text(encoding='utf-8'))
        for link in parser.targets:
            if link.startswith(('http:','https:')):continue
            checked+=1
            if link.startswith('#'):
                if link[1:] not in parser.ids:broken.append(str(f)+':'+link)
            elif not (f.parent/link.split('#')[0]).resolve().exists():broken.append(str(f)+':'+link)
    return dict(pages=len(pages),links_checked=checked,broken=broken)


BASELINES=Path(__file__).resolve().with_name('t257-baselines.json')


def parse_fingerprints(path):
    found={}
    for line in Path(path).read_text(errors='replace').splitlines():
        m=re.search(r'T257_FINGERPRINT type=(\S+) seed=(\d+) level=FULL .*signature=(\S+) texture=(\S+)',line)
        if m:found[f'{m[1]}/{m[2]}']=dict(signature=m[3],texture_sha256=m[4])
    return found


def resolve_expectations(manifest,baseline,baselines):
    """Expected hash per protected file under an explicitly named candidate-B baseline.

    Versioned candidate-B files take the baseline's strict hash, or are delegated to the geometry
    fingerprint check; every other protected file keeps the manifest's strict hash."""
    if baseline not in baselines['baselines']:raise SystemExit(f'Unknown T257 baseline {baseline!r}; known: {sorted(baselines["baselines"])}')
    spec=baselines['baselines'][baseline];expected={};delegated=[]
    for name,digest in manifest['protected_files'].items():
        if name in spec.get('additive_files',{}):continue  # verified by verify_additive (frozen bytes + insertions only)
        if name in spec.get('strict_files',{}):expected[name]=spec['strict_files'][name]
        elif name in spec.get('fingerprint_files',{}):delegated.append(name)
        elif name in baselines['versioned_files']:raise SystemExit(f'Baseline {baseline} does not version {name}')
        else:expected[name]=digest
    return expected,delegated,spec


def verify_additive(root,name,rule,base_dir):
    """A shared plumbing file that later candidates extended: its frozen byte copy must hash to the
    capture-time digest, and the current file must equal that copy plus inserted lines only (no line of
    the captured source removed or changed)."""
    import difflib
    frozen=(base_dir/rule['frozen_copy']).resolve()
    if not frozen.is_file() or hashlib.sha256(frozen.read_bytes()).hexdigest()!=rule['sha256_at_capture']:return False
    cur=root/name
    if not cur.is_file():return False
    a=frozen.read_bytes().decode('utf-8').splitlines();b=cur.read_bytes().decode('utf-8').splitlines()
    ops=difflib.SequenceMatcher(None,a,b,autojunk=False).get_opcodes()
    return all(tag in ('equal','insert') for tag,*_ in ops)


def verify_frozen(path,baseline,fingerprint_log=None,baselines_path=BASELINES):
    manifest=json.loads(path.read_text(encoding='utf-8'));root=Path(__file__).resolve().parents[4];changed=[]
    baselines=json.loads(Path(baselines_path).read_text(encoding='utf-8'))
    expected,delegated,spec=resolve_expectations(manifest,baseline,baselines)
    for name,digest in expected.items():
        f=root/name
        if not f.is_file() or hashlib.sha256(f.read_bytes()).hexdigest()!=digest:changed.append(name)
    for name,digest in manifest['original_capture_files'].items():
        f=Path(name)
        if not f.is_file() or hashlib.sha256(f.read_bytes()).hexdigest()!=digest:changed.append(name)
    additive=sorted(spec.get('additive_files',{}))
    for name in additive:
        if name in manifest['protected_files'] and not verify_additive(root,name,spec['additive_files'][name],Path(baselines_path).resolve().parent):changed.append(name)
    fingerprints=None
    if delegated:
        # Fail closed: delegated files need a fingerprint log reproducing every recorded geometry.
        fingerprints=parse_fingerprints(fingerprint_log) if fingerprint_log else {}
        for key,want in spec['geometry_fingerprints'].items():
            if fingerprints.get(key)!=want:changed.append(f'geometry:{key}')
    head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()
    if head!=manifest['head']:changed.append('HEAD')
    return dict(baseline=baseline,baseline_status=spec['status'],protected_files=len(manifest['protected_files']),
                strict_hashes=len(expected),fingerprint_verified=delegated,additive_verified=additive,
                geometry_fingerprints=len(spec.get('geometry_fingerprints',{})) if delegated else 0,
                original_pngs=len(manifest['original_capture_files']),changed=changed,head=head,passed=not changed)


def recovered_main():
    parser=argparse.ArgumentParser(description='Frozen T257 A/B resume planning and native review gallery')
    parser.add_argument('capture_folder');parser.add_argument('capture_log');parser.add_argument('pass_name')
    parser.add_argument('--source',nargs=2,action='append',default=[],metavar=('FOLDER','LOG'))
    parser.add_argument('--plan',type=Path,help='Write a pending-arm list plus preservation hashes; no gallery export')
    parser.add_argument('--require-complete',action='store_true')
    parser.add_argument('--geometry-log',type=Path,help='CPU geometry measurement log; not visual acceptance')
    parser.add_argument('--verify-frozen',type=Path,help='Initial preservation/resume manifest')
    parser.add_argument('--baseline',help='Candidate-B baseline the gallery was captured from (t257-baselines.json), e.g. B-v2, B-v3')
    parser.add_argument('--fingerprint-log',type=Path,help='convectiveGrowthFieldFingerprint output (required by fingerprint-versioned baselines)')
    args=parser.parse_args();sources=[(args.capture_folder,args.capture_log)]+args.source
    records,parity,traversal=evidence_sources(sources); expected=expected_shots();pending=[];invalid=[]
    for t in TYPES:
        for seed in SEEDS:
            for view in VIEWS:
                b=f'T257_{t}__{seed}__{view}';a=f'T257_{t}__{seed}__a_{view}'
                if not (usable(records.get(a)) and usable(records.get(b)) and pair_matches(records[a],records[b])):
                    pending += [b,a]
                    if a in records or b in records: invalid.append(dict(type=t,seed=seed,view=view,reason='incomplete, clipped or unmatched pair'))
    attrs=expected[:2]
    if not(all(usable(records.get(s)) for s in attrs) and any(t['passed'] for t in traversal)):pending+=attrs
    pending += [s for s in expected[-6:] if not usable(records.get(s))]
    pending=[s for s in expected if s in set(pending)]
    if args.plan:
        manifest=freeze_manifest(sources,records);manifest.update(pending=pending,invalid_pairs=invalid,sources=sources)
        args.plan.parent.mkdir(parents=True,exist_ok=True)
        args.plan.write_text(json.dumps(manifest,indent=2),encoding='utf-8')
        args.plan.with_suffix('.txt').write_text('\n'.join(pending)+'\n',encoding='utf-8')
        print(json.dumps(dict(preserved=len(records),pending=len(pending),invalid_pairs=invalid,plan=str(args.plan)),indent=2));return
    geometry=[]
    if args.geometry_log:
        for line in args.geometry_log.read_text(errors='replace').splitlines():
            m=re.search(r'T257_GEOMETRY (.+)',line)
            if not m:continue
            fields=dict(re.findall(r'(\w+)=([^ ]+)',m[1]));rec={k:(v if k in ('type','seed','model') else float(v)) for k,v in fields.items()}
            if rec['type']=='cumulonimbus_calvus':rec['canopyThicknessToHeight']=rec['canopyLateralExtent']=None
            geometry.append(rec)
    out=Path(__file__).resolve().parent/'native'/args.pass_name;out.mkdir(parents=True,exist_ok=True)
    exported=[];pairs=[]
    for shot,r in records.items():
        _,t,seed,view=re.match(r'(T257)_(.+)__(\d+)__(.+)',shot).groups()
        d=out/t/seed;d.mkdir(parents=True,exist_ok=True)
        for src,suffix in ((r['raw'],'__raw.png'),(r['screen'],'.png')):shutil.copyfile(src,d/(view+suffix))
        clean={k:v for k,v in r.items() if k not in ('raw','screen')}
        clean.update(type=t,seed=seed,view=view,model='A' if view.startswith('a_') else 'B',
                     file=f'{t}/{seed}/{view}__raw.png',sha256=hashlib.sha256(r['raw'].read_bytes()).hexdigest(),
                     silhouette=metrics(r['raw'],view.removeprefix('a_')))
        exported.append(clean)
    for g in geometry:
        side='a_side' if g['model']=='A' else 'side'
        matched=[r for r in exported if r['type']==g['type'] and r['seed']==g['seed'] and r['view']==side]
        g['outlineIrregularity']=matched[0]['silhouette'].get('outline_detail') if matched else None
    for t in TYPES:
        for seed in SEEDS:
            for view in VIEWS:
                a=records.get(f'T257_{t}__{seed}__a_{view}');b=records.get(f'T257_{t}__{seed}__{view}')
                if not a or not b:continue
                pair_file=f'{t}/{seed}/pair-{view}.png';label=f'{t} / {seed} / {view}'
                label_pair(a['screen'],b['screen'],label,out/pair_file)
                detail_file=f'{t}/{seed}/detail-{view}.png';crop=shared_detail_crop(a,b)
                label_pair(a['screen'],b['screen'],label,out/detail_file,crop)
                pairs.append(dict(type=t,seed=seed,view=view,pair_file=pair_file,detail_file=detail_file,shared_screen_crop=crop,cameras_match=pair_matches(a,b),
                                  valid=usable(a) and usable(b) and pair_matches(a,b),
                                  a=f'{t}/{seed}/a_{view}__raw.png',b=f'{t}/{seed}/{view}__raw.png'))
    make_sheet(out,[p for p in pairs if p['view'] in ('side','portrait_front','above','transition')],'contact-overall.jpg')
    make_sheet(out,pairs,'contact-all-views.jpg')
    for t in TYPES:
        make_sheet(out,[p for p in pairs if p['type']==t],f'{t}/contact-sheet.jpg')
        for seed in SEEDS:make_sheet(out,[p for p in pairs if p['type']==t and p['seed']==seed],f'{t}/{seed}/contact-sheet.jpg')
    style="<style>body{background:#10151d;color:#dee8f4;font:16px system-ui;margin:20px}a{color:#83c2ff}img{max-width:100%}figure{margin:18px 0}</style>"
    notice='<p>A = REJECTED. B = EXPERIMENTAL, awaiting visual review; not accepted or productionized. Flat neutral light, micro/history off. Original frames/raw targets and uncropped paired frames retained. Detail panels/contact sheets use one identical A/B crop window and scale, with the entire silhouette plus margin; crop coordinates are recorded in comparison.json.</p>'
    def page(title,body):return f'<!doctype html><meta charset="utf-8"><title>{html.escape(title)}</title>{style}<h1>{html.escape(title)}</h1>{notice}'+body
    overview=f'<p>{len(pairs)}/171 A/B pairs; {len(records)}/350 unique arms. Missing/invalid arms: {len(pending)}.</p><p><a href="../../../t257-convective-growth-field.md">Report and review status</a> | <a href="metrics.json">Measurements/provenance</a> | <a href="comparison.json">Pair validation</a></p>'
    overview+='<ul>'+''.join(f'<li><a href="{t}/index.html">{t}</a> — <a href="{t}/contact-sheet.jpg">type contact sheet</a></li>' for t in TYPES)+'</ul><a href="contact-overall.jpg">Overall comparison sheet (side/front/above/transition)</a> | <a href="contact-all-views.jpg">All views contact sheet</a> | <a href="validation.html">Traversal and control arms</a>'
    (out/'index.html').write_text(page('T257 frozen A/B native visual review — '+args.pass_name,overview),encoding='utf-8')
    support='<a href="index.html">All families</a><p>Candidate B diagnostics, not anatomy acceptance or performance optimization.</p>'
    for r in exported:
        if not r['view'].startswith(('control_','attribution_')):continue
        frame=r['file'].replace('__raw','')
        support+=f'<figure><figcaption>{r["view"]} — B = EXPERIMENTAL</figcaption><a href="{frame}"><img loading="lazy" src="{frame}"></a><a href="{r["file"]}">Unmodified raw cloud target</a></figure>'
    (out/'validation.html').write_text(page('T257 B traversal and control evidence',support),encoding='utf-8')
    for t in TYPES:
        body='<a href="../index.html">All families</a><ul>'+''.join(f'<li><a href="{s}/index.html">seed {s}</a></li>' for s in SEEDS)+'</ul><img src="contact-sheet.jpg">'
        (out/t/'index.html').write_text(page(t,body),encoding='utf-8')
        for seed in SEEDS:
            rows=[p for p in pairs if p['type']==t and p['seed']==seed]
            body='<a href="../index.html">Family</a> | <a href="../../index.html">All families</a><p>Inspect base, tower, heads/creases, crown, transition, canopy thickness/edge and asymmetry. Ratios do not establish realism.</p><ul>'
            body+=''.join(f'<li><a href="#{p["view"]}">{p["view"]}</a></li>' for p in rows)+'</ul>'
            gm=[g for g in geometry if g['type']==t and g['seed']==seed]
            if gm:
                body+='<h2>Anatomy measurements (not visual acceptance)</h2><table><tr><th>Candidate</th><th>Height / tower width</th><th>Base width / height</th><th>Canopy thickness / height</th><th>Canopy lateral extent (blocks)</th><th>Tower lean (blocks)</th><th>Outline irregularity (native side)</th></tr>'
                for g in gm:
                    body+='<tr><td>'+g['model']+'</td>'+''.join('<td>'+('n/a' if g.get(k) is None else f'{g[k]:.3f}')+'</td>' for k in ('heightToTowerWidth','baseWidthToHeight','canopyThicknessToHeight','canopyLateralExtent','towerLean','outlineIrregularity'))+'</tr>'
                body+='</table><p>Body measures: 10-block occupancy grid. Canopy alone: 20-block grid, X span and median best-crosswind vertical thickness in its 40–85% downwind band, excluding body/merge. Lean: authored tower-axis displacement. Outline irregularity: native alpha perimeter relative to morphologically smoothed perimeter; scale-dependent diagnostic, not a realism score. Calvus has no canopy.</p>'
            for p in rows:
                body+=f'<figure id="{p["view"]}"><figcaption>{p["view"]} — matching cameras: {p["cameras_match"]}; valid framing: {p["valid"]}</figcaption><a href="detail-{p["view"]}.png"><img loading="lazy" src="detail-{p["view"]}.png"></a><p><a href="pair-{p["view"]}.png">Uncropped A/B comparison</a> | <a href="a_{p["view"]}__raw.png">A raw</a> | <a href="{p["view"]}__raw.png">B raw</a> | <a href="a_{p["view"]}.png">A full frame</a> | <a href="{p["view"]}.png">B full frame</a></p></figure>'
            (out/t/seed/'index.html').write_text(page(f'{t} — seed {seed}',body),encoding='utf-8')
    result=dict(records=exported,geometry_measurements=geometry,geometry_source_log=str(args.geometry_log) if args.geometry_log else None,
                parity=parity,traversal=traversal,pending=pending,invalid_pairs=invalid,
                capture_complete=not pending,candidate_a='REJECTED',candidate_b='EXPERIMENTAL',sources=sources)
    (out/'metrics.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    (out/'comparison.json').write_text(json.dumps(dict(pairs=pairs,missing_or_invalid=pending,acceptance='NOT_APPROVED'),indent=2),encoding='utf-8')
    audit=verify_links(out)
    audit['missing_parity_arms']=sorted(expected_parity_shots()-parity.keys())
    if args.verify_frozen:
        if not args.baseline:raise SystemExit('--verify-frozen needs an explicit --baseline (B-v2 or B-v3)')
        audit['preservation']=verify_frozen(args.verify_frozen,args.baseline,args.fingerprint_log)
    (out/'gallery-audit.json').write_text(json.dumps(audit,indent=2),encoding='utf-8')
    print(json.dumps(dict(arms=len(records),pairs=len(pairs),pending=len(pending),parity_arms=len(parity),output=str(out)),indent=2))
    if args.require_complete and (pending or len(pairs)!=171 or len(geometry)!=18 or audit['broken'] or audit['missing_parity_arms'] \
            or not audit.get('preservation',{}).get('passed',False) \
            or any(not p['valid'] for p in pairs) or any(not p['passed'] for p in parity.values()) or not any(t['passed'] for t in traversal)):
        raise SystemExit('Incomplete/failed evidence: gallery is not ready for visual review')


if __name__ == "__main__":
    recovered_main()
