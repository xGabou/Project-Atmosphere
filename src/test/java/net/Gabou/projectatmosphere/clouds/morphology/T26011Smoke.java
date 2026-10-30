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
            case "plan14" -> { for (String sd : a[1].split(",")) plan14(Long.parseLong(sd), T2608Preview.floats(a[2])); }
            case "render14" -> render14(Long.parseLong(a[1]), a[2], java.nio.file.Path.of(a[3]), a[4].split(","), a.length > 5 ? Quality.parse(a[5]) : Quality.NORMAL);
            case "union14" -> union14(Long.parseLong(a[1]), a[2], a.length > 3 ? Quality.parse(a[3]) : Quality.FAST, Candidate.T14);
            case "union15" -> union14(Long.parseLong(a[1]), a[2], a.length > 3 ? Quality.parse(a[3]) : Quality.FAST, Candidate.T15);
            case "union18" -> { for (String c : a[3].split(",")) union14(Long.parseLong(a[1]), a[2], a.length > 4 ? Quality.parse(a[4]) : Quality.FAST, Candidate.parse(c)); }
            case "growth18" -> { for (String c : a[3].split(",")) growth14(Long.parseLong(a[1]), T2608Preview.floats(a[2]), Candidate.parse(c)); }
            case "growth15" -> growth14(Long.parseLong(a[1]), T2608Preview.floats(a[2]), Candidate.T15);
            case "plan15" -> {
                for (String sd : a[1].split(",")) {
                    var p = MulticellSystemT26011.plan(Long.parseLong(sd));
                    System.out.println("T26015_PLAN seed=" + sd + " " + p.describe());
                    for (float t : T2608Preview.floats(a[2])) for (int v : new int[]{14, 15}) {
                        var sp = FoundationT26014.spec(p, t, v);
                        System.out.printf(Locale.ROOT, "  t=%.0f %s%n", t, sp == null ? "v" + v + " no foundation" : sp.describe());
                    }
                }
            }
            case "light16" -> { for (String c : a[3].split(",")) light16(Long.parseLong(a[1]), a[2], Candidate.parse(c), a.length > 4 ? java.nio.file.Path.of(a[4]) : null); }
            case "light18" -> light18(Long.parseLong(a[1]), a[2], Float.parseFloat(a[3]), a[4].split(","), a.length > 5 ? java.nio.file.Path.of(a[5]) : null);
            case "light17" -> { for (String c : a[3].split(",")) light17(Long.parseLong(a[1]), a[2], Candidate.parse(c)); }
            case "plan18" -> {
                for (String sd : a[1].split(",")) {
                    var p = MulticellSystemT26011.plan(Long.parseLong(sd));
                    for (float t : T2608Preview.floats(a[2])) for (int v : new int[]{15, FoundationT26014.V18A, FoundationT26014.V18B, FoundationT26014.V18C, FoundationT26014.V18D, FoundationT26014.V18E}) {
                        var sp = FoundationT26014.spec(p, t, v);
                        System.out.printf(Locale.ROOT, "T26018_PLAN seed=%s t=%.0f %s%n", sd, t, sp == null ? "v" + v + " no foundation" : sp.describe());
                    }
                }
            }
            case "metrics18n" -> { for (String sd : a[1].split(",")) for (String v : a[3].split(",")) metrics15(Long.parseLong(sd), Float.parseFloat(a[2]), Integer.parseInt(v)); }
            case "metrics18" -> { for (String sd : a[1].split(",")) for (int v : new int[]{14, 15, FoundationT26014.V18A, FoundationT26014.V18B, FoundationT26014.V18C, FoundationT26014.V18D, FoundationT26014.V18E}) metrics15(Long.parseLong(sd), Float.parseFloat(a[2]), v); }
            case "metrics15" -> { for (String sd : a[1].split(",")) for (int v : new int[]{14, 15}) metrics15(Long.parseLong(sd), Float.parseFloat(a[2]), v); }
            case "probe14" -> {
                var p = MulticellSystemT26011.plan(Long.parseLong(a[1]));
                var s = FoundationT26014.spec(p, Float.parseFloat(a[2]), a.length > 3 ? Integer.parseInt(a[3]) : 14);
                var f = FoundationT26014.field(s);
                for (var m : s.masses) {
                    StringBuilder sb = new StringBuilder();
                    for (float y = -200; y <= 4000; y += 200) sb.append(String.format(Locale.ROOT, " %.0f:%.0f", y, f.distance(m.cx, y, m.cz)));
                    System.out.printf(Locale.ROOT, "%s c=(%.0f,%.0f) R=%.0f el=%.2f T=%.0f base=%.0f |%s%n", m.label, m.cx, m.cz, m.R, m.elong, m.T, FoundationT26014.base(s, m.cx, m.cz), sb);
                }
            }
            case "slice14" -> {
                // vertical slice through the strongest link (x along the link axis), field sign / value -> PNG
                var p = MulticellSystemT26011.plan(Long.parseLong(a[1]));
                var s = FoundationT26014.spec(p, Float.parseFloat(a[2]), a.length > 4 ? Integer.parseInt(a[4]) : 14);
                var f = FoundationT26014.field(s);
                float[] fc = s.focus();
                int W = 1200, H = 300;
                float span = 24000, top = 6000;
                var img = new java.awt.image.BufferedImage(W, H, java.awt.image.BufferedImage.TYPE_INT_RGB);
                for (int i = 0; i < W; i++) for (int j = 0; j < H; j++) {
                    float u = (i / (float) W - .5f) * span, y = top * (1 - j / (float) H) + Float.parseFloat(System.getProperty("pa.slice.low", "-500"));
                    float d = f.distance(fc[0] + fc[3] * u, y, fc[2] + fc[4] * u);
                    int c = d < 0 ? 0xE0E0E0 : (Math.abs(d % 200) < 15 ? 0x404060 : 0x101020);
                    img.setRGB(i, j, c);
                }
                javax.imageio.ImageIO.write(img, "png", new java.io.File(a[3]));
            }
            case "under18" -> {
                // T260.18 geometry check: the foundation underside seen from directly below (first solid sample scanning up
                // per 100 m column), hillshaded from a low light; black = no foundation. Model metres.
                var p = MulticellSystemT26011.plan(Long.parseLong(a[1]));
                var s = FoundationT26014.spec(p, Float.parseFloat(a[2]), Integer.parseInt(a[4]));
                var f = FoundationT26014.field(s);
                var b = s.bounds();
                float step = 60, cx = .5f * (b.minX() + b.maxX()), cz = .5f * (b.minZ() + b.maxZ());
                int W = 640, H = 640;
                float[] hgt = new float[W * H];
                for (int i = 0; i < W; i++) for (int j = 0; j < H; j++) {
                    float x = cx + (i - W / 2f) * step, z = cz + (j - H / 2f) * step, y = b.minY();
                    float found = Float.NaN;
                    while (y < s.ybMax + 1500) {
                        float d = f.distance(x, y, z);
                        if (d < 0) { found = y; break; }
                        y += Math.max(25, .25f * d);
                    }
                    hgt[i + j * W] = found;
                }
                var img = new java.awt.image.BufferedImage(W, H, java.awt.image.BufferedImage.TYPE_INT_RGB);
                for (int i = 1; i < W - 1; i++) for (int j = 1; j < H - 1; j++) {
                    float h = hgt[i + j * W];
                    if (Float.isNaN(h)) continue;
                    float hx = hgt[i + 1 + j * W], hz = hgt[i + (j + 1) * W];
                    float gx = Float.isNaN(hx) ? 0 : (hx - h) / step, gz = Float.isNaN(hz) ? 0 : (hz - h) / step;
                    float lit = .55f + .45f * (float) Math.max(-1, Math.min(1, (gx * .8f + gz * .6f) * 2.5f));
                    float depth = Math.max(0, Math.min(1, (s.ybMax - h) / 2000f)); // darker = higher (recessed), lighter = hanging lower
                    int g = (int) (255 * Math.max(0, Math.min(1, lit * (.35f + .65f * depth))));
                    img.setRGB(i, j, g << 16 | g << 8 | g);
                }
                javax.imageio.ImageIO.write(img, "png", new java.io.File(a[3]));
            }
            case "lip14" -> {
                // Largest finite-difference gradient of the foundation field (blocks per block, scale 0.085) near its surface.
                var p = MulticellSystemT26011.plan(Long.parseLong(a[1]));
                var s = FoundationT26014.spec(p, Float.parseFloat(a[2]), a.length > 3 ? Integer.parseInt(a[3]) : 14);
                var f = new CloudPreviewModels.Scaled(FoundationT26014.field(s), Scale.uniform(CloudPreviewModels.DEFAULT_SCALE));
                var b = s.bounds();
                java.util.Random q = new java.util.Random(7);
                float sc = CloudPreviewModels.DEFAULT_SCALE, max = 0, h = 1.5f;
                double sum = 0; int n = 0;
                for (int k = 0; k < 4_000_000; k++) {
                    float x = (b.minX() + q.nextFloat() * (b.maxX() - b.minX())) * sc, y = (b.minY() + q.nextFloat() * (b.maxY() - b.minY())) * sc,
                            z = (b.minZ() + q.nextFloat() * (b.maxZ() - b.minZ())) * sc;
                    float d0 = f.distance(x, y, z);
                    if (Math.abs(d0) > 150) continue;
                    float gx = (f.distance(x + h, y, z) - d0) / h, gy = (f.distance(x, y + h, z) - d0) / h, gz = (f.distance(x, y, z + h) - d0) / h;
                    float g = (float) Math.sqrt(gx * gx + gy * gy + gz * gz);
                    if (g > 6 && n % 1 == 0 && k % 7 == 0) System.out.printf(Locale.ROOT, "  steep g=%.1f at model (%.0f, %.0f, %.0f) d=%.1f grad=(%.1f, %.1f, %.1f) topMax=%.0f%n", g, x / sc, y / sc, z / sc, d0, gx, gy, gz, s.topMax);
                    max = Math.max(max, g); sum += g; n++;
                }
                System.out.printf(Locale.ROOT, "T260" + s.version + "_LIP seed=%s t=%s samples=%d meanGrad=%.2f maxGrad=%.2f%n", a[1], a[2], n, sum / n, max);
            }
            case "growth14" -> growth14(Long.parseLong(a[1]), T2608Preview.floats(a[2]), Candidate.T14);
            case "cells15" -> {
                // T260.15 probe: live cells of every group (top, trunk width / axis at fractions of the top), model metres.
                var p = MulticellSystemT26011.plan(Long.parseLong(a[1]));
                float t = Float.parseFloat(a[2]);
                for (var g : p.groups()) {
                    float tau = g.local(t);
                    if (tau < 0) continue;
                    var st = MulticellSystemT26011.scenario(g).at(tau);
                    System.out.printf(Locale.ROOT, "G%d tau=%.0f cells=%d base=%.0f%n", g.index(), tau, st.geoms.size(), ConnectionsT26012.baseOffset(p, g));
                    for (var c : st.geoms) {
                        StringBuilder sb = new StringBuilder();
                        for (float f : new float[]{.05f, .2f, .35f, .5f, .7f, .9f}) {
                            float y = f * c.trunk.top;
                            sb.append(String.format(Locale.ROOT, " %.2f:w%.0f@(%.0f,%.0f)", f, c.trunk.width(y), c.trunk.axisX(y), c.trunk.axisZ(y)));
                        }
                        System.out.printf(Locale.ROOT, "  %s top=%.0f R=%.0f dd=%.2f em=%.2f yb=%.0f%s%n", c.spec.name, c.st[0], c.st[1], c.st[2], c.st[3], c.trunk.yb, sb);
                    }
                }
            }
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

    /** T260.14 organisation: groups, links (strength over time) and the foundation at the given times. */
    static void plan14(long seed, float[] times) {
        var p = MulticellSystemT26011.plan(seed);
        var o = FoundationT26014.organise(p);
        System.out.println("T26014_PLAN seed=" + seed + " " + p.describe() + " linksEverFormed=" + o.edges().size());
        for (var e : o.edges()) {
            StringBuilder sb = new StringBuilder();
            for (float t : new float[]{10, 30, 50, 75, 105}) sb.append(String.format(Locale.ROOT, " A(%.0f)=%.2f", t, e.at(t)));
            System.out.printf(Locale.ROOT, "  G%d-G%d threshold=%.2f pot0=%.2f formed=%.1f%s%n", e.a(), e.b(), e.threshold(), e.pot0(), e.formed(), sb);
        }
        for (float t : times) {
            var s = FoundationT26014.spec(p, t);
            System.out.printf(Locale.ROOT, "  t=%.0f %s%n", t, s == null ? "no foundation" : s.describe());
        }
    }

    /** CPU renders (T260.10 preview marcher, sparse neutral light) at identical SYSTEM-scale cameras (frozen from the first candidate). */
    static void render14(long seed, String stage, java.nio.file.Path out, String[] cands, Quality q) throws Exception {
        java.nio.file.Files.createDirectories(out);
        double[][] cams = null;
        String[] names = {"ground", "three_quarter", "eside", "above", "under", "ounder"};
        for (String cn : cands) {
            Candidate c = Candidate.parse(cn);
            var b = bake12(seed, stage, c, q);
            if (cams == null) {
                var d = b.blocks();
                double cx = d.centerX(), cz = d.centerZ(), w = Math.max(d.width(), d.depth()), h = d.height(), y0 = d.minY();
                double sx = -Math.sqrt(.5), sz = Math.sqrt(.5);
                // elevated side: across the system's long axis, sun side
                double ax = d.width() >= d.depth() ? 0 : 1, az = d.width() >= d.depth() ? 1 : 0;
                if (ax * sx + az * sz < 0) { ax = -ax; az = -az; }
                cams = new double[][]{
                        {cx + sx * (.5 * w + 800), y0 - 40, cz + sz * (.5 * w + 800), cx, y0 + .3 * h, cz, 70},
                        {cx + (sx * .8 - sz * .6) * (w + 700), y0 + .9 * h, cz + (sz * .8 + sx * .6) * (w + 700), cx, y0 + .25 * h, cz, 60},
                        {cx + ax * (.9 * w + 600), y0 + .45 * h, cz + az * (.9 * w + 600), cx, y0 + .12 * h, cz, 60},
                        {cx + sx * .35 * w, y0 + h + .9 * w, cz + sz * .35 * w, cx, y0, cz, 60},
                        // T260.15: straight up from below the foundation, and obliquely across its underside
                        {cx, y0 - 140, cz, cx + 1, y0 + 600, cz + 40, 100},
                        {cx - sx * .3 * w, y0 - 110, cz - sz * .3 * w, cx + sx * .2 * w, y0 + 40, cz + sz * .2 * w, 90}};
            }
            var light = new T26010Preview.SparseLight(b.volume(), T2606Preview.EXT);
            for (int k = 0; k < cams.length; k++) {
                var img = T26010Preview.march(b.volume(), light, cams[k], 960, 540);
                T2606Preview.write(out.resolve("s" + seed + "_" + cn + "_" + names[k] + ".png"), img, 960, 540, "lit");
            }
        }
    }

    /** T260.14: union vs ONE monolithic bake of the min of every region field (foundation region included); audits. */
    static void union14(long seed, String stage, Quality q, Candidate c) {
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
        PersistentConvectionT2608.BAND = Math.max(saved, (CloudPreviewModels.FND_BLEND + q.feather) / r.scale().min());
        var fr = CloudPreviewModels.foundationRegion(r, plan, regions);
        if (fr == null) { PersistentConvectionT2608.BAND = saved; System.out.println("T260" + CloudPreviewModels.foundationVersion(c) + "_UNION seed=" + seed + " stage=" + stage + " no foundation"); return; }
        long f0 = System.nanoTime();
        var fv = new SparseBrickVolume(fr.field(), fr.domain(), q.voxel, q.feather, CloudPreviewModels.fndLipschitz(c), 0f);
        double fMs = (System.nanoTime() - f0) / 1e6;
        int audit = fv.auditCoarseSkips(fr.field());
        parts.add(fv);
        fields.add(fr.field());
        lip = Math.max(lip, CloudPreviewModels.fndLipschitz(c));
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
        var st = fv.stats();
        debugDiff(u, mono, parts, fields, q);
        compare(String.format(Locale.ROOT, "T260" + CloudPreviewModels.foundationVersion(c) + "_UNION seed=%d stage=%s q=%s regions=%d foundationBakeMs=%.0f foundationCoarseSkipped=%d/%d foundationAuditBadSkips=%d foundationBricks=%d foundationMB=%.1f",
                seed, stage, q, parts.size(), fMs, st.coarseSkipped(), st.gridCells(), audit, fv.liveBricks(), fv.cpuBytes() / 1048576.0), u, mono, q);
    }

    /** T260.14 persistence: the foundation at several system times (same seed). Re-bakes t twice to check determinism. */
    static void growth14(long seed, float[] times, Candidate c) {
        var plan = MulticellSystemT26011.plan(seed);
        Quality q = Quality.FAST;
        for (float t : times) {
            long[] sig = new long[2];
            String desc = null;
            for (int rep = 0; rep < 2; rep++) {
                CloudPreviewModels.clearRegionCache();
                var r = new Request(c, seed, t, Scale.uniform(CloudPreviewModels.DEFAULT_SCALE), q);
                float saved = PersistentConvectionT2608.BAND;
                PersistentConvectionT2608.BAND = Math.max(saved, (CloudPreviewModels.FND_BLEND + q.feather) / r.scale().min());
                var regions = CloudPreviewModels.regions12(r, plan, List.of());
                var fr = CloudPreviewModels.foundationRegion(r, plan, regions);
                if (fr == null) { PersistentConvectionT2608.BAND = saved; break; }
                var fv = new SparseBrickVolume(fr.field(), fr.domain(), q.voxel, q.feather, CloudPreviewModels.fndLipschitz(c), 0f);
                PersistentConvectionT2608.BAND = saved;
                sig[rep] = fv.signature();
                desc = fr.spec().describe() + " regionBricks=" + fv.liveBricks();
            }
            System.out.printf(Locale.ROOT, "T260" + CloudPreviewModels.foundationVersion(c) + "_GROWTH seed=%d t=%.0f %s deterministic=%s%n", seed, t, desc == null ? "no foundation" : desc, desc == null || sig[0] == sig[1]);
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

    /**
     * T260.15 foundation metrics (model metres, the foundation field alone, 200 m columns): underside height relative to
     * the interpolated group base (spread, P5-P95 range, flat fraction within +-40 m of the median, mean slope between
     * neighbouring columns), thickness (P10 / P50 / P90, coefficient of variation), tower integration (per tall live
     * tower of an organised group: highest height whose ring at 1.15 x the trunk width is >= 25 % inside the
     * foundation, as a fraction of the top; crown contact = any ring point inside above 60 % of the top) and the outer
     * extensions' aspect ratios.
     */
    /** Interior underside: columns at least 1 km thick whose underside lies below base + INNER_MAX (T260.15: 600 m; 1400 m from T260.18, B raises the base). */
    static float INNER_MAX = Float.parseFloat(System.getProperty("pa.m15.inner", "600"));

    static void metrics15(long seed, float t, int version) {
        var p = MulticellSystemT26011.plan(seed);
        var s = FoundationT26014.spec(p, t, version);
        if (s == null) { System.out.printf(Locale.ROOT, "T26015_METRICS seed=%d t=%.0f v%d no foundation%n", seed, t, version); return; }
        var f = FoundationT26014.field(s);
        var b = s.bounds();
        float step = 200;
        int nx = (int) ((b.maxX() - b.minX()) / step), nz = (int) ((b.maxZ() - b.minZ()) / step);
        float[][] under = new float[nx][nz];
        List<Float> rel = new ArrayList<>(), thick = new ArrayList<>(), inner = new ArrayList<>();
        for (int i = 0; i < nx; i++) for (int k = 0; k < nz; k++) {
            float x = b.minX() + (i + .5f) * step, z = b.minZ() + (k + .5f) * step;
            under[i][k] = Float.NaN;
            float y = b.minY(), bot = Float.NaN, top = Float.NaN;
            while (y < b.maxY()) { float d = f.distance(x, y, z); if (d < 0) { bot = y; break; } y += Math.max(d / 3, 12); }
            if (Float.isNaN(bot)) continue;
            y = b.maxY();
            while (y > bot) { float d = f.distance(x, y, z); if (d < 0) { top = y; break; } y -= Math.max(d / 3, 12); }
            float r = bot - FoundationT26014.baseIdw(s, x, z);
            under[i][k] = r;
            rel.add(r);
            float th = (Float.isNaN(top) ? bot : top) - bot;
            thick.add(th);
            if (th >= 1000 && r < INNER_MAX) inner.add(r); // interior underside (not a flank overhang under a carve or dome)
        }
        double slope = 0; int ns = 0;
        for (int i = 0; i + 1 < nx; i++) for (int k = 0; k + 1 < nz; k++) {
            if (Float.isNaN(under[i][k])) continue;
            if (!Float.isNaN(under[i + 1][k])) { slope += Math.abs(under[i + 1][k] - under[i][k]); ns++; }
            if (!Float.isNaN(under[i][k + 1])) { slope += Math.abs(under[i][k + 1] - under[i][k]); ns++; }
        }
        // T260.18: local depth relief - range of the underside height within every 1 km window (5 x 5 columns), and the
        // share of columns hanging > 300 m below / raised > 300 m above the groups' interpolated base
        List<Float> local = new ArrayList<>();
        for (int i = 2; i + 2 < nx; i++) for (int k = 2; k + 2 < nz; k++) {
            if (Float.isNaN(under[i][k])) continue;
            float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE; int cnt = 0;
            for (int u = -2; u <= 2; u++) for (int v = -2; v <= 2; v++) { float r = under[i + u][k + v]; if (!Float.isNaN(r)) { lo = Math.min(lo, r); hi = Math.max(hi, r); cnt++; } }
            if (cnt >= 13) local.add(hi - lo);
        }
        local.sort(Float::compare);
        int deep = 0, raised = 0;
        for (float r : rel) { if (r < -300) deep++; if (r > 300) raised++; }
        System.out.printf(Locale.ROOT, "T26018_DEPTH seed=%d t=%.0f v%d columns=%d localRange1km P25=%.0f P50=%.0f P75=%.0f P90=%.0fm hanging(<-300m)=%.0f%% raised(>+300m)=%.0f%% lowest=%.0fm highest=%.0fm%n",
                seed, t, version, rel.size(), pct(local, .25f), pct(local, .5f), pct(local, .75f), pct(local, .9f), 100f * deep / Math.max(1, rel.size()), 100f * raised / Math.max(1, rel.size()),
                rel.stream().min(Float::compare).orElse(0f), rel.stream().max(Float::compare).orElse(0f));
        rel.sort(Float::compare); thick.sort(Float::compare); inner.sort(Float::compare);
        float imed = pct(inner, .5f);
        int iflat = 0; double iu = 0, i2 = 0;
        for (float r : inner) { if (Math.abs(r - imed) < 40) iflat++; iu += r; i2 += r * r; }
        iu /= Math.max(1, inner.size());
        double isd = Math.sqrt(Math.max(0, i2 / Math.max(1, inner.size()) - iu * iu));
        float med = pct(rel, .5f);
        int flat = 0; double mu = 0, m2 = 0, tm = 0, t2 = 0;
        for (float r : rel) { if (Math.abs(r - med) < 40) flat++; mu += r; m2 += r * r; }
        for (float r : thick) { tm += r; t2 += r * r; }
        int n = rel.size();
        mu /= n; tm /= n;
        double sd = Math.sqrt(Math.max(0, m2 / n - mu * mu)), tsd = Math.sqrt(Math.max(0, t2 / n - tm * tm));
        // tower integration
        var env = p.env();
        var o = FoundationT26014.organise(p);
        int[] gs = s.groups();
        List<Float> integ = new ArrayList<>(), tall = new ArrayList<>();
        int towers = 0, crown = 0, tallCrown = 0;
        for (int gi : gs) {
            var g = p.groups().get(gi);
            float tau = t - g.birth(), gx = g.x() - env.windX() * tau, gz = g.z() - env.windZ() * tau, oy = ConnectionsT26012.baseOffset(p, g);
            var st = o.scen().get(gi).at(tau);
            for (var c : st.geoms) {
                if (c.st[0] < 3500 || c.st[2] > .75f) continue;
                towers++;
                float best = 0;
                boolean touch = false;
                for (float h = 100; h < c.st[0]; h += 100) {
                    float ax = gx + c.trunk.axisX(h), az = gz + c.trunk.axisZ(h), w = 1.15f * c.trunk.width(h);
                    int in = 0;
                    for (int r = 0; r < 12; r++) {
                        double an = r * Math.PI / 6;
                        if (f.distance(ax + w * (float) Math.cos(an), oy + h, az + w * (float) Math.sin(an)) < 0) in++;
                    }
                    if (in >= 3) best = h;
                    if (in > 0 && h > .6f * c.st[0]) touch = true;
                }
                integ.add(best / c.st[0]);
                if (Boolean.getBoolean("pa.m15.debug") && c.st[0] > 9000) {
                    StringBuilder sb = new StringBuilder();
                    for (float h = 0; h < c.st[0]; h += 1000) {
                        float ax = gx + c.trunk.axisX(h), az = gz + c.trunk.axisZ(h), w = 1.15f * c.trunk.width(h);
                        sb.append(String.format(Locale.ROOT, " h%.0f:axis%.0f/ring%.0f", h, f.distance(ax, oy + h, az), f.distance(ax + w, oy + h, az)));
                    }
                    System.out.printf(Locale.ROOT, "  tower G%d %s top=%.0f best=%.0f%s%n", gi, c.spec.name, c.st[0], best, sb);
                }
                if (touch) crown++;
                if (c.st[0] >= 7000) { tall.add(best / c.st[0]); if (touch) tallCrown++; }
            }
        }
        integ.sort(Float::compare);
        float imean = 0; int i30 = 0;
        for (float v : integ) { imean += v; if (v >= .3f) i30++; }
        imean /= Math.max(1, integ.size());
        tall.sort(Float::compare);
        float tmean = 0;
        for (float v : tall) tmean += v;
        tmean /= Math.max(1, tall.size());
        StringBuilder ex = new StringBuilder();
        for (var m : s.masses) if (m.kind == 2) ex.append(String.format(Locale.ROOT, " %.2f/%.1f", m.elong, 2 * m.R * m.elong / m.T));
        StringBuilder ch = new StringBuilder();
        for (float[] e : s.extents) ch.append(String.format(Locale.ROOT, " %.1f/%.1f", e[0] / e[1], e[0] / e[2]));
        System.out.printf(Locale.ROOT, "T26015_METRICS seed=%d t=%.0f v%d columns=%d underside: sd=%.0fm P5..P95=%.0f..%.0fm range=%.0fm flat(+-40m)=%.0f%% meanStep=%.1fm/200m"
                        + " | interior underside (thick>=1km): n=%d sd=%.0fm P5..P95=%.0f..%.0fm flat(+-40m)=%.0f%%"
                        + " | thickness: P10=%.0f P50=%.0f P90=%.0fm cv=%.2f | integration: towers=%d mean=%.2f P50=%.2f max=%.2f >=0.3:%d crownContact=%d"
                        + " tall(>=7km)=%d mean=%.2f max=%.2f crownContact=%d"
                        + " | outer masses (plan elong / length:thickness)=[%s] chains (beyond rim length:width / length:thickness)=[%s]%n",
                seed, t, version, n, sd, pct(rel, .05f), pct(rel, .95f), pct(rel, .95f) - pct(rel, .05f), 100f * flat / n, slope / Math.max(1, ns),
                inner.size(), isd, pct(inner, .05f), pct(inner, .95f), 100f * iflat / Math.max(1, inner.size()),
                pct(thick, .1f), pct(thick, .5f), pct(thick, .9f), tsd / tm, towers, imean, pct(integ, .5f), integ.isEmpty() ? 0 : integ.get(integ.size() - 1), i30, crown,
                tall.size(), tmean, tall.isEmpty() ? 0 : tall.get(tall.size() - 1), tallCrown,
                ex.toString().trim(), ch.toString().trim());
    }

    // ---------------------------------------------------------------- T260.16 lighting attribution (CPU mirror)

    static final float EXT16 = .055f, EXPO16 = 3.2f, SUN16 = .25f; // ExtinctionScale (CELLS), exposure, E / (4 pi)

    /** Shader paNeutralDensity: the volume's density, 0 outside its bounds. */
    static float dens16(SparseBrickVolume v, Bounds b, float x, float y, float z) {
        if (x < b.minX() || y < b.minY() || z < b.minZ() || x > b.maxX() || y > b.maxY() || z > b.maxZ()) return 0;
        return v.density(x, y, z);
    }

    /** CPU mirror of the shader's mode 29 candidate at local (x, y, z); returns {radiance, tau, skin, normalY}. */
    static float[] candidate16(SparseBrickVolume v, Bounds b, NeutralLightVolume lv, float x, float y, float z, float[] light) {
        float tau = lv.opticalDepth(x, y, z, NeutralLightVolume.SUN, 4f, 1.5f, 64f, 14, 64f);
        float direct = SUN16 * (float) Math.exp(-light[0]);
        float ms = SUN16 * .6f * (1 - (float) Math.exp(-tau)) / (1 + .1125f * tau);
        float h = 3f;
        float gx = (dens16(v, b, x + h, y, z) - dens16(v, b, x - h, y, z)) / (2 * h);
        float gy = (dens16(v, b, x, y + h, z) - dens16(v, b, x, y - h, z)) / (2 * h);
        float gz = (dens16(v, b, x, y, z + h) - dens16(v, b, x, y, z - h)) / (2 * h);
        float m = (float) Math.sqrt(gx * gx + gy * gy + gz * gz);
        float nx = m > 1e-5f ? -gx / m : 0, ny = m > 1e-5f ? -gy / m : 0, nz = m > 1e-5f ? -gz / m : 0;
        float t = Math.max(0, Math.min(1, (m - .015f) / (.06f - .015f)));
        float skin = t * t * (3 - 2 * t);
        float[] up = norm(nx * .6f, ny * .6f + .8f, nz * .6f), dn = norm(nx * .6f, ny * .6f - .8f, nz * .6f);
        float vUp = (float) Math.exp(-lv.opticalDepth(x, y, z, up, 3f, 2f, 48f, 5, 8f));
        float vDn = (float) Math.exp(-lv.opticalDepth(x, y, z, dn, 3f, 2f, 48f, 5, 8f));
        float lambert = .9f * (NeutralLightVolume.SKY_L * vUp * (.5f + .5f * ny) + NeutralLightVolume.GROUND_L * vDn * (.5f - .5f * ny));
        float amb = light[1] + (lambert - light[1]) * skin;
        return new float[]{EXPO16 * (direct + ms + amb), tau, skin, ny};
    }

    static float[] norm(float x, float y, float z) { float l = (float) Math.sqrt(x * x + y * y + z * z); return new float[]{x / l, y / l, z / l}; }

    /**
     * T260.16: why the underside looks the same. Bakes the system (twice: determinism of the volume and the light
     * lattice), then renders every underside column with an orthographic camera looking straight UP, front to back with
     * the march's Beer-Lambert compositing (1-block steps) and CPU mirrors of the preview lighting: T260.5 neutral (baked
     * lattice), the enhanced-fill diagnostic and the T260.16 candidate. Reports the underside relief, what the visible
     * samples see (sun od, ambient), radiance spread / neighbour contrast / correlation with the base height; PNGs.
     */
    static void light16(long seed, String stage, Candidate c, java.nio.file.Path out) throws Exception {
        CloudPreviewModels.clearRegionCache();
        long t0 = System.nanoTime();
        var bk = bake12(seed, stage, c, Quality.NORMAL);
        double bakeS = (System.nanoTime() - t0) / 1e9;
        var v = bk.volume();
        Bounds b = v.bounds();
        float spacing = CloudPreviewModels.lightSpacing(v);
        t0 = System.nanoTime();
        var lv = new NeutralLightVolume(v, EXT16, spacing);
        double lightS = (System.nanoTime() - t0) / 1e9;
        long lsig = lv.signature(v.signature());
        var lv2 = new NeutralLightVolume(v, EXT16, spacing);
        CloudPreviewModels.clearRegionCache();
        var bk2 = CloudPreviewModels.bake(bk.request());
        var lv3 = new NeutralLightVolume(bk2.volume(), EXT16, spacing);
        System.out.printf(Locale.ROOT, "T26016_DETERMINISM %s seed=%d stage=%s volume=%s/%s light=%s/%s/%s identical=%s bakeS=%.1f lightS=%.1f %s%n", c.id, seed, stage,
                Long.toHexString(v.signature()), Long.toHexString(bk2.volume().signature()), Long.toHexString(lsig), Long.toHexString(lv2.signature(v.signature())),
                Long.toHexString(lv3.signature(bk2.volume().signature())), v.signature() == bk2.volume().signature() && lsig == lv2.signature(v.signature())
                        && lsig == lv3.signature(bk2.volume().signature()), bakeS, lightS, lv.stats());
        float step = 6f;
        int nxc = (int) ((b.maxX() - b.minX()) / step), nzc = (int) ((b.maxZ() - b.minZ()) / step);
        float lowest = Float.MAX_VALUE;
        float[] yb = new float[nxc * nzc];
        java.util.Arrays.fill(yb, Float.NaN);
        for (int k = 0; k < nzc; k++) for (int i = 0; i < nxc; i++) {
            float x = b.minX() + (i + .5f) * step, z = b.minZ() + (k + .5f) * step;
            for (float y = b.minY(); y < b.minY() + 160; y += 1f) if (v.density(x, y, z) > .5f) { yb[k * nxc + i] = y; lowest = Math.min(lowest, y); break; }
        }
        // underside: the column's first material within 100 blocks of the system's lowest point, solid 60 blocks above it
        float[][] L = new float[4][nxc * nzc];
        final float low = lowest;
        double[] odW = new double[nxc * nzc], ambW = new double[nxc * nzc], satW = new double[nxc * nzc], wsum = new double[nxc * nzc];
        java.util.stream.IntStream.range(0, nzc).parallel().forEach(k -> {
            for (int i = 0; i < nxc; i++) {
                int id = k * nxc + i;
                float y0 = yb[id];
                if (Float.isNaN(y0) || y0 > low + 100) { yb[id] = Float.NaN; continue; }
                float x = b.minX() + (i + .5f) * step, z = b.minZ() + (k + .5f) * step;
                if (v.density(x, y0 + 60, z) < .5f) { yb[id] = Float.NaN; continue; }
                float T = 1, accN = 0, accF = 0, accC = 0, accD = 0;
                for (float y = y0 - 8; y < y0 + 200 && T > .005f; y += 1f) {
                    float d = dens16(v, b, x, y + .5f, z);
                    if (d <= 0) continue;
                    float st = (float) Math.exp(-d * EXT16), w = T * (1 - st);
                    float[] light = lv.sample(x, y + .5f, z);
                    float direct = SUN16 * (float) Math.exp(-light[0]);
                    accN += w * EXPO16 * (direct + light[1]);
                    accF += w * EXPO16 * (direct + 3 * light[1]);
                    accC += w * candidate16(v, b, lv, x, y + .5f, z, light)[0];
                    accD += w * d;
                    odW[id] += w * light[0]; ambW[id] += w * light[1]; satW[id] += w * (light[0] >= 7.9f ? 1 : 0); wsum[id] += w;
                    T *= st;
                }
                L[0][id] = accN; L[1][id] = accF; L[2][id] = accC; L[3][id] = accD;
            }
        });
        List<Float> rel = new ArrayList<>();
        double sw = 0, so = 0, sa = 0, ss = 0;
        for (int id = 0; id < yb.length; id++) if (!Float.isNaN(yb[id])) { rel.add(yb[id] - low); sw += wsum[id]; so += odW[id]; sa += ambW[id]; ss += satW[id]; }
        rel.sort(Float::compare);
        double mr = rel.stream().mapToDouble(f -> f).average().orElse(0), sdr = Math.sqrt(rel.stream().mapToDouble(f -> (f - mr) * (f - mr)).average().orElse(0));
        System.out.printf(Locale.ROOT, "T26016_UNDERSIDE %s seed=%d columns=%d (6-block grid) base height above lowest: sd=%.1f P5..P95=%.0f..%.0f blocks | visible samples (transmittance-weighted): sunOd=%.2f odSaturated(>=7.9)=%.1f%% ambient=%.4f%n",
                c.id, seed, rel.size(), sdr, pct(rel, .05f), pct(rel, .95f), so / sw, 100 * ss / sw, sa / sw);
        String[] names = {"neutral(20)", "fill(26)", "candidate(29)", "density(21)"};
        for (int m = 0; m < 4; m++) {
            double s1 = 0, s2 = 0, nb = 0, nbn = 0, sxy = 0, sx = 0, sxx = 0;
            int n = 0;
            for (int k = 0; k < nzc; k++) for (int i = 0; i < nxc; i++) {
                int id = k * nxc + i;
                if (Float.isNaN(yb[id])) continue;
                double r = L[m][id], h = yb[id];
                s1 += r; s2 += r * r; n++;
                sx += h; sxx += h * h; sxy += h * r;
                if (i + 1 < nxc && !Float.isNaN(yb[id + 1])) { nb += Math.abs(r - L[m][id + 1]); nbn++; }
                if (k + 1 < nzc && !Float.isNaN(yb[id + nxc])) { nb += Math.abs(r - L[m][id + nxc]); nbn++; }
            }
            double mean = s1 / n, sd = Math.sqrt(Math.max(0, s2 / n - mean * mean));
            double corr = (sxy / n - sx / n * mean) / Math.sqrt(Math.max(1e-12, (sxx / n - sx / n * sx / n) * (s2 / n - mean * mean)));
            System.out.printf(Locale.ROOT, "  %-14s radiance mean=%.4f sd=%.4f cv=%.3f neighbourContrast=%.4f (rel %.3f) corr(baseHeight)=%+.2f display8bit sd=%.1f%n", names[m], mean, sd, sd / mean,
                    nb / nbn, nb / nbn / mean, corr, 255 * sd);
        }
        if (out != null) {
            java.nio.file.Files.createDirectories(out);
            String[] files = {"neutral", "fill", "candidate", "density", "baseheight"};
            for (int m = 0; m < 5; m++) {
                var img = new java.awt.image.BufferedImage(nxc, nzc, java.awt.image.BufferedImage.TYPE_INT_RGB);
                for (int k = 0; k < nzc; k++) for (int i = 0; i < nxc; i++) {
                    int id = k * nxc + i, rgb;
                    if (Float.isNaN(yb[id])) rgb = 0x203050;
                    else {
                        float val = m < 4 ? L[m][id] : Math.min(1, (yb[id] - low) / 60f);
                        int g8 = Math.max(0, Math.min(255, Math.round(255 * val)));
                        rgb = g8 * 0x010101;
                    }
                    img.setRGB(i, k, rgb);
                }
                javax.imageio.ImageIO.write(img, "png", out.resolve(String.format(Locale.ROOT, "cpu_up_%d_%s_%s.png", seed, c.id, files[m])).toFile());
            }
        }
    }

    /** T260.17: light bake with the directional block for both suns, twice each (determinism), cost and texture size. */
    static void light17(long seed, String stage, Candidate c) {
        CloudPreviewModels.clearRegionCache();
        var bk = bake12(seed, stage, c, Quality.NORMAL);
        var v = bk.volume();
        float spacing = CloudPreviewModels.lightSpacing(v);
        for (float elev : new float[]{35, 12}) {
            var base = new NeutralLightVolume(v, EXT16, spacing, NeutralLightVolume.sun(elev), false);
            var a1 = new NeutralLightVolume(v, EXT16, spacing, NeutralLightVolume.sun(elev), true);
            var a2 = new NeutralLightVolume(v, EXT16, spacing, NeutralLightVolume.sun(elev), true);
            System.out.printf(Locale.ROOT, "T26017_LIGHT %s seed=%d sun=%.0f identical=%s | without directional: %s | with: %s%n", c.id, seed, elev,
                    a1.signature(v.signature()) == a2.signature(v.signature()), base.stats(), a1.stats());
        }
        var t16 = new NeutralLightVolume(v, EXT16, spacing);
        System.out.printf(Locale.ROOT, "T26017_LIGHT %s seed=%d T260.16 default lattice: %s%n", c.id, seed, t16.stats());
    }


    // ---------------------------------------------------------------- T260.18 stage 2 lighting (CPU mirror)

    static final String[] L18 = {"neutral20", "graz38", "G", "GS", "LS", "LS0", "AO19", "GH", "A30", "Gc"};

    /** Unit direction at elevation el / azimuth az (degrees; azimuth from +z toward +x, the paNeutralMarched convention). */
    static float[] dir18(double elDeg, double azDeg) {
        double el = Math.toRadians(elDeg), az = Math.toRadians(azDeg);
        return new float[]{(float) (Math.cos(el) * Math.sin(az)), (float) Math.sin(el), (float) (Math.cos(el) * Math.cos(az))};
    }

    /**
     * Radiance of each T260.18 lighting model at local (x, y, z):
     * 0 T260.5 neutral (mode 20); 1 T260.17 D (mode 38, baked grazing quadrature on the baked normal);
     * 2 G: bounded grazing shadows - six rays at +-8 deg (alternating sky / ground, 60 deg apart, the first toward the sun
     *   azimuth) marched per sample over ~160 blocks, soft (wrapped) Lambert on the baked normal, 0.4 of the ambient;
     *   the baked +-30 deg ambient (sky / ground split on the same normal) supplies the other 0.6;
     * 3 GS: G whose ground rays see the ground's own sun shadow (albedo 0.2, sun x transmittance from the ground point).
     */
    static void models18(SparseBrickVolume v, Bounds b, NeutralLightVolume lv, float[] sun, float groundY, float x, float y, float z, float[] out, float[] cache, float[] gc) {
        float[] light = lv.sample(x, y, z);
        float direct = SUN16 * (float) Math.exp(-light[0]);
        out[0] = EXPO16 * (direct + light[1]);
        float[] dir = lv.directionalSample(x, y, z);
        float m = (float) Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
        float nx = m > 1e-3f ? dir[0] / m : 0, ny = m > 1e-3f ? dir[1] / m : 0, nz = m > 1e-3f ? dir[2] / m : 0;
        float skin = Math.min(1, 1.5f * m);
        float tau = dir[3];
        float ms = SUN16 * .6f * (1 - (float) Math.exp(-tau)) / (1 + .1125f * tau);
        float[] ud = lv.grazingSample(x, y, z);
        float lam = .9f * (ud[0] * (.5f + .5f * ny) + ud[1] * (.5f - .5f * ny));
        float amb38 = .5f * (ud[0] + ud[1]) + (lam - .5f * (ud[0] + ud[1])) * skin;
        out[1] = EXPO16 * (direct + ms + amb38);
        // the baked +-30 deg ambient on the normal (T260.17 directional ambient)
        float share = dir[4];
        float a30 = light[1] + (.9f * 2f * light[1] * (share * (.5f + .5f * ny) + (1 - share) * (.5f - .5f * ny)) - light[1]) * skin;
        double sunAz = Math.toDegrees(Math.atan2(sun[0], sun[2]));
        float g = 0, gs = 0;
        for (int k = 0; k < 6 && light[1] > .002f; k++) { // buried samples (ambient ~0) skip the rays, as the shader does
            boolean up = (k & 1) == 0;
            float[] d = dir18(up ? 8 : -8, sunAz + 60 * k);
            float vis = (float) Math.exp(-lv.opticalDepth(x, y, z, d, 3f, 1.7f, 64f, 7, 8f));
            float w = 1 + skin * (nx * d[0] + ny * d[1] + nz * d[2]);
            float L = up ? NeutralLightVolume.SKY_L : NeutralLightVolume.GROUND_L, Ls = L;
            if (!up) {
                float t = (groundY - y) / d[1];
                float gx = x + d[0] * t, gz = z + d[2] * t;
                float ts = (float) Math.exp(-lv.opticalDepth(gx, groundY, gz, sun, 2f, 1.4f, 40f, 72, 8f));
                Ls = .2f / (float) Math.PI * ((float) Math.PI * sun[1] * ts + (float) Math.PI * NeutralLightVolume.SKY_L);
            }
            g += L * vis * w;
            gs += Ls * vis * w;
        }
        g /= 6; gs /= 6;
        out[2] = EXPO16 * (direct + .6f * a30 + .4f * g);
        out[3] = EXPO16 * (direct + .6f * a30 + .4f * gs);
        // LS / LS0: Lambertian skin. Sky above from the baked share (T260.17 form); the lower hemisphere from nine marched
        // rays leaving the surface (q + 6 n): straight down, four at -40 deg, four grazing at -12 deg (sun azimuth + 90 k),
        // solid-angle weights 0.84 / 0.70 / 0.66 sr, cosine-weighted on the normal. Ground radiance: LS sees the ground's own
        // sun shadow (albedo 0.2), LS0 the T260.5 unshadowed 0.145. Evaluated once per surface (first skin sample of the
        // column, reused within 20 blocks) - the per-pixel bounded cost of the shader version.
        if (skin > .05f && (cache[3] == 0 || Math.abs(y - cache[1]) > 20)) {
            float ox = x + 6 * nx, oy = y + 6 * ny, oz = z + 6 * nz;
            float e = 0, e0 = 0;
            for (int k = 0; k < 9; k++) {
                float[] d = k == 0 ? new float[]{0, -1, 0} : dir18(k <= 4 ? -40 : -12, sunAz + 90 * ((k - 1) & 3) + (k <= 4 ? 45 : 0));
                float wgt = k == 0 ? .84f : k <= 4 ? .70f : .66f;
                float cos = Math.max(0, nx * d[0] + ny * d[1] + nz * d[2]);
                if (cos <= 0) continue;
                float vis = (float) Math.exp(-lv.opticalDepth(ox, oy, oz, d, 3f, 1.7f, 64f, 7, 8f));
                float t = (groundY - oy) / d[1];
                float gx = ox + d[0] * t, gz = oz + d[2] * t;
                float ts = (float) Math.exp(-lv.opticalDepth(gx, groundY, gz, sun, 2f, 1.4f, 40f, 72, 8f));
                float Lg = .2f / (float) Math.PI * ((float) Math.PI * sun[1] * ts + (float) Math.PI * NeutralLightVolume.SKY_L);
                e += Lg * vis * cos * wgt;
                e0 += NeutralLightVolume.GROUND_L * vis * cos * wgt;
            }
            // AO19 (upper-bound probe): 19 rays (nadir + rings of six at -60 / -30 / -10 deg), reach ~400 blocks, ground 0.145
            float e19 = 0;
            for (int k = 0; k < 19; k++) {
                float[] d = k == 0 ? new float[]{0, -1, 0} : dir18(k <= 6 ? -60 : k <= 12 ? -30 : -10, sunAz + 60 * ((k - 1) % 6) + 30 * (((k - 1) / 6) & 1));
                float wgt = k == 0 ? .21f : k <= 6 ? 2 * (float) Math.PI * (.966f - .707f) / 6 : k <= 12 ? 2 * (float) Math.PI * (.707f - .342f) / 6 : 2 * (float) Math.PI * .342f / 6;
                float cos = Math.max(0, nx * d[0] + ny * d[1] + nz * d[2]);
                if (cos <= 0) continue;
                float vis = (float) Math.exp(-lv.opticalDepth(ox, oy, oz, d, 3f, 1.6f, 80f, 10, 8f));
                e19 += NeutralLightVolume.GROUND_L * vis * cos * wgt;
            }
            cache[0] = x; cache[1] = y; cache[2] = z; cache[3] = 1; cache[4] = e; cache[5] = e0; cache[6] = e19;
        }
        float upper = 2f * light[1] * share * (.5f + .5f * ny);
        float ls = cache[3] == 0 ? light[1] : .9f * (upper + cache[4] / (float) Math.PI);
        float ls0 = cache[3] == 0 ? light[1] : .9f * (upper + cache[5] / (float) Math.PI);
        out[4] = EXPO16 * (direct + light[1] + (ls - light[1]) * skin);
        out[5] = EXPO16 * (direct + light[1] + (ls0 - light[1]) * skin);
        // GH: G with a hybrid sun shadow - a sharp per-sample march over the first ~120 blocks (2 x1.5 to 32, 9 steps)
        // continued by the baked (lattice) sun depth at its end point
        float near = 0, sx = 0, h = 2f;
        for (int k = 0; k < 9; k++) {
            float mid = sx + .5f * h;
            near += dens16(v, b, x + sun[0] * mid, y + sun[1] * mid, z + sun[2] * mid) * h;
            sx += h; h = Math.min(h * 1.5f, 32f);
        }
        float far = lv.sample(x + sun[0] * sx, y + sun[1] * sx, z + sun[2] * sx)[0];
        float directH = SUN16 * (float) Math.exp(-Math.min(8f, near * EXT16 + far));
        out[7] = EXPO16 * (directH + .6f * a30 + .4f * g);
        out[8] = EXPO16 * (direct + a30); // L-dir: mode 35, the baked directional ambient alone
        // Gc (L-graz-c, mode 41): G's six rays evaluated once per surface - at the first skin sample along the view ray,
        // reused within 20 blocks of it; samples without a surface fall back to the isotropic baked ambient for that 0.4
        if (skin > .05f && light[1] > .002f && (gc[3] == 0 || dist3(x, y, z, gc) > 20)) {
            float gg = 0;
            for (int k = 0; k < 6; k++) {
                boolean up = (k & 1) == 0;
                float[] d = dir18(up ? 8 : -8, sunAz + 60 * k);
                float vis = (float) Math.exp(-lv.opticalDepth(x, y, z, d, 3f, 1.7f, 64f, 7, 8f));
                gg += (up ? NeutralLightVolume.SKY_L : NeutralLightVolume.GROUND_L) * vis * (1 + skin * (nx * d[0] + ny * d[1] + nz * d[2]));
            }
            gc[0] = x; gc[1] = y; gc[2] = z; gc[3] = 1; gc[4] = gg / 6;
        }
        float gUse = gc[3] != 0 && dist3(x, y, z, gc) <= 20 ? gc[4] : light[1];
        out[9] = EXPO16 * (direct + .6f * a30 + .4f * gUse);
        float ao = cache[3] == 0 ? light[1] : .9f * (upper + cache[6] / (float) Math.PI);
        out[6] = EXPO16 * (direct + light[1] + (ao - light[1]) * skin);
    }

    /**
     * T260.18 stage 2: does a lighting model REVEAL the frozen D2 masses or only brighten them? Per candidate (identical
     * seed, stage, sun, ground plane, exposure), the underside seen straight up (orthographic, 6-block columns, 1-block
     * Beer-Lambert compositing like light16) under every model of {@link #models18}. Reports the radiance mean, the
     * relative mass-scale contrast (sd of the 96-block high-pass over the mean), its correlation with the high-passed
     * hanging depth (positive = lower masses brighter), and writes fixed-exposure PNGs.
     */
    static void light18(long seed, String stage, float elev, String[] cands, java.nio.file.Path out) throws Exception {
        float[] sun = NeutralLightVolume.sun(elev);
        float groundY = Float.NaN;
        for (String cs : cands) {
            Candidate c = Candidate.parse(cs);
            CloudPreviewModels.clearRegionCache();
            var bk = bake12(seed, stage, c, Quality.NORMAL);
            var v = bk.volume();
            Bounds b = v.bounds();
            float spacing = CloudPreviewModels.lightSpacing(v);
            long t0 = System.nanoTime();
            var lv = new NeutralLightVolume(v, EXT16, spacing, sun, true);
            System.out.printf(Locale.ROOT, "T26018_LIGHTBAKE %s seed=%d sun=%.0f %s (%.1f s)%n", c.id, seed, elev, lv.stats(), (System.nanoTime() - t0) / 1e9);
            float step = 6f;
            int nxc = (int) ((b.maxX() - b.minX()) / step), nzc = (int) ((b.maxZ() - b.minZ()) / step);
            float lowest = Float.MAX_VALUE;
            float[] yb = new float[nxc * nzc];
            java.util.Arrays.fill(yb, Float.NaN);
            for (int k = 0; k < nzc; k++) for (int i = 0; i < nxc; i++) {
                float x = b.minX() + (i + .5f) * step, z = b.minZ() + (k + .5f) * step;
                for (float y = b.minY(); y < b.minY() + 300; y += 1f) if (v.density(x, y, z) > .5f) { yb[k * nxc + i] = y; lowest = Math.min(lowest, y); break; }
            }
            if (Float.isNaN(groundY)) groundY = lowest - 80; // the first candidate fixes one ground plane for all
            final float low = lowest, gy = groundY;
            float[][] L = new float[L18.length][nxc * nzc];
            java.util.stream.IntStream.range(0, nzc).parallel().forEach(k -> {
                float[] r = new float[L18.length];
                for (int i = 0; i < nxc; i++) {
                    int id = k * nxc + i;
                    float y0 = yb[id];
                    if (Float.isNaN(y0) || y0 > low + 220) { yb[id] = Float.NaN; continue; }
                    float x = b.minX() + (i + .5f) * step, z = b.minZ() + (k + .5f) * step;
                    if (v.density(x, y0 + 60, z) < .5f) { yb[id] = Float.NaN; continue; }
                    float T = 1;
                    float[] acc = new float[L18.length], cache = new float[7], gc = new float[5];
                    for (float y = y0 - 8; y < y0 + 200 && T > .01f; y += 1f) {
                        float d = dens16(v, b, x, y + .5f, z);
                        if (d <= 0) continue;
                        float st = (float) Math.exp(-d * EXT16), w = T * (1 - st);
                        models18(v, b, lv, sun, gy, x, y + .5f, z, r, cache, gc);
                        for (int m = 0; m < r.length; m++) acc[m] += w * r[m];
                        T *= st;
                    }
                    for (int m = 0; m < r.length; m++) L[m][id] = acc[m];
                }
            });
            // 96-block high-pass of the hanging depth and of each radiance (box mean over valid columns, radius 16)
            int R = 16;
            float[] hp = highPass18(yb, null, nxc, nzc, R);
            boolean[] interior = new boolean[yb.length];
            for (int k = 0; k < nzc; k++) for (int i = 0; i < nxc; i++) {
                boolean ok = !Float.isNaN(yb[k * nxc + i]);
                for (int dk = -10; dk <= 10 && ok; dk++) for (int di = -10; di <= 10 && ok; di++) {
                    int kk = k + dk, ii = i + di;
                    if (kk < 0 || ii < 0 || kk >= nzc || ii >= nxc || Float.isNaN(yb[kk * nxc + ii])) ok = false;
                }
                interior[k * nxc + i] = ok;
            }
            for (int m = 0; m < L18.length; m++) {
                float[] lhp = highPass18(yb, L[m], nxc, nzc, R);
                double s1 = 0, s2 = 0, h1 = 0, h2 = 0, hl = 0, hh = 0;
                int n = 0;
                for (int id = 0; id < yb.length; id++) {
                    if (!interior[id] || Float.isNaN(lhp[id])) continue;
                    double l = L[m][id], a = lhp[id], dpt = -hp[id];
                    s1 += l; s2 += l * l; h1 += a; h2 += a * a; hl += a * dpt; hh += dpt * dpt; n++;
                }
                double mean = s1 / n, sd = Math.sqrt(Math.max(0, s2 / n - mean * mean)), hsd = Math.sqrt(Math.max(0, h2 / n - (h1 / n) * (h1 / n)));
                double corr = hl / n / Math.sqrt(Math.max(1e-12, h2 / n * hh / n));
                System.out.printf(Locale.ROOT, "T26018_L %s seed=%d sun=%.0f %-9s mean=%.4f (8bit %.0f) cv=%.3f interior: massContrast(hp96 sd/mean)=%.3f corr(hp radiance, hp hanging depth)=%+.2f columns=%d%n",
                        c.id, seed, elev, L18[m], mean, 255 * mean, sd / mean, hsd / mean, corr, n);
            }
            if (out != null) {
                java.nio.file.Files.createDirectories(out);
                for (int m = 0; m <= L18.length; m++) {
                    var img = new java.awt.image.BufferedImage(nxc, nzc, java.awt.image.BufferedImage.TYPE_INT_RGB);
                    for (int k = 0; k < nzc; k++) for (int i = 0; i < nxc; i++) {
                        int id = k * nxc + i, rgb;
                        if (Float.isNaN(yb[id])) rgb = 0x203050;
                        else {
                            float val = m < L18.length ? L[m][id] : Math.min(1, (yb[id] - low) / 220f);
                            int g8 = Math.max(0, Math.min(255, Math.round(255 * val)));
                            rgb = g8 * 0x010101;
                        }
                        img.setRGB(i, k, rgb);
                    }
                    javax.imageio.ImageIO.write(img, "png", out.resolve(String.format(Locale.ROOT, "up_%d_s%.0f_%s_%s.png", seed, elev, c.id, m < L18.length ? L18[m] : "height")).toFile());
                }
            }
        }
    }

    static float dist3(float x, float y, float z, float[] c) { float a = x - c[0], b = y - c[1], d = z - c[2]; return (float) Math.sqrt(a * a + b * b + d * d); }

    /** value (or the column height when values is null) minus its box mean over valid columns within radius R; NaN where invalid. */
    static float[] highPass18(float[] yb, float[] values, int nx, int nz, int R) {
        float[] src = new float[yb.length], outv = new float[yb.length];
        for (int id = 0; id < yb.length; id++) src[id] = Float.isNaN(yb[id]) ? Float.NaN : values == null ? yb[id] : values[id];
        float[] sx = new float[yb.length], cx = new float[yb.length];
        for (int k = 0; k < nz; k++) for (int i = 0; i < nx; i++) {
            float s = 0, c = 0;
            for (int d = -R; d <= R; d++) { int ii = i + d; if (ii < 0 || ii >= nx) continue; float val = src[k * nx + ii]; if (!Float.isNaN(val)) { s += val; c++; } }
            sx[k * nx + i] = s; cx[k * nx + i] = c;
        }
        for (int k = 0; k < nz; k++) for (int i = 0; i < nx; i++) {
            int id = k * nx + i;
            if (Float.isNaN(src[id])) { outv[id] = Float.NaN; continue; }
            float s = 0, c = 0;
            for (int d = -R; d <= R; d++) { int kk = k + d; if (kk < 0 || kk >= nz) continue; s += sx[kk * nx + i]; c += cx[kk * nx + i]; }
            outv[id] = c > 0 ? src[id] - s / c : Float.NaN;
        }
        return outv;
    }

    static float pct(List<Float> v, float q) { return v.isEmpty() ? Float.NaN : v.get(Math.min(v.size() - 1, Math.max(0, Math.round(q * (v.size() - 1))))); }
}
