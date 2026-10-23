package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.morphology.CloudPreviewModels.Candidate;
import net.Gabou.projectatmosphere.clouds.morphology.CloudPreviewModels.Quality;
import net.Gabou.projectatmosphere.clouds.morphology.CloudPreviewModels.Request;
import net.Gabou.projectatmosphere.clouds.morphology.CloudPreviewModels.Scale;
import net.Gabou.projectatmosphere.clouds.morphology.StormMorphologyPrototype.Bounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * T260.11 smoke checks (short, not a campaign):
 * <pre>
 * plan seeds              group plans (extent, organisation, groups with birth / position / environment)
 * bake seeds stages [q]   t11 bakes: dimensions, groups, region / union cost
 * determinism seed stage  two cold bakes (region cache cleared) -> identical signatures
 * union seed stage        region union vs ONE monolithic bake of the min of the same placed group fields on the same
 *                         lattice: densities compared at every lattice sample (expected max diff 0); coarse-skip audit
 *                         of the largest region
 * </pre>
 */
public final class T26011Smoke {
    public static void main(String[] a) throws Exception {
        switch (a[0]) {
            case "plan" -> { for (String sd : a[1].split(",")) plan(Long.parseLong(sd)); }
            case "bake" -> {
                Quality q = a.length > 3 ? Quality.parse(a[3]) : Quality.NORMAL;
                for (String sd : a[1].split(",")) for (String st : a[2].split(",")) {
                    try { bake(Long.parseLong(sd), st, q); }
                    catch (IllegalStateException e) { System.out.println("T26011_BAKE seed=" + sd + " stage=" + st + " none: " + e.getMessage()); }
                }
            }
            case "determinism" -> determinism(Long.parseLong(a[1]), a[2]);
            case "union" -> union(Long.parseLong(a[1]), a[2], a.length > 3 ? Quality.parse(a[3]) : Quality.FAST);
            case "plan12" -> { for (String sd : a[1].split(",")) plan12(Long.parseLong(sd)); }
            case "bake12" -> {
                Candidate c = Candidate.parse(a[3]);
                for (String sd : a[1].split(",")) for (String st : a[2].split(",")) bake12(Long.parseLong(sd), st, c, a.length > 4 ? Quality.parse(a[4]) : Quality.NORMAL);
            }
            case "determinism12" -> {
                CloudPreviewModels.clearRegionCache();
                var x = bake12(Long.parseLong(a[1]), a[2], Candidate.T12, Quality.FAST);
                CloudPreviewModels.clearRegionCache();
                var y = bake12(Long.parseLong(a[1]), a[2], Candidate.T12, Quality.FAST);
                boolean same = x.groups().size() == y.groups().size();
                for (int i = 0; same && i < x.groups().size(); i++) same = x.groups().get(i).regionSignature() == y.groups().get(i).regionSignature();
                System.out.printf(Locale.ROOT, "T26012_DETERMINISM seed=%s stage=%s union=%s/%s regions=%s deterministic=%s%n", a[1], a[2], Long.toHexString(x.volume().signature()),
                        Long.toHexString(y.volume().signature()), same, same && x.volume().signature() == y.volume().signature());
            }
            case "union12" -> union12(Long.parseLong(a[1]), a[2], a.length > 3 ? Quality.parse(a[3]) : Quality.FAST);
            case "bake13" -> { for (String c : a[3].split(",")) bake12(Long.parseLong(a[1]), a[2], Candidate.parse(c), a.length > 4 ? Quality.parse(a[4]) : Quality.NORMAL); }
            case "render13" -> render13(Long.parseLong(a[1]), a[2], java.nio.file.Path.of(a[3]), a[4].split(","));
            case "union13" -> union13(Long.parseLong(a[1]), a[2], Candidate.parse(a[3]), a.length > 4 ? Quality.parse(a[4]) : Quality.FAST);
            case "growth13" -> growth13(Long.parseLong(a[1]), Candidate.parse(a[2]), T2608Preview.floats(a[3]));
            default -> throw new IllegalArgumentException(a[0]);
        }
    }

    static void plan(long seed) {
        var p = MulticellSystemT26011.plan(seed);
        System.out.println("T26011_PLAN seed=" + seed + " " + p.describe());
        for (var g : p.groups())
            System.out.printf(Locale.ROOT, "  G%-2d parent=%2d birth=%6.1f x=%7.0f z=%7.0f failed=%s %s%n", g.index(), g.parent(), g.birth(), g.x(), g.z(), g.failed(), g.env().describe());
    }

    static CloudPreviewModels.Baked bake(long seed, String stage, Quality q) {
        var r = new Request(Candidate.T11, seed, CloudPreviewModels.stage(Candidate.T11, stage), Scale.uniform(CloudPreviewModels.DEFAULT_SCALE), q);
        var b = CloudPreviewModels.bake(r);
        System.out.println(CloudPreviewModels.lastSystemLog);
        System.out.printf(Locale.ROOT, "T26011_BAKE seed=%d stage=%s t=%.0f cells=%d groups=%d W=%.0f D=%.0f H=%.0f (y %.0f..%.0f) model %.1fx%.1fx%.1fkm bricks=%d textureMB=%.1f lightSpacing=%.0f signature=%s%n",
                seed, stage, r.stage(), b.cells(), b.groups().size(), b.blocks().width(), b.blocks().depth(), b.blocks().height(), b.blocks().minY(), b.blocks().maxY(),
                b.model().width() / 1000, b.model().depth() / 1000, b.model().height() / 1000, b.volume().liveBricks(),
                b.volume().textureWidth() * (double) b.volume().textureHeight() * 16 / 1048576, CloudPreviewModels.lightSpacing(b.volume()), Long.toHexString(b.volume().signature()));
        for (var g : b.groups())
            System.out.printf(Locale.ROOT, "  G%-2d %-10s cells=%2d tau=%6.1f W=%4.0f D=%4.0f top=%4.0f centre=(%5.0f,%5.0f) cached=%s%n", g.index(), g.role(), g.cells(), g.localTime(),
                    g.blocks().width(), g.blocks().depth(), g.blocks().maxY(), g.blocks().centerX(), g.blocks().centerZ(), g.cached());
        return b;
    }

    static void determinism(long seed, String stage) {
        CloudPreviewModels.clearRegionCache();
        var a = bake(seed, stage, Quality.FAST);
        CloudPreviewModels.clearRegionCache();
        var b = bake(seed, stage, Quality.FAST);
        boolean groups = a.groups().size() == b.groups().size();
        for (int i = 0; groups && i < a.groups().size(); i++) groups = a.groups().get(i).regionSignature() == b.groups().get(i).regionSignature();
        System.out.printf(Locale.ROOT, "T26011_DETERMINISM seed=%d stage=%s union=%s/%s regions=%s deterministic=%s%n", seed, stage, Long.toHexString(a.volume().signature()),
                Long.toHexString(b.volume().signature()), groups, groups && a.volume().signature() == b.volume().signature());
    }

    static void plan12(long seed) {
        var p = MulticellSystemT26011.plan(seed);
        var cs = ConnectionsT26012.plan(p);
        System.out.println("T26012_PLAN seed=" + seed + " groups=" + p.groups().size() + " connections=" + cs.size());
        for (var c : cs) {
            StringBuilder sb = new StringBuilder();
            for (float t : new float[]{10, 30, 50, 75, 105}) sb.append(String.format(Locale.ROOT, " A(%.0f)=%.2f", t, c.at(t)));
            System.out.printf(Locale.ROOT, "  G%d-G%d threshold=%.2f merger=%s cells=%d%s%n", c.a(), c.b(), c.threshold(), c.merger(), c.cells().size(), sb);
            for (var cp : c.cells()) System.out.printf(Locale.ROOT, "     cell level=%.2f birth=%.1f decay=%s hPot=%.0f rPot=%.0f at (%.0f, %.0f)%n", cp.level(), cp.birth(),
                    cp.decay() > 1e8f ? "-" : String.format(Locale.ROOT, "%.1f", cp.decay()), cp.hPot(), cp.rPot(), cp.x(), cp.z());
        }
    }

    static CloudPreviewModels.Baked bake12(long seed, String stage, Candidate c, Quality q) {
        var r = new Request(c, seed, CloudPreviewModels.stage(c, stage), Scale.uniform(CloudPreviewModels.DEFAULT_SCALE), q);
        var b = CloudPreviewModels.bake(r);
        System.out.println(CloudPreviewModels.lastSystemLog);
        System.out.printf(Locale.ROOT, "T26012_BAKE %s seed=%d stage=%s cells=%d groups=%d W=%.0f D=%.0f H=%.0f (y %.0f..%.0f) bricks=%d signature=%s%n", c.id, seed, stage, b.cells(),
                b.groups().size(), b.blocks().width(), b.blocks().depth(), b.blocks().height(), b.blocks().minY(), b.blocks().maxY(), b.volume().liveBricks(), Long.toHexString(b.volume().signature()));
        for (var ci : b.connections())
            System.out.printf(Locale.ROOT, "  C G%d-G%d strength=%.2f live=%d/%d merger=%s at (%.0f, %.0f, %.0f)%n", ci.a(), ci.b(), ci.strength(), ci.liveCells(), ci.cells(), ci.merger(), ci.x(), ci.y(), ci.z());
        return b;
    }

    /** T260.12 region union against one monolithic bake of the min of the same region fields (connections included). */
    static void union12(long seed, String stage, Quality q) {
        CloudPreviewModels.clearRegionCache();
        var r = new Request(Candidate.T12, seed, CloudPreviewModels.stage(Candidate.T12, stage), Scale.uniform(CloudPreviewModels.DEFAULT_SCALE), q);
        var plan = MulticellSystemT26011.plan(seed);
        var cs = ConnectionsT26012.plan(plan);
        float saved = PersistentConvectionT2608.BAND;
        PersistentConvectionT2608.BAND = Math.max(saved, .75f * q.feather / r.scale().min());
        var regions = CloudPreviewModels.regions12(r, plan, cs);
        List<SparseBrickVolume> parts = new ArrayList<>();
        List<SparseBrickVolume.Field> fields = new ArrayList<>();
        float lip = 0;
        for (var rs : regions) {
            parts.add(new SparseBrickVolume(rs.field(), rs.domain(), q.voxel, q.feather, rs.lipschitz(), 0f));
            fields.add(rs.field());
            lip = Math.max(lip, rs.lipschitz());
        }
        var u = SparseBrickVolume.union(parts);
        SparseBrickVolume.Field min = new SparseBrickVolume.Field() {
            @Override public float distance(float x, float y, float z) { float d = Float.MAX_VALUE; for (var f : fields) d = Math.min(d, f.distance(x, y, z)); return d; }
            @Override public SparseBrickVolume.Field local(Bounds box) {
                List<SparseBrickVolume.Field> ls = new ArrayList<>();
                for (var f : fields) ls.add(f.local(box));
                return (x, y, z) -> { float d = Float.MAX_VALUE; for (var f : ls) d = Math.min(d, f.distance(x, y, z)); return d; };
            }
        };
        var mono = new SparseBrickVolume(min, u.bounds(), q.voxel, q.feather, lip, 0f);
        int audit = 0;
        for (int i = 0; i < parts.size(); i++) audit += parts.get(i).auditCoarseSkips(fields.get(i));
        PersistentConvectionT2608.BAND = saved;
        compare("T26012_UNION seed=" + seed + " stage=" + stage + " regions=" + parts.size() + " connections=" + cs.size() + " regionAuditBadSkips=" + audit, u, mono, q);
    }

    /** The first differing samples (> 0.05): union / monolithic densities and every region's own density and field value. */
    static void debugDiff(SparseBrickVolume u, SparseBrickVolume mono, List<SparseBrickVolume> parts, List<SparseBrickVolume.Field> fields, Quality q) {
        Bounds b = u.bounds();
        int[] g = u.grid();
        int shown = 0;
        for (int k = 0; k < g[2] && shown < 6; k++) for (int j = 0; j < g[1] && shown < 6; j++) for (int i = 0; i < g[0] && shown < 6; i++) {
            if (u.cellState(i, j, k) == SparseBrickVolume.EMPTY && mono.cellState(i, j, k) == SparseBrickVolume.EMPTY) continue;
            for (int c = 0; c < SparseBrickVolume.B && shown < 6; c++) for (int bb = 0; bb < SparseBrickVolume.B && shown < 6; bb++) for (int aa = 0; aa < SparseBrickVolume.B && shown < 6; aa++) {
                float x = b.minX() + (i * SparseBrickVolume.B + aa + .5f) * q.voxel, y = b.minY() + (j * SparseBrickVolume.B + bb + .5f) * q.voxel, z = b.minZ() + (k * SparseBrickVolume.B + c + .5f) * q.voxel;
                float du = u.density(x, y, z), dm = mono.density(x, y, z);
                if (Math.abs(du - dm) <= .05f) continue;
                shown++;
                StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "DIFF at (%.0f, %.0f, %.0f) union=%.3f mono=%.3f |", x, y, z, du, dm));
                for (int n = 0; n < parts.size(); n++) {
                    var pb = parts.get(n).bounds();
                    boolean in = pb.contains(x, y, z);
                    sb.append(String.format(Locale.ROOT, " r%d %s dens=%.3f field=%.2f", n, in ? "in" : "out", in ? parts.get(n).density(x, y, z) : 0f, fields.get(n).distance(x, y, z)));
                }
                System.out.println(sb);
            }
        }
    }

    static void compare(String head, SparseBrickVolume u, SparseBrickVolume mono, Quality q) {
        Bounds b = u.bounds();
        int[] g = u.grid();
        double maxDiff = 0;
        long compared = 0, differ = 0, visible = 0;
        for (int k = 0; k < g[2]; k++) for (int j = 0; j < g[1]; j++) for (int i = 0; i < g[0]; i++) {
            if (u.cellState(i, j, k) == SparseBrickVolume.EMPTY && mono.cellState(i, j, k) == SparseBrickVolume.EMPTY) continue;
            for (int c = 0; c < SparseBrickVolume.B; c++) for (int bb = 0; bb < SparseBrickVolume.B; bb++) for (int aa = 0; aa < SparseBrickVolume.B; aa++) {
                float x = b.minX() + (i * SparseBrickVolume.B + aa + .5f) * q.voxel, y = b.minY() + (j * SparseBrickVolume.B + bb + .5f) * q.voxel, z = b.minZ() + (k * SparseBrickVolume.B + c + .5f) * q.voxel;
                double d = Math.abs(u.density(x, y, z) - mono.density(x, y, z));
                compared++;
                if (d > 1e-6) differ++;
                if (d > .05) visible++;
                maxDiff = Math.max(maxDiff, d);
            }
        }
        System.out.printf(Locale.ROOT, "%s unionBricks=%d monoBricks=%d samples=%d differing=%d over0.05=%d maxDensityDiff=%.6f%n", head, u.liveBricks(), mono.liveBricks(), compared, differ, visible, maxDiff);
    }

    /** CPU renders (T260.10 preview marcher, sparse neutral light) of candidates at identical cameras around the lower mass. */
    static void render13(long seed, String stage, java.nio.file.Path out, String[] cands) throws Exception {
        java.nio.file.Files.createDirectories(out);
        double[] target = null;
        double ground = 0;
        for (String cn : cands) {
            Candidate c = Candidate.parse(cn);
            var b = bake12(seed, stage, c, Quality.NORMAL);
            if (target == null) {
                if (b.connections().isEmpty()) throw new IllegalStateException("first candidate has no lower mass");
                var ci = b.connections().get(0);
                target = new double[]{ci.x(), ci.y(), ci.z(), ci.axisX(), ci.axisZ()};
                ground = b.blocks().minY() - 40;
            }
            var light = new T26010Preview.SparseLight(b.volume(), T2606Preview.EXT);
            double px = -target[4], pz = target[3];
            if (px * -0.707 + pz * 0.707 < 0) { px = -px; pz = -pz; }
            double[][] cams = {
                    {target[0] + px * 700, ground + 10, target[2] + pz * 700, target[0], target[1] + 120, target[2], 70},
                    {target[0] + px * 1500 - pz * 900, target[1] + 900, target[2] + pz * 1500 + px * 900, target[0], target[1] + 150, target[2], 55},
                    {target[0] + px * 400, target[1] + 1500, target[2] + pz * 400, target[0], target[1], target[2], 70}};
            String[] names = {"ground", "three_quarter", "above"};
            for (int k = 0; k < cams.length; k++) {
                var img = T26010Preview.march(b.volume(), light, cams[k], 960, 540);
                T2606Preview.write(out.resolve(cn + "_" + names[k] + ".png"), img, 960, 540, "lit");
            }
        }
    }

    /** T260.13: union vs ONE monolithic bake of the min of every region field (lower-mass region included); audits. */
    static void union13(long seed, String stage, Candidate c, Quality q) {
        CloudPreviewModels.clearRegionCache();
        var r = new Request(c, seed, CloudPreviewModels.stage(c, stage), Scale.uniform(CloudPreviewModels.DEFAULT_SCALE), q);
        var plan = MulticellSystemT26011.plan(seed);
        float saved = PersistentConvectionT2608.BAND;
        PersistentConvectionT2608.BAND = Math.max(saved, .75f * q.feather / r.scale().min());
        var regions = CloudPreviewModels.regions12(r, plan, List.of());
        List<SparseBrickVolume> parts = new ArrayList<>();
        List<SparseBrickVolume.Field> fields = new ArrayList<>();
        float lip = 0;
        for (var rs : regions) {
            parts.add(new SparseBrickVolume(rs.field(), rs.domain(), q.voxel, q.feather, rs.lipschitz(), 0f));
            fields.add(rs.field());
            lip = Math.max(lip, rs.lipschitz());
        }
        PersistentConvectionT2608.BAND = Math.max(saved, (CloudPreviewModels.LM_BLEND + q.feather) / r.scale().min());
        var lr = CloudPreviewModels.lowerRegion(r, plan, regions);
        var lv = new SparseBrickVolume(lr.field(), lr.domain(), q.voxel, q.feather, CloudPreviewModels.LM_LIPSCHITZ, 0f);
        int lmAudit = lv.auditCoarseSkips(lr.field());
        parts.add(lv);
        fields.add(lr.field());
        lip = Math.max(lip, CloudPreviewModels.LM_LIPSCHITZ);
        var u = SparseBrickVolume.union(parts);
        SparseBrickVolume.Field min = new SparseBrickVolume.Field() {
            @Override public float distance(float x, float y, float z) { float d = Float.MAX_VALUE; for (var f : fields) d = Math.min(d, f.distance(x, y, z)); return d; }
            @Override public SparseBrickVolume.Field local(Bounds box) {
                List<SparseBrickVolume.Field> ls = new ArrayList<>();
                for (var f : fields) ls.add(f.local(box));
                return (x, y, z) -> { float d = Float.MAX_VALUE; for (var f : ls) d = Math.min(d, f.distance(x, y, z)); return d; };
            }
        };
        var mono = new SparseBrickVolume(min, u.bounds(), q.voxel, q.feather, lip, 0f);
        PersistentConvectionT2608.BAND = saved;
        var st = lv.stats();
        debugDiff(u, mono, parts, fields, q);
        compare(String.format(Locale.ROOT, "T26013_UNION %s seed=%d stage=%s regions=%d lowerMassCoarseSkipped=%d/%d lowerMassAuditBadSkips=%d lowerMassBricks=%d",
                c.id, seed, stage, parts.size(), st.coarseSkipped(), st.gridCells(), lmAudit, lv.liveBricks()), u, mono, q);
    }

    /** T260.13 persistence: the lower mass of one candidate at several system times (same seed): strength, parts, bricks. */
    static void growth13(long seed, Candidate c, float[] times) {
        var plan = MulticellSystemT26011.plan(seed);
        Quality q = Quality.FAST;
        for (float t : times) {
            var r = new Request(c, seed, t, Scale.uniform(CloudPreviewModels.DEFAULT_SCALE), q);
            float saved = PersistentConvectionT2608.BAND;
            PersistentConvectionT2608.BAND = Math.max(saved, (CloudPreviewModels.LM_BLEND + q.feather) / r.scale().min());
            var regions = CloudPreviewModels.regions12(r, plan, List.of());
            var lr = CloudPreviewModels.lowerRegion(r, plan, regions);
            if (lr == null) { PersistentConvectionT2608.BAND = saved; System.out.printf(Locale.ROOT, "T26013_GROWTH %s t=%.0f none%n", c.id, t); continue; }
            var lv = new SparseBrickVolume(lr.field(), lr.domain(), q.voxel, q.feather, CloudPreviewModels.LM_LIPSCHITZ, 0f);
            PersistentConvectionT2608.BAND = saved;
            var d = CloudPreviewModels.measure(lv);
            System.out.printf(Locale.ROOT, "T26013_GROWTH %s t=%.0f %s regionBricks=%d signature=%s%n", c.id, t, lr.spec().describe(), lv.liveBricks(), Long.toHexString(lv.signature()));
        }
    }

    /** Region union against one monolithic bake of the min of the same placed fields (same lattice, same settings). */
    static void union(long seed, String stage, Quality q) {
        CloudPreviewModels.clearRegionCache();
        Scale s = Scale.uniform(CloudPreviewModels.DEFAULT_SCALE);
        float t = CloudPreviewModels.stage(Candidate.T11, stage);
        var plan = MulticellSystemT26011.plan(seed);
        float cell = SparseBrickVolume.B * q.voxel;
        List<SparseBrickVolume.Field> fields = new ArrayList<>();
        List<SparseBrickVolume> parts = new ArrayList<>();
        float lip = 0;
        float saved = PersistentConvectionT2608.BAND;
        PersistentConvectionT2608.BAND = Math.max(saved, .75f * q.feather / s.min());
        SparseBrickVolume biggest = null;
        SparseBrickVolume.Field biggestField = null;
        for (var g : plan.groups()) {
            float tau = g.local(t);
            if (tau < -8) continue;
            var st = MulticellSystemT26011.scenario(g).at(tau);
            if (st.geoms.isEmpty()) continue;
            float ox = Math.round((g.x() - plan.env().windX() * tau) * s.horizontal() / cell) * cell, oz = Math.round((g.z() - plan.env().windZ() * tau) * s.horizontal() / cell) * cell;
            var f = st.field();
            lip = Math.max(lip, f.floor > ConvectiveVolumeT2607.FULL_SKIP_FLOOR ? 3f : 2.5f);
            var sf = new CloudPreviewModels.Scaled(f, s, ox, oz);
            fields.add(sf);
            var v = CloudPreviewModels.region(st, s, q, ox, oz);
            parts.add(v);
            if (biggest == null || v.liveBricks() > biggest.liveBricks()) { biggest = v; biggestField = sf; }
        }
        var u = SparseBrickVolume.union(parts);
        SparseBrickVolume.Field min = new SparseBrickVolume.Field() {
            @Override public float distance(float x, float y, float z) {
                float d = Float.MAX_VALUE;
                for (var f : fields) d = Math.min(d, f.distance(x, y, z));
                return d;
            }
            @Override public SparseBrickVolume.Field local(Bounds box) {
                List<SparseBrickVolume.Field> ls = new ArrayList<>();
                for (var f : fields) ls.add(f.local(box));
                return (x, y, z) -> { float d = Float.MAX_VALUE; for (var f : ls) d = Math.min(d, f.distance(x, y, z)); return d; };
            }
        };
        long m0 = System.nanoTime();
        var mono = new SparseBrickVolume(min, u.bounds(), q.voxel, q.feather, lip, 0f);
        double monoMs = (System.nanoTime() - m0) / 1e6;
        int audit = biggest.auditCoarseSkips(biggestField);
        PersistentConvectionT2608.BAND = saved;
        Bounds b = u.bounds();
        int[] g = u.grid();
        float vox = q.voxel;
        double maxDiff = 0;
        long compared = 0, differ = 0;
        for (int k = 0; k < g[2]; k++) for (int j = 0; j < g[1]; j++) for (int i = 0; i < g[0]; i++) {
            if (u.cellState(i, j, k) == SparseBrickVolume.EMPTY && mono.cellState(i, j, k) == SparseBrickVolume.EMPTY) continue;
            for (int c = 0; c < SparseBrickVolume.B; c++) for (int bb = 0; bb < SparseBrickVolume.B; bb++) for (int aa = 0; aa < SparseBrickVolume.B; aa++) {
                float x = b.minX() + (i * SparseBrickVolume.B + aa + .5f) * vox, y = b.minY() + (j * SparseBrickVolume.B + bb + .5f) * vox, z = b.minZ() + (k * SparseBrickVolume.B + c + .5f) * vox;
                double d = Math.abs(u.density(x, y, z) - mono.density(x, y, z));
                compared++;
                if (d > 1e-6) differ++;
                maxDiff = Math.max(maxDiff, d);
            }
        }
        System.out.printf(Locale.ROOT, "T26011_UNION seed=%d stage=%s regions=%d unionBricks=%d monoBricks=%d monoBakeMs=%.0f samples=%d differing=%d maxDensityDiff=%.6f regionAuditBadSkips=%d (largest region %d bricks)%n",
                seed, stage, parts.size(), u.liveBricks(), mono.liveBricks(), monoMs, compared, differ, maxDiff, audit, biggest.liveBricks());
    }
}
