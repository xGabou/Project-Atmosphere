package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.client.render.volumetric.ConvectiveSystemCapturePlan;
import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260.Component;
import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260.Recipe;
import net.Gabou.projectatmosphere.clouds.morphology.StormMorphologyPrototype.Bounds;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * T260.4 artefact attribution (CPU). Renders one seeded system at the native camera plan through several integrators
 * so every image difference has exactly one cause:
 * <ul>
 *   <li>{@code exact}: replica of the fixture GPU march with interval skipping off (fine step 4, sample at the step
 *   start, one-fine-step blue-noise origin phase from the same 64x64 texture, extinction 0.055, cut at T 0.015);</li>
 *   <li>{@code interval}: the same march with the shader's interval skip (box test at the step midpoint);</li>
 *   <li>{@code fixed}: the interval skip with the box test at the sample point (candidate renderer fix);</li>
 *   <li>{@code ref}: converged march of the baked volume (step 0.25, no jitter): what the volume really contains;</li>
 *   <li>{@code field}: converged march of the analytic distance field (step 0.5): what the recipe really contains.</li>
 * </ul>
 * exact-vs-interval = traversal; exact-vs-ref = march sampling; ref-vs-field = sparse-brick sampling.
 * Args: outDir seed variant(C|W|B) framing(BC|BW) views(comma) [scale (1 = 1200x675)]
 */
public final class T2604Attribution {
    static final float EXT = .055f, FINE = 4f;
    static final int BLUE = 64;
    static final int[] BLUE_RANK = blueNoise(BLUE);

    public static void main(String[] args) throws Exception {
        if (args[0].equals("anatomy")) { anatomy(args); return; }
        Path out = Path.of(args[0]);
        long seed = Long.parseLong(args[1]);
        String variant = args[2], framing = args[3];
        String[] views = args[4].split(",");
        double scale = args.length > 5 ? Double.parseDouble(args[5]) : 1;
        String modes = args.length > 6 ? args[6] : "exact,interval,lattice,fixed,ref,field";
        Files.createDirectories(out);
        Recipe r = ConvectiveSystemT260Gallery.variant(variant, seed);
        var program = new ConvectiveSystemT260.Program(r, null, 6, 12, .25f);
        var v = program.volume();
        float[] pts = ConvectiveSystemT260Gallery.unionExtremes(seed, framing);
        System.out.printf(Locale.ROOT, "T2604 seed=%d variant=%s signature=%s bounds=%s boxes=%d%n", seed, variant,
                Long.toHexString(v.signature()), v.bounds(), v.boxCount());
        for (int i = 0; i < v.boxCount(); i++) System.out.printf(Locale.ROOT, "  box %d %s%n", i, java.util.Arrays.toString(v.box(i)));
        for (int c = 0; c < r.components().size(); c++) {
            Component k = r.components().get(c);
            System.out.printf(Locale.ROOT, "  comp %2d %-22s role=%-10s group=%d blend=%.0f top=%.0f anchor=(%.0f,%.0f)%n",
                    c, k.name(), k.role(), k.group(), k.blend(), k.top(), k.anchorX(), k.anchorZ());
        }
        int W = (int) Math.round(1200 * scale), H = (int) Math.round(675 * scale);
        for (String view : views) {
            double fov = ConvectiveSystemCapturePlan.optionFov(view, 110) * 1.1;
            double[] pose = ConvectiveSystemCapturePlan.pose(view, program.bounds(), pts, fov, 1200 / 675.0);
            List<float[]> imgs = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (String m : modes.split(",")) {
                long t0 = System.nanoTime();
                float[] a = render(program, pose, W, H, fov, m);
                imgs.add(a);
                names.add(m);
                write(out.resolve(variant + "_" + view + "__" + m + ".png"), a, W, H);
                System.out.printf(Locale.ROOT, "  view=%s mode=%s ms=%.0f coverage=%.4f%n", view, m, (System.nanoTime() - t0) / 1e6, coverage(a));
            }
            for (int i = 0; i < imgs.size(); i++) for (int j = i + 1; j < imgs.size(); j++) {
                float[] a = imgs.get(i), b = imgs.get(j);
                float max = 0; double sum = 0; int over = 0;
                for (int k = 0; k < a.length; k++) { float d = Math.abs(a[k] - b[k]); max = Math.max(max, d); sum += d; if (d > .02f) over++; }
                System.out.printf(Locale.ROOT, "  view=%s diff %s-%s maxAbsAlpha=%.4f mean=%.5f pixels>0.02=%d%n", view, names.get(i), names.get(j), max, sum / a.length, over);
                writeDiff(out.resolve(variant + "_" + view + "__diff_" + names.get(i) + "_" + names.get(j) + ".png"), a, b, W, H);
            }
        }
    }

    // ------------------------------------------------------------------ lower-cloud anatomy metrics

    /**
     * Lower-mass tongue metrics. Planform (orthographic, 12-block columns): a column is "turret" if any inside point
     * (baked density > 0.5) is owned by an attached non-lower-mass component, "exposed lower mass" if it is inside
     * only where the lower mass owns it. Overhang = distance from an exposed column to the nearest turret column.
     * Views (native cameras, 600x338): shelf = fraction of attached-silhouette columns whose top is lower mass.
     * Args: anatomy outDir variant seeds(comma) [framing]
     */
    static void anatomy(String[] args) throws Exception {
        Path out = Path.of(args[1]);
        Files.createDirectories(out);
        String variant = args[2], framing = args.length > 4 ? args[4] : "BC";
        StringBuilder json = new StringBuilder("[\n");
        for (String ss : args[3].split(",")) {
            long seed = Long.parseLong(ss);
            Recipe r = ConvectiveSystemT260Gallery.variant(variant, seed);
            var program = new ConvectiveSystemT260.Program(r, null, 6, 12, .25f);
            var v = program.volume();
            var field = program.field();
            Bounds b = v.bounds();
            float hp = r.primaryHeight();
            int lm = -1;
            boolean[] attached = new boolean[r.components().size()];
            for (int c = 0; c < attached.length; c++) {
                Component k = r.components().get(c);
                attached[c] = k.group() == 0;
                if (k.role() == ConvectiveSystemT260.Role.LOWER_MASS) lm = c;
            }
            final int lower = lm;
            float cell = 12, dy = 12;
            int nx = (int) Math.ceil((b.maxX() - b.minX()) / cell), nz = (int) Math.ceil((b.maxZ() - b.minZ()) / cell);
            int[] state = new int[nx * nz]; // 0 none, 1 turret, 2 exposed lower mass, 3 detached
            IntStream.range(0, nx * nz).parallel().forEach(q -> {
                int i = q % nx, k = q / nx;
                float x = b.minX() + (i + .5f) * cell, z = b.minZ() + (k + .5f) * cell;
                boolean turret = false, lowerOwned = false, detached = false;
                for (float y = Math.max(b.minY(), 0) + dy * .5f; y < b.maxY(); y += dy) {
                    if (v.density(x, y, z) <= .5f) continue;
                    int o = field.owner(x, y, z);
                    if (o < 0) continue;
                    if (!attached[o]) detached = true;
                    else if (o == lower) lowerOwned = true;
                    else turret = true;
                }
                state[q] = turret ? 1 : lowerOwned ? 2 : detached ? 3 : 0;
            });
            List<int[]> turrets = new ArrayList<>();
            for (int q = 0; q < state.length; q++) if (state[q] == 1) turrets.add(new int[]{q % nx, q / nx});
            float[] over = new float[state.length];
            IntStream.range(0, state.length).parallel().forEach(q -> {
                if (state[q] != 2) return;
                int i = q % nx, k = q / nx;
                double best = 1e18;
                for (int[] t : turrets) best = Math.min(best, (double) (t[0] - i) * (t[0] - i) + (double) (t[1] - k) * (t[1] - k));
                over[q] = (float) Math.sqrt(best) * cell;
            });
            int turretN = 0, exposed = 0, tongue = 0;
            float maxOver = 0;
            for (int q = 0; q < state.length; q++) {
                if (state[q] == 1) turretN++;
                if (state[q] == 2) { exposed++; maxOver = Math.max(maxOver, over[q]); if (over[q] > .10f * hp) tongue++; }
            }
            // Plan image: turret grey, exposed lower mass blue (brighter = larger overhang), detached teal.
            BufferedImage plan = new BufferedImage(nx, nz, BufferedImage.TYPE_INT_RGB);
            for (int q = 0; q < state.length; q++) {
                int rgb;
                if (state[q] == 1) rgb = 0x8a8f99;
                else if (state[q] == 3) rgb = 0x2fb3a8;
                else if (state[q] == 2) { int t = (int) Math.min(255, 60 + 195 * over[q] / (.3f * hp)); rgb = (t / 3) << 16 | (t / 2) << 8 | t; }
                else rgb = 0x10151d;
                plan.setRGB(q % nx, q / nx, rgb);
            }
            ImageIO.write(plan, "png", out.resolve(variant + "_" + seed + "__plan.png").toFile());
            // Whole attached group at low heights: footprint convexity deficit and number of separate regions.
            StringBuilder lj = new StringBuilder();
            for (float frac : new float[]{.06f, .15f, .30f}) {
                float yy = frac * hp;
                boolean[] in = new boolean[nx * nz];
                List<double[]> hp2 = new ArrayList<>();
                int area = 0;
                for (int q = 0; q < in.length; q++) {
                    float x = b.minX() + (q % nx + .5f) * cell, z = b.minZ() + (q / nx + .5f) * cell;
                    if (v.density(x, yy, z) <= .5f) continue;
                    int o = field.owner(x, yy, z);
                    if (o < 0 || !attached[o]) continue;
                    in[q] = true; area++; hp2.add(new double[]{x, z});
                }
                int regions = 0;
                int[] lab = new int[in.length];
                for (int q = 0; q < in.length; q++) {
                    if (!in[q] || lab[q] != 0) continue;
                    regions++;
                    java.util.ArrayDeque<Integer> st = new java.util.ArrayDeque<>(); st.push(q); lab[q] = regions;
                    while (!st.isEmpty()) {
                        int u = st.pop(), ui = u % nx, uk = u / nx;
                        int[][] nb = {{ui + 1, uk}, {ui - 1, uk}, {ui, uk + 1}, {ui, uk - 1}};
                        for (int[] w : nb) {
                            if (w[0] < 0 || w[1] < 0 || w[0] >= nx || w[1] >= nz) continue;
                            int wq = w[1] * nx + w[0];
                            if (in[wq] && lab[wq] == 0) { lab[wq] = regions; st.push(wq); }
                        }
                    }
                }
                double hull = hp2.size() < 3 ? 1 : ConvectiveSystemT260Gallery.hullArea(hp2);
                if (lj.length() > 0) lj.append(',');
                lj.append(String.format(Locale.ROOT, "\"%.2fH\":{\"convexityDeficit\":%.3f,\"regions\":%d,\"areaKm2\":%.3f}", frac, 1 - area * cell * cell / hull, regions, area * cell * cell / 1e6));
            }
            float[] pts = ConvectiveSystemT260Gallery.unionExtremes(seed, framing);
            StringBuilder vj = new StringBuilder();
            double shelfSum = 0;
            int nv = 0;
            for (String view : ConvectiveSystemT260Gallery.VIEWS) {
                double fov = ConvectiveSystemCapturePlan.optionFov(view, 110) * 1.1;
                double[] pose = ConvectiveSystemCapturePlan.pose(view, b, pts, fov, 1200 / 675.0);
                int W = 600, H = 338;
                int[] own = owners(program, pose, W, H, fov);
                int cols = 0, shelf = 0, run = 0, longest = 0;
                for (int i = 0; i < W; i++) {
                    int top = -1;
                    for (int j = 0; j < H; j++) { int o = own[j * W + i]; if (o >= 0 && attached[o]) { top = o; break; } }
                    if (top < 0) { run = 0; continue; }
                    cols++;
                    if (top == lower) { shelf++; run++; longest = Math.max(longest, run); } else run = 0;
                }
                // Low end: longest run of silhouette columns at either end of the attached group lower than 25 % of the
                // tallest column (a low tapering tongue), as a fraction of the group's width.
                int[] colH = new int[W];
                int first = -1, last = -1, tallest = 0;
                for (int i = 0; i < W; i++) {
                    int top = -1, bottom = -1;
                    for (int j = 0; j < H; j++) { int o = own[j * W + i]; if (o >= 0 && attached[o]) { if (top < 0) top = j; bottom = j; } }
                    colH[i] = top < 0 ? 0 : bottom - top + 1;
                    if (top >= 0) { if (first < 0) first = i; last = i; tallest = Math.max(tallest, colH[i]); }
                }
                int lowL = 0, lowR = 0;
                if (first >= 0) {
                    while (first + lowL <= last && colH[first + lowL] < .25 * tallest) lowL++;
                    while (last - lowR >= first && colH[last - lowR] < .25 * tallest) lowR++;
                }
                double lowEnd = first < 0 ? 0 : Math.max(lowL, lowR) / (double) (last - first + 1);
                double sf = cols == 0 ? 0 : shelf / (double) cols, lr = cols == 0 ? 0 : longest / (double) cols;
                shelfSum += sf; nv++;
                if (vj.length() > 0) vj.append(',');
                vj.append(String.format(Locale.ROOT, "\"%s\":{\"shelf\":%.3f,\"longestShelfRun\":%.3f,\"lowEnd\":%.3f}", view, sf, lr, lowEnd));
                BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
                for (int q = 0; q < own.length; q++) {
                    int o = own[q];
                    int rgb = o < 0 ? 0x10151d : !attached[o] ? 0x2fb3a8 : o == lower ? 0x6f8fd8
                            : r.components().get(o).role() == ConvectiveSystemT260.Role.PRIMARY ? 0xf26b3a : 0xe0a63a;
                    im.setRGB(q % W, q / W, rgb);
                }
                ImageIO.write(im, "png", out.resolve(variant + "_" + seed + "__owners_" + view + ".png").toFile());
            }
            String line = String.format(Locale.ROOT, "{\"variant\":\"%s\",\"seed\":%d,\"signature\":\"%s\",\"bubbles\":%d,\"lowerMassBubbles\":%d,\"turretColumns\":%d,"
                    + "\"exposedLowerMassColumns\":%d,\"exposedFraction\":%.3f,\"tongueColumns\":%d,\"maxOverhangOverH\":%.3f,\"meanShelf\":%.3f,\"lowPlanform\":{%s},\"views\":{%s}}",
                    variant, seed, Long.toHexString(v.signature()), r.bubbles().size(), r.bubbles().stream().filter(x -> x.comp() == lower).count(),
                    turretN, exposed, exposed / (double) Math.max(1, turretN + exposed), tongue, maxOver / hp, shelfSum / nv, lj, vj);
            System.out.println("T2604_ANATOMY " + line);
            json.append(json.length() > 2 ? ",\n" : "").append(line);
        }
        Files.writeString(out.resolve(variant + "-anatomy.json"), json.append("\n]\n").toString());
    }

    static int[] owners(ConvectiveSystemT260.Program p, double[] pose, int W, int H, double fov) {
        var v = p.volume();
        Bounds b = v.bounds();
        var field = p.field();
        double[] cam = {pose[0], pose[1], pose[2]};
        double[] fwd = norm(new double[]{pose[3] - pose[0], pose[4] - pose[1], pose[5] - pose[2]});
        double[] right = norm(cross(fwd, new double[]{0, 1, 0})), up = cross(right, fwd);
        double ty = Math.tan(Math.toRadians(fov) / 2), tx = ty * W / H;
        int[] own = new int[W * H];
        java.util.Arrays.fill(own, -1);
        IntStream.range(0, H).parallel().forEach(j -> {
            for (int i = 0; i < W; i++) {
                double sx = (2 * (i + .5) / W - 1) * tx, sy = (1 - 2 * (j + .5) / H) * ty;
                double[] d = norm(new double[]{fwd[0] + right[0] * sx + up[0] * sy, fwd[1] + right[1] * sx + up[1] * sy, fwd[2] + right[2] * sx + up[2] * sy});
                double[] sl = ConvectiveSystemT260Gallery.slab(cam, d, b);
                if (sl == null) continue;
                for (double t = sl[0]; t < sl[1]; t += 3) {
                    float x = (float) (cam[0] + d[0] * t), y = (float) (cam[1] + d[1] * t), z = (float) (cam[2] + d[2] * t);
                    if (v.density(x, y, z) > .5f) { own[j * W + i] = field.owner(x, y, z); break; }
                }
            }
        });
        return own;
    }

    static double coverage(float[] a) { int n = 0; for (float x : a) if (x > .5f) n++; return n / (double) a.length; }

    /** Alpha of one ray set; the camera model is the T260 gallery's (vertical FOV, look-at up = +y). */
    static float[] render(ConvectiveSystemT260.Program p, double[] pose, int W, int H, double fov, String mode) {
        var v = p.volume();
        Bounds b = v.bounds();
        var field = p.field();
        float feather = v.feather();
        double[] cam = {pose[0], pose[1], pose[2]};
        double[] fwd = norm(new double[]{pose[3] - pose[0], pose[4] - pose[1], pose[5] - pose[2]});
        double[] right = norm(cross(fwd, new double[]{0, 1, 0})), up = cross(right, fwd);
        double ty = Math.tan(Math.toRadians(fov) / 2), tx = ty * W / H;
        float[] alpha = new float[W * H];
        float[][] boxes = new float[v.boxCount()][];
        for (int i = 0; i < boxes.length; i++) boxes[i] = v.box(i);
        IntStream.range(0, H).parallel().forEach(j -> {
            for (int i = 0; i < W; i++) {
                double sx = (2 * (i + .5) / W - 1) * tx, sy = (1 - 2 * (j + .5) / H) * ty;
                float[] d = toF(norm(new double[]{fwd[0] + right[0] * sx + up[0] * sy, fwd[1] + right[1] * sx + up[1] * sy, fwd[2] + right[2] * sx + up[2] * sy}));
                float[] o = toF(cam);
                // Slab (SlabBaseY..SlabTopY = volume min/max y) then the whole-program AABB, as the shader does.
                float t0, t1;
                if (Math.abs(d[1]) < 1e-5f) { t0 = 0; t1 = 12000; }
                else { float ta = (b.minY() - o[1]) / d[1], tb = (b.maxY() - o[1]) / d[1]; t0 = Math.min(ta, tb); t1 = Math.max(ta, tb); }
                t0 = Math.max(t0, 0); t1 = Math.min(t1, 12000);
                float[] lo = {b.minX(), b.minY(), b.minZ()}, hi = {b.maxX(), b.maxY(), b.maxZ()};
                for (int a = 0; a < 3; a++) {
                    if (Math.abs(d[a]) < 1e-5f) { if (o[a] < lo[a] || o[a] > hi[a]) t1 = -1; continue; }
                    float aa = (lo[a] - o[a]) / d[a], bb = (hi[a] - o[a]) / d[a];
                    t0 = Math.max(t0, Math.min(aa, bb)); t1 = Math.min(t1, Math.max(aa, bb));
                }
                if (t1 <= t0) continue;
                double trans = 1;
                switch (mode) {
                    case "ref", "field" -> {
                        float step = mode.equals("ref") ? .25f : .5f;
                        for (float t = t0 + step * .5f; t < t1 && trans > 1e-4; t += step) {
                            float x = o[0] + d[0] * t, y = o[1] + d[1] * t, z = o[2] + d[2] * t;
                            float dens;
                            if (mode.equals("ref")) dens = v.density(x, y, z);
                            else {
                                // Analytic field only where the lattice cell is non-empty or within one feather of it.
                                if (v.density(x, y, z) <= 0 && emptyAround(v, x, y, z)) continue;
                                dens = Math.max(0, Math.min(1, .5f - field.distance(x, y, z) / feather));
                            }
                            if (dens > 0) trans *= Math.exp(-dens * EXT * step);
                        }
                    }
                    default -> {
                        // exact/interval = pre-T260.4 shader (accumulated t, midpoint box test);
                        // lattice/fixed = T260.4 shader (t = base + k * fine, box test at the sample, 0.05 margin).
                        boolean lattice = mode.equals("lattice") || mode.equals("fixed");
                        boolean skip = mode.equals("interval") || mode.equals("fixed");
                        float[][] iv = skip ? intervals(o, d, t0, t1, boxes) : null;
                        int fx = i, fy = H - 1 - j;
                        float blue = BLUE_RANK[(fy % BLUE) * BLUE + fx % BLUE] / 255f;
                        float span = t1 - t0;
                        float base = t0 + blue * Math.min(FINE, span), t = base;
                        int idx = 0, k = 0;
                        for (int s = 0; s < 2048; s++) {
                            if (t >= t1 || trans < .015) break;
                            if (skip && lattice) {
                                while (idx < 3 && t > iv[idx][1] + .05f) idx++;
                                if (idx >= 3) break;
                                float entry = iv[idx][0] - .05f;
                                if (t < entry) {
                                    k = Math.max(k, (int) Math.ceil((entry - base) / FINE));
                                    t = base + k * FINE;
                                    if (t >= t1) break;
                                }
                            } else if (skip) {
                                float probe = t + Math.min(FINE, t1 - t) * .5f;
                                while (idx < 3 && probe > iv[idx][1]) idx++;
                                if (idx >= 3) break;
                                if (probe < iv[idx][0]) {
                                    t += (float) Math.ceil((iv[idx][0] - probe) / FINE) * FINE;
                                    if (t >= t1) break;
                                }
                            }
                            float stepLength = Math.min(FINE, t1 - t);
                            float x = o[0] + d[0] * t, y = o[1] + d[1] * t, z = o[2] + d[2] * t;
                            float dens = b.contains(x, y, z) ? v.densityFromTexture(x, y, z) : 0;
                            if (dens > .0008f) trans *= Math.exp(-dens * EXT * stepLength);
                            t += stepLength;
                            if (lattice) t = base + (++k) * FINE;
                        }
                    }
                }
                alpha[j * W + i] = (float) (1 - trans);
            }
        });
        return alpha;
    }

    static boolean emptyAround(SparseBrickVolume v, float x, float y, float z) {
        float r = v.feather();
        for (int a = -1; a <= 1; a += 2) {
            if (v.density(x + a * r, y, z) > 0 || v.density(x, y + a * r, z) > 0 || v.density(x, y, z + a * r) > 0) return false;
        }
        return true;
    }

    /** paMorphRayIntervals: up to three boxes, sorted by entry, overlaps merged front to back. */
    static float[][] intervals(float[] o, float[] d, float low, float high, float[][] boxes) {
        float[][] iv = new float[3][];
        for (int i = 0; i < 3; i++) {
            iv[i] = new float[]{1e20f, -1f};
            if (i >= boxes.length) continue;
            float a = low, bb = high;
            for (int axis = 0; axis < 3; axis++) {
                float lo = boxes[i][axis], hi = boxes[i][axis + 3];
                if (Math.abs(d[axis]) < 1e-8f) { if (o[axis] < lo || o[axis] > hi) bb = -1; }
                else { float ta = (lo - o[axis]) / d[axis], tb = (hi - o[axis]) / d[axis]; a = Math.max(a, Math.min(ta, tb)); bb = Math.min(bb, Math.max(ta, tb)); }
            }
            if (bb >= a) iv[i] = new float[]{a, bb};
        }
        if (iv[1][0] < iv[0][0]) { float[] t = iv[0]; iv[0] = iv[1]; iv[1] = t; }
        if (iv[2][0] < iv[1][0]) { float[] t = iv[1]; iv[1] = iv[2]; iv[2] = t; }
        if (iv[1][0] < iv[0][0]) { float[] t = iv[0]; iv[0] = iv[1]; iv[1] = t; }
        if (iv[1][1] >= 0 && iv[1][0] <= iv[0][1]) { iv[0][1] = Math.max(iv[0][1], iv[1][1]); iv[1] = iv[2]; iv[2] = new float[]{1e20f, -1f}; }
        if (iv[1][1] >= 0 && iv[1][0] <= iv[0][1]) { iv[0][1] = Math.max(iv[0][1], iv[1][1]); iv[1] = iv[2]; iv[2] = new float[]{1e20f, -1f}; }
        if (iv[2][1] >= 0 && iv[2][0] <= iv[1][1]) { iv[1][1] = Math.max(iv[1][1], iv[2][1]); iv[2] = new float[]{1e20f, -1f}; }
        return iv;
    }

    static void write(Path f, float[] a, int W, int H) throws Exception {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int k = 0; k < a.length; k++) {
            int g = (int) (0x10 + (235 - 0x10) * a[k]);
            im.setRGB(k % W, k / W, g << 16 | g << 8 | Math.min(255, g + 8));
        }
        ImageIO.write(im, "png", f.toFile());
    }

    static void writeDiff(Path f, float[] a, float[] b, int W, int H) throws Exception {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int k = 0; k < a.length; k++) {
            float d = (a[k] - b[k]) * 8;
            int base = (int) (40 * Math.max(a[k], b[k]));
            int rr = Math.min(255, base + (int) (255 * Math.max(0, d))), bl = Math.min(255, base + (int) (255 * Math.max(0, -d)));
            im.setRGB(k % W, k / W, rr << 16 | base << 8 | bl);
        }
        ImageIO.write(im, "png", f.toFile());
    }

    static float[] toF(double[] a) { return new float[]{(float) a[0], (float) a[1], (float) a[2]}; }
    static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    static double[] norm(double[] a) { double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); return new double[]{a[0] / l, a[1] / l, a[2] / l}; }

    /** Copy of CloudNoiseTextureManager.bakeBlueNoise (void-and-cluster ranks), returned as the uploaded byte values. */
    static int[] blueNoise(int size) {
        int count = size * size;
        float[] energy = new float[count];
        boolean[] placed = new boolean[count];
        int[] rank = new int[count];
        float sigma = 1.9F;
        int radius = 6;
        float[] kernel = new float[(radius * 2 + 1) * (radius * 2 + 1)];
        int ki = 0;
        for (int dy = -radius; dy <= radius; dy++) for (int dx = -radius; dx <= radius; dx++) kernel[ki++] = (float) Math.exp(-(dx * dx + dy * dy) / (2.0F * sigma * sigma));
        int seedIndex = Integer.remainderUnsigned(hashInt(12345), count);
        for (int placedCount = 0; placedCount < count; placedCount++) {
            int best = -1;
            float bestEnergy = Float.MAX_VALUE;
            for (int i = 0; i < count; i++) if (!placed[i] && energy[i] < bestEnergy) { bestEnergy = energy[i]; best = i; }
            if (placedCount == 0) best = seedIndex;
            placed[best] = true;
            rank[best] = placedCount;
            int bx = best % size, by = best / size;
            ki = 0;
            for (int dy = -radius; dy <= radius; dy++) for (int dx = -radius; dx <= radius; dx++)
                energy[Math.floorMod(by + dy, size) * size + Math.floorMod(bx + dx, size)] += kernel[ki++];
        }
        int[] out = new int[count];
        for (int i = 0; i < count; i++) out[i] = Math.round(rank[i] * 255.0F / (count - 1));
        return out;
    }

    static int hashInt(int value) {
        int h = value * 747796405 + -1403630843;
        h = ((h >>> ((h >>> 28) + 4)) ^ h) * 277803737;
        return h ^ (h >>> 22);
    }
}
