package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260.Bubble;
import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260.Recipe;
import net.Gabou.projectatmosphere.clouds.morphology.StormMorphologyPrototype.Bounds;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * T260.6 structural metrics of a baked arm (what the renderer samples: the sparse-brick volume), all relative to D at
 * the same seed:
 * <ul>
 * <li>junctions: surface samples (density 0.5 crossings on a 6-block lattice above the base), outward normal from the
 * smoothed density gradient, mean curvature div(n). A hard seam is a concave sample with curvature radius < 15 blocks;
 * seams = clusters of >= 8 hard-seam samples; normal jumps = neighbouring surface samples more than 35 deg apart;</li>
 * <li>valleys: between every pair of turrets of one attached cell (D's turret tops, identical for all arms), the depth
 * of the surface profile below the lower summit along the line joining them, relative to that summit's height;</li>
 * <li>silhouettes: flat alpha (CPU, native cameras) against D: mean boundary displacement (XOR / D perimeter, px) and
 * area ratio;</li>
 * <li>connectivity: connected occupied regions (density > 0.5, 9-block lattice) and the minimum gap between regions;</li>
 * <li>density: interior density statistics (mean, CV, 5th percentile, minimum), enclosed low-density cavities.</li>
 * </ul>
 * Args: out seeds(comma) arms(comma, first must be D)
 */
public final class T2606Metrics {
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        StringBuilder json = new StringBuilder("[\n");
        for (String ss : args[1].split(",")) {
            long seed = Long.parseLong(ss);
            Recipe r = ConvectiveSystemT260.calvusD(seed);
            Bounds frame = new ConvectiveSystemT260.Program(r, null, 6, 12, .25f).bounds();
            float[][] dSil = null;
            for (String arm : args[2].split(",")) {
                long t0 = System.nanoTime();
                var p = T2606Preview.arm(r, arm, false);
                double bakeMs = (System.nanoTime() - t0) / 1e6;
                long t1 = System.nanoTime();
                var light = new NeutralLightVolume(p.volume(), T2606Preview.EXT);
                double lightMs = (System.nanoTime() - t1) / 1e6;
                SparseBrickVolume v = p.volume();
                // C changes density, never the material support (density > 0): geometry metrics of a C arm are measured
                // on its geometry twin (the same arm without C), after proving the supports are identical.
                SparseBrickVolume g = v;
                String support = "null";
                if (arm.contains("C")) {
                    String twin = arm.replace("C", "");
                    g = T2606Preview.arm(r, twin.isEmpty() ? "D" : twin, false).volume();
                    support = supportMismatch(v, g);
                }
                String junction = junctions(g);
                String macro = junctions(g, 12, 40);
                String valleys = valleys(r, g);
                String conn = connectivity(g);
                String dens = density(v);
                String[] views = {"side", "front", "above", "below", "low_angle"};
                float[][] sil = new float[views.length][];
                StringBuilder sj = new StringBuilder();
                for (int i = 0; i < views.length; i++) {
                    double[] cam = T2606Preview.camera(views[i], seed, frame);
                    sil[i] = T2606Preview.march(p, v, null, cam, 600, 338, "flat").alpha();
                    if (dSil != null) {
                        if (sj.length() > 0) sj.append(',');
                        sj.append('"').append(views[i]).append("\":").append(silhouette(dSil[i], sil[i], 600, 338));
                    }
                }
                if (arm.equals("D")) dSil = sil;
                String line = String.format(Locale.ROOT, "{\"seed\":%d,\"arm\":\"%s\",\"signature\":\"%s\",\"geometryBakeMs\":%.0f,\"fieldMs\":%.0f,\"lightBakeMs\":%.0f,"
                                + "\"textureMB\":%.1f,\"lightRows\":\"%s\",\"cells\":%s,\"supportMismatchVsGeometryTwin\":%s,\"junctions\":%s,\"macroJunctions\":%s,\"valleys\":%s,\"connectivity\":%s,\"density\":%s,\"silhouette\":{%s}}",
                        seed, arm, Long.toHexString(p.signature()), bakeMs, p.fieldMs(), lightMs, v.stats().textureBytes() / 1048576.0, light.stats(),
                        Arrays.toString(v.counts()), support, junction, macro, valleys, conn, dens, sj);
                System.out.println("T2606_METRICS " + line);
                json.append(json.length() > 2 ? ",\n" : "").append(line);
            }
        }
        Files.writeString(out.resolve("t2606-metrics.json"), json.append("\n]\n").toString());
    }

    // ------------------------------------------------------------------ junctions

    static float dens(SparseBrickVolume v, Bounds b, float x, float y, float z) { return b.contains(x, y, z) ? v.density(x, y, z) : 0; }

    /** Outward unit normal from the density gradient (h = 4). */
    static float[] normal(SparseBrickVolume v, Bounds b, float x, float y, float z) { return normal(v, b, x, y, z, 4); }

    static float[] normal(SparseBrickVolume v, Bounds b, float x, float y, float z, float h) {
        float gx = dens(v, b, x + h, y, z) - dens(v, b, x - h, y, z), gy = dens(v, b, x, y + h, z) - dens(v, b, x, y - h, z), gz = dens(v, b, x, y, z + h) - dens(v, b, x, y, z - h);
        float n = (float) Math.sqrt(gx * gx + gy * gy + gz * gz);
        return n < 1e-6f ? null : new float[]{-gx / n, -gy / n, -gz / n};
    }

    static String junctions(SparseBrickVolume v) { return junctions(v, 4, 15); }

    /**
     * Junction statistics at a smoothing scale: normals from the density gradient over +-h blocks, a hard seam is a
     * concave sample whose curvature radius is below {@code radius}. h = 4 / 15 blocks resolves every crease (including
     * the creases between cauliflower domes); h = 12 / 40 blocks only the macro junctions between large structures.
     */
    static String junctions(SparseBrickVolume v, float hs, float radius) {
        Bounds b = v.bounds();
        float s = 6;
        int nx = (int) ((b.maxX() - b.minX()) / s), ny = (int) ((b.maxY() - b.minY()) / s), nz = (int) ((b.maxZ() - b.minZ()) / s);
        int[] state = new int[nx * ny * nz]; // 0 none, 1 surface, 2 hard seam
        float[][] normals = new float[state.length][];
        float[] curv = new float[state.length];
        IntStream.range(0, nz).parallel().forEach(k -> {
            for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
                float x = b.minX() + i * s, y = b.minY() + j * s, z = b.minZ() + k * s;
                if (y < 12) continue; // the condensation-base cut is the same flat crease for every arm
                float d0 = dens(v, b, x, y, z);
                boolean cross = (d0 - .5f) * (dens(v, b, x + s, y, z) - .5f) < 0 || (d0 - .5f) * (dens(v, b, x, y + s, z) - .5f) < 0 || (d0 - .5f) * (dens(v, b, x, y, z + s) - .5f) < 0;
                if (!cross) continue;
                float[] n = normal(v, b, x, y, z, hs);
                if (n == null) continue;
                float h = hs, div = 0;
                float[][] off = {{h, 0, 0}, {0, h, 0}, {0, 0, h}};
                boolean ok = true;
                for (int a = 0; a < 3 && ok; a++) {
                    float[] np = normal(v, b, x + off[a][0], y + off[a][1], z + off[a][2], hs), nm = normal(v, b, x - off[a][0], y - off[a][1], z - off[a][2], hs);
                    if (np == null || nm == null) { ok = false; break; }
                    div += (np[a] - nm[a]) / (2 * h);
                }
                if (!ok) continue;
                int q = (k * ny + j) * nx + i;
                normals[q] = n;
                curv[q] = div;
                state[q] = div < -2f / radius ? 2 : 1;
            }
        });
        int surface = 0, hard = 0, jumps = 0, pairs = 0;
        List<Float> cs = new ArrayList<>();
        for (int q = 0; q < state.length; q++) {
            if (state[q] == 0) continue;
            surface++;
            if (state[q] == 2) hard++;
            cs.add(curv[q]);
            int i = q % nx, j = (q / nx) % ny, k = q / (nx * ny);
            int[][] nb = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
            for (int[] o : nb) {
                int a = i + o[0], c = j + o[1], e = k + o[2];
                if (a >= nx || c >= ny || e >= nz) continue;
                int w = (e * ny + c) * nx + a;
                if (state[w] == 0) continue;
                pairs++;
                float dot = normals[q][0] * normals[w][0] + normals[q][1] * normals[w][1] + normals[q][2] * normals[w][2];
                if (dot < Math.cos(Math.toRadians(35))) jumps++;
            }
        }
        // Seams: 26-connected clusters of hard-seam samples with >= 8 members.
        int[] lab = new int[state.length];
        int seams = 0, seamSamples = 0;
        ArrayDeque<Integer> st = new ArrayDeque<>();
        for (int q = 0; q < state.length; q++) {
            if (state[q] != 2 || lab[q] != 0) continue;
            int size = 0;
            st.push(q); lab[q] = 1;
            while (!st.isEmpty()) {
                int u = st.pop(); size++;
                int i = u % nx, j = (u / nx) % ny, k = u / (nx * ny);
                for (int dk = -1; dk <= 1; dk++) for (int dj = -1; dj <= 1; dj++) for (int di = -1; di <= 1; di++) {
                    int a = i + di, c = j + dj, e = k + dk;
                    if (a < 0 || c < 0 || e < 0 || a >= nx || c >= ny || e >= nz) continue;
                    int w = (e * ny + c) * nx + a;
                    if (state[w] == 2 && lab[w] == 0) { lab[w] = 1; st.push(w); }
                }
            }
            if (size >= 8) { seams++; seamSamples += size; }
        }
        cs.sort(Float::compare);
        float p5 = cs.isEmpty() ? 0 : cs.get((int) (cs.size() * .05)), p50 = cs.isEmpty() ? 0 : cs.get(cs.size() / 2);
        return String.format(Locale.ROOT, "{\"surfaceSamples\":%d,\"hardSeamFraction\":%.4f,\"seams\":%d,\"seamSamples\":%d,\"normalJumpFraction\":%.4f,"
                + "\"curvatureP5\":%.4f,\"curvatureP50\":%.4f,\"concaveRadiusP5\":%.1f}", surface, hard / (double) Math.max(1, surface), seams, seamSamples,
                jumps / (double) Math.max(1, pairs), p5, p50, p5 < 0 ? -2 / p5 : -1);
    }

    /** Lattice points (3 blocks) where exactly one of two volumes has material (density > 0). */
    static String supportMismatch(SparseBrickVolume a, SparseBrickVolume b) {
        Bounds bb = a.bounds();
        float s = 3;
        int nx = (int) ((bb.maxX() - bb.minX()) / s), ny = (int) ((bb.maxY() - bb.minY()) / s), nz = (int) ((bb.maxZ() - bb.minZ()) / s);
        long[] mis = new long[nz], tot = new long[nz];
        IntStream.range(0, nz).parallel().forEach(k -> {
            for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
                float x = bb.minX() + i * s + .37f, y = bb.minY() + j * s + .61f, z = bb.minZ() + k * s + .13f;
                boolean p = dens(a, a.bounds(), x, y, z) > 0, q = dens(b, b.bounds(), x, y, z) > 0;
                if (p || q) tot[k]++;
                if (p != q) mis[k]++;
            }
        });
        return String.format(Locale.ROOT, "{\"mismatch\":%d,\"occupied\":%d}", Arrays.stream(mis).sum(), Arrays.stream(tot).sum());
    }

    // ------------------------------------------------------------------ valleys

    /** Highest occupied height (density > 0.5) of the vertical column at (x, z), or the base when empty. */
    static float top(SparseBrickVolume v, Bounds b, float x, float z) {
        for (float y = b.maxY() - 2; y > Math.max(b.minY(), 0); y -= 3) if (dens(v, b, x, y, z) > .5f) return y;
        return 0;
    }

    static String valleys(Recipe r, SparseBrickVolume v) {
        Bounds b = v.bounds();
        var s = new ConvectiveVolumeT2606.Structure(r);
        List<Float> rel = new ArrayList<>();
        int pairs = 0, open = 0;
        for (int t = 0; t < s.turretCount(); t++) for (int u = t + 1; u < s.turretCount(); u++) {
            if (s.turretComp(t) != s.turretComp(u)) continue;
            var comp = r.components().get(s.turretComp(t));
            if (comp.group() != 0 || comp.role() == ConvectiveSystemT260.Role.LOWER_MASS) continue;
            int[] a = s.turret(t), c = s.turret(u);
            Bubble ta = r.bubbles().get(a[a.length - 1]), tc = r.bubbles().get(c[c.length - 1]);
            float sep = (float) Math.hypot(ta.x() - tc.x(), ta.z() - tc.z());
            if (sep > 1.6f * (Math.max(ta.rx(), ta.rz()) + Math.max(tc.rx(), tc.rz())) || sep < 1) continue;
            float ha = top(v, b, ta.x(), ta.z()), hc = top(v, b, tc.x(), tc.z());
            float low = Float.MAX_VALUE;
            for (int k = 1; k < 40; k++) {
                float f = k / 40f;
                low = Math.min(low, top(v, b, ta.x() + (tc.x() - ta.x()) * f, ta.z() + (tc.z() - ta.z()) * f));
            }
            float summit = Math.min(ha, hc);
            if (summit <= 20) continue;
            pairs++;
            float depth = Math.max(0, summit - low) / summit;
            rel.add(depth);
            if (depth > .08f) open++;
        }
        rel.sort(Float::compare);
        return String.format(Locale.ROOT, "{\"pairs\":%d,\"openValleys\":%d,\"openFraction\":%.3f,\"relDepthP50\":%.4f,\"relDepthP90\":%.4f}", pairs, open,
                open / (double) Math.max(1, pairs), rel.isEmpty() ? 0 : rel.get(rel.size() / 2), rel.isEmpty() ? 0 : rel.get((int) (rel.size() * .9)));
    }

    // ------------------------------------------------------------------ connectivity

    static String connectivity(SparseBrickVolume v) {
        Bounds b = v.bounds();
        float s = 9;
        int nx = (int) ((b.maxX() - b.minX()) / s), ny = (int) ((b.maxY() - b.minY()) / s), nz = (int) ((b.maxZ() - b.minZ()) / s);
        boolean[] occ = new boolean[nx * ny * nz];
        IntStream.range(0, nz).parallel().forEach(k -> {
            for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++)
                occ[(k * ny + j) * nx + i] = dens(v, b, b.minX() + (i + .5f) * s, b.minY() + (j + .5f) * s, b.minZ() + (k + .5f) * s) > .5f;
        });
        int[] lab = new int[occ.length];
        List<Integer> sizes = new ArrayList<>();
        ArrayDeque<Integer> st = new ArrayDeque<>();
        for (int q = 0; q < occ.length; q++) {
            if (!occ[q] || lab[q] != 0) continue;
            int id = sizes.size() + 1, size = 0;
            st.push(q); lab[q] = id;
            while (!st.isEmpty()) {
                int u = st.pop(); size++;
                int i = u % nx, j = (u / nx) % ny, k = u / (nx * ny);
                int[][] nb = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
                for (int[] o : nb) {
                    int a = i + o[0], c = j + o[1], e = k + o[2];
                    if (a < 0 || c < 0 || e < 0 || a >= nx || c >= ny || e >= nz) continue;
                    int w = (e * ny + c) * nx + a;
                    if (occ[w] && lab[w] == 0) { lab[w] = id; st.push(w); }
                }
            }
            sizes.add(size);
        }
        // Regions of >= 20 cells; min gap between them from their boundary cells (horizontal + vertical distance).
        List<Integer> big = new ArrayList<>();
        for (int i = 0; i < sizes.size(); i++) if (sizes.get(i) >= 20) big.add(i + 1);
        List<List<float[]>> bnd = new ArrayList<>();
        for (int id : big) bnd.add(new ArrayList<>());
        for (int q = 0; q < occ.length; q++) {
            if (lab[q] == 0) continue;
            int idx = big.indexOf(lab[q]);
            if (idx < 0) continue;
            int i = q % nx, j = (q / nx) % ny, k = q / (nx * ny);
            boolean edge = i == 0 || j == 0 || k == 0 || i == nx - 1 || j == ny - 1 || k == nz - 1
                    || !occ[q + 1] || !occ[q - 1] || !occ[q + nx] || !occ[q - nx] || !occ[q + nx * ny] || !occ[q - nx * ny];
            if (edge) bnd.get(idx).add(new float[]{i * s, j * s, k * s});
        }
        double minGap = Double.MAX_VALUE;
        for (int a = 0; a < bnd.size(); a++) for (int c = a + 1; c < bnd.size(); c++) {
            List<float[]> pa = bnd.get(a), pc = bnd.get(c);
            int stepA = Math.max(1, pa.size() / 3000), stepC = Math.max(1, pc.size() / 3000);
            for (int i = 0; i < pa.size(); i += stepA) for (int k = 0; k < pc.size(); k += stepC) {
                float[] x = pa.get(i), y = pc.get(k);
                double d = Math.sqrt((x[0] - y[0]) * (x[0] - y[0]) + (x[1] - y[1]) * (x[1] - y[1]) + (x[2] - y[2]) * (x[2] - y[2]));
                minGap = Math.min(minGap, d);
            }
        }
        List<Integer> bs = new ArrayList<>();
        for (int id : big) bs.add(sizes.get(id - 1));
        bs.sort((x, y) -> y - x);
        return String.format(Locale.ROOT, "{\"regions\":%d,\"regionCells\":%s,\"minGapBlocks\":%.0f}", big.size(), bs, minGap == Double.MAX_VALUE ? -1 : minGap - s);
    }

    // ------------------------------------------------------------------ density

    static String density(SparseBrickVolume v) {
        Bounds b = v.bounds();
        float s = 6;
        int nx = (int) ((b.maxX() - b.minX()) / s), ny = (int) ((b.maxY() - b.minY()) / s), nz = (int) ((b.maxZ() - b.minZ()) / s);
        double[][] acc = new double[nz][];
        IntStream.range(0, nz).parallel().forEach(k -> {
            double sum = 0, sq = 0, n = 0, min = 1, cav = 0;
            int[] hist = new int[101];
            for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
                float x = b.minX() + i * s, y = b.minY() + j * s, z = b.minZ() + k * s;
                float d = dens(v, b, x, y, z);
                if (d <= 0) continue;
                // Interior: every neighbour 12 blocks away is material too (outside the surface ramp).
                boolean inside = true;
                for (int a = 0; a < 6 && inside; a++) {
                    float ox = a == 0 ? 12 : a == 1 ? -12 : 0, oy = a == 2 ? 12 : a == 3 ? -12 : 0, oz = a == 4 ? 12 : a == 5 ? -12 : 0;
                    inside = dens(v, b, x + ox, y + oy, z + oz) > .3f;
                }
                if (!inside) continue;
                sum += d; sq += d * d; n++; min = Math.min(min, d);
                hist[Math.round(d * 100)]++;
                if (d < .35f) cav++;
            }
            double[] r = new double[106];
            r[0] = sum; r[1] = sq; r[2] = n; r[3] = min; r[4] = cav;
            for (int h = 0; h <= 100; h++) r[5 + h] = hist[h];
            acc[k] = r;
        });
        double sum = 0, sq = 0, n = 0, min = 1, cav = 0;
        double[] hist = new double[101];
        for (double[] r : acc) { sum += r[0]; sq += r[1]; n += r[2]; min = Math.min(min, r[3]); cav += r[4]; for (int h = 0; h <= 100; h++) hist[h] += r[5 + h]; }
        double mean = sum / Math.max(1, n), sd = Math.sqrt(Math.max(0, sq / Math.max(1, n) - mean * mean));
        double c = 0, p5 = 0;
        for (int h = 0; h <= 100; h++) { c += hist[h]; if (c >= .05 * n) { p5 = h / 100.0; break; } }
        return String.format(Locale.ROOT, "{\"interiorSamples\":%.0f,\"mean\":%.4f,\"cv\":%.4f,\"p5\":%.2f,\"min\":%.3f,\"belowPoint35\":%.0f}", n, mean, sd / Math.max(1e-9, mean), p5, min, cav);
    }

    // ------------------------------------------------------------------ silhouettes

    static String silhouette(float[] d, float[] a, int W, int H) {
        int xor = 0, per = 0, ad = 0, aa = 0;
        for (int j = 0; j < H; j++) for (int i = 0; i < W; i++) {
            int q = j * W + i;
            boolean x = d[q] > .5f, y = a[q] > .5f;
            if (x) ad++;
            if (y) aa++;
            if (x != y) xor++;
            if (x && i > 0 && j > 0 && i < W - 1 && j < H - 1 && (d[q - 1] <= .5f || d[q + 1] <= .5f || d[q - W] <= .5f || d[q + W] <= .5f)) per++;
        }
        return String.format(Locale.ROOT, "{\"meanDisplacementPx\":%.2f,\"areaRatio\":%.4f}", xor / (double) Math.max(1, per), aa / (double) Math.max(1, ad));
    }
}
