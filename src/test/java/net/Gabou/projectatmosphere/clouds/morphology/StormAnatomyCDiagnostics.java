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
 * T258 diagnostics: B v3 vs candidate C0/C1/C2/C3 from the NATIVE frames of one session (identical
 * cameras, effective lens), plus resolution-free CPU geometry metrics. Args: captureFolder log outDir.
 *
 * <p>Silhouette metrics are model independent (the same code reads B v3 and C):
 * <ul>
 * <li>displacement: mean boundary distance between two silhouettes (exact EDT), px and / width;</li>
 * <li>valleys: a morphological closing with a disk of 0.10 x silhouette height fills every notch
 * narrower than the disk; each connected filled region is a valley, its depth the largest distance
 * from the closed outline. Valleys with sky directly above (open upward or sideways) are turret
 * notches; valleys under cloud are undersides (anvil, overhang). Outline lobes = turret notches + 1;</li>
 * <li>convexity deficit 1 - area / hull area, plan-view polar edge residual, side-view anvil thickness
 * profile, connected components of the projected silhouette.</li>
 * </ul>
 * B v3's core/full displacement uses CPU silhouettes of its CORE and FULL fields at the same native
 * camera (the native B v3 frame validates the CPU lens by IoU), because the native B v3 core was not
 * re-captured in T258.
 */
public final class StormAnatomyCDiagnostics {
    static final String[] TYPES = {"cumulonimbus_calvus", "cumulonimbus_capillatus", "supercell"};
    static final long[] SEEDS = {20261008L, 20268927L, 20276846L};
    static final List<String> VIEWS = List.of("side", "opposite_side", "front", "back", "above", "below", "low_angle", "far", "close", "upwind",
            "downwind", "crosswind_left", "crosswind_right", "tower", "transition", "canopy_anvil", "updraft_oblique", "portrait", "portrait_front");
    static final List<String> ZOOM = List.of("side", "portrait_front", "above", "transition");
    static final Set<String> SIDE = Set.of("side", "opposite_side", "close", "far", "crosswind_left", "crosswind_right", "portrait");
    static final Set<String> PLAN = Set.of("above", "below");
    static final int W = 1200, H = 675;
    static final String[] ARMS = {"b3_", "c0_", "c1_", "c2_", ""};

    record Pose(double[] origin, double[] camera, double[] aim) {}

    interface Field { float d(float x, float y, float z); }

    static CloudShapeProfile shape(String type) { return CloudShapeProfile.defaultFor(type, CloudFamily.CUMULONIMBUS, null); }

    public static void main(String[] args) throws Exception {
        Path folder = Path.of(args[0]), log = Path.of(args[1]), out = Path.of(args[2]);
        Files.createDirectories(out);
        Map<String, Pose> poses = new HashMap<>();
        Map<String, Double> fovs = new HashMap<>();
        Pattern pose = Pattern.compile("T258_POSE shot=(\\S+) .*origin=\\(([^)]+)\\).*camera=\\(([^)]+)\\) aim=\\(([^)]+)\\)");
        Pattern lens = Pattern.compile("T258_LENS shot=(\\S+) .*effectiveFov=(\\S+)");
        for (String line : Files.readAllLines(log)) {
            Matcher m = pose.matcher(line);
            if (m.find()) poses.put(m.group(1), new Pose(vec(m.group(2)), vec(m.group(3)), vec(m.group(4))));
            m = lens.matcher(line);
            if (m.find()) fovs.put(m.group(1), Double.parseDouble(m.group(2)));
        }
        StringBuilder metrics = new StringBuilder();
        String only = System.getenv("T258_DIAG_ONLY");
        boolean skipGeometry = "1".equals(System.getenv("T258_DIAG_NO_GEOMETRY"));
        for (String type : TYPES) for (long seed : SEEDS) {
            if (only != null && !(type + "__" + seed).contains(only) && !only.contains("__")) continue;
            var b = ConvectiveGrowthFieldPrototype.compile(type, shape(type), seed);
            var bCore = ConvectiveGrowthFieldPrototype.compile(type, shape(type), seed, ConvectiveGrowthFieldPrototype.Detail.CORE);
            var c3 = StormAnatomyCandidateC.compile(type, shape(type), seed);
            if (!skipGeometry) {
                String g = geometry(type, seed, b, c3);
                metrics.append(g).append('\n');
                System.out.println(g);
            }
            String base = "T258_" + type + "__" + seed + "__";
            for (String view : VIEWS) {
                if (only != null && !(base + view).contains(only)) continue;
                Pose p = poses.get(base + view);
                File[] f = new File[ARMS.length];
                boolean ok = p != null;
                for (int a = 0; a < ARMS.length && ok; a++) {
                    f[a] = folder.resolve(base + ARMS[a] + view + "__raw.png").toFile();
                    Pose q = poses.get(base + ARMS[a] + view);
                    ok = f[a].isFile() && q != null && Arrays.equals(q.camera, p.camera) && Arrays.equals(q.aim, p.aim) && Arrays.equals(q.origin, p.origin);
                    if (q != null && !(Arrays.equals(q.camera, p.camera) && Arrays.equals(q.aim, p.aim))) throw new IllegalStateException("Cameras differ: " + base + ARMS[a] + view);
                }
                if (!ok) { System.out.println("T258_DIAG_MISSING " + base + view); continue; }
                String line = view(type, seed, view, p, fovs.get(base + view), f, b, bCore, c3, out);
                metrics.append(line).append('\n');
                System.out.println(line.length() > 300 ? line.substring(0, 300) + "..." : line);
            }
            for (String view : ZOOM) {
                File fb = folder.resolve(base + "b3_z4_" + view + "__raw.png").toFile(), fc = folder.resolve(base + "z4_" + view + "__raw.png").toFile();
                if (!fb.isFile() || !fc.isFile()) { System.out.println("T258_DIAG_MISSING " + base + "z4_" + view); continue; }
                zoomPair(ImageIO.read(fb), ImageIO.read(fc), out.resolve("zoom4_" + type + "__" + seed + "__" + view + ".png").toFile(),
                        fovs.getOrDefault(base + "z4_" + view, 0.0));
            }
        }
        Files.writeString(out.resolve("t258-diagnostic-metrics.jsonl"), metrics.toString());
    }

    // ------------------------------------------------------------------------------------ geometry

    static String geometry(String type, long seed, ConvectiveGrowthFieldPrototype.Program b, StormAnatomyCandidateC.Program c) {
        var els = c.elements();
        // Major groups: their summit heights, and the overlap of each group's outer surface with other groups.
        Map<Integer, List<Integer>> groups = new TreeMap<>();
        for (int i = 0; i < els.size(); i++) if (els.get(i).major()) groups.computeIfAbsent(els.get(i).group(), k -> new ArrayList<>()).add(i);
        var rng = new SplittableRandom(seed);
        double overlapSum = 0;
        int overlapGroups = 0;
        List<Double> tops = new ArrayList<>();
        for (var entry : groups.entrySet()) {
            int own = 0, buried = 0;
            float top = -1e9f;
            for (int idx : entry.getValue()) {
                var e = els.get(idx);
                if (e.shape() == StormAnatomyCandidateC.Shape.ELLIPSOID) top = Math.max(top, e.p()[1] + e.p()[4]);
                for (int n = 0; n < 160; n++) {
                    float[] s = surfacePoint(e, rng);
                    // On this group's own outer surface (not inside its other members)?
                    boolean inOwn = false;
                    for (int j : entry.getValue()) if (j != idx && StormAnatomyCandidateC.primitive(els.get(j), s[0], s[1], s[2]) < 0) { inOwn = true; break; }
                    if (inOwn) continue;
                    own++;
                    for (int j = 0; j < els.size(); j++) {
                        var o = els.get(j);
                        if (o.canopy() || o.group() == entry.getKey() || o.level().ordinal() > StormAnatomyCandidateC.Level.C1.ordinal() || entry.getValue().contains(j)) continue;
                        if (StormAnatomyCandidateC.primitive(o, s[0], s[1], s[2]) < 0) { buried++; break; }
                    }
                }
            }
            if (own > 0) { overlapSum += buried / (double) own; overlapGroups++; }
            tops.add((double) (top - c.baseY()) / c.height());
        }
        Collections.sort(tops);
        // Upper-body visibility: body surface points above mid-height not inside the canopy.
        double[] visC = upperVisibility((x, y, z) -> c.exactBodyDistance(x, y, z, -1), c::exactCanopyDistance, c.bounds(), c.baseY(), c.height(), c.feather(), seed);
        double[] visB = upperVisibility(b::bodyDistance, b::canopyDistance, b.bounds(), b.baseY(), b.height(), b.feather(), seed);
        // Canopy hierarchy: plan area / hull of the core (root + primary) vs full planform.
        String canopy = "null";
        if (c.hasCanopy()) {
            double[] core = planStats(c, StormAnatomyCandidateC.CANOPY_CORE_ROLES), full = planStats(c, null);
            canopy = String.format(Locale.ROOT, "{\"core_area\":%.0f,\"full_area\":%.0f,\"core_convexity_deficit\":%.4f,\"full_convexity_deficit\":%.4f,\"core_polar_residual\":%.4f,\"full_polar_residual\":%.4f}",
                    core[0], full[0], core[1], full[1], core[2], full[2]);
        }
        Map<String, Integer> roles = new TreeMap<>();
        for (var e : els) roles.merge(e.role().name(), 1, Integer::sum);
        int[] comps = new int[4];
        for (var l : StormAnatomyCandidateC.Level.values()) comps[l.ordinal()] = StormAnatomyCSandbox.components(
                l == StormAnatomyCandidateC.Level.C3 ? c : StormAnatomyCandidateC.compile(type, shape(type), seed, l))[0];
        return String.format(Locale.ROOT, "{\"kind\":\"geometry\",\"type\":\"%s\",\"seed\":%d,\"H\":%.1f,\"voxel\":%.2f,\"dims\":%s,\"texture\":\"%dx%d\",\"feather\":%.2f,"
                        + "\"major_groups\":%d,\"major_top_heights\":%s,\"major_height_spread\":%.3f,\"major_surface_buried_in_other_majors\":%.4f,"
                        + "\"upper_body_visible_C\":%.4f,\"upper_body_visible_B\":%.4f,\"components_C0_C3\":%s,\"roles\":%s,\"canopy\":%s}",
                type, seed, c.height(), c.voxel(), Arrays.toString(c.dims()), c.textureWidth(), c.textureHeight(), c.feather(), groups.size(), tops,
                tops.get(tops.size() - 1) - tops.get(0), overlapGroups == 0 ? 0 : overlapSum / overlapGroups, visC[0], visB[0], Arrays.toString(comps),
                json(roles), canopy);
    }

    static float[] surfacePoint(StormAnatomyCandidateC.Element e, SplittableRandom r) {
        float[] p = e.p();
        double u = r.nextDouble(-1, 1), th = r.nextDouble(0, 2 * Math.PI), s = Math.sqrt(1 - u * u);
        if (e.shape() == StormAnatomyCandidateC.Shape.ELLIPSOID) {
            double lx = s * Math.cos(th) * p[3], ly = u * p[4], lz = s * Math.sin(th) * p[5];
            double c = Math.cos(p[6]), sn = Math.sin(p[6]);
            // Inverse of the primitive's yaw (u = qx c - qz s, w = qx s + qz c).
            return new float[]{(float) (p[0] + lx * c + lz * sn), (float) (p[1] + ly), (float) (p[2] - lx * sn + lz * c)};
        }
        double t = r.nextDouble();
        double rr = p[3] + (p[7] - p[3]) * t;
        return new float[]{(float) (p[0] + (p[4] - p[0]) * t + s * Math.cos(th) * rr), (float) (p[1] + (p[5] - p[1]) * t + u * rr), (float) (p[2] + (p[6] - p[2]) * t + s * Math.sin(th) * rr)};
    }

    /** {visible fraction}: body iso-surface points with y above mid-height that lie outside the canopy. */
    static double[] upperVisibility(Field body, Field canopy, StormMorphologyPrototype.Bounds bb, float baseY, float h, float feather, long seed) {
        var r = new SplittableRandom(seed ^ 3);
        int n = 0, vis = 0;
        for (int i = 0; i < 4_000_000 && n < 3000; i++) {
            float x = (float) r.nextDouble(bb.minX(), bb.maxX()), y = (float) r.nextDouble(baseY + .5f * h, bb.maxY()), z = (float) r.nextDouble(bb.minZ(), bb.maxZ());
            float d = body.d(x, y, z);
            if (Math.abs(d) > feather * .25f) continue;
            n++;
            if (canopy.d(x, y, z) > 0) vis++;
        }
        return new double[]{n == 0 ? 1 : vis / (double) n, n};
    }

    /** {area, convexity deficit, polar residual} of the CPU canopy planform for a role subset. */
    static double[] planStats(StormAnatomyCandidateC.Program c, Set<StormAnatomyCandidateC.Role> roles) {
        var b = c.bounds();
        int n = 400;
        float sx = (b.maxX() - b.minX()) / n, sz = (b.maxZ() - b.minZ()) / n;
        boolean[] m = new boolean[n * n];
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) m[j * n + i] = c.canopyPlanform(b.minX() + (i + .5f) * sx, b.minZ() + (j + .5f) * sz, roles) < 0;
        double area = 0;
        for (boolean v : m) if (v) area++;
        return new double[]{area * sx * sz, convexityDeficit(m, n, n), polarResidual(m, n, n)};
    }

    // ------------------------------------------------------------------------------------ per view

    static String view(String type, long seed, String view, Pose p, Double effFov, File[] f, ConvectiveGrowthFieldPrototype.Program b,
                       ConvectiveGrowthFieldPrototype.Program bCore, StormAnatomyCandidateC.Program c3, Path out) throws Exception {
        double fov = effFov == null ? 121 : effFov;
        BufferedImage[] im = new BufferedImage[f.length];
        boolean[][] m = new boolean[f.length][];
        for (int a = 0; a < f.length; a++) { im[a] = ImageIO.read(f[a]); m[a] = mask(im[a]); }
        double[] cam = sub(p.camera, p.origin), aim = sub(p.aim, p.origin);
        boolean[] cpuB = cpuMask(b::referenceDistance, b.bounds(), b.feather(), cam, aim, fov);
        boolean[] cpuBCore = cpuMask(bCore::referenceDistance, bCore.bounds(), bCore.feather(), cam, aim, fov);
        boolean[] cpuC = cpuMask(c3::sampledDistance, c3.bounds(), c3.feather(), cam, aim, fov);
        double iouB = iou(cpuB, m[0]), iouC = iou(cpuC, m[4]);
        int[] box = bbox(m[4]), boxB = bbox(m[0]);
        double wC = box[2] - box[0] + 1, hC = box[3] - box[1] + 1, wB = boxB[2] - boxB[0] + 1, hB = boxB[3] - boxB[1] + 1;
        // Layer displacement against the C0 core (and successive layers).
        float[][] in = new float[5][], outD = new float[5][];
        for (int a = 0; a < 5; a++) { in[a] = edt(m[a], true); outD[a] = edt(m[a], false); }
        double[] d01 = displacement(m[2], in[1], outD[1]), d12 = displacement(m[3], in[2], outD[2]), d23 = displacement(m[4], in[3], outD[3]), d03 = displacement(m[4], in[1], outD[1]);
        float[] bIn = edt(cpuBCore, true), bOut = edt(cpuBCore, false);
        double[] dB = displacement(cpuB, bIn, bOut);
        int[] boxBc = bbox(cpuB);
        double wBc = Math.max(1, boxBc[2] - boxBc[0] + 1);
        // Valleys and outline structure.
        Valleys vB = valleys(m[0], hB), vC = valleys(m[4], hC), vC1 = valleys(m[2], hC), vC0 = valleys(m[1], hC);
        double cdB = convexityDeficit(m[0], W, H), cdC = convexityDeficit(m[4], W, H);
        int[] ccB = components2d(m[0]), ccC = components2d(m[4]);
        String plan = "";
        if (PLAN.contains(view)) plan = String.format(Locale.ROOT, ",\"polar_residual_B\":%.4f,\"polar_residual_C\":%.4f", polarResidual(m[0], W, H), polarResidual(m[4], W, H));
        String anvil = "";
        if (SIDE.contains(view) && !type.contains("calvus")) {
            double[] aB = anvilEdges(m[0], boxB), aC = anvilEdges(m[4], box);
            anvil = String.format(Locale.ROOT, ",\"anvil_B\":%s,\"anvil_C\":%s", arr(aB), arr(aC));
        }
        composite(type, seed, view, im, m, vC, vB, out);
        return String.format(Locale.ROOT, "{\"kind\":\"view\",\"type\":\"%s\",\"seed\":%d,\"view\":\"%s\",\"fov\":%.2f,\"size_B\":[%.0f,%.0f],\"size_C\":[%.0f,%.0f],"
                        + "\"iou_cpu_native_B\":%.4f,\"iou_cpu_native_C\":%.4f,"
                        + "\"disp_C0_C1\":%s,\"disp_C1_C2\":%s,\"disp_C2_C3\":%s,\"disp_C0_C3\":%s,\"disp_C0_C3_rel\":%.4f,\"disp_C0_C1_rel\":%.4f,\"disp_Bcore_Bfull_cpu\":%s,\"disp_B_rel\":%.4f,"
                        + "\"valleys_B\":%s,\"valleys_C\":%s,\"valleys_C1\":%s,\"valleys_C0\":%s,\"convexity_deficit_B\":%.4f,\"convexity_deficit_C\":%.4f,"
                        + "\"components_B\":%s,\"components_C\":%s%s%s}",
                type, seed, view, fov, wB, hB, wC, hC, iouB, iouC, arr(d01), arr(d12), arr(d23), arr(d03), d03[0] / wC, d01[0] / wC, arr(dB), dB[0] / wBc,
                vB.json(), vC.json(), vC1.json(), vC0.json(), cdB, cdC, Arrays.toString(ccB), Arrays.toString(ccC), plan, anvil);
    }

    /** CPU silhouette (density > 0 anywhere along the native ray). */
    static boolean[] cpuMask(Field f, StormMorphologyPrototype.Bounds b, float feather, double[] cam, double[] aim, double fov) {
        double[] fwd = norm(sub(aim, cam));
        double[] right = norm(cross(fwd, new double[]{0, 1, 0})), up = cross(right, fwd);
        double ty = Math.tan(Math.toRadians(fov) / 2), tx = ty * W / H;
        boolean[] m = new boolean[W * H];
        IntStream.range(0, H).parallel().forEach(j -> {
            for (int i = 0; i < W; i++) {
                double sx = (2 * (i + .5) / W - 1) * tx, sy = (1 - 2 * (j + .5) / H) * ty;
                double[] d = norm(new double[]{fwd[0] + right[0] * sx + up[0] * sy, fwd[1] + right[1] * sx + up[1] * sy, fwd[2] + right[2] * sx + up[2] * sy});
                double[] sl = slab(cam, d, b);
                if (sl == null) continue;
                double t = sl[0];
                for (int step = 0; step < 6000 && t < sl[1]; step++) {
                    float dist = f.d((float) (cam[0] + d[0] * t), (float) (cam[1] + d[1] * t), (float) (cam[2] + d[2] * t));
                    // density > 2/255 * (1/extinction-ish): the native alpha threshold ~ a few blocks of thin edge.
                    if (dist < feather * .45f) { m[j * W + i] = true; break; }
                    t += Math.max(dist - feather * .45f, 1.0) * .9;
                }
            }
        });
        return m;
    }

    record Valleys(int notches, int undersides, double[] depths, double deepest, double meanTop3, int lobes) {
        String json() {
            return String.format(Locale.ROOT, "{\"notches\":%d,\"undersides\":%d,\"lobes\":%d,\"deepest_rel\":%.4f,\"mean_top3_rel\":%.4f,\"depths_px\":%s}",
                    notches, undersides, lobes, deepest, meanTop3, arr(Arrays.copyOf(depths, Math.min(depths.length, 8))));
        }
    }

    /** Morphological-closing valleys (see class comment). Depths in px; relative values / silhouette height. */
    static Valleys valleys(boolean[] m, double h) {
        int R = (int) Math.max(4, Math.round(.10 * h));
        float[] distToMask = edt(m, false);
        boolean[] dil = new boolean[W * H];
        for (int k = 0; k < dil.length; k++) dil[k] = m[k] || distToMask[k] <= R;
        float[] distToOutside = edt(dil, true);
        boolean[] closed = new boolean[W * H];
        for (int k = 0; k < closed.length; k++) closed[k] = m[k] || distToOutside[k] > R;
        float[] depthField = edt(closed, true);
        int[] label = new int[W * H];
        int[] queue = new int[W * H];
        List<Double> notch = new ArrayList<>();
        int under = 0, next = 0;
        double minDepth = Math.max(3, .03 * h);
        for (int k = 0; k < m.length; k++) {
            if (!closed[k] || m[k] || label[k] != 0) continue;
            next++;
            int head = 0, tail = 0, covered = 0, size = 0;
            double depth = 0;
            queue[tail++] = k; label[k] = next;
            while (head < tail) {
                int c = queue[head++]; size++;
                depth = Math.max(depth, depthField[c]);
                int ci = c % W, cj = c / W;
                boolean above = false;
                for (int j = cj - 1; j >= 0; j--) if (m[j * W + ci]) { above = true; break; }
                if (above) covered++;
                int[] nb = {c - 1, c + 1, c - W, c + W};
                for (int q : nb) {
                    if (q < 0 || q >= m.length || label[q] != 0 || !closed[q] || m[q]) continue;
                    if (Math.abs(q % W - ci) > 1) continue;
                    label[q] = next; queue[tail++] = q;
                }
            }
            if (depth < minDepth) continue;
            if (covered > size / 2) under++; else notch.add(depth);
        }
        notch.sort(Comparator.reverseOrder());
        double[] d = notch.stream().mapToDouble(Double::doubleValue).toArray();
        double top3 = 0;
        for (int i = 0; i < Math.min(3, d.length); i++) top3 += d[i];
        top3 = d.length == 0 ? 0 : top3 / Math.min(3, d.length);
        return new Valleys(d.length, under, d, d.length == 0 ? 0 : d[0] / h, top3 / h, d.length + 1);
    }

    // ------------------------------------------------------------------------------------ images

    static void composite(String type, long seed, String view, BufferedImage[] im, boolean[][] m, Valleys vC, Valleys vB, Path out) throws Exception {
        int[] box = bbox(m[0]);
        for (boolean[] mm : m) { int[] b = bbox(mm); box = new int[]{Math.min(box[0], b[0]), Math.min(box[1], b[1]), Math.max(box[2], b[2]), Math.max(box[3], b[3])}; }
        int pad = 14, cw = Math.min(W, box[2] - box[0] + 1 + 2 * pad), ch = Math.min(H, box[3] - box[1] + 1 + 2 * pad);
        int x0 = Math.max(0, Math.min(W - cw, box[0] - pad)), y0 = Math.max(0, Math.min(H - ch, box[1] - pad));
        int scale = Math.max(1, Math.min(3, 300 / Math.max(cw, ch) + 1));
        BufferedImage o = new BufferedImage((cw * scale + 4) * 5, ch * scale, BufferedImage.TYPE_INT_RGB);
        boolean[] e0 = edge(m[1]);
        for (int a = 0; a < 5; a++) {
            boolean[] e = edge(m[a]);
            for (int j = 0; j < ch * scale; j++) for (int i = 0; i < cw * scale; i++) {
                int x = x0 + i / scale, y = y0 + j / scale, k = y * W + x;
                int c = im[a].getRGB(x, y), al = c >>> 24;
                int r = ((c >> 16 & 255) * al + 0x10 * (255 - al)) / 255, g = ((c >> 8 & 255) * al + 0x15 * (255 - al)) / 255, bl = ((c & 255) * al + 0x1d * (255 - al)) / 255;
                int rgb = r << 16 | g << 8 | bl;
                if (a == 4 && e0[k]) rgb = 0x00c8ff; // C0 core outline over the complete C3
                if (e[k]) rgb = a == 0 ? 0xff7070 : 0xffffff;
                o.setRGB(a * (cw * scale + 4) + i, j, rgb);
            }
        }
        ImageIO.write(o, "png", out.resolve("cmp_" + type + "__" + seed + "__" + view + ".png").toFile());
    }

    static void zoomPair(BufferedImage b, BufferedImage c, File file, double fov) throws Exception {
        BufferedImage o = new BufferedImage(W * 2 + 8, H, BufferedImage.TYPE_INT_RGB);
        for (int s = 0; s < 2; s++) {
            BufferedImage im = s == 0 ? b : c;
            for (int j = 0; j < H; j++) for (int i = 0; i < W; i++) {
                int px = im.getRGB(i, j), al = px >>> 24;
                int r = ((px >> 16 & 255) * al + 0x10 * (255 - al)) / 255, g = ((px >> 8 & 255) * al + 0x15 * (255 - al)) / 255, bl = ((px & 255) * al + 0x1d * (255 - al)) / 255;
                o.setRGB(s * (W + 8) + i, j, r << 16 | g << 8 | bl);
            }
        }
        ImageIO.write(o, "png", file);
    }

    // ------------------------------------------------------------------------------------ helpers

    static boolean[] mask(BufferedImage im) {
        boolean[] m = new boolean[W * H];
        for (int j = 0; j < H; j++) for (int i = 0; i < W; i++) m[j * W + i] = ((im.getRGB(i, j) >>> 24) & 255) > 2;
        return m;
    }
    static double iou(boolean[] a, boolean[] b) {
        int i = 0, u = 0;
        for (int k = 0; k < a.length; k++) { if (a[k] && b[k]) i++; if (a[k] || b[k]) u++; }
        return u == 0 ? 1 : i / (double) u;
    }
    static boolean[] edge(boolean[] m) {
        boolean[] e = new boolean[W * H];
        for (int j = 1; j < H - 1; j++) for (int i = 1; i < W - 1; i++) {
            int k = j * W + i;
            e[k] = m[k] && (!m[k - 1] || !m[k + 1] || !m[k - W] || !m[k + W]);
        }
        return e;
    }
    static int[] bbox(boolean[] m) {
        int x0 = W, y0 = H, x1 = -1, y1 = -1;
        for (int k = 0; k < m.length; k++) if (m[k]) { int i = k % W, j = k / W; x0 = Math.min(x0, i); x1 = Math.max(x1, i); y0 = Math.min(y0, j); y1 = Math.max(y1, j); }
        if (x1 < 0) return new int[]{0, 0, 0, 0};
        return new int[]{x0, y0, x1, y1};
    }
    /** {components, detached pixels} of the 8-connected silhouette. */
    static int[] components2d(boolean[] m) {
        int[] label = new int[m.length], queue = new int[m.length];
        int next = 0, largest = 0, total = 0;
        for (int k = 0; k < m.length; k++) {
            if (!m[k] || label[k] != 0) continue;
            next++;
            int head = 0, tail = 0;
            queue[tail++] = k; label[k] = next;
            while (head < tail) {
                int c = queue[head++], ci = c % W, cj = c / W;
                for (int dj = -1; dj <= 1; dj++) for (int di = -1; di <= 1; di++) {
                    int a = ci + di, bj = cj + dj;
                    if (a < 0 || bj < 0 || a >= W || bj >= H) continue;
                    int q = bj * W + a;
                    if (m[q] && label[q] == 0) { label[q] = next; queue[tail++] = q; }
                }
            }
            largest = Math.max(largest, tail);
            total += tail;
        }
        return new int[]{next, total - largest};
    }
    static double convexityDeficit(boolean[] m, int w, int h) {
        List<int[]> pts = new ArrayList<>();
        double area = 0;
        for (int j = 0; j < h; j++) {
            int lo = -1, hi = -1;
            for (int i = 0; i < w; i++) if (m[j * w + i]) { area++; if (lo < 0) lo = i; hi = i; }
            if (lo >= 0) { pts.add(new int[]{lo, j}); pts.add(new int[]{hi + 1, j}); pts.add(new int[]{lo, j + 1}); pts.add(new int[]{hi + 1, j + 1}); }
        }
        if (pts.size() < 3) return 0;
        pts.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
        int[][] hull = new int[pts.size() * 2][];
        int k = 0;
        for (int[] p : pts) { while (k >= 2 && crossZ(hull[k - 2], hull[k - 1], p) <= 0) k--; hull[k++] = p; }
        for (int i = pts.size() - 2, t = k + 1; i >= 0; i--) { int[] p = pts.get(i); while (k >= t && crossZ(hull[k - 2], hull[k - 1], p) <= 0) k--; hull[k++] = p; }
        double ha = 0;
        for (int i = 0; i < k - 1; i++) ha += (double) hull[i][0] * hull[i + 1][1] - (double) hull[i + 1][0] * hull[i][1];
        ha = Math.abs(ha) / 2;
        return ha <= 0 ? 0 : Math.max(0, 1 - area / ha);
    }
    static long crossZ(int[] o, int[] a, int[] b) { return (long) (a[0] - o[0]) * (b[1] - o[1]) - (long) (a[1] - o[1]) * (b[0] - o[0]); }
    /** Polar outer radius about the centroid, RMS residual vs a 20 degree Gaussian, / mean radius. */
    static double polarResidual(boolean[] m, int w, int h) {
        double cx = 0, cy = 0; int n = 0;
        for (int k = 0; k < m.length; k++) if (m[k]) { cx += k % w; cy += k / w; n++; }
        if (n == 0) return 0;
        cx /= n; cy /= n;
        float[] r = new float[720];
        for (int a = 0; a < 720; a++) {
            double th = Math.PI * a / 360, dx = Math.cos(th), dy = Math.sin(th);
            for (double t = 0; t < 3000; t += .5) { int i = (int) (cx + dx * t), j = (int) (cy + dy * t); if (i < 0 || j < 0 || i >= w || j >= h) break; if (m[j * w + i]) r[a] = (float) t; }
        }
        float[] s = smooth(r, 40);
        double res = 0, mean = 0;
        for (int a = 0; a < 720; a++) { res += sq(r[a] - s[a]); mean += r[a]; }
        return Math.sqrt(res / 720) / (mean / 720);
    }
    static float[] smooth(float[] s, double sigma) {
        int n = s.length, r = (int) Math.ceil(3 * sigma);
        float[] o = new float[n];
        for (int i = 0; i < n; i++) { double a = 0, w = 0; for (int d = -r; d <= r; d++) { double g = Math.exp(-d * d / (2 * sigma * sigma)); a += g * s[Math.floorMod(i + d, n)]; w += g; } o[i] = (float) (a / w); }
        return o;
    }
    /** Side-view anvil (downwind 40-90 % of the span): {thickness/length, top RMS from line / length, bottom RMS / length, thickness CV}. */
    static double[] anvilEdges(boolean[] m, int[] box) {
        int w = box[2] - box[0] + 1;
        int lo = box[0] + (int) (.40 * w), hi = box[0] + (int) (.90 * w);
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
            for (int j = t; j < H && m[j * W + i]; j++) bb = j;
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
    /** {mean, p95, max} distance of the boundary of m to the reference boundary (signed EDT pair). */
    static double[] displacement(boolean[] m, float[] refIn, float[] refOut) {
        boolean[] e = edge(m);
        List<Float> d = new ArrayList<>();
        for (int k = 0; k < e.length; k++) if (e[k]) d.add(Math.max(refIn[k], refOut[k]));
        if (d.isEmpty()) return new double[]{0, 0, 0};
        Collections.sort(d);
        double mean = 0; for (float x : d) mean += x;
        return new double[]{mean / d.size(), d.get((int) (d.size() * .95)), d.get(d.size() - 1)};
    }
    static String json(Map<String, Integer> m) {
        StringBuilder s = new StringBuilder("{");
        for (var e : m.entrySet()) s.append(s.length() > 1 ? "," : "").append('"').append(e.getKey()).append("\":").append(e.getValue());
        return s.append('}').toString();
    }
    static String arr(double[] v) { StringBuilder s = new StringBuilder("["); for (int i = 0; i < v.length; i++) s.append(i == 0 ? "" : ",").append(String.format(Locale.ROOT, "%.4f", v[i])); return s.append(']').toString(); }
    static double[] vec(String s) { return Arrays.stream(s.split(",")).mapToDouble(Double::parseDouble).toArray(); }
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
