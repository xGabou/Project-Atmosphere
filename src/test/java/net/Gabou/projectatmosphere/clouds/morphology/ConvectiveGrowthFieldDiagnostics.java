package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.type.CloudFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudShapeProfile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * T257 B v3 structural attribution (diagnostic only, never acceptance evidence by itself).
 *
 * Reads the native D0/D1/D2 cloud-target captures (core / core + mesoscale heads / complete B v3 at the
 * identical seed and camera) and renders D3: the SAME complete geometry (FULL program, fingerprint-
 * identical to the D2 fixture) ray-cast exactly on the CPU at the native camera, lens and resolution,
 * exposing what flat light cannot show - surface creases, interior occluding contours and the head relief
 * above the core surface. Writes per-view panels and one metrics line per view (JSON).
 *
 * Args: captureFolder captureLog outDir
 */
public final class ConvectiveGrowthFieldDiagnostics {
    static final String[] TYPES = {"cumulonimbus_calvus", "cumulonimbus_capillatus", "supercell"};
    static final long[] SEEDS = {20261008L, 20268927L, 20276846L};
    static final String[] VIEWS = {"side", "opposite_side", "front", "back", "above", "below", "low_angle", "far", "close", "upwind",
            "downwind", "crosswind_left", "crosswind_right", "tower", "transition", "canopy_anvil", "updraft_oblique", "portrait", "portrait_front"};
    static final Set<String> SIDE = Set.of("side", "opposite_side", "close", "far", "crosswind_left", "crosswind_right", "portrait");
    static final Set<String> PLAN = Set.of("above", "below");
    static final int W = 1200, H = 675;
    static final double FOV = 110;
    /** The game applies its flying FOV modifier (x1.1) on top of options.fov 110; calibrated: CPU/native silhouette IoU 0.994 at 1.1 vs 0.80 at 1.05 and 1.15. */
    static final double FOV_MODIFIER = Double.parseDouble(System.getenv().getOrDefault("T257_DIAG_FOV_MODIFIER", "1.1"));

    record Pose(double[] origin, double[] camera, double[] aim) {}

    public static void main(String[] args) throws Exception {
        Path folder = Path.of(args[0]), log = Path.of(args[1]), out = Path.of(args[2]);
        Files.createDirectories(out);
        Map<String, Pose> poses = new HashMap<>();
        Map<String, Double> fovs = new HashMap<>();
        double screenScale = 1600.0 / W;
        Pattern pose = Pattern.compile("T257_POSE shot=(\\S+) .*origin=\\(([^)]+)\\).*camera=\\(([^)]+)\\) aim=\\(([^)]+)\\)");
        Pattern lens = Pattern.compile("T257_LENS shot=(\\S+) fov=(\\S+)");
        Pattern main = Pattern.compile("T098_CAPTURE shot=T257\\S+ .*mainTarget=(\\d+)x(\\d+)");
        for (String line : Files.readAllLines(log)) {
            Matcher m = pose.matcher(line);
            if (m.find()) poses.put(m.group(1), new Pose(vec(m.group(2)), vec(m.group(3)), vec(m.group(4))));
            m = lens.matcher(line);
            if (m.find()) fovs.put(m.group(1), Double.parseDouble(m.group(2)));
            m = main.matcher(line);
            if (m.find()) screenScale = Double.parseDouble(m.group(1)) / W;
        }
        StringBuilder metrics = new StringBuilder();
        // Resolution-free geometry first (one line per type/seed).
        for (String type : TYPES) for (long seed : SEEDS) metrics.append(geometry(type, seed)).append('\n');
        String only = System.getenv("T257_DIAG_ONLY");
        for (String type : TYPES) for (long seed : SEEDS) for (String view : VIEWS) {
            String base = "T257_" + type + "__" + seed + "__";
            if (only != null && !(base + view).contains(only)) continue;
            Pose p = poses.get(base + view);
            File f0 = folder.resolve(base + "d0_" + view + "__raw.png").toFile(), f1 = folder.resolve(base + "d1_" + view + "__raw.png").toFile(),
                    f2 = folder.resolve(base + view + "__raw.png").toFile();
            if (p == null || !f0.isFile() || !f1.isFile() || !f2.isFile()) { System.out.println("T257_DIAG_MISSING " + base + view); continue; }
            for (String arm : new String[]{"d0_", "d1_"}) {
                Pose q = poses.get(base + arm + view);
                if (q == null || !Arrays.equals(q.camera, p.camera) || !Arrays.equals(q.aim, p.aim) || !Arrays.equals(q.origin, p.origin)) {
                    throw new IllegalStateException("D0/D1/D2 cameras differ for " + base + view);
                }
            }
            double fov = fovs.getOrDefault(base + view, FOV) * FOV_MODIFIER;
            String line = view(type, seed, view, p, fov, screenScale, f0, f1, f2, out);
            metrics.append(line).append('\n');
            System.out.println(line.length() > 400 ? line.substring(0, 400) + "..." : line);
        }
        Files.writeString(out.resolve("t257-diagnostic-metrics.jsonl"), metrics.toString());
    }

    static double[] vec(String s) { return Arrays.stream(s.split(",")).mapToDouble(Double::parseDouble).toArray(); }

    static CloudShapeProfile shape(String type) { return CloudShapeProfile.defaultFor(type, CloudFamily.CUMULONIMBUS, null); }

    // ---------------------------------------------------------------- resolution-free geometry (3D)

    static String geometry(String type, long seed) {
        var full = ConvectiveGrowthFieldPrototype.compile(type, shape(type), seed);
        var meso = ConvectiveGrowthFieldPrototype.compile(type, shape(type), seed, ConvectiveGrowthFieldPrototype.Detail.MESO);
        float Hh = full.height(), yBase = full.baseY(), feather = full.feather();
        float sc = full.param(7, 2);
        // Envelope merging: along each map row, the depth of the valley between adjacent displacement maxima
        // relative to the lower of the two peaks (1 = heads fully separated by creases, 0 = merged envelope).
        double[] col = valleyStats(full, 0, .30f, sc - .14f), colMeso = valleyStats(meso, 0, .30f, sc - .14f);
        double[] crown = valleyStats(full, 1, .5f, 1f);
        // Swallowed relief: column/crown surface points (found by bisection from the axis) that lie inside
        // the complete union deeper than feather/2 are invisible; weight by local head displacement.
        double buriedRelief = 0, totalRelief = 0, buriedArea = 0, area = 0, upperBuried = 0, upperArea = 0;
        for (int j = 0; j < 96; j++) {
            float s = .02f + (sc - .03f) * j / 95f, y = yBase + Hh * s;
            float cx = full.spineX(s), cz = full.spineZ(s);
            for (int i = 0; i < 128; i++) {
                float th = (float) (-Math.PI + 2 * Math.PI * (i + .5) / 128);
                float dx = (float) Math.cos(th), dz = (float) Math.sin(th);
                float lo = 0, hi = 4 * Hh;
                if (full.columnDistance(cx, y, cz) > 0) continue;
                for (int k = 0; k < 40; k++) { float mid = .5f * (lo + hi); if (full.columnDistance(cx + dx * mid, y, cz + dz * mid) > 0) hi = mid; else lo = mid; }
                float px = cx + dx * lo, pz = cz + dz * lo;
                float qx = px - full.spineX(s), qz = pz - full.spineZ(s);
                float disp = full.sampleMap(0, (float) Math.atan2(qz, qx), s);
                boolean buried = full.referenceDistance(px + dx * feather * .5f, y, pz + dz * feather * .5f) < -feather * .5f;
                area++; totalRelief += disp;
                if (buried) { buriedArea++; buriedRelief += disp; }
                if (s > .5f) { upperArea++; if (buried) upperBuried++; }
            }
        }
        float r0mid = full.profile(.45f);
        float cellH = full.param(21, 1), cellAmp = full.param(21, 2), cellRad = full.param(21, 3);
        float crownR = full.param(9, 0), crownCell = full.param(22, 2);
        return String.format(Locale.ROOT,
                "{\"kind\":\"geometry\",\"type\":\"%s\",\"seed\":%d,\"H\":%.1f,\"feather\":%.2f,\"r0_mid\":%.1f,\"cellH\":%.1f,\"cellRadius\":%.1f,"
                        + "\"cellAmpMax\":%.1f,\"cellAmpOverBodyWidth\":%.4f,\"cellAmpOverRadius\":%.3f,\"octave2Amp\":%.1f,\"crownRadius\":%.1f,\"crownCellAmpBlocks\":%.1f,"
                        + "\"sideTurretAmpMax\":%.1f,\"turrets\":%d,\"crownHeads\":%d,"
                        + "\"columnValleyRatio\":%.3f,\"columnValleyRatioMeso\":%.3f,\"columnCoverage\":%.3f,\"columnMeanDisp\":%.1f,\"crownValleyRatio\":%.3f,\"crownCoverage\":%.3f,"
                        + "\"buriedColumnArea\":%.3f,\"buriedColumnRelief\":%.3f,\"buriedUpperColumnArea\":%.3f,\"creaseDepthOverFeather\":%.2f}",
                type, seed, Hh, feather, r0mid, cellH, cellRad, cellAmp, cellAmp / (2 * r0mid), cellAmp / cellRad, full.param(23, 0), crownR, crownCell * crownR,
                full.param(14, 0), full.turrets().size(), full.crownHeads().size(),
                col[0], colMeso[0], col[1], col[2], crown[0], crown[1],
                buriedArea / Math.max(1, area), buriedRelief / Math.max(1e-6, totalRelief), upperBuried / Math.max(1, upperArea), col[3] / feather);
    }

    /** {mean valley/peak ratio, coverage (disp > 10% max), mean displacement, mean valley depth (blocks)}. */
    static double[] valleyStats(ConvectiveGrowthFieldPrototype.Program p, int lane, float v0, float v1) {
        int n = 512;
        double ratio = 0, depth = 0, cover = 0, mean = 0, count = 0, cells = 0, rows = 0;
        float max = 0;
        for (int j = 0; j < 64; j++) for (int i = 0; i < n; i++) max = Math.max(max, p.sampleMap(lane, (float) (-Math.PI + 2 * Math.PI * i / n), v0 + (v1 - v0) * j / 63f));
        for (int j = 0; j < 64; j++) {
            float v = v0 + (v1 - v0) * j / 63f;
            float[] d = new float[n];
            for (int i = 0; i < n; i++) { d[i] = p.sampleMap(lane, (float) (-Math.PI + 2 * Math.PI * i / n), v); mean += d[i]; if (d[i] > .1f * max) cover++; cells++; }
            List<Integer> peaks = new ArrayList<>();
            for (int i = 0; i < n; i++) if (d[i] > d[(i + n - 1) % n] && d[i] >= d[(i + 1) % n] && d[i] > .1f * max) peaks.add(i);
            for (int k = 0; k < peaks.size() && peaks.size() > 1; k++) {
                int a = peaks.get(k), b = peaks.get((k + 1) % peaks.size());
                float lo = Float.MAX_VALUE;
                for (int i = a; i != b; i = (i + 1) % n) lo = Math.min(lo, d[i]);
                float top = Math.min(d[a], d[b]);
                ratio += (top - lo) / top; depth += top - lo; count++;
            }
            rows++;
        }
        return new double[]{count == 0 ? 0 : ratio / count, cover / cells, mean / cells, count == 0 ? 0 : depth / count};
    }

    // ---------------------------------------------------------------- per view

    static String view(String type, long seed, String view, Pose pose, double fov, double screenScale,
                       File f0, File f1, File f2, Path out) throws Exception {
        BufferedImage i0 = ImageIO.read(f0), i1 = ImageIO.read(f1), i2 = ImageIO.read(f2);
        boolean[] m0 = mask(i0), m1 = mask(i1), m2 = mask(i2);
        var full = ConvectiveGrowthFieldPrototype.compile(type, shape(type), seed);
        var core = ConvectiveGrowthFieldPrototype.compile(type, shape(type), seed, ConvectiveGrowthFieldPrototype.Detail.CORE);
        double[] o = {pose.origin[0], pose.origin[1], pose.origin[2]};
        double[] cam = {pose.camera[0] - o[0], pose.camera[1] - o[1], pose.camera[2] - o[2]};
        double[] fwd = norm(sub(new double[]{pose.aim[0] - o[0], pose.aim[1] - o[1], pose.aim[2] - o[2]}, cam));
        double[] right = norm(cross(fwd, new double[]{0, 1, 0})), up = cross(right, fwd);
        double ty = Math.tan(Math.toRadians(fov) / 2), tx = ty * W / H, focal = (H / 2.0) / ty;
        double aimDist = Math.sqrt(sq(pose.aim[0] - pose.camera[0]) + sq(pose.aim[1] - pose.camera[1]) + sq(pose.aim[2] - pose.camera[2]));

        // D3: exact sphere trace of the complete field (Lipschitz-safe step), hit at the density-0.5 iso-surface.
        float feather = full.feather();
        var b = full.bounds();
        float[] depth = new float[W * H], nx = new float[W * H], ny = new float[W * H], nz = new float[W * H], relief = new float[W * H];
        byte[] comp = new byte[W * H];
        boolean[] cpuSil = new boolean[W * H];
        IntStream.range(0, H).parallel().forEach(j -> {
            for (int i = 0; i < W; i++) {
                double sx = (2 * (i + .5) / W - 1) * tx, sy = (1 - 2 * (j + .5) / H) * ty;
                double[] d = norm(new double[]{fwd[0] + right[0] * sx + up[0] * sy, fwd[1] + right[1] * sx + up[1] * sy, fwd[2] + right[2] * sx + up[2] * sy});
                double[] slab = slab(cam, d, b);
                int k = j * W + i;
                depth[k] = -1;
                if (slab == null) continue;
                double t = slab[0];
                for (int step = 0; step < 4000 && t < slab[1]; step++) {
                    float x = (float) (cam[0] + d[0] * t), y = (float) (cam[1] + d[1] * t), z = (float) (cam[2] + d[2] * t);
                    float dist = full.referenceDistance(x, y, z);
                    if (dist < feather * .5f) cpuSil[k] = true;
                    if (dist < .25f) {
                        depth[k] = (float) t;
                        float e = 3;
                        float gx = full.referenceDistance(x + e, y, z) - full.referenceDistance(x - e, y, z);
                        float gy = full.referenceDistance(x, y + e, z) - full.referenceDistance(x, y - e, z);
                        float gz = full.referenceDistance(x, y, z + e) - full.referenceDistance(x, y, z - e);
                        float gl = (float) Math.sqrt(gx * gx + gy * gy + gz * gz) + 1e-9f;
                        nx[k] = gx / gl; ny[k] = gy / gl; nz[k] = gz / gl;
                        float col = full.columnDistance(x, y, z), cr = full.crownDistance(x, y, z), can = full.canopyDistance(x, y, z);
                        comp[k] = (byte) (can <= Math.min(col, cr) ? 3 : cr < col ? 2 : 1);
                        relief[k] = Math.max(0, core.referenceDistance(x, y, z)) * (float) (focal / t); // px above the core surface
                        break;
                    }
                    t += Math.max(dist / 6.5, .5);
                }
            }
        });

        // Silhouette geometry.
        int[] box = bbox(m2);
        int widthPx = box[2] - box[0] + 1, heightPx = box[3] - box[1] + 1;
        float[] edt0In = edt(m0, true), edt0Out = edt(m0, false), edt1In = edt(m1, true), edt1Out = edt(m1, false), edt2Out = edt(m2, false);
        double[] disp01 = displacement(m1, edt0In, edt0Out), disp02 = displacement(m2, edt0In, edt0Out), disp12 = displacement(m2, edt1In, edt1Out);
        int xor02 = 0, area0 = 0;
        for (int k = 0; k < W * H; k++) { if (m0[k] != m2[k]) xor02++; if (m0[k]) area0++; }
        // Gallery scale: the review contact sheet thumbnails an A/B detail pair (two crops + 54 px header) into 642x178.
        double cropW = widthPx * 1.24 * screenScale, cropH = heightPx * 1.24 * screenScale;
        double gallery = Math.min(642 / (2 * cropW + 8), 178 / (cropH + 54)) * screenScale; // sheet px per native px
        int[] contour2 = largestContour(m2), contour1 = largestContour(m1);
        float[] sig2 = signal(contour2, edt0In, edt0Out), sig1 = signal(contour1, edt0In, edt0Out);
        int heads2 = peaks(sig2, 2.0, 4).size(), heads1 = peaks(sig1, 2.0, 4).size();
        int heads2Gallery = peaks(sig2, 1.0 / gallery, (int) Math.ceil(2 / gallery)).size();
        List<Integer> pk = peaks(sig2, 2.0, 4);
        double ampRel = 0;
        for (int idx : pk) {
            int y = contour2[2 * idx + 1];
            int rw = rowWidth(m2, y);
            ampRel += prominence(sig2, idx) / Math.max(1, rw);
        }
        ampRel = pk.isEmpty() ? 0 : ampRel / pk.size();
        double[] crownIrr = {crown(m0, box), crown(m1, box), crown(m2, box)};
        double[] heightVar = {heightVariation(m0, box), heightVariation(m1, box), heightVariation(m2, box)};
        double[] planEdge = PLAN.contains(view) ? new double[]{polarEdge(m0), polarEdge(m1), polarEdge(m2)} : null;
        double[][] anvil = SIDE.contains(view) && !type.endsWith("calvus") ? new double[][]{anvilEdges(m0, box), anvilEdges(m1, box), anvilEdges(m2, box)} : null;

        // D3 contour budget: creases (normal turns > 25 deg over 2 px at continuous depth) and interior
        // occluding contours (depth jumps > 3 %). Visible in flat light only on the outer silhouette.
        boolean[] crease = new boolean[W * H], occl = new boolean[W * H];
        int creaseN = 0, creaseOuter = 0, occlN = 0, occlOuter = 0, hitN = 0;
        double reliefSum = 0, reliefOuter = 0;
        float[] edtCpuOut = edt(hitMask(depth), false);
        boolean[] hit = hitMask(depth);
        float[] edtHitIn = edt(hit, true);
        for (int j = 2; j < H - 2; j++) for (int i = 2; i < W - 2; i++) {
            int k = j * W + i;
            if (depth[k] < 0) continue;
            hitN++;
            boolean outer = edtHitIn[k] <= 2.5f;
            reliefSum += relief[k];
            if (edtHitIn[k] <= 3.5f) reliefOuter += relief[k];
            for (int[] dd : new int[][]{{2, 0}, {0, 2}}) {
                int q = (j + dd[1]) * W + i + dd[0];
                if (depth[q] < 0) continue;
                if (Math.abs(depth[q] - depth[k]) > .03f * depth[k]) { if (!occl[k]) { occl[k] = true; occlN++; if (outer) occlOuter++; } }
                else if (nx[k] * nx[q] + ny[k] * ny[q] + nz[k] * nz[q] < Math.cos(Math.toRadians(25))) {
                    if (!crease[k]) { crease[k] = true; creaseN++; if (outer) creaseOuter++; }
                }
            }
        }
        int iou = 0, uni = 0;
        for (int k = 0; k < W * H; k++) { if (cpuSil[k] && m2[k]) iou++; if (cpuSil[k] || m2[k]) uni++; }
        double featherPx = feather * focal / aimDist;

        // Panels: D0 | D1 | D2 native (unaltered alpha over dark ground) | D3 diagnostics | relief.
        int[] crop = {Math.max(0, box[0] - widthPx / 10 - 8), Math.max(0, box[1] - heightPx / 10 - 8),
                Math.min(W - 1, box[2] + widthPx / 10 + 8), Math.min(H - 1, box[3] + heightPx / 10 + 8)};
        int cw = crop[2] - crop[0] + 1, ch = crop[3] - crop[1] + 1;
        BufferedImage panel = new BufferedImage(cw * 5 + 32, ch, BufferedImage.TYPE_INT_RGB);
        BufferedImage d3 = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB), rel = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        boolean[] e0 = edge(m0), e1 = edge(m1), e2 = edge(m2);
        for (int k = 0; k < W * H; k++) {
            int i = k % W, j = k / W, rgb = 0x10151d;
            if (depth[k] >= 0) {
                // Headlight N.V shading of the exact surface: a geometry probe, not a lighting proposal.
                double[] d = norm(new double[]{fwd[0] + right[0] * ((2 * (i + .5) / W - 1) * tx) + up[0] * ((1 - 2 * (j + .5) / H) * ty),
                        fwd[1] + right[1] * ((2 * (i + .5) / W - 1) * tx) + up[1] * ((1 - 2 * (j + .5) / H) * ty),
                        fwd[2] + right[2] * ((2 * (i + .5) / W - 1) * tx) + up[2] * ((1 - 2 * (j + .5) / H) * ty)});
                double ndv = Math.max(0, -(nx[k] * d[0] + ny[k] * d[1] + nz[k] * d[2]));
                int g = (int) (40 + 180 * ndv);
                int tint = comp[k] == 3 ? 0x000a18 : comp[k] == 2 ? 0x0a0a00 : 0;
                rgb = (g << 16 | g << 8 | g) + tint;
            }
            if (crease[k]) rgb = 0xff3030;
            if (occl[k]) rgb = 0xff40ff;
            if (e0[k]) rgb = 0x30d0ff;
            if (e2[k]) rgb = 0xffffff;
            d3.setRGB(i, j, rgb);
            int rr = depth[k] < 0 ? 0x10151d : heat(relief[k] / 12.0);
            if (e2[k]) rr = 0xffffff;
            if (e0[k]) rr = 0x30d0ff;
            rel.setRGB(i, j, rr);
        }
        BufferedImage[] srcs = {flat(i0, e0, 0x30d0ff), flat(i1, e1, 0xffd040), flat(i2, e2, 0xffffff), d3, rel};
        for (int s = 0; s < 5; s++) for (int j = 0; j < ch; j++) for (int i = 0; i < cw; i++) panel.setRGB(s * (cw + 8) + i, j, srcs[s].getRGB(crop[0] + i, crop[1] + j));
        for (int s = 1; s < 5; s++) for (int j = 0; j < ch; j++) for (int i = 0; i < 8; i++) panel.setRGB(s * (cw + 8) - 8 + i, j, 0x344155);
        File dir = out.resolve(type).resolve(Long.toString(seed)).toFile();
        dir.mkdirs();
        ImageIO.write(panel, "png", new File(dir, "diag-" + view + ".png"));
        ImageIO.write(d3, "png", new File(dir, "d3_" + view + ".png"));
        ImageIO.write(rel, "png", new File(dir, "relief_" + view + ".png"));

        if (ZOOM_VIEWS.contains(view)) zoom(full, core, cam, fwd, right, up, tx, ty, crop, 4, new File(dir, "zoom4_" + view + ".png"));

        StringBuilder js = new StringBuilder();
        js.append(String.format(Locale.ROOT, "{\"kind\":\"view\",\"type\":\"%s\",\"seed\":%d,\"view\":\"%s\",\"panel\":\"%s/%d/diag-%s.png\",", type, seed, view, type, seed, view));
        js.append(String.format(Locale.ROOT, "\"widthPx\":%d,\"heightPx\":%d,\"galleryScale\":%.4f,\"cameraDistance\":%.0f,\"featherPx\":%.2f,\"cpuNativeIoU\":%.4f,",
                widthPx, heightPx, gallery, aimDist, featherPx, iou / (double) Math.max(1, uni)));
        js.append(String.format(Locale.ROOT, "\"disp01\":%s,\"disp02\":%s,\"disp12\":%s,\"xor02OverArea0\":%.4f,", arr(disp01), arr(disp02), arr(disp12), xor02 / (double) Math.max(1, area0)));
        js.append(String.format(Locale.ROOT, "\"disp02MeanOverWidth\":%.4f,\"heads\":{\"d1\":%d,\"d2\":%d,\"d2Gallery\":%d},\"headProminenceOverRowWidth\":%.4f,",
                disp02[0] / widthPx, heads1, heads2, heads2Gallery, ampRel));
        js.append(String.format(Locale.ROOT, "\"crownIrregularity\":%s,\"heightVariation\":%s,", arr(crownIrr), arr(heightVar)));
        if (planEdge != null) js.append(String.format(Locale.ROOT, "\"canopyEdgeVariation\":%s,", arr(planEdge)));
        if (anvil != null) js.append(String.format(Locale.ROOT, "\"anvil\":{\"d0\":%s,\"d1\":%s,\"d2\":%s},", arr(anvil[0]), arr(anvil[1]), arr(anvil[2])));
        js.append(String.format(Locale.ROOT, "\"d3\":{\"hitPx\":%d,\"creasePx\":%d,\"creaseOnSilhouette\":%.4f,\"occludingPx\":%d,\"occludingOnSilhouette\":%.4f,\"meanReliefPx\":%.2f,\"reliefOnSilhouette\":%.4f}}",
                hitN, creaseN, creaseOuter / (double) Math.max(1, creaseN), occlN, occlOuter / (double) Math.max(1, occlN), reliefSum / Math.max(1, hitN), reliefOuter / Math.max(1e-9, reliefSum)));
        return js.toString();
    }

    static final Set<String> ZOOM_VIEWS = Set.of("side", "portrait_front", "above", "transition");

    /**
     * Resolution probe: the same camera ray-cast at Z x the native pixel density over the native crop.
     * Tiles: exact iso-silhouette of CORE | exact iso-silhouette of FULL (both flat, density 0.5) | FULL
     * headlight shading with creases (red). Answers whether the native resolution hides structure.
     */
    static void zoom(ConvectiveGrowthFieldPrototype.Program full, ConvectiveGrowthFieldPrototype.Program core, double[] cam, double[] fwd,
                     double[] right, double[] up, double tx, double ty, int[] crop, int Z, File file) throws Exception {
        int w = (crop[2] - crop[0] + 1) * Z, h = (crop[3] - crop[1] + 1) * Z;
        BufferedImage img = new BufferedImage(3 * w + 16, h, BufferedImage.TYPE_INT_RGB);
        float[] depth = new float[w * h], nx = new float[w * h], ny = new float[w * h], nz = new float[w * h];
        boolean[] coreSil = new boolean[w * h];
        double[][] dirs = new double[w * h][];
        IntStream.range(0, h).parallel().forEach(j -> {
            for (int i = 0; i < w; i++) {
                double u = crop[0] + (i + .5) / Z, v = crop[1] + (j + .5) / Z;
                double sx = (2 * u / W - 1) * tx, sy = (1 - 2 * v / H) * ty;
                double[] d = norm(new double[]{fwd[0] + right[0] * sx + up[0] * sy, fwd[1] + right[1] * sx + up[1] * sy, fwd[2] + right[2] * sx + up[2] * sy});
                int k = j * w + i;
                dirs[k] = d;
                depth[k] = hit(full, cam, d, nx, ny, nz, k);
                coreSil[k] = hit(core, cam, d, null, null, null, k) >= 0;
            }
        });
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) {
            int k = j * w + i;
            img.setRGB(i, j, coreSil[k] ? 0x9aa6b8 : 0x10151d);
            img.setRGB(w + 8 + i, j, depth[k] >= 0 ? 0x9aa6b8 : 0x10151d);
            int rgb = 0x10151d;
            if (depth[k] >= 0) {
                double ndv = Math.max(0, -(nx[k] * dirs[k][0] + ny[k] * dirs[k][1] + nz[k] * dirs[k][2]));
                int g = (int) (40 + 180 * ndv);
                rgb = g << 16 | g << 8 | g;
                for (int[] dd : new int[][]{{2, 0}, {0, 2}}) {
                    int ii = i + dd[0], jj = j + dd[1];
                    if (ii >= w || jj >= h) continue;
                    int q = jj * w + ii;
                    if (depth[q] < 0 || Math.abs(depth[q] - depth[k]) > .03f * depth[k]) continue;
                    if (nx[k] * nx[q] + ny[k] * ny[q] + nz[k] * nz[q] < Math.cos(Math.toRadians(25))) rgb = 0xff3030;
                }
            }
            img.setRGB(2 * w + 16 + i, j, rgb);
        }
        ImageIO.write(img, "png", file);
    }

    /** Sphere trace to the density-0.5 iso-surface; returns the hit distance or -1, normal into n*[k]. */
    static float hit(ConvectiveGrowthFieldPrototype.Program p, double[] cam, double[] d, float[] nx, float[] ny, float[] nz, int k) {
        double[] slab = slab(cam, d, p.bounds());
        if (slab == null) return -1;
        double t = slab[0];
        for (int step = 0; step < 4000 && t < slab[1]; step++) {
            float x = (float) (cam[0] + d[0] * t), y = (float) (cam[1] + d[1] * t), z = (float) (cam[2] + d[2] * t);
            float dist = p.referenceDistance(x, y, z);
            if (dist < .25f) {
                if (nx != null) {
                    float e = 3;
                    float gx = p.referenceDistance(x + e, y, z) - p.referenceDistance(x - e, y, z);
                    float gy = p.referenceDistance(x, y + e, z) - p.referenceDistance(x, y - e, z);
                    float gz = p.referenceDistance(x, y, z + e) - p.referenceDistance(x, y, z - e);
                    float gl = (float) Math.sqrt(gx * gx + gy * gy + gz * gz) + 1e-9f;
                    nx[k] = gx / gl; ny[k] = gy / gl; nz[k] = gz / gl;
                }
                return (float) t;
            }
            t += Math.max(dist / 6.5, .5);
        }
        return -1;
    }

    // ---------------------------------------------------------------- image helpers

    static boolean[] mask(BufferedImage im) {
        boolean[] m = new boolean[W * H];
        for (int j = 0; j < H; j++) for (int i = 0; i < W; i++) m[j * W + i] = ((im.getRGB(i, j) >>> 24) & 255) > 2;
        return m;
    }
    static boolean[] hitMask(float[] depth) { boolean[] m = new boolean[depth.length]; for (int k = 0; k < m.length; k++) m[k] = depth[k] >= 0; return m; }
    static boolean[] edge(boolean[] m) {
        boolean[] e = new boolean[W * H];
        for (int j = 1; j < H - 1; j++) for (int i = 1; i < W - 1; i++) {
            int k = j * W + i;
            e[k] = m[k] && (!m[k - 1] || !m[k + 1] || !m[k - W] || !m[k + W]);
        }
        return e;
    }
    /** Native frame, alpha over the dark ground (unaltered values), outline drawn on its edge. */
    static BufferedImage flat(BufferedImage im, boolean[] e, int colour) {
        BufferedImage o = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < H; j++) for (int i = 0; i < W; i++) {
            int c = im.getRGB(i, j), a = c >>> 24;
            int r = ((c >> 16 & 255) * a + 0x10 * (255 - a)) / 255, g = ((c >> 8 & 255) * a + 0x15 * (255 - a)) / 255, bl = ((c & 255) * a + 0x1d * (255 - a)) / 255;
            o.setRGB(i, j, e[j * W + i] ? colour : r << 16 | g << 8 | bl);
        }
        return o;
    }
    static int heat(double v) {
        v = Math.max(0, Math.min(1, v));
        int r = (int) (255 * Math.min(1, 2 * v)), g = (int) (255 * Math.max(0, 2 * v - 1)), bb = (int) (60 * (1 - v));
        return r << 16 | g << 8 | bb;
    }
    static int[] bbox(boolean[] m) {
        int x0 = W, y0 = H, x1 = -1, y1 = -1;
        for (int k = 0; k < m.length; k++) if (m[k]) { int i = k % W, j = k / W; x0 = Math.min(x0, i); x1 = Math.max(x1, i); y0 = Math.min(y0, j); y1 = Math.max(y1, j); }
        return new int[]{x0, y0, x1, y1};
    }
    static int rowWidth(boolean[] m, int y) { int lo = -1, hi = -1; for (int i = 0; i < W; i++) if (m[y * W + i]) { if (lo < 0) lo = i; hi = i; } return lo < 0 ? 0 : hi - lo + 1; }

    /** Exact Euclidean distance transform to the nearest pixel NOT in {inside ? m : !m} (Felzenszwalb). */
    static float[] edt(boolean[] m, boolean inside) {
        double INF = 1e12;
        double[] f = new double[W * H];
        for (int k = 0; k < f.length; k++) f[k] = (m[k] == inside) ? INF : 0;
        double[] tmp = new double[Math.max(W, H)], dd = new double[Math.max(W, H)];
        for (int i = 0; i < W; i++) { for (int j = 0; j < H; j++) tmp[j] = f[j * W + i]; dt1(tmp, H, dd); for (int j = 0; j < H; j++) f[j * W + i] = dd[j]; }
        for (int j = 0; j < H; j++) { System.arraycopy(f, j * W, tmp, 0, W); dt1(tmp, W, dd); System.arraycopy(dd, 0, f, j * W, W); }
        float[] r = new float[W * H];
        for (int k = 0; k < r.length; k++) r[k] = (float) Math.sqrt(f[k]);
        return r;
    }
    static void dt1(double[] f, int n, double[] d) {
        int[] v = new int[n]; double[] z = new double[n + 1]; int k = 0; v[0] = 0; z[0] = -1e30; z[1] = 1e30;
        for (int q = 1; q < n; q++) {
            double s;
            while (true) { s = ((f[q] + q * (double) q) - (f[v[k]] + v[k] * (double) v[k])) / (2.0 * q - 2.0 * v[k]); if (s <= z[k] && k > 0) k--; else break; }
            if (s <= z[k]) { v[0] = q; z[0] = -1e30; z[1] = 1e30; k = 0; continue; }
            k++; v[k] = q; z[k] = s; z[k + 1] = 1e30;
        }
        k = 0;
        for (int q = 0; q < n; q++) { while (z[k + 1] < q) k++; d[q] = sq(q - v[k]) + f[v[k]]; }
    }
    /** {mean, p95, max} |distance| of the boundary of m to the reference boundary (signed EDT pair). */
    static double[] displacement(boolean[] m, float[] refIn, float[] refOut) {
        boolean[] e = edge(m);
        List<Float> d = new ArrayList<>();
        for (int k = 0; k < e.length; k++) if (e[k]) d.add(Math.max(refIn[k], refOut[k]));
        if (d.isEmpty()) return new double[]{0, 0, 0};
        Collections.sort(d);
        double mean = 0; for (float x : d) mean += x;
        return new double[]{mean / d.size(), d.get((int) (d.size() * .95)), d.get(d.size() - 1)};
    }
    /** Outer boundary of the largest 8-connected component, Moore-neighbour traced: x,y pairs. */
    static int[] largestContour(boolean[] m) {
        int[] label = new int[W * H];
        int best = 0, bestSize = 0, next = 0;
        int[] queue = new int[W * H];
        for (int k = 0; k < m.length; k++) {
            if (!m[k] || label[k] != 0) continue;
            next++; int head = 0, tail = 0; queue[tail++] = k; label[k] = next; int size = 0;
            while (head < tail) {
                int c = queue[head++]; size++; int ci = c % W, cj = c / W;
                for (int dj = -1; dj <= 1; dj++) for (int di = -1; di <= 1; di++) {
                    int a = ci + di, bj = cj + dj;
                    if (a < 0 || bj < 0 || a >= W || bj >= H) continue;
                    int q = bj * W + a;
                    if (m[q] && label[q] == 0) { label[q] = next; queue[tail++] = q; }
                }
            }
            if (size > bestSize) { bestSize = size; best = next; }
        }
        if (best == 0) return new int[0];
        int start = -1;
        for (int k = 0; k < m.length; k++) if (label[k] == best) { start = k; break; }
        int[] dx = {1, 1, 0, -1, -1, -1, 0, 1}, dy = {0, 1, 1, 1, 0, -1, -1, -1};
        List<Integer> pts = new ArrayList<>();
        int cx = start % W, cy = start / W, dir = 7;
        int sx = cx, sy = cy;
        for (int guard = 0; guard < 4 * W * H; guard++) {
            pts.add(cx); pts.add(cy);
            int found = -1;
            for (int t = 0; t < 8; t++) {
                int dd = (dir + 6 + t) % 8; // start left of the previous direction
                int a = cx + dx[dd], bj = cy + dy[dd];
                if (a >= 0 && bj >= 0 && a < W && bj < H && label[bj * W + a] == best) { found = dd; break; }
            }
            if (found < 0) break;
            cx += dx[found]; cy += dy[found]; dir = found;
            if (cx == sx && cy == sy && pts.size() > 4) break;
        }
        return pts.stream().mapToInt(Integer::intValue).toArray();
    }
    /** Signed distance of each contour pixel to the core silhouette (positive outside), Gaussian-smoothed. */
    static float[] signal(int[] contour, float[] in, float[] out) {
        int n = contour.length / 2;
        float[] s = new float[n];
        for (int i = 0; i < n; i++) { int k = contour[2 * i + 1] * W + contour[2 * i]; s[i] = out[k] > 0 ? out[k] : -in[k]; }
        return smooth(s, 1.5);
    }
    static float[] smooth(float[] s, double sigma) {
        int n = s.length, r = (int) Math.ceil(3 * sigma);
        float[] o = new float[n];
        if (n == 0) return o;
        for (int i = 0; i < n; i++) { double a = 0, w = 0; for (int d = -r; d <= r; d++) { double g = Math.exp(-d * d / (2 * sigma * sigma)); a += g * s[Math.floorMod(i + d, n)]; w += g; } o[i] = (float) (a / w); }
        return o;
    }
    static double prominence(float[] s, int i) {
        int n = s.length; float lmin = s[i], rmin = s[i];
        int k = 1;
        for (; k < n; k++) { float v = s[Math.floorMod(i - k, n)]; if (v > s[i]) break; lmin = Math.min(lmin, v); }
        boolean leftHigher = k < n;
        int k2 = 1;
        for (; k2 < n; k2++) { float v = s[(i + k2) % n]; if (v > s[i]) break; rmin = Math.min(rmin, v); }
        if (!leftHigher && k2 >= n) { float mn = s[i]; for (float v : s) mn = Math.min(mn, v); return s[i] - mn; }
        return s[i] - Math.max(lmin, rmin);
    }
    static List<Integer> peaks(float[] s, double minProm, int minSep) {
        int n = s.length;
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!(s[i] > s[Math.floorMod(i - 1, n)] && s[i] >= s[(i + 1) % n])) continue;
            if (prominence(s, i) < minProm) continue;
            if (!out.isEmpty() && i - out.get(out.size() - 1) < minSep) continue;
            out.add(i);
        }
        return out;
    }
    /** Upper-envelope roughness in the top 30 % of the silhouette: residual RMS vs a 0.08W Gaussian, / width. */
    static double crown(boolean[] m, int[] box) {
        int w = box[2] - box[0] + 1, h = box[3] - box[1] + 1;
        List<Float> top = new ArrayList<>();
        for (int i = box[0]; i <= box[2]; i++) { int y = -1; for (int j = 0; j < H; j++) if (m[j * W + i]) { y = j; break; } top.add(y < 0 ? Float.NaN : (float) y); }
        return envelopeResidual(top, box[1] + .30 * h, .08 * w) / w;
    }
    static double envelopeResidual(List<Float> prof, double limit, double sigma) {
        int n = prof.size(); double a = 0; int c = 0; int r = (int) Math.ceil(3 * sigma);
        for (int i = 0; i < n; i++) {
            float v = prof.get(i);
            if (Float.isNaN(v) || v > limit) continue;
            double s = 0, w = 0;
            for (int d = -r; d <= r; d++) { int q = i + d; if (q < 0 || q >= n || Float.isNaN(prof.get(q))) continue; double g = Math.exp(-d * d / (2 * sigma * sigma)); s += g * prof.get(q); w += g; }
            a += sq(v - s / w); c++;
        }
        return c == 0 ? 0 : Math.sqrt(a / c);
    }
    /** Left/right edge roughness with height: residual RMS vs a 0.08H Gaussian along y, / width. */
    static double heightVariation(boolean[] m, int[] box) {
        int w = box[2] - box[0] + 1, h = box[3] - box[1] + 1;
        List<Float> left = new ArrayList<>(), right = new ArrayList<>();
        for (int j = box[1]; j <= box[3]; j++) { int lo = -1, hi = -1; for (int i = 0; i < W; i++) if (m[j * W + i]) { if (lo < 0) lo = i; hi = i; } left.add(lo < 0 ? Float.NaN : lo); right.add(hi < 0 ? Float.NaN : (float) -hi); }
        return .5 * (envelopeResidual(left, 1e9, .08 * h) + envelopeResidual(right, 1e9, .08 * h)) / w;
    }
    /** Plan-view outline: polar radius about the centroid, residual RMS vs a 20 degree Gaussian, / mean radius. */
    static double polarEdge(boolean[] m) {
        double cx = 0, cy = 0; int n = 0;
        for (int k = 0; k < m.length; k++) if (m[k]) { cx += k % W; cy += k / W; n++; }
        if (n == 0) return 0;
        cx /= n; cy /= n;
        float[] r = new float[720];
        for (int a = 0; a < 720; a++) {
            double th = Math.PI * a / 360, dx = Math.cos(th), dy = Math.sin(th);
            for (double t = 0; t < 2000; t += .5) { int i = (int) (cx + dx * t), j = (int) (cy + dy * t); if (i < 0 || j < 0 || i >= W || j >= H) break; if (m[j * W + i]) r[a] = (float) t; }
        }
        float[] s = smooth(r, 40);
        double res = 0, mean = 0;
        for (int a = 0; a < 720; a++) { res += sq(r[a] - s[a]); mean += r[a]; }
        return Math.sqrt(res / 720) / (mean / 720);
    }
    /** Side-view anvil (downwind 40-90 % of the silhouette span): {thickness/length, top RMS from line / length, bottom RMS / length, thickness CV}. */
    static double[] anvilEdges(boolean[] m, int[] box) {
        int w = box[2] - box[0] + 1;
        int lo = box[0] + (int) (.40 * w), hi = box[0] + (int) (.90 * w);
        // Downwind is the side the silhouette extends further from the column; pick the wider-reaching half.
        int[] colH = new int[W];
        for (int i = box[0]; i <= box[2]; i++) for (int j = 0; j < H; j++) if (m[j * W + i]) colH[i]++;
        double leftMass = 0, rightMass = 0;
        for (int i = box[0]; i <= box[2]; i++) { if (i < box[0] + w / 2) leftMass += colH[i]; else rightMass += colH[i]; }
        if (leftMass < rightMass) { lo = box[0] + (int) (.10 * w); hi = box[0] + (int) (.60 * w); }
        List<double[]> top = new ArrayList<>(), bot = new ArrayList<>();
        double th = 0, th2 = 0; int c = 0;
        for (int i = lo; i <= hi; i++) {
            int t = -1, bb = -1;
            for (int j = 0; j < H; j++) if (m[j * W + i]) { t = j; break; }
            if (t < 0) continue;
            for (int j = t; j < H && m[j * W + i]; j++) bb = j; // first contiguous run below the top: the anvil sheet
            top.add(new double[]{i, t}); bot.add(new double[]{i, bb});
            th += bb - t + 1; th2 += sq(bb - t + 1); c++;
        }
        if (c < 4) return new double[]{0, 0, 0, 0};
        double mean = th / c, sd = Math.sqrt(Math.max(0, th2 / c - mean * mean)), len = hi - lo + 1;
        return new double[]{mean / len, lineResidual(top) / len, lineResidual(bot) / len, sd / mean};
    }
    static double lineResidual(List<double[]> p) {
        double sx = 0, sy = 0, sxx = 0, sxy = 0; int n = p.size();
        for (double[] q : p) { sx += q[0]; sy += q[1]; sxx += q[0] * q[0]; sxy += q[0] * q[1]; }
        double b = (n * sxy - sx * sy) / Math.max(1e-9, n * sxx - sx * sx), a = (sy - b * sx) / n, r = 0;
        for (double[] q : p) r += sq(q[1] - (a + b * q[0]));
        return Math.sqrt(r / n);
    }

    static String arr(double[] v) { StringBuilder s = new StringBuilder("["); for (int i = 0; i < v.length; i++) s.append(i == 0 ? "" : ",").append(String.format(Locale.ROOT, "%.4f", v[i])); return s.append(']').toString(); }
    static double sq(double v) { return v * v; }
    static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    static double[] norm(double[] a) { double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); return new double[]{a[0] / l, a[1] / l, a[2] / l}; }
    static double[] slab(double[] o, double[] d, StormMorphologyPrototype.Bounds b) {
        double t0 = 0, t1 = 1e9;
        double[] lo = {b.minX(), b.minY(), b.minZ()}, hi = {b.maxX(), b.maxY(), b.maxZ()};
        for (int a = 0; a < 3; a++) {
            if (Math.abs(d[a]) < 1e-12) { if (o[a] < lo[a] || o[a] > hi[a]) return null; continue; }
            double ta = (lo[a] - o[a]) / d[a], tb = (hi[a] - o[a]) / d[a];
            t0 = Math.max(t0, Math.min(ta, tb)); t1 = Math.min(t1, Math.max(ta, tb));
        }
        return t0 < t1 ? new double[]{t0, t1} : null;
    }
}
