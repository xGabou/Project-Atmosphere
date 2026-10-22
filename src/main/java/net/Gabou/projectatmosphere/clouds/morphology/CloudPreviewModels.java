package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.morphology.PersistentConvectionT2608.Scenario;
import net.Gabou.projectatmosphere.clouds.morphology.PersistentConvectionT2608.SystemState;
import net.Gabou.projectatmosphere.clouds.morphology.StormMorphologyPrototype.Bounds;

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
        T10("t10", "T260.10 environment-driven vertical development (current experimental candidate)"),
        T9("t9", "T260.9 storm-scale growth (accepted experimental baseline, shallow)");

        public final String id, description;
        Candidate(String id, String description) { this.id = id; this.description = description; }

        public static Candidate parse(String s) {
            for (Candidate c : values()) if (c.id.equalsIgnoreCase(s)) return c;
            throw new IllegalArgumentException("Unknown candidate '" + s + "' (t10, t9)");
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

    /** Named growth stages (model minutes; the candidates develop until t = 46). */
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
                        int cells, double buildMs, double bakeMs, String environment) {}

    /** Occupied extent (density >= 0.5). */
    public record Dims(float width, float depth, float minY, float maxY, float centerX, float centerZ) {
        public float height() { return maxY - minY; }
    }

    public static Scenario scenario(Candidate c, long seed) {
        PersistentConvectionT2608.VARIANT = 4; // both candidates use the C4 cells
        return switch (c) {
            case T10 -> VerticalGrowthT26010.build(seed, 46);
            case T9 -> SystemGrowthT2609.build(seed, 46);
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
        return new Baked(r, new Program(v), v, blocks, metres, profile.baseAgl(), st.geoms.size(), buildMs, bakeMs, env.describe() + " " + profile.describe());
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

        Scaled(SparseBrickVolume.Field inner, Scale s) {
            this.inner = inner;
            this.s = s;
            ih = 1 / s.horizontal(); iv = 1 / s.vertical(); m = s.min();
        }

        @Override public float distance(float x, float y, float z) {
            return m * inner.distance(x * ih, PersistentConvectionT2608.BASE_Y + y * iv, z * ih);
        }

        @Override public SparseBrickVolume.Field local(Bounds box) {
            float base = PersistentConvectionT2608.BASE_Y;
            return new Scaled(inner.local(new Bounds(box.minX() * ih, base + box.minY() * iv, box.minZ() * ih, box.maxX() * ih, base + box.maxY() * iv, box.maxZ() * ih)), s);
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
