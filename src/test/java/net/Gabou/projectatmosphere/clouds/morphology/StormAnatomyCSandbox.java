package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.type.CloudFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudShapeProfile;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * T258 CPU geometry sandbox for candidate C. Contracts: determinism, level nesting (identical bounds
 * and grid for C0..C3), conservative bounds and occupancy grid, the texture packing reproduces the CPU
 * sampler exactly (GLSL mirror), baked-vs-exact agreement, one connected component per storm at every
 * level, multi-seed topology, valley guard, descriptor control responses, bounded texture size, and
 * B v3 geometry fingerprints unchanged. Exit code 1 on any failure.
 */
public final class StormAnatomyCSandbox {
    static final String[] TYPES = {"cumulonimbus_calvus", "cumulonimbus_capillatus", "supercell"};
    static final long[] SEEDS = {20261008L, 20268927L, 20276846L};
    static int passed, failed;

    static void check(boolean ok, String name, String detail) {
        if (ok) passed++; else failed++;
        System.out.println((ok ? "T258_SANDBOX PASS " : "T258_SANDBOX FAIL ") + name + " " + detail);
    }
    static CloudShapeProfile shape(String type) { return CloudShapeProfile.defaultFor(type, CloudFamily.CUMULONIMBUS, null); }

    public static void main(String[] args) throws Exception {
        for (String type : TYPES) for (long seed : SEEDS) {
            String id = type + "/" + seed;
            var c3 = StormAnatomyCandidateC.compile(type, shape(type), seed);
            var again = StormAnatomyCandidateC.compile(type, shape(type), seed);
            check(c3.signature() == again.signature() && Arrays.equals(c3.textureData(), again.textureData()), "determinism", id);
            var levels = new StormAnatomyCandidateC.Program[4];
            for (var l : StormAnatomyCandidateC.Level.values())
                levels[l.ordinal()] = l == StormAnatomyCandidateC.Level.C3 ? c3 : StormAnatomyCandidateC.compile(type, shape(type), seed, l);
            boolean nest = true;
            for (var p : levels) nest &= p.bounds().equals(c3.bounds()) && Arrays.equals(p.dims(), c3.dims()) && p.voxel() == c3.voxel()
                    && sameElements(p.elements(), c3.elements());
            check(nest, "levels_share_bounds_grid_elements", id);
            Set<Long> sigs = new HashSet<>();
            for (var p : levels) sigs.add(p.signature());
            check(sigs.size() == 4, "levels_distinct_geometry", id + " signatures=" + sigs.size());
            for (var p : levels) {
                String lid = id + "/" + p.level();
                check(boundsConservative(p), "bounds_faces_empty", lid);
                double[] g = gridConservative(p, seed);
                check(g[0] == 0, "grid_conservative", lid + String.format(Locale.ROOT, " violations=%.0f samples=%.0f emptyFraction=%.3f", g[0], g[1], g[2]));
                check(textureMirror(p, seed) == 0f, "texture_packing_mirrors_sampler", lid);
                int[] comp = components(p);
                check(comp[0] == 1, "one_connected_component", lid + " components=" + comp[0] + " largestVoxels=" + comp[1] + " detachedVoxels=" + comp[2]);
                check(p.textureHeight() <= 2048 && p.dims()[0] * p.dims()[1] * p.dims()[2] <= StormAnatomyCandidateC.MAX_SAMPLES * 1.25,
                        "bounded_texture", lid + " texture=" + p.textureWidth() + "x" + p.textureHeight() + " samples=" + p.dims()[0] * p.dims()[1] * p.dims()[2]);
            }
            double[] agree = bakedVsExact(c3, seed);
            check(agree[1] < c3.feather(), "baked_matches_exact_near_surface", id + String.format(Locale.ROOT, " p50=%.2f p99=%.2f feather=%.1f voxel=%.2f", agree[0], agree[1], c3.feather(), c3.voxel()));
            topology(type, c3, id);
            check(valleyGuard(c3), "secondaries_avoid_major_valleys", id);
        }
        controls();
        fingerprints();
        System.out.println("T258_SANDBOX_SUMMARY passed=" + passed + " failed=" + failed);
        if (failed > 0) System.exit(1);
    }

    static boolean sameElements(List<StormAnatomyCandidateC.Element> a, List<StormAnatomyCandidateC.Element> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            var x = a.get(i); var y = b.get(i);
            if (x.role() != y.role() || x.level() != y.level() || x.blend() != y.blend() || !Arrays.equals(x.p(), y.p())) return false;
        }
        return true;
    }

    static boolean boundsConservative(StormAnatomyCandidateC.Program p) {
        var b = p.bounds();
        var r = new SplittableRandom(7);
        for (int n = 0; n < 6000; n++) {
            float x = (float) r.nextDouble(b.minX(), b.maxX()), y = (float) r.nextDouble(b.minY(), b.maxY()), z = (float) r.nextDouble(b.minZ(), b.maxZ());
            switch (n % 6) { case 0 -> x = b.minX(); case 1 -> x = b.maxX(); case 2 -> y = b.minY(); case 3 -> y = b.maxY(); case 4 -> z = b.minZ(); default -> z = b.maxZ(); }
            if (p.sampledDistance(x, y, z) < p.feather() * .5f) return false;
        }
        return true;
    }

    /** {violations, samples, empty fraction}: an empty cell must never contain density. */
    static double[] gridConservative(StormAnatomyCandidateC.Program p, long seed) {
        var b = p.bounds();
        var r = new SplittableRandom(seed);
        int bad = 0, empty = 0, n = 300_000;
        for (int i = 0; i < n; i++) {
            float x = (float) r.nextDouble(b.minX(), b.maxX()), y = (float) r.nextDouble(b.minY(), b.maxY()), z = (float) r.nextDouble(b.minZ(), b.maxZ());
            if (!p.gridOccupied(x, y, z)) { empty++; if (p.sampledDistance(x, y, z) < p.feather() * .5f) bad++; }
        }
        return new double[]{bad, n, empty / (double) n};
    }

    /** Max |GLSL-emulated texture lookup - CPU sampler| over random points (must be exactly 0). */
    static float textureMirror(StormAnatomyCandidateC.Program p, long seed) {
        float[] t = p.textureData();
        int W = p.textureWidth();
        int nx = (int) t[0], ny = (int) t[1], voxel = 0;
        float vox = t[3], minX = t[24], minY = t[25], minZ = t[26];
        int sdfStart = (int) t[28], gridStart = (int) t[29], G = (int) t[27];
        var b = p.bounds();
        var r = new SplittableRandom(seed ^ 99);
        float worst = 0;
        for (int i = 0; i < 50_000; i++) {
            float x = (float) r.nextDouble(b.minX(), b.maxX()), y = (float) r.nextDouble(b.minY(), b.maxY()), z = (float) r.nextDouble(b.minZ(), b.maxZ());
            // Grid (four cells per texel).
            int gi = Math.max(0, Math.min(G - 1, (int) Math.floor((x - b.minX()) / (b.maxX() - b.minX()) * G)));
            int gj = Math.max(0, Math.min(G - 1, (int) Math.floor((y - b.minY()) / (b.maxY() - b.minY()) * G)));
            int gk = Math.max(0, Math.min(G - 1, (int) Math.floor((z - b.minZ()) / (b.maxZ() - b.minZ()) * G)));
            boolean occ = t[gridStart * 4 + (gk * G + gj) * G + gi] > .5f;
            if (occ != p.gridOccupied(x, y, z)) return Float.POSITIVE_INFINITY;
            float gx = (x - minX) / vox, gy = (y - minY) / vox, gz = (z - minZ) / vox;
            int nz = p.dims()[2];
            int i0 = Math.max(0, Math.min(nx - 2, (int) Math.floor(gx))), j0 = Math.max(0, Math.min(ny - 2, (int) Math.floor(gy))), k0 = Math.max(0, Math.min(nz - 2, (int) Math.floor(gz)));
            float fx = Math.max(0, Math.min(1, gx - i0)), fy = Math.max(0, Math.min(1, gy - j0)), fz = Math.max(0, Math.min(1, gz - k0));
            int a = (sdfStart + (k0 * ny + j0) * nx + i0) * 4, c = a + nx * 4;
            float m0 = t[a] + (t[c] - t[a]) * fy, m1 = t[a + 1] + (t[c + 1] - t[a + 1]) * fy, m2 = t[a + 2] + (t[c + 2] - t[a + 2]) * fy, m3 = t[a + 3] + (t[c + 3] - t[a + 3]) * fy;
            float lo = m0 + (m1 - m0) * fx, hi = m2 + (m3 - m2) * fx;
            float d = lo + (hi - lo) * fz;
            worst = Math.max(worst, Math.abs(d - p.sampledDistance(x, y, z)));
        }
        return worst + voxel;
    }

    /** {components, largest, detached voxels} of the density > 0.5 voxels (26-connectivity). */
    static int[] components(StormAnatomyCandidateC.Program p) {
        int[] d = p.dims();
        int nx = d[0], ny = d[1], nz = d[2];
        float[] s = p.sdfCopy();
        int[] label = new int[s.length];
        int comps = 0, largest = 0, total = 0;
        int[] queue = new int[s.length];
        for (int o = 0; o < s.length; o++) {
            if (s[o] >= 0 || label[o] != 0) continue;
            comps++;
            int head = 0, tail = 0, size = 0;
            queue[tail++] = o; label[o] = comps;
            while (head < tail) {
                int c = queue[head++]; size++;
                int i = c % nx, j = (c / nx) % ny, k = c / (nx * ny);
                for (int dk = -1; dk <= 1; dk++) for (int dj = -1; dj <= 1; dj++) for (int di = -1; di <= 1; di++) {
                    int a = i + di, b = j + dj, e = k + dk;
                    if (a < 0 || b < 0 || e < 0 || a >= nx || b >= ny || e >= nz) continue;
                    int q = (e * ny + b) * nx + a;
                    if (s[q] < 0 && label[q] == 0) { label[q] = comps; queue[tail++] = q; }
                }
            }
            largest = Math.max(largest, size);
            total += size;
        }
        return new int[]{comps, largest, total - largest};
    }

    /** {p50, p99} |baked - exact| at random points within one feather of the exact surface. */
    static double[] bakedVsExact(StormAnatomyCandidateC.Program p, long seed) {
        var b = p.bounds();
        var r = new SplittableRandom(seed ^ 5);
        List<Double> err = new ArrayList<>();
        for (int n = 0; n < 400_000 && err.size() < 4000; n++) {
            float x = (float) r.nextDouble(b.minX(), b.maxX()), y = (float) r.nextDouble(b.minY(), b.maxY()), z = (float) r.nextDouble(b.minZ(), b.maxZ());
            float baked = p.sampledDistance(x, y, z);
            if (Math.abs(baked) > p.feather()) continue;
            float body = p.exactBodyDistance(x, y, z, -1), can = p.exactCanopyDistance(x, y, z);
            float exact = can >= 1e4f ? body : body >= 1e4f ? can : StormAnatomyCandidateC.smin(body, can, p.height() * .07f);
            // Only the sign-relevant agreement matters for the silhouette: compare inside the band.
            err.add((double) Math.abs(Math.max(-p.feather(), Math.min(p.feather(), exact)) - baked));
        }
        Collections.sort(err);
        return new double[]{err.get(err.size() / 2), err.get((int) (err.size() * .99))};
    }

    static void topology(String type, StormAnatomyCandidateC.Program c, String id) {
        Map<StormAnatomyCandidateC.Role, Integer> n = new EnumMap<>(StormAnatomyCandidateC.Role.class);
        Map<Integer, Float> topOfGroup = new HashMap<>();
        for (var e : c.elements()) {
            n.merge(e.role(), 1, Integer::sum);
            if (e.major() && e.shape() == StormAnatomyCandidateC.Shape.ELLIPSOID) topOfGroup.merge(e.group(), e.p()[1] + e.p()[4], Math::max);
        }
        boolean cal = type.contains("calvus"), sup = type.equals("supercell");
        int towers = n.getOrDefault(StormAnatomyCandidateC.Role.TOWER_HEAD, 0);
        check(towers >= (sup ? 6 : 3), "major_towers_present", id + " towerHeads=" + towers);
        check(n.getOrDefault(StormAnatomyCandidateC.Role.SECONDARY, 0) >= 2, "secondary_turrets_present", id + " secondaries=" + n.getOrDefault(StormAnatomyCandidateC.Role.SECONDARY, 0));
        check(cal != c.hasCanopy(), "canopy_presence", id + " canopy=" + c.hasCanopy());
        if (sup) check(n.getOrDefault(StormAnatomyCandidateC.Role.OVERSHOOT, 0) == 1 && n.getOrDefault(StormAnatomyCandidateC.Role.FLANK, 0) >= 5
                && n.getOrDefault(StormAnatomyCandidateC.Role.WALL, 0) == 1, "supercell_anatomy", id + " " + n);
        // Irregular heights: distinct major group summits spread over at least 0.2 H.
        var tops = new ArrayList<>(topOfGroup.values());
        Collections.sort(tops);
        float spread = tops.isEmpty() ? 0 : (tops.get(tops.size() - 1) - tops.get(0)) / c.height();
        check(tops.size() >= 3 && spread >= .2f, "irregular_major_heights", id + String.format(Locale.ROOT, " groups=%d spread=%.2fH", tops.size(), spread));
        if (c.hasCanopy()) {
            Set<StormAnatomyCandidateC.Role> roles = EnumSet.noneOf(StormAnatomyCandidateC.Role.class);
            for (var e : c.elements()) if (e.canopy()) roles.add(e.role());
            check(roles.containsAll(EnumSet.of(StormAnatomyCandidateC.Role.CANOPY_ROOT, StormAnatomyCandidateC.Role.CANOPY_PRIMARY, StormAnatomyCandidateC.Role.CANOPY_FAN,
                    StormAnatomyCandidateC.Role.CANOPY_OUTFLOW, StormAnatomyCandidateC.Role.CANOPY_FIBRE)), "canopy_hierarchy", id + " " + roles);
        }
    }

    /** Every secondary centre stays at least its radius from every major structure of another group. */
    static boolean valleyGuard(StormAnatomyCandidateC.Program c) {
        var els = c.elements();
        for (var s : els) {
            if (s.role() != StormAnatomyCandidateC.Role.SECONDARY) continue;
            for (var m : els) {
                if (!m.major() || m.group() == s.group()) continue;
                if (StormAnatomyCandidateC.primitive(m, s.p()[0], s.p()[1], s.p()[2]) < s.p()[3] * 1.1f - 1e-3f) return false;
            }
        }
        return true;
    }

    static void controls() {
        var base = shape("supercell");
        java.util.function.Function<float[], CloudShapeProfile> make = v -> new CloudShapeProfile("prototype/control", base.getBaseRadius(), base.getBaseOffset(),
                base.getTopOffset(), base.getLobeCountMin(), base.getLobeCountMax(), base.getLobeStrength(), v[0], v[1], v[2], v[3],
                base.getAnvilSpread(), base.getBaseFlattening(), base.getEdgeRaggedness(), v[4]);
        var neutral = StormAnatomyCandidateC.compile("supercell", make.apply(new float[]{0, 0, 0, 0, 0}), 20261008L);
        var tilt = StormAnatomyCandidateC.compile("supercell", make.apply(new float[]{.8f, 0, 0, 0, 0}), 20261008L);
        var wall = StormAnatomyCandidateC.compile("supercell", make.apply(new float[]{0, 0, 0, 0, 1}), 20261008L);
        float topN = trunkTopX(neutral), topT = trunkTopX(tilt);
        check(topT > topN + neutral.height() * .05f, "control_tilt_leans_updraft", String.format(Locale.ROOT, "neutral=%.0f tilt=%.0f", topN, topT));
        check(neutral.elements().stream().noneMatch(e -> e.role() == StormAnatomyCandidateC.Role.WALL)
                && wall.elements().stream().anyMatch(e -> e.role() == StormAnatomyCandidateC.Role.WALL), "control_wall_toggles_wall_cloud", "");
    }
    static float trunkTopX(StormAnatomyCandidateC.Program p) {
        for (var e : p.elements()) if (e.role() == StormAnatomyCandidateC.Role.TRUNK) return e.p()[4];
        return Float.NaN;
    }

    /** B v3 FULL geometry must still reproduce the frozen fingerprints (t257-baselines.json). */
    static void fingerprints() throws Exception {
        Path json = Path.of("specs/001-native-storm-rendering/validation/t257-convective-growth-field/t257-baselines.json");
        String text = Files.readString(json);
        int at = text.indexOf("\"geometry_fingerprints\"");
        Matcher m = Pattern.compile("\"([a-z_]+)/(\\d+)\":\\s*\\{\\s*\"signature\":\\s*\"([0-9a-f]+)\",\\s*\"texture_sha256\":\\s*\"([0-9a-f]+)\"").matcher(text.substring(at));
        int count = 0;
        while (m.find()) {
            var p = ConvectiveGrowthFieldPrototype.compile(m.group(1), shape(m.group(1)), Long.parseLong(m.group(2)));
            float[] t = p.textureData();
            ByteBuffer buf = ByteBuffer.allocate(t.length * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (float v : t) buf.putFloat(v);
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(buf.array()));
            boolean ok = Long.toUnsignedString(p.signature(), 16).equals(m.group(3)) && sha.equals(m.group(4));
            check(ok, "b_v3_fingerprint_unchanged", m.group(1) + "/" + m.group(2));
            count++;
        }
        check(count == 9, "b_v3_fingerprint_count", "count=" + count);
    }
}
