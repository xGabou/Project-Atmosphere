package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.morphology.PersistentConvectionT2608.Scenario;
import net.Gabou.projectatmosphere.clouds.morphology.PersistentConvectionT2608.SystemState;
import net.Gabou.projectatmosphere.clouds.morphology.StormMorphologyPrototype.Bounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Experimental in-game cloud preview (never production, never weather / save / network authority): builds one of the
 * experimental morphology candidates for a seed and growth stage and bakes it at MINECRAFT-COMPRESSED dimensions.
 * <p>
 * The candidates model clouds in metres (1 model unit = 1 m). The preview maps them into blocks with a configurable
 * conversion: a horizontal and a vertical factor (blocks per metre). The default 0.085 in both axes keeps the model's
 * proportions and puts the experimental systems inside the initial visual targets:
 * <pre>
 *   individual cumulonimbus  300 - 800 blocks wide        (model ~3.5 - 9 km)
 *   multicell storm          1000 - 2500 blocks wide      (model ~12 - 29 km)
 *   deep Cb vertical extent  ~350 - 1000 blocks           (model ~4 - 12 km)
 * </pre>
 * The field is never evaluated at full scale: the bake samples the compressed lattice and maps every sample back into
 * the model ({@link Scaled}), so a 12 km system costs what a 1 km volume costs. Anisotropic factors keep the field
 * conservative by scaling distances with the smaller factor (the Lipschitz bound of the model field still holds).
 */
public final class CloudPreviewModels {
    private CloudPreviewModels() {}

    public static final float DEFAULT_SCALE = .085f;
    /** Largest compressed extent (blocks) a preview bake accepts; beyond it the request is refused, never baked. */
    public static final float MAX_EXTENT = 6000;
    /** Largest number of voxels in the compressed lattice. */
    public static final long MAX_VOXELS = 600_000_000L;

    public enum Candidate {
        T18A("t18a", "T260.18 A: T260.15 with deep asymmetric hanging masses instead of the shallow underside lobes"),
        T18AF("t18af", "T260.18 A foundation geometry alone, without the towers (diagnostic only)"),
        T18B("t18b", "T260.18 B: T260.15 with an uneven foundation depth (raised and lowered portions, deep recesses, open bays)"),
        T18BF("t18bf", "T260.18 B foundation geometry alone, without the towers (diagnostic only)"),
        T18C("t18c", "T260.18 C: T260.15 with integrated tower roots on a thinner lens (instead of the sleeves)"),
        T18CF("t18cf", "T260.18 C foundation geometry alone, without the towers (diagnostic only)"),
        T18D("t18d", "T260.18 D: broad compound hanging masses (asymmetric, merging into the base) with integrated tower roots"),
        T18DF("t18df", "T260.18 D foundation geometry alone, without the towers (diagnostic only)"),
        T18E("t18e", "T260.18 E: asymmetric continuously lowered base downshear of the towers, recesses, integrated tower roots"),
        T18EF("t18ef", "T260.18 E foundation geometry alone, without the towers (diagnostic only)"),
        T18D2("t18d2", "T260.18 D2: deeper broad compound hanging masses (up to 1.7 km, never a pendant) with integrated tower roots"),
        T18D2F("t18d2f", "T260.18 D2 foundation geometry alone, without the towers (diagnostic only)"),
        T19("t19", "Stabilisation S7a: D2 with a base that sags under each mass (no shared flat cut)"),
        T18F("t18g", "T260.18 F: D2 masses on the asymmetric lowered base of E, recesses, integrated tower roots"),
        T18FF("t18gf", "T260.18 F foundation geometry alone, without the towers (diagnostic only)"),
        T15("t15", "T260.15 shared foundation vertical anatomy: underside relief, tower sleeves, compact outer extensions"),
        T15F("t15f", "T260.15 foundation geometry alone, without the towers (diagnostic only)"),
        T14("t14", "T260.14 shared lower convective foundation under organised neighbouring groups"),
        T14F("t14f", "T260.14 foundation geometry alone, without the towers (diagnostic only)"),
        T13A("t13a", "T260.13 A: one broad, strongly irregular lower mass between the strongest connected pair"),
        T13B("t13b", "T260.13 B: several overlapping lower masses with partial gaps"),
        T13C("t13c", "T260.13 C: growth tongues from both groups meeting asymmetrically"),
        T12("t12", "T260.12 organic connections between T260.11 groups (not accepted; comparison)"),
        T12_OFF("t12off", "T260.12 framework with connections disabled (WITH / WITHOUT comparison)"),
        T11("t11", "T260.11 Minecraft-scale multicell organisation: persistent groups of T260.10 cells (experimental baseline)"),
        T10("t10", "T260.10 environment-driven vertical development (single cluster)"),
        T9("t9", "T260.9 storm-scale growth (accepted experimental baseline, shallow)");

        public final String id, description;
        Candidate(String id, String description) { this.id = id; this.description = description; }

        public static Candidate parse(String s) {
            for (Candidate c : values()) if (c.id.equalsIgnoreCase(s)) return c;
            throw new IllegalArgumentException("Unknown candidate '" + s + "' (t18d2, t18d2f, t18g, t18gf, t18d, t18df, t18e, t18ef, t18a, t18af, t18b, t18bf, t18c, t18cf, t15, t15f, t14, t14f, t13a, t13b, t13c, t12, t12off, t11, t10, t9)");
        }
    }

    /** Bake quality: voxel and density feather in blocks of the compressed preview. */
    public enum Quality {
        FAST(8, 16), NORMAL(6, 12), HIGH(4, 8);
        public final float voxel, feather;
        Quality(float voxel, float feather) { this.voxel = voxel; this.feather = feather; }
        public static Quality parse(String s) { return valueOf(s.toUpperCase(Locale.ROOT)); }
    }

    /** Model metres -> blocks. */
    public record Scale(float horizontal, float vertical) {
        public Scale {
            if (!(horizontal > 0 && horizontal <= 1 && vertical > 0 && vertical <= 1)) throw new IllegalArgumentException("scale factors must be in (0, 1]");
        }
        public float min() { return Math.min(horizontal, vertical); }
        public static Scale uniform(float s) { return new Scale(s, s); }
    }

    /**
     * Named growth stages per candidate (model minutes). T260.11 systems live longer than one cluster (groups keep
     * initiating), so their stages are later: initial 10, developing 30, growing 50, mature 75, late 105 (up to 150).
     */
    public static float stage(Candidate c, String s) {
        if (c == Candidate.T10 || c == Candidate.T9) return stage(s);
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "initial", "early" -> 10;
            case "developing" -> 30;
            case "growing" -> 50;
            case "mature" -> 75;
            case "late", "weakening" -> 105;
            default -> {
                float t = Float.parseFloat(s);
                if (t < -10 || t > MulticellSystemT26011.T_END) throw new IllegalArgumentException("stage minutes must be within -10..150");
                yield t;
            }
        };
    }

    /** Named growth stages (model minutes; the single-cluster candidates develop until t = 46). */
    public static float stage(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "initial", "early" -> 4;
            case "developing" -> 12;
            case "growing" -> 24;
            case "mature" -> 40;
            default -> {
                float t = Float.parseFloat(s);
                if (t < -8 || t > 46) throw new IllegalArgumentException("stage minutes must be within -8..46");
                yield t;
            }
        };
    }

    public record Request(Candidate candidate, long seed, float stage, Scale scale, Quality quality) {
        public String describe() {
            return String.format(Locale.ROOT, "%s seed=%d t=%.0f scale=%.3f/%.3f quality=%s", candidate.id, seed, stage, scale.horizontal(), scale.vertical(),
                    quality.name().toLowerCase(Locale.ROOT));
        }
    }

    /**
     * A baked preview: the compressed volume (local frame: x/z centred on the system, y = 0 at the cloud base), the
     * measured dimensions in blocks and the model's own dimensions in metres.
     */
    public record Baked(Request request, Program program, SparseBrickVolume volume, Dims blocks, Dims model, float baseAglMetres,
                        int cells, double buildMs, double bakeMs, String environment, List<GroupInfo> groups, double unionMs, int regionsBaked,
                        List<ConnectionInfo> connections, List<ComponentBox> components) {
        public Baked(Request request, Program program, SparseBrickVolume volume, Dims blocks, Dims model, float baseAglMetres, int cells, double buildMs,
                     double bakeMs, String environment, List<GroupInfo> groups, double unionMs, int regionsBaked) {
            this(request, program, volume, blocks, model, baseAglMetres, cells, buildMs, bakeMs, environment, groups, unionMs, regionsBaked, List.of(), List.of());
        }
        public Baked(Request request, Program program, SparseBrickVolume volume, Dims blocks, Dims model, float baseAglMetres, int cells, double buildMs,
                     double bakeMs, String environment, List<GroupInfo> groups, double unionMs, int regionsBaked, List<ConnectionInfo> connections) {
            this(request, program, volume, blocks, model, baseAglMetres, cells, buildMs, bakeMs, environment, groups, unionMs, regionsBaked, connections, List.of());
        }
    }

    /** T260.13: a labelled component box in the system block frame {minX, minY, minZ, maxX, maxY, maxZ}. */
    public record ComponentBox(String label, float[] box) {}

    /**
     * T260.12: one connection at the baked time (system block frame): strength, live / planned cells, centre of its
     * live cells and the pair axis (unit, blocks) for the close inspection view.
     */
    public record ConnectionInfo(int a, int b, float strength, int liveCells, int cells, boolean merger, float x, float y, float z, float axisX, float axisZ) {}

    /** T260.11: one convective group of a baked system (dimensions in the system's block frame). */
    public record GroupInfo(int index, String role, int cells, float localTime, Dims blocks, long regionSignature, boolean cached) {}

    /** Occupied extent (density >= 0.5). */
    public record Dims(float width, float depth, float minY, float maxY, float centerX, float centerZ) {
        public float height() { return maxY - minY; }
    }

    public static Scenario scenario(Candidate c, long seed) {
        PersistentConvectionT2608.VARIANT = 4; // both candidates use the C4 cells
        return switch (c) {
            case T10 -> VerticalGrowthT26010.build(seed, 46);
            case T9 -> SystemGrowthT2609.build(seed, 46);
            case T11, T12, T12_OFF, T13A, T13B, T13C, T14, T14F, T15, T15F, T18A, T18AF, T18B, T18BF, T18C, T18CF, T18D, T18DF, T18E, T18EF, T18D2, T18D2F, T18F, T18FF, T19 -> throw new IllegalArgumentException(c.id + " is a set of group scenarios (MulticellSystemT26011.plan)");
        };
    }

    /** Compressed extent of a request without baking (cheap: the analytic content box). */
    public static Bounds compressedBox(Request r) {
        SystemState st = scenario(r.candidate(), r.seed()).at(r.stage());
        return compress(st.bounds(), r.scale(), 0);
    }

    static Bounds compress(Bounds b, Scale s, float pad) {
        float base = PersistentConvectionT2608.BASE_Y;
        return new Bounds(b.minX() * s.horizontal() - pad, (b.minY() - base) * s.vertical() - pad, b.minZ() * s.horizontal() - pad,
                b.maxX() * s.horizontal() + pad, (b.maxY() - base) * s.vertical() + pad, b.maxZ() * s.horizontal() + pad);
    }

    /** Builds and bakes a preview. Thread: any (not the render thread); the T260.8 model statics are set during the bake. */
    public static synchronized Baked bake(Request r) {
        if (r.candidate() == Candidate.T11) return bakeSystem(r);
        if (r.candidate() == Candidate.T12 || r.candidate() == Candidate.T12_OFF) return bakeSystem12(r);
        if (r.candidate() == Candidate.T13A || r.candidate() == Candidate.T13B || r.candidate() == Candidate.T13C) return bakeSystem13(r);
        if (foundationVersion(r.candidate()) > 0) return bakeSystem14(r);
        long t0 = System.nanoTime();
        Scenario sc = scenario(r.candidate(), r.seed());
        SystemState st = sc.at(r.stage());
        if (st.geoms.isEmpty()) throw new IllegalStateException("no live cells at t=" + r.stage() + " (try a later stage)");
        PersistentConvectionT2608.Field field = st.field();
        Bounds content = st.bounds();
        Scale s = r.scale();
        // The model's blend margin (pair blends up to KMAX, relief, warp) lies outside the content box.
        Bounds model = new Bounds(content.minX() - 260, content.minY() - 40, content.minZ() - 260, content.maxX() + 260, content.maxY() + 260, content.maxZ() + 260);
        Bounds domain = compress(model, s, 2 * r.quality().voxel);
        float ex = Math.max(domain.maxX() - domain.minX(), Math.max(domain.maxY() - domain.minY(), domain.maxZ() - domain.minZ()));
        double voxels = (double) (domain.maxX() - domain.minX()) * (domain.maxY() - domain.minY()) * (domain.maxZ() - domain.minZ()) / Math.pow(r.quality().voxel, 3);
        if (ex > MAX_EXTENT || voxels > MAX_VOXELS) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "refused: compressed extent %.0f blocks / %.0fM voxels exceeds the preview limit (%.0f blocks / %dM); lower the scale or the quality",
                    ex, voxels / 1e6, MAX_EXTENT, MAX_VOXELS / 1_000_000));
        }
        double buildMs = (System.nanoTime() - t0) / 1e6;
        // Lipschitz bound of the model field (as PersistentConvectionT2608.bake): scaling by the smaller factor keeps it.
        float lip = field.floor > ConvectiveVolumeT2607.FULL_SKIP_FLOOR ? 3f : 2.5f;
        // The model evaluates its distance exactly only within BAND (+12) metres of the surface; the compressed density
        // ramp spans +-feather/2 blocks, i.e. feather / (2 min-scale) metres: widen the exact band to cover it.
        float savedBand = PersistentConvectionT2608.BAND;
        long t1 = System.nanoTime();
        SparseBrickVolume v;
        try {
            PersistentConvectionT2608.BAND = Math.max(savedBand, .75f * r.quality().feather / s.min());
            v = new SparseBrickVolume(new Scaled(field, s), domain, r.quality().voxel, r.quality().feather, lip, 0f);
        } finally {
            PersistentConvectionT2608.BAND = savedBand;
        }
        double bakeMs = (System.nanoTime() - t1) / 1e6;
        Dims blocks = measure(v);
        Dims metres = new Dims(blocks.width() / s.horizontal(), blocks.depth() / s.horizontal(), blocks.minY() / s.vertical(), blocks.maxY() / s.vertical(),
                blocks.centerX() / s.horizontal(), blocks.centerZ() / s.horizontal());
        var env = SystemGrowthT2609.env(r.seed());
        var profile = VerticalGrowthT26010.profile(env);
        return new Baked(r, new Program(v), v, blocks, metres, profile.baseAgl(), st.geoms.size(), buildMs, bakeMs, env.describe() + " " + profile.describe(),
                List.of(), 0, 1);
    }

    // ================================================================ T260.11 region volumes

    /** Region volumes of individual groups, keyed by group identity, local time, scale, quality and placement. */
    private static final java.util.Map<String, SparseBrickVolume> REGIONS = new java.util.LinkedHashMap<>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, SparseBrickVolume> e) { return size() > 96; }
    };

    /** Drops every cached region volume (smoke checks: a cold re-bake must reproduce the same signatures). */
    static synchronized void clearRegionCache() { REGIONS.clear(); }

    /** Last T260.11 bake summary line (logged by the caller). */
    public static volatile String lastSystemLog = "";

    /**
     * T260.11: every live group is baked as its OWN region volume (its own cells only, its own domain) on one shared
     * global lattice, cached per group; the renderer then receives their brick union (no field evaluation, see
     * {@link SparseBrickVolume#union}). Gaps between groups cost index entries only.
     */
    static Baked bakeSystem(Request r) {
        long t0 = System.nanoTime();
        Scale s = r.scale();
        Quality q = r.quality();
        float cell = SparseBrickVolume.B * q.voxel;
        var plan = MulticellSystemT26011.plan(r.seed());
        List<SparseBrickVolume> parts = new ArrayList<>();
        List<GroupInfo> infos = new ArrayList<>();
        int cells = 0, baked = 0;
        double buildMs = 0, bakeMs = 0;
        float savedBand = PersistentConvectionT2608.BAND;
        try {
            PersistentConvectionT2608.BAND = Math.max(savedBand, .75f * q.feather / s.min());
            for (var g : plan.groups()) {
                float tau = g.local(r.stage());
                if (tau < -8) continue; // not initiated yet
                long b0 = System.nanoTime();
                SystemState st = MulticellSystemT26011.scenario(g).at(tau);
                buildMs += (System.nanoTime() - b0) / 1e6;
                if (st.geoms.isEmpty()) continue; // dissipated
                // Placement in the steering-wind frame (the group's own cells drift with the shared wind).
                float ox = (g.x() - plan.env().windX() * tau) * s.horizontal(), oz = (g.z() - plan.env().windZ() * tau) * s.horizontal();
                ox = Math.round(ox / cell) * cell; // whole lattice cells: region bricks align with every other region
                oz = Math.round(oz / cell) * cell;
                String key = String.format(Locale.ROOT, "%x/%.3f/%.4f/%.4f/%s/%.0f/%.0f", g.seed(), tau, s.horizontal(), s.vertical(), q, ox, oz);
                SparseBrickVolume v = REGIONS.get(key);
                boolean cached = v != null;
                if (!cached) {
                    long b1 = System.nanoTime();
                    v = region(st, s, q, ox, oz);
                    bakeMs += (System.nanoTime() - b1) / 1e6;
                    REGIONS.put(key, v);
                    baked++;
                }
                parts.add(v);
                cells += st.geoms.size();
                infos.add(new GroupInfo(g.index(), role(g, tau, st), st.geoms.size(), tau, measure(v), v.signature(), cached));
            }
        } finally {
            PersistentConvectionT2608.BAND = savedBand;
        }
        if (parts.isEmpty()) throw new IllegalStateException("no live groups at t=" + r.stage() + " (try a later stage)");
        long u0 = System.nanoTime();
        SparseBrickVolume v = SparseBrickVolume.union(parts);
        double unionMs = (System.nanoTime() - u0) / 1e6;
        float ex = Math.max(v.bounds().maxX() - v.bounds().minX(), v.bounds().maxZ() - v.bounds().minZ());
        if (ex > MAX_EXTENT) throw new IllegalArgumentException(String.format(Locale.ROOT, "refused: compressed extent %.0f blocks exceeds the preview limit (%.0f)", ex, MAX_EXTENT));
        Dims blocks = measure(v);
        Dims metres = new Dims(blocks.width() / s.horizontal(), blocks.depth() / s.horizontal(), blocks.minY() / s.vertical(), blocks.maxY() / s.vertical(),
                blocks.centerX() / s.horizontal(), blocks.centerZ() / s.horizontal());
        lastSystemLog = String.format(Locale.ROOT, "T26011_SYSTEM %s %s live=%d regionsBaked=%d buildMs=%.0f bakeMs=%.0f unionMs=%.0f totalMs=%.0f",
                r.describe(), plan.describe(), parts.size(), baked, buildMs, bakeMs, unionMs, (System.nanoTime() - t0) / 1e6);
        return new Baked(r, new Program(v), v, blocks, metres, plan.profile().baseAgl(), cells, buildMs, bakeMs, plan.describe() + " " + plan.profile().describe(),
                infos, unionMs, baked);
    }

    // ================================================================ T260.12 connected regions

    /** One group region of a T260.12 bake (exposed for the smoke checks). */
    record RegionSpec(MulticellSystemT26011.Group group, float tau, SystemState state, Scaled field, Bounds domain, float lipschitz, String key) {}

    /**
     * T260.12 regions: one per live group (its cells plus the cells of its connections), at the EXACT placement (only
     * the domain is snapped to the shared lattice, so connection cells duplicated in two regions coincide exactly) and at
     * the group's own cloud base.
     */
    static List<RegionSpec> regions12(Request r, MulticellSystemT26011.Plan plan, List<ConnectionsT26012.Connection> cs) {
        Scale s = r.scale();
        Quality q = r.quality();
        float cell = SparseBrickVolume.B * q.voxel;
        List<RegionSpec> out = new ArrayList<>();
        for (var g : plan.groups()) {
            float tau = g.local(r.stage());
            if (tau < -8) continue;
            SystemState st = MulticellSystemT26011.scenario(g, plan, cs).at(tau);
            if (st.geoms.isEmpty()) continue;
            float ox = (g.x() - plan.env().windX() * tau) * s.horizontal(), oz = (g.z() - plan.env().windZ() * tau) * s.horizontal();
            float oy = ConnectionsT26012.baseOffset(plan, g) * s.vertical();
            PersistentConvectionT2608.Field field = st.field();
            Bounds content = st.bounds();
            Bounds model = new Bounds(content.minX() - 260, content.minY() - 40, content.minZ() - 260, content.maxX() + 260, content.maxY() + 260, content.maxZ() + 260);
            Bounds c = compress(model, s, 2 * q.voxel);
            Bounds dom = new Bounds((float) Math.floor((c.minX() + ox) / cell) * cell, (float) Math.floor((c.minY() + oy) / cell) * cell,
                    (float) Math.floor((c.minZ() + oz) / cell) * cell, (float) Math.ceil((c.maxX() + ox) / cell) * cell, (float) Math.ceil((c.maxY() + oy) / cell) * cell,
                    (float) Math.ceil((c.maxZ() + oz) / cell) * cell);
            float lip = field.floor > ConvectiveVolumeT2607.FULL_SKIP_FLOOR ? 3f : 2.5f;
            String key = String.format(Locale.ROOT, "t12/%s/%x/%.3f/%.4f/%.4f/%s/%.3f/%.3f/%.3f", cs.isEmpty() ? "off" : "on", g.seed(), tau, s.horizontal(), s.vertical(), q, ox, oy, oz);
            out.add(new RegionSpec(g, tau, st, new Scaled(field, s, ox, oy, oz), dom, lip, key));
        }
        return out;
    }

    static Baked bakeSystem12(Request r) {
        long t0 = System.nanoTime();
        Scale s = r.scale();
        Quality q = r.quality();
        var plan = MulticellSystemT26011.plan(r.seed());
        List<ConnectionsT26012.Connection> cs = r.candidate() == Candidate.T12 ? ConnectionsT26012.plan(plan) : List.of();
        List<SparseBrickVolume> parts = new ArrayList<>();
        List<GroupInfo> infos = new ArrayList<>();
        int cells = 0, baked = 0;
        double buildMs, bakeMs = 0;
        float savedBand = PersistentConvectionT2608.BAND;
        try {
            PersistentConvectionT2608.BAND = Math.max(savedBand, .75f * q.feather / s.min());
            long b0 = System.nanoTime();
            List<RegionSpec> regions = regions12(r, plan, cs);
            buildMs = (System.nanoTime() - b0) / 1e6;
            for (RegionSpec rs : regions) {
                SparseBrickVolume v = REGIONS.get(rs.key());
                boolean cached = v != null;
                if (!cached) {
                    long b1 = System.nanoTime();
                    v = new SparseBrickVolume(rs.field(), rs.domain(), q.voxel, q.feather, rs.lipschitz(), 0f);
                    bakeMs += (System.nanoTime() - b1) / 1e6;
                    REGIONS.put(rs.key(), v);
                    baked++;
                }
                parts.add(v);
                int own = 0;
                for (var gc : rs.state().geoms) if (!gc.spec.name.startsWith("X")) own++;
                cells += own;
                infos.add(new GroupInfo(rs.group().index(), own == 0 ? "connection-only" : role(rs.group(), rs.tau(), rs.state()), own, rs.tau(), measure(v), v.signature(), cached));
            }
        } finally {
            PersistentConvectionT2608.BAND = savedBand;
        }
        if (parts.isEmpty()) throw new IllegalStateException("no live groups at t=" + r.stage() + " (try a later stage)");
        long u0 = System.nanoTime();
        SparseBrickVolume v = SparseBrickVolume.union(parts);
        double unionMs = (System.nanoTime() - u0) / 1e6;
        float ex = Math.max(v.bounds().maxX() - v.bounds().minX(), v.bounds().maxZ() - v.bounds().minZ());
        if (ex > MAX_EXTENT) throw new IllegalArgumentException(String.format(Locale.ROOT, "refused: compressed extent %.0f blocks exceeds the preview limit (%.0f)", ex, MAX_EXTENT));
        // Connections at this time (each counted once, though its cells live in two regions).
        List<ConnectionInfo> ci = new ArrayList<>();
        int connCells = 0;
        for (var c : cs) {
            int live = 0;
            float sx = 0, sz = 0, sh = 0;
            for (var cp : c.cells()) {
                if (cp.birth() > r.stage() || r.stage() > cp.decay() + 14) continue;
                live++;
                sx += cp.x(); sz += cp.z(); sh += cp.hPot();
            }
            connCells += live;
            var ga = plan.groups().get(c.a()); var gb = plan.groups().get(c.b());
            float ax = gb.x() - ga.x(), az = gb.z() - ga.z(), al = (float) Math.hypot(ax, az);
            float base = .5f * (ConnectionsT26012.baseOffset(plan, ga) + ConnectionsT26012.baseOffset(plan, gb));
            if (live > 0) ci.add(new ConnectionInfo(c.a(), c.b(), c.at(r.stage()), live, c.cells().size(), c.merger(), sx / live * s.horizontal(),
                    (base + .5f * sh / live) * s.vertical(), sz / live * s.horizontal(), ax / al, az / al));
        }
        Dims blocks = measure(v);
        Dims metres = new Dims(blocks.width() / s.horizontal(), blocks.depth() / s.horizontal(), blocks.minY() / s.vertical(), blocks.maxY() / s.vertical(),
                blocks.centerX() / s.horizontal(), blocks.centerZ() / s.horizontal());
        lastSystemLog = String.format(Locale.ROOT, "T26012_SYSTEM %s %s live=%d regionsBaked=%d connectionsPlanned=%d connectionsLive=%d connectionCellsLive=%d buildMs=%.0f bakeMs=%.0f unionMs=%.0f totalMs=%.0f",
                r.describe(), plan.describe(), parts.size(), baked, cs.size(), ci.size(), connCells, buildMs, bakeMs, unionMs, (System.nanoTime() - t0) / 1e6);
        return new Baked(r, new Program(v), v, blocks, metres, plan.profile().baseAgl(), cells, buildMs, bakeMs, plan.describe() + " " + plan.profile().describe(),
                infos, unionMs, baked, ci);
    }

    // ================================================================ T260.13 lower-mass region

    public static LowerMassT26013.Variant variant(Candidate c) {
        return c == Candidate.T13A ? LowerMassT26013.Variant.A : c == Candidate.T13B ? LowerMassT26013.Variant.B : LowerMassT26013.Variant.C;
    }

    /** Smooth-union blend of the lower mass with the two groups' region fields (blocks). */
    static final float LM_BLEND = 22;

    /**
     * T260.13 lower-mass region: its own volume whose field is min(smin(lower mass, group a), smin(lower mass, group b)) (exact group region
     * fields, blocks), so the component grows INTO the towers' bases with a fillet. Outside the blend range the region
     * equals min(group a, group b), i.e. exactly what the group regions hold: no seam at its boundary.
     */
    record LowerRegion(LowerMassT26013.Spec spec, SparseBrickVolume.Field field, Bounds domain, String key) {}

    static LowerRegion lowerRegion(Request r, MulticellSystemT26011.Plan plan, List<RegionSpec> groups) {
        var cs = ConnectionsT26012.plan(plan);
        var spec = LowerMassT26013.spec(plan, cs, r.stage(), variant(r.candidate()));
        if (spec == null || spec.bounds() == null) return null;
        Scale s = r.scale();
        float cell = SparseBrickVolume.B * r.quality().voxel;
        var lm = new Scaled(LowerMassT26013.field(spec), s);
        List<SparseBrickVolume.Field> gf = new ArrayList<>();
        for (RegionSpec rs : groups) if (rs.group().index() == spec.a || rs.group().index() == spec.b) gf.add(rs.field());
        SparseBrickVolume.Field f = new SparseBrickVolume.Field() {
            // Blend the lower mass with EACH group separately (never the groups with each other): away from the lower mass
            // this is exactly min(group a, group b), i.e. what the group regions hold, so the region boundary is seamless.
            @Override public float distance(float x, float y, float z) {
                float l = lm.distance(x, y, z), d = l;
                for (var g : gf) d = Math.min(d, ConvectiveSystemT260.smin(l, g.distance(x, y, z), LM_BLEND));
                return d;
            }
            @Override public SparseBrickVolume.Field local(Bounds box) {
                var l = lm.local(box);
                List<SparseBrickVolume.Field> ls = new ArrayList<>();
                for (var g : gf) ls.add(g.local(box));
                return (x, y, z) -> {
                    float lv = l.distance(x, y, z), d = lv;
                    for (var g : ls) d = Math.min(d, ConvectiveSystemT260.smin(lv, g.distance(x, y, z), LM_BLEND));
                    return d;
                };
            }
        };
        Bounds c = compress(spec.bounds(), s, LM_BLEND + 2 * r.quality().voxel);
        Bounds dom = new Bounds((float) Math.floor(c.minX() / cell) * cell, (float) Math.floor(c.minY() / cell) * cell, (float) Math.floor(c.minZ() / cell) * cell,
                (float) Math.ceil(c.maxX() / cell) * cell, (float) Math.ceil(c.maxY() / cell) * cell, (float) Math.ceil(c.maxZ() / cell) * cell);
        String key = String.format(Locale.ROOT, "t13/%s/%x/%.3f/%.4f/%.4f/%s", spec.variant, spec.seed, r.stage(), s.horizontal(), s.vertical(), r.quality());
        return new LowerRegion(spec, f, dom, key);
    }

    /** Lipschitz bound used for the lower-mass region (pseudo-distance with warped outline and folds; audited). */
    static final float LM_LIPSCHITZ = 3.5f;

    static Baked bakeSystem13(Request r) {
        long t0 = System.nanoTime();
        Scale s = r.scale();
        Quality q = r.quality();
        var plan = MulticellSystemT26011.plan(r.seed());
        List<SparseBrickVolume> parts = new ArrayList<>();
        List<GroupInfo> infos = new ArrayList<>();
        List<ComponentBox> comps = new ArrayList<>();
        List<ConnectionInfo> ci = new ArrayList<>();
        int cells = 0, baked = 0;
        double buildMs, bakeMs = 0, lmMs = 0;
        float savedBand = PersistentConvectionT2608.BAND;
        String lmInfo = "none";
        long lmBricks = 0;
        try {
            PersistentConvectionT2608.BAND = Math.max(savedBand, .75f * q.feather / s.min());
            long b0 = System.nanoTime();
            List<RegionSpec> regions = regions12(r, plan, List.of());
            buildMs = (System.nanoTime() - b0) / 1e6;
            for (RegionSpec rs : regions) {
                SparseBrickVolume v = REGIONS.get(rs.key());
                boolean cached = v != null;
                if (!cached) {
                    long b1 = System.nanoTime();
                    v = new SparseBrickVolume(rs.field(), rs.domain(), q.voxel, q.feather, rs.lipschitz(), 0f);
                    bakeMs += (System.nanoTime() - b1) / 1e6;
                    REGIONS.put(rs.key(), v);
                    baked++;
                }
                parts.add(v);
                cells += rs.state().geoms.size();
                infos.add(new GroupInfo(rs.group().index(), role(rs.group(), rs.tau(), rs.state()), rs.state().geoms.size(), rs.tau(), measure(v), v.signature(), cached));
            }
            // The group fields inside the blend must be exact a little further out than the density ramp.
            PersistentConvectionT2608.BAND = Math.max(savedBand, (LM_BLEND + q.feather) / s.min());
            LowerRegion lr = lowerRegion(r, plan, regions);
            if (lr != null) {
                SparseBrickVolume v = REGIONS.get(lr.key());
                if (v == null) {
                    long b1 = System.nanoTime();
                    v = new SparseBrickVolume(lr.field(), lr.domain(), q.voxel, q.feather, LM_LIPSCHITZ, 0f);
                    lmMs = (System.nanoTime() - b1) / 1e6;
                    REGIONS.put(lr.key(), v);
                    baked++;
                }
                parts.add(v);
                lmBricks = v.liveBricks();
                lmInfo = lr.spec().describe() + String.format(Locale.ROOT, " regionBricks=%d regionMB=%.1f", v.liveBricks(), v.cpuBytes() / 1048576.0);
                for (var part : lr.spec().parts) {
                    float[] bx = part.box();
                    comps.add(new ComponentBox(part.label(), new float[]{bx[0] * s.horizontal(), bx[1] * s.vertical(), bx[2] * s.horizontal(), bx[3] * s.horizontal(),
                            bx[4] * s.vertical(), bx[5] * s.horizontal()}));
                }
                comps.add(new ComponentBox("lower-mass region", new float[]{lr.domain().minX(), lr.domain().minY(), lr.domain().minZ(), lr.domain().maxX(), lr.domain().maxY(),
                        lr.domain().maxZ()}));
                float[] c = lr.spec().centre();
                ci.add(new ConnectionInfo(lr.spec().a, lr.spec().b, lr.spec().strength, lr.spec().parts.size(), lr.spec().parts.size(), false, c[0] * s.horizontal(),
                        c[1] * s.vertical(), c[2] * s.horizontal(), c[3], c[4]));
            }
        } finally {
            PersistentConvectionT2608.BAND = savedBand;
        }
        if (parts.isEmpty()) throw new IllegalStateException("no live groups at t=" + r.stage() + " (try a later stage)");
        long u0 = System.nanoTime();
        SparseBrickVolume v = SparseBrickVolume.union(parts);
        double unionMs = (System.nanoTime() - u0) / 1e6;
        Dims blocks = measure(v);
        Dims metres = new Dims(blocks.width() / s.horizontal(), blocks.depth() / s.horizontal(), blocks.minY() / s.vertical(), blocks.maxY() / s.vertical(),
                blocks.centerX() / s.horizontal(), blocks.centerZ() / s.horizontal());
        lastSystemLog = String.format(Locale.ROOT, "T26013_SYSTEM %s groups=%d regionsBaked=%d groupBakeMs=%.0f lowerMassBakeMs=%.0f lowerMassBricks=%d unionMs=%.0f totalMs=%.0f unionBricks=%d textureMB=%.1f lowerMass=[%s]",
                r.describe(), regions12Count(infos), baked, bakeMs, lmMs, lmBricks, unionMs, (System.nanoTime() - t0) / 1e6, v.liveBricks(),
                v.textureWidth() * (double) v.textureHeight() * 16 / 1048576, lmInfo);
        return new Baked(r, new Program(v), v, blocks, metres, plan.profile().baseAgl(), cells, buildMs, bakeMs + lmMs, plan.describe() + " " + plan.profile().describe(),
                infos, unionMs, baked, ci, comps);
    }

    private static int regions12Count(List<GroupInfo> infos) { return infos.size(); }

    // ================================================================ T260.14 shared lower foundation

    /** Smooth-union blend of the foundation with every group region it touches (blocks; larger than T260.13's fillet). */
    static final float FND_BLEND = 30;
    /** Lipschitz bound used for the foundation region (warped outlines, folds, billows; audited). */
    static final float FND_LIPSCHITZ = 3.5f;
    /** T260.15 (underside masses, depressions, sleeves): measured max gradient 3.34 (lip14 ... 15), bound with margin. */
    static final float FND15_LIPSCHITZ = 4f;
    /** T260.18 A / B / C: measured max gradient (lip14 ... 181-183), bound with margin. */
    static final float FND18_LIPSCHITZ = 4f;
    static float fndLipschitz(Candidate c) { return foundationVersion(c) > 15 ? FND18_LIPSCHITZ : foundationVersion(c) >= 15 ? FND15_LIPSCHITZ : FND_LIPSCHITZ; }

    /**
     * T260.14 foundation region: its own volume (cached separately; the group regions are never re-baked when only the
     * foundation changes). Field = min over the groups whose region overlaps it of smin(foundation, group), so every
     * tower rises out of the foundation with a fillet, and away from the foundation the region equals the min of the
     * group fields (what the group regions hold): no seam at the region boundary.
     */
    record FoundationRegion(FoundationT26014.Spec spec, SparseBrickVolume.Field field, Bounds domain, String key, int blendedGroups) {}

    static FoundationRegion foundationRegion(Request r, MulticellSystemT26011.Plan plan, List<RegionSpec> groups) {
        var spec = FoundationT26014.spec(plan, r.stage(), foundationVersion(r.candidate()));
        if (spec == null) return null;
        boolean solo = isolated(r.candidate());
        Scale s = r.scale();
        float cell = SparseBrickVolume.B * r.quality().voxel;
        var fnd = new Scaled(FoundationT26014.field(spec), s);
        Bounds c = compress(spec.bounds(), s, FND_BLEND + 2 * r.quality().voxel);
        Bounds dom = new Bounds((float) Math.floor(c.minX() / cell) * cell, (float) Math.floor(c.minY() / cell) * cell, (float) Math.floor(c.minZ() / cell) * cell,
                (float) Math.ceil(c.maxX() / cell) * cell, (float) Math.ceil(c.maxY() / cell) * cell, (float) Math.ceil(c.maxZ() / cell) * cell);
        List<SparseBrickVolume.Field> gf = new ArrayList<>();
        List<Bounds> gd = new ArrayList<>();
        for (RegionSpec rs : groups) {
            Bounds b = rs.domain();
            if (solo || Boolean.getBoolean("pa.t14.noblend")) break; // the foundation alone (diagnostic candidates / debug)
            if (b.maxX() < dom.minX() || b.minX() > dom.maxX() || b.maxY() < dom.minY() || b.minY() > dom.maxY() || b.maxZ() < dom.minZ() || b.minZ() > dom.maxZ()) continue;
            gf.add(rs.field());
            gd.add(b);
        }
        // Group fields are needed only near the foundation. With F = min_g smin(l, g, k) the region holds
        // F' = max(F, l - L1) where l < L0 and l - L1 beyond (L1 = 1.25 k + feather, L0 = L1 + feather): F' >= F, and
        // wherever min(groups, F') < feather it equals min(groups, F) (F' > F only where F comes from a group lying
        // deeper than L1 - k / 4, which the group regions hold anyway), so the UNION is unchanged while the group fields
        // are skipped wherever the foundation is far (F' never decreases across the switch: coarse skips stay valid).
        float feather = r.quality().feather, l1 = 1.25f * FND_BLEND + feather, l0 = l1 + feather;
        SparseBrickVolume.Field f = new SparseBrickVolume.Field() {
            @Override public float distance(float x, float y, float z) {
                float l = fnd.distance(x, y, z);
                if (l >= l0) return l - l1;
                if (l <= -feather) return l; // solid foundation: density saturated whatever the groups are
                float d = l;
                for (var g : gf) d = Math.min(d, ConvectiveSystemT260.smin(l, g.distance(x, y, z), FND_BLEND));
                return Math.max(d, l - l1);
            }
            @Override public SparseBrickVolume.Field local(Bounds box) {
                var l = fnd.local(box);
                List<SparseBrickVolume.Field> ls = new ArrayList<>();
                for (var g : gf) ls.add(g.local(box));
                return (x, y, z) -> {
                    float lv = l.distance(x, y, z);
                    if (lv >= l0) return lv - l1;
                    if (lv <= -feather) return lv;
                    float d = lv;
                    for (var g : ls) d = Math.min(d, ConvectiveSystemT260.smin(lv, g.distance(x, y, z), FND_BLEND));
                    return Math.max(d, lv - l1);
                };
            }
        };
        String key = String.format(Locale.ROOT, "t%d%s/%x/%.3f/%.4f/%.4f/%s", spec.version, solo ? "f" : "", spec.seed, r.stage(), s.horizontal(), s.vertical(), r.quality());
        return new FoundationRegion(spec, f, dom, key, gf.size());
    }

    /** Foundation version of a T260.14 / T260.15 / T260.18 candidate (0: not a foundation candidate). */
    public static int foundationVersion(Candidate c) {
        return switch (c) {
            case T14, T14F -> 14;
            case T15, T15F -> 15;
            case T18A, T18AF -> FoundationT26014.V18A;
            case T18B, T18BF -> FoundationT26014.V18B;
            case T18C, T18CF -> FoundationT26014.V18C;
            case T18D, T18DF -> FoundationT26014.V18D;
            case T18E, T18EF -> FoundationT26014.V18E;
            case T18D2, T18D2F -> FoundationT26014.V18D2;
            case T19 -> FoundationT26014.V19;
            case T18F, T18FF -> FoundationT26014.V18F;
            default -> 0;
        };
    }
    /** Diagnostic candidates: the foundation geometry alone (no tower blend, no group regions shown). */
    public static boolean isolated(Candidate c) { return c == Candidate.T14F || c == Candidate.T15F || c == Candidate.T18AF || c == Candidate.T18BF || c == Candidate.T18CF || c == Candidate.T18DF || c == Candidate.T18EF || c == Candidate.T18D2F || c == Candidate.T18FF; }
    /** System log label: T26014 / T26015 / T26018A-C. */
    static String foundationLabel(Candidate c) { int v = foundationVersion(c); return v > 15 ? "T26018" + FoundationT26014.variant18(v) : "T2601" + v % 10; }

    static Baked bakeSystem14(Request r) {
        long t0 = System.nanoTime();
        Scale s = r.scale();
        Quality q = r.quality();
        var plan = MulticellSystemT26011.plan(r.seed());
        List<SparseBrickVolume> parts = new ArrayList<>();
        List<GroupInfo> infos = new ArrayList<>();
        List<ComponentBox> comps = new ArrayList<>();
        List<ConnectionInfo> ci = new ArrayList<>();
        int cells = 0, baked = 0;
        double buildMs, bakeMs = 0, fMs = 0;
        float savedBand = PersistentConvectionT2608.BAND;
        String fInfo = "none (no organised pair: the group regions only, as T260.12 / T260.13 place them)";
        long fBricks = 0;
        try {
            PersistentConvectionT2608.BAND = Math.max(savedBand, .75f * q.feather / s.min());
            long b0 = System.nanoTime();
            List<RegionSpec> regions = regions12(r, plan, List.of());
            buildMs = (System.nanoTime() - b0) / 1e6;
            for (RegionSpec rs : regions) {
                SparseBrickVolume v = REGIONS.get(rs.key());
                boolean cached = v != null;
                if (!cached) {
                    long b1 = System.nanoTime();
                    v = new SparseBrickVolume(rs.field(), rs.domain(), q.voxel, q.feather, rs.lipschitz(), 0f);
                    bakeMs += (System.nanoTime() - b1) / 1e6;
                    REGIONS.put(rs.key(), v);
                    baked++;
                }
                parts.add(v);
                cells += rs.state().geoms.size();
                infos.add(new GroupInfo(rs.group().index(), role(rs.group(), rs.tau(), rs.state()), rs.state().geoms.size(), rs.tau(), measure(v), v.signature(), cached));
            }
            PersistentConvectionT2608.BAND = Math.max(savedBand, (FND_BLEND + q.feather) / s.min());
            FoundationRegion fr = foundationRegion(r, plan, regions);
            if (fr != null) {
                SparseBrickVolume v = REGIONS.get(fr.key());
                boolean cached = v != null;
                if (!cached) {
                    long b1 = System.nanoTime();
                    v = new SparseBrickVolume(fr.field(), fr.domain(), q.voxel, q.feather, fndLipschitz(r.candidate()), 0f);
                    fMs = (System.nanoTime() - b1) / 1e6;
                    REGIONS.put(fr.key(), v);
                    baked++;
                }
                if (isolated(r.candidate()) || Boolean.getBoolean("pa.t14.only")) parts.clear(); // the foundation region alone
                parts.add(v);
                fBricks = v.liveBricks();
                fInfo = fr.spec().describe() + String.format(Locale.ROOT, " blendedGroups=%d regionBricks=%d regionMB=%.1f cached=%s", fr.blendedGroups(), v.liveBricks(),
                        v.cpuBytes() / 1048576.0, cached);
                for (var part : fr.spec().parts) {
                    float[] bx = part.box();
                    comps.add(new ComponentBox(part.label(), new float[]{bx[0] * s.horizontal(), bx[1] * s.vertical(), bx[2] * s.horizontal(), bx[3] * s.horizontal(),
                            bx[4] * s.vertical(), bx[5] * s.horizontal()}));
                }
                comps.add(new ComponentBox("lower-mass region (foundation)", new float[]{fr.domain().minX(), fr.domain().minY(), fr.domain().minZ(), fr.domain().maxX(),
                        fr.domain().maxY(), fr.domain().maxZ()}));
                float[] c = fr.spec().focus();
                for (float[] l : fr.spec().links)
                    ci.add(new ConnectionInfo((int) l[0], (int) l[1], l[2], fr.spec().masses.size(), fr.spec().masses.size(), false, c[0] * s.horizontal(), c[1] * s.vertical(),
                            c[2] * s.horizontal(), c[3], c[4]));
                ci.sort((a, b) -> Float.compare(b.strength(), a.strength()));
            }
        } finally {
            PersistentConvectionT2608.BAND = savedBand;
        }
        if (parts.isEmpty()) throw new IllegalStateException("no live groups at t=" + r.stage() + " (try a later stage)");
        long u0 = System.nanoTime();
        SparseBrickVolume v = SparseBrickVolume.union(parts);
        double unionMs = (System.nanoTime() - u0) / 1e6;
        Dims blocks = measure(v);
        Dims metres = new Dims(blocks.width() / s.horizontal(), blocks.depth() / s.horizontal(), blocks.minY() / s.vertical(), blocks.maxY() / s.vertical(),
                blocks.centerX() / s.horizontal(), blocks.centerZ() / s.horizontal());
        lastSystemLog = String.format(Locale.ROOT, foundationLabel(r.candidate()) + "_SYSTEM %s groups=%d regionsBaked=%d groupBakeMs=%.0f foundationBakeMs=%.0f foundationBricks=%d unionMs=%.0f totalMs=%.0f unionBricks=%d textureMB=%.1f foundation=[%s]",
                r.describe(), infos.size(), baked, bakeMs, fMs, fBricks, unionMs, (System.nanoTime() - t0) / 1e6, v.liveBricks(),
                v.textureWidth() * (double) v.textureHeight() * 16 / 1048576, fInfo);
        return new Baked(r, new Program(v), v, blocks, metres, plan.profile().baseAgl(), cells, buildMs, bakeMs + fMs, plan.describe() + " " + plan.profile().describe(),
                infos, unionMs, baked, ci, comps);
    }

    /** Lifecycle label of a group at local time tau (from its cells' own states). */
    static String role(MulticellSystemT26011.Group g, float tau, SystemState st) {
        if (g.failed()) return "shallow";
        if (tau < 14) return "developing";
        int dd = 0;
        for (var c : st.geoms) if (c.st[2] > .3f) dd++;
        return dd * 2 > st.geoms.size() ? "weakening" : "mature";
    }

    /** One group's region volume at a lattice-aligned placement (blocks). */
    static SparseBrickVolume region(SystemState st, Scale s, Quality q, float ox, float oz) {
        PersistentConvectionT2608.Field field = st.field();
        Bounds content = st.bounds();
        float cell = SparseBrickVolume.B * q.voxel;
        Bounds model = new Bounds(content.minX() - 260, content.minY() - 40, content.minZ() - 260, content.maxX() + 260, content.maxY() + 260, content.maxZ() + 260);
        Bounds c = compress(model, s, 2 * q.voxel);
        Bounds dom = new Bounds((float) Math.floor((c.minX() + ox) / cell) * cell, (float) Math.floor(c.minY() / cell) * cell, (float) Math.floor((c.minZ() + oz) / cell) * cell,
                (float) Math.ceil((c.maxX() + ox) / cell) * cell, (float) Math.ceil(c.maxY() / cell) * cell, (float) Math.ceil((c.maxZ() + oz) / cell) * cell);
        float lip = field.floor > ConvectiveVolumeT2607.FULL_SKIP_FLOOR ? 3f : 2.5f;
        return new SparseBrickVolume(new Scaled(field, s, ox, oz), dom, q.voxel, q.feather, lip, 0f);
    }

    /** Light lattice spacing for a volume: 10 blocks, coarser for km-wide assemblies (about 6 M lattice points). */
    public static float lightSpacing(SparseBrickVolume v) {
        Bounds b = v.bounds();
        double vol = (double) (b.maxX() - b.minX()) * (b.maxY() - b.minY()) * (b.maxZ() - b.minZ());
        return (float) Math.max(10, Math.ceil(Math.cbrt(vol / 6e6)));
    }

    /** Occupied extent of a baked volume: every voxel-lattice point with density >= 0.5 in a non-empty coarse cell. */
    public static Dims measure(SparseBrickVolume v) {
        Bounds b = v.bounds();
        int[] g = v.grid();
        float vox = v.voxel(), cell = SparseBrickVolume.B * vox;
        float[] m = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int k = 0; k < g[2]; k++) for (int j = 0; j < g[1]; j++) for (int i = 0; i < g[0]; i++) {
            if (v.cellState(i, j, k) == SparseBrickVolume.EMPTY) continue;
            for (int c = 0; c <= SparseBrickVolume.B; c++) for (int bb = 0; bb <= SparseBrickVolume.B; bb++) for (int a = 0; a <= SparseBrickVolume.B; a++) {
                float x = b.minX() + i * cell + a * vox, y = b.minY() + j * cell + bb * vox, z = b.minZ() + k * cell + c * vox;
                if (v.density(x, y, z) < .5f) continue;
                m[0] = Math.min(m[0], x); m[1] = Math.min(m[1], y); m[2] = Math.min(m[2], z);
                m[3] = Math.max(m[3], x); m[4] = Math.max(m[4], y); m[5] = Math.max(m[5], z);
            }
        }
        if (m[0] > m[3]) return new Dims(0, 0, 0, 0, 0, 0);
        return new Dims(m[3] - m[0], m[5] - m[2], m[1], m[4], .5f * (m[0] + m[3]), .5f * (m[2] + m[5]));
    }

    /** The model field sampled on the compressed lattice (local y = 0 at the model's cloud base). Thread-safe. */
    static final class Scaled implements SparseBrickVolume.Field {
        private final SparseBrickVolume.Field inner;
        private final Scale s;
        private final float ih, iv, m;

        private final float ox, oy, oz;

        Scaled(SparseBrickVolume.Field inner, Scale s) { this(inner, s, 0, 0, 0); }

        /** T260.11: placed at a block offset (x, z) in the system frame. */
        Scaled(SparseBrickVolume.Field inner, Scale s, float ox, float oz) { this(inner, s, ox, 0, oz); }

        /** T260.12: and at a vertical block offset (the group's own cloud base). */
        Scaled(SparseBrickVolume.Field inner, Scale s, float ox, float oy, float oz) {
            this.inner = inner;
            this.s = s;
            this.ox = ox; this.oy = oy; this.oz = oz;
            ih = 1 / s.horizontal(); iv = 1 / s.vertical(); m = s.min();
        }

        @Override public float distance(float x, float y, float z) {
            return m * inner.distance((x - ox) * ih, PersistentConvectionT2608.BASE_Y + (y - oy) * iv, (z - oz) * ih);
        }

        @Override public SparseBrickVolume.Field local(Bounds box) {
            float base = PersistentConvectionT2608.BASE_Y;
            return new Scaled(inner.local(new Bounds((box.minX() - ox) * ih, base + (box.minY() - oy) * iv, (box.minZ() - oz) * ih, (box.maxX() - ox) * ih,
                    base + (box.maxY() - oy) * iv, (box.maxZ() - oz) * ih)), s, ox, oy, oz);
        }
    }

    /** Fixture program over a baked preview volume (same texture layout as the T260 sparse-brick fixtures). */
    public static final class Program implements StormGeometryProgram {
        private final SparseBrickVolume volume;
        Program(SparseBrickVolume volume) { this.volume = volume; }

        public SparseBrickVolume volume() { return volume; }
        @Override public float radius() { return (volume.bounds().maxX() - volume.bounds().minX()) * .5f; }
        @Override public float height() { return volume.bounds().maxY(); }
        @Override public float axisX(float fraction) { return 0; }
        @Override public float density(float x, float y, float z, StormMorphologyPrototype.Scale scale) {
            if (!volume.bounds().contains(x, y, z)) return 0;
            return volume.density(x, y, z);
        }
        @Override public Bounds bounds() { return volume.bounds(); }
        @Override public float[] packed() { return java.util.Arrays.copyOf(volume.texture(), SparseBrickVolume.HEADER_TEXELS * 4); }
        @Override public long signature() { return volume.signature(); }
        @Override public int version() { return ConvectiveSystemT260.VERSION; }
        @Override public boolean growthDefined() { return true; }
        @Override public float[] textureData() { return volume.texture(); }
        @Override public int textureWidth() { return volume.textureWidth(); }
        @Override public int textureHeight() { return volume.textureHeight(); }
    }

    /** Demonstration presets (candidate T10, default scale): seed and stage per kind. */
    public record Preset(String name, Candidate candidate, long seed, float stage, String note) {}

    // Measured at the default scale (normal quality): isolated ~305 x 325 x 590, multicell ~1060 x 850 x 960,
    // broad ~1240 x 1220 x 1110 blocks. The candidate never grows wider than ~14 km (T260.9 initiates <= 18 cells), so
    // no seed reaches the 2500-5000 block "large system" target at proportion-preserving scale.
    public static final List<Preset> PRESETS = List.of(
            new Preset("isolated", Candidate.T10, 2609102L, 12, "isolated developing cumulonimbus (4 cells, high dry base)"),
            new Preset("multicell", Candidate.T10, 2609101L, 40, "sheared multicell storm (48 cells)"),
            new Preset("broad", Candidate.T10, 2609196L, 46, "broadest convective system found in a 400-seed scan (63 cells, weak shear)"));

    public static Preset preset(String name) {
        for (Preset p : PRESETS) if (p.name().equalsIgnoreCase(name)) return p;
        throw new IllegalArgumentException("Unknown preset '" + name + "' (isolated, multicell, broad)");
    }

    /** CLI check: bake presets at the default scale and print dimensions / cost. args: [scale] [quality] [preset...] */
    public static void main(String[] args) {
        float sc = args.length > 0 ? Float.parseFloat(args[0]) : DEFAULT_SCALE;
        Quality q = args.length > 1 ? Quality.parse(args[1]) : Quality.NORMAL;
        List<Preset> ps = args.length > 2 ? java.util.Arrays.stream(args, 2, args.length).map(CloudPreviewModels::preset).toList() : PRESETS;
        for (Preset p : ps) {
            var b = bake(new Request(p.candidate(), p.seed(), p.stage(), Scale.uniform(sc), q));
            System.out.printf(Locale.ROOT, "CLOUD_PREVIEW preset=%s %s cells=%d blocks W=%.0f D=%.0f H=%.0f (y %.0f..%.0f) model W=%.1fkm D=%.1fkm H=%.1fkm buildMs=%.0f bakeMs=%.0f bricks=%d textureMB=%.1f signature=%s%n",
                    p.name(), b.request().describe(), b.cells(), b.blocks().width(), b.blocks().depth(), b.blocks().height(), b.blocks().minY(), b.blocks().maxY(),
                    b.model().width() / 1000, b.model().depth() / 1000, b.model().height() / 1000, b.buildMs(), b.bakeMs(), b.volume().liveBricks(),
                    b.volume().textureWidth() * (double) b.volume().textureHeight() * 16 / 1048576, Long.toHexString(b.volume().signature()));
        }
    }
}
