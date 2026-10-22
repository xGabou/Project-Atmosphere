package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.morphology.StormMorphologyPrototype.Bounds;
import net.Gabou.projectatmosphere.clouds.morphology.StormMorphologyPrototype.Scale;

import java.util.ArrayList;
import java.util.List;

/**
 * T260 experimental convective SYSTEM (never production, never saved or networked). A system is a frozen recipe of
 * components (cells, lower mass, associated cumulus), each a set of ellipsoidal growth bubbles. Composition happens on
 * the CPU only: bubbles of one component blend with that component's radius; components joined by an ATTACHED link
 * blend with the link radius; unlinked components use a hard union (they stay separate, never bridged); a flat
 * condensation base cuts everything. The result is baked once into a {@link SparseBrickVolume}; every camera samples
 * that same frozen volume. GPU cost is independent of bubble and component counts.
 */
public final class ConvectiveSystemT260 {
    private ConvectiveSystemT260() {}

    public static final int VERSION = 1;

    /** Ellipsoidal growth bubble; {@code comp} indexes the component list. */
    public record Bubble(float x, float y, float z, float rx, float ry, float rz, int comp, int tier) {
        float maxR() { return Math.max(rx, Math.max(ry, rz)); }
    }

    public enum Role { PRIMARY, SECONDARY, LOWER_MASS, ASSOCIATED, PLACEHOLDER }

    /** One component; {@code group} = attachment group (components of one group blend with {@code linkBlend}). */
    public record Component(String name, Role role, int group, float blend, float anchorX, float anchorZ, float top) {}

    /** Frozen recipe: what was built, for diagnostics, galleries and component identification. */
    public record Recipe(String kind, long seed, List<Component> components, List<Bubble> bubbles, float baseY,
                         float baseBlend, float linkBlend, float primaryHeight, float windX, float windZ) {}

    // ------------------------------------------------------------------ field

    /** Distance field of a recipe, restricted to an optional component subset (isolated views). */
    public static final class Field implements SparseBrickVolume.Field {
        private final Recipe recipe;
        private final Bubble[] bubbles;
        private final float[] lo, hi;     // per-bubble influence AABB (xyz min, xyz max)
        private final float reach;
        private final boolean[] enabled;

        public Field(Recipe recipe, boolean[] enabledComponents, float band) {
            this.recipe = recipe;
            List<Bubble> keep = new ArrayList<>();
            for (Bubble b : recipe.bubbles()) if (enabledComponents == null || enabledComponents[b.comp()]) keep.add(b);
            bubbles = keep.toArray(new Bubble[0]);
            enabled = enabledComponents;
            float maxBlend = recipe.linkBlend();
            for (Component c : recipe.components()) maxBlend = Math.max(maxBlend, c.blend());
            reach = band + 2 * maxBlend + recipe.baseBlend();
            lo = new float[bubbles.length * 3];
            hi = new float[bubbles.length * 3];
            for (int n = 0; n < bubbles.length; n++) {
                Bubble b = bubbles[n];
                lo[n * 3] = b.x - b.rx; lo[n * 3 + 1] = b.y - b.ry; lo[n * 3 + 2] = b.z - b.rz;
                hi[n * 3] = b.x + b.rx; hi[n * 3 + 1] = b.y + b.ry; hi[n * 3 + 2] = b.z + b.rz;
            }
        }


        @Override public SparseBrickVolume.Field local(Bounds box) {
            List<Bubble> near = new ArrayList<>();
            for (int n = 0; n < bubbles.length; n++) {
                if (lo[n * 3] - reach > box.maxX() || hi[n * 3] + reach < box.minX()) continue;
                if (lo[n * 3 + 1] - reach > box.maxY() || hi[n * 3 + 1] + reach < box.minY()) continue;
                if (lo[n * 3 + 2] - reach > box.maxZ() || hi[n * 3 + 2] + reach < box.minZ()) continue;
                near.add(bubbles[n]);
            }
            return new Compact(recipe, near, -1);
        }

        @Override public float distance(float x, float y, float z) { return whole().distance(x, y, z); }

        private volatile Compact wholeCache;
        private synchronized Compact wholeInit() { if (wholeCache == null) wholeCache = new Compact(recipe, java.util.Arrays.asList(bubbles), -1); return wholeCache; }
        private Compact whole() { Compact c = wholeCache; return c != null ? c : wholeInit(); }

        /** Distance with one component's contribution only ({@code only} >= 0), for component identification. */
        public float distance(float x, float y, float z, int only) {
            return only < 0 ? whole().distance(x, y, z) : new Compact(recipe, java.util.Arrays.asList(bubbles), only).distance(x, y, z);
        }

        /** Index of the component that owns a point (smallest component distance), -1 outside. */
        private volatile Compact[] perComponent;
        private synchronized Compact[] perComponents() {
            if (perComponent == null) {
                Compact[] per = new Compact[recipe.components().size()];
                for (int c = 0; c < per.length; c++) per[c] = new Compact(recipe, java.util.Arrays.asList(bubbles), c);
                perComponent = per;
            }
            return perComponent;
        }
        public int owner(float x, float y, float z) {
            Compact[] per = perComponents();
            int best = -1;
            float bd = BIG;
            for (int c = 0; c < per.length; c++) {
                float d = per[c].distance(x, y, z);
                if (d < bd) { bd = d; best = c; }
            }
            return bd <= 0 ? best : -1;
        }
    }

    /** Allocation-free evaluator over a bubble subset: bubbles sorted by component, components by group. */
    static final class Compact implements SparseBrickVolume.Field {
        private final float[] b;          // x y z rx ry rz k per bubble
        private final int[] compStart;    // bubble range per present component
        private final float[] compBlend;
        private final int[] groupStart;   // component range per present group
        private final float linkBlend, baseY, baseBlend;

        Compact(Recipe recipe, List<Bubble> source, int only) {
            List<Bubble> list = new ArrayList<>();
            for (Bubble x : source) if (only < 0 || x.comp() == only) list.add(x);
            List<Component> comps = recipe.components();
            list.sort((p, q) -> p.comp() != q.comp()
                    ? (comps.get(p.comp()).group() != comps.get(q.comp()).group() ? Integer.compare(comps.get(p.comp()).group(), comps.get(q.comp()).group()) : Integer.compare(p.comp(), q.comp()))
                    : 0);
            b = new float[list.size() * 7];
            List<Integer> cs = new ArrayList<>(), gs = new ArrayList<>();
            List<Float> cb = new ArrayList<>();
            int lastComp = -1, lastGroup = -1;
            for (int n = 0; n < list.size(); n++) {
                Bubble x = list.get(n);
                b[n * 7] = x.x; b[n * 7 + 1] = x.y; b[n * 7 + 2] = x.z; b[n * 7 + 3] = x.rx; b[n * 7 + 4] = x.ry; b[n * 7 + 5] = x.rz;
                b[n * 7 + 6] = comps.get(x.comp()).blend() * TIER_BLEND[Math.min(x.tier(), TIER_BLEND.length - 1)];
                if (x.comp() != lastComp) {
                    int g = comps.get(x.comp()).group();
                    if (g != lastGroup) { gs.add(cs.size()); lastGroup = g; }
                    cs.add(n);
                    cb.add(comps.get(x.comp()).blend());
                    lastComp = x.comp();
                }
            }
            cs.add(list.size());
            gs.add(cs.size() - 1);
            compStart = cs.stream().mapToInt(Integer::intValue).toArray();
            groupStart = gs.stream().mapToInt(Integer::intValue).toArray();
            compBlend = new float[cb.size()];
            for (int i = 0; i < compBlend.length; i++) compBlend[i] = cb.get(i);
            linkBlend = recipe.linkBlend();
            baseY = recipe.baseY();
            baseBlend = recipe.baseBlend();
        }

        @Override public float distance(float x, float y, float z) {
            float result = BIG;
            for (int g = 0; g + 1 < groupStart.length; g++) {
                float dg = BIG;
                for (int c = groupStart[g]; c < groupStart[g + 1]; c++) {
                    float dc = BIG;
                    for (int n = compStart[c]; n < compStart[c + 1]; n++) {
                        int o = n * 7;
                        float d = ellipsoid(x - b[o], y - b[o + 1], z - b[o + 2], b[o + 3], b[o + 4], b[o + 5]);
                        dc = dc >= BIG ? d : smin(dc, d, b[o + 6]);
                    }
                    dg = dg >= BIG ? dc : smin(dg, dc, linkBlend);
                }
                result = Math.min(result, dg);
            }
            if (result >= BIG) return BIG;
            return smax(result, baseY - y, baseBlend);
        }
    }

    static final float BIG = 1e4f;
    /** Blend factor per bubble tier: 0 macro thermal, 1 cauliflower lump, 2 small lump, 3 soft (glaciating) crown lump, 4 crown dome. */
    static final float[] TIER_BLEND = {1f, .34f, .22f, .62f, .45f};

    /** Ellipsoid distance (k0*(k0-1)/k1; exact for spheres, a close bound elsewhere). */
    static float ellipsoid(float px, float py, float pz, float rx, float ry, float rz) {
        float ax = px / rx, ay = py / ry, az = pz / rz;
        float k0 = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        float bx = ax / rx, by = ay / ry, bz = az / rz;
        float k1 = (float) Math.sqrt(bx * bx + by * by + bz * bz);
        if (k1 < 1e-9f) return -Math.min(rx, Math.min(ry, rz));
        return k0 * (k0 - 1) / k1;
    }
    static float smin(float a, float b, float k) { if (k <= 0) return Math.min(a, b); float h = Math.max(k - Math.abs(a - b), 0) / k; return Math.min(a, b) - h * h * k * .25f; }
    static float smax(float a, float b, float k) { if (k <= 0) return Math.max(a, b); float h = Math.max(k - Math.abs(a - b), 0) / k; return Math.max(a, b) + h * h * k * .25f; }

    // ------------------------------------------------------------------ program

    /** Baked, frozen system. All cameras of a seed render this one object. */
    public static final class Program implements StormGeometryProgram {
        private Recipe recipe;
        private final Field field;
        private final SparseBrickVolume volume;
        private final boolean[] enabled;

        public Program(Recipe recipe, boolean[] enabledComponents, float voxel, float feather, float headroom) {
            this.recipe = recipe;
            this.enabled = enabledComponents;
            float band = feather * 2.5f;
            field = new Field(recipe, enabledComponents, band);
            volume = new SparseBrickVolume(field, domain(recipe, enabledComponents, band), voxel, feather, 1.5f, headroom);
        }

        public static Bounds domain(Recipe r, boolean[] enabled, float band) {
            float[] e = {1e9f, r.baseY() - band, 1e9f, -1e9f, -1e9f, -1e9f};
            for (Bubble b : r.bubbles()) {
                if (enabled != null && !enabled[b.comp()]) continue;
                float m = band + r.components().get(b.comp()).blend() + r.linkBlend();
                e[0] = Math.min(e[0], b.x - b.rx - m); e[2] = Math.min(e[2], b.z - b.rz - m);
                e[3] = Math.max(e[3], b.x + b.rx + m); e[4] = Math.max(e[4], b.y + b.ry + m); e[5] = Math.max(e[5], b.z + b.rz + m);
            }
            return new Bounds(e[0], e[1], e[2], e[3], e[4], e[5]);
        }

        public Recipe recipe() { return recipe; }
        /** After an incremental {@link SparseBrickVolume#update}, the recipe the volume now represents. */
        public void adopt(Recipe next) { recipe = next; }
        public Field field() { return field; }
        public SparseBrickVolume volume() { return volume; }
        public boolean[] enabledComponents() { return enabled == null ? null : enabled.clone(); }

        @Override public float radius() { return (volume.bounds().maxX() - volume.bounds().minX()) * .5f; }
        @Override public float height() { return recipe.primaryHeight(); }
        @Override public float axisX(float fraction) {
            for (Component c : recipe.components()) if (c.role() == Role.PRIMARY || c.role() == Role.PLACEHOLDER) return c.anchorX();
            return 0;
        }
        @Override public float density(float x, float y, float z, Scale scale) {
            if (!volume.bounds().contains(x, y, z)) return 0;
            return volume.density(x, y, z);
        }
        @Override public Bounds bounds() { return volume.bounds(); }
        @Override public float[] packed() { return java.util.Arrays.copyOf(volume.texture(), SparseBrickVolume.HEADER_TEXELS * 4); }
        @Override public long signature() { return volume.signature(); }
        @Override public int version() { return VERSION; }
        @Override public boolean growthDefined() { return true; }
        @Override public float[] textureData() { return volume.texture(); }
        @Override public int textureWidth() { return volume.textureWidth(); }
        @Override public int textureHeight() { return volume.textureHeight(); }
    }

    // ------------------------------------------------------------------ phase 1 placeholder spike

    /**
     * Deliberately shapeless placeholder system for the sparse-brick feasibility spike: {@code count} spheres in a
     * system-sized domain (2800 x 1150 x 2400 blocks), separate components, no anatomy.
     */
    public static Recipe placeholder(int count, float rMin, float rMax, long seed) {
        java.util.Random random = new java.util.Random(seed);
        List<Component> comps = new ArrayList<>();
        List<Bubble> bubbles = new ArrayList<>();
        for (int n = 0; n < count; n++) {
            float r = rMin + (rMax - rMin) * random.nextFloat();
            float x = -1400 + r + (2800 - 2 * r) * random.nextFloat();
            float z = -1200 + r + (2400 - 2 * r) * random.nextFloat();
            float y = Math.max(r * .6f, 60 + (1100 - r - 60) * random.nextFloat());
            comps.add(new Component("sphere" + n, Role.PLACEHOLDER, n, 0, x, z, y + r));
            bubbles.add(new Bubble(x, y, z, r, r, r, n, 0));
        }
        return new Recipe("placeholder_" + count, seed, comps, bubbles, 0, 20, 0, 1150, 1, 0);
    }

    // ------------------------------------------------------------------ developing system (update-cost evidence)

    public record DevStep(Recipe recipe, Bounds region) {}

    /**
     * One growth step of a developing system: the bubbles of the tallest component's upper third rise and swell
     * slightly (bounded, alternating every 12 steps so a long arm stays in range). Returns the new recipe and a box
     * containing every changed surface before and after.
     */
    public static DevStep developStep(Recipe r, int step) {
        int target = 0;
        for (int c = 1; c < r.components().size(); c++) if (r.components().get(c).top() > r.components().get(target).top()) target = c;
        float top = r.components().get(target).top();
        float sign = ((step - 1) / 12) % 2 == 0 ? 1 : -1;
        List<Bubble> list = new ArrayList<>(r.bubbles());
        float[] e = {1e9f, 1e9f, 1e9f, -1e9f, -1e9f, -1e9f};
        float rise = 0;
        for (int n = 0; n < list.size(); n++) {
            Bubble b = list.get(n);
            if (b.comp() != target || b.y() + b.ry() < top - (top - r.baseY()) / 3) continue;
            float g = 1 + .015f * sign, dy = 5f * sign;
            Bubble q = new Bubble(b.x(), b.y() + dy, b.z(), b.rx() * g, b.ry() * g, b.rz() * g, b.comp(), b.tier());
            list.set(n, q);
            rise = Math.max(rise, q.y() + q.ry() - top);
            for (Bubble x : new Bubble[]{b, q}) {
                e[0] = Math.min(e[0], x.x() - x.rx()); e[1] = Math.min(e[1], x.y() - x.ry()); e[2] = Math.min(e[2], x.z() - x.rz());
                e[3] = Math.max(e[3], x.x() + x.rx()); e[4] = Math.max(e[4], x.y() + x.ry()); e[5] = Math.max(e[5], x.z() + x.rz());
            }
        }
        List<Component> comps = new ArrayList<>(r.components());
        Component c = comps.get(target);
        comps.set(target, new Component(c.name(), c.role(), c.group(), c.blend(), c.anchorX(), c.anchorZ(), c.top() + Math.max(rise, 0)));
        float m = 30 + 2 * Math.max(r.linkBlend(), c.blend()) + r.baseBlend() + 12;
        Bounds region = new Bounds(e[0] - m, e[1] - m, e[2] - m, e[3] + m, e[4] + m, e[5] + m);
        return new DevStep(new Recipe(r.kind(), r.seed(), comps, list, r.baseY(), r.baseBlend(), r.linkBlend(), r.primaryHeight(), r.windX(), r.windZ()), region);
    }

    // ------------------------------------------------------------------ phase 2/3 calvus system

    /** T260.3 calvus system layout (current candidate C). */
    public static Recipe calvus(long seed) { return CalvusSystemC.build(seed); }

    /** T260.3 broader alternative (C26: broad growth regions), for comparison only. */
    public static Recipe calvusBroad(long seed) { return CalvusSystemC.buildBroad(seed); }

    /** T260.2 refined calvus system, FROZEN as the T260.3 baseline (do not edit: signatures are checked). */
    public static Recipe calvusB(long seed) { return CalvusSystemB.build(seed); }

    /** T260.4 candidate D: C25 with the lower cloud field refined (no low tongues). Experimental. */
    public static Recipe calvusD(long seed) { return CalvusSystemD.build(seed); }

    /** T260.1 calvus system, FROZEN as the T260.2 A baseline (do not edit: signatures are checked). */
    public static Recipe calvusA(long seed) { return new Calvus(seed).build(); }

    /**
     * Cumulonimbus calvus SYSTEM (T259/T260 references): one dominant tower rising off-centre out of a broad, irregular,
     * partly connected lower mass; two attached secondary towers of graded height; optionally one detached developing
     * cell; two to four separate associated cumulus. Towers are stacks of rising thermals that wander and taper, with
     * convective shoulders, a multi-summit softening crown on the primary, and cauliflower lumps only where the parent
     * surface is exposed (never buried, never in a crease, so valleys and notches stay open). Unknown spatial
     * relationships (line vs cluster orientation, along/cross placement) are bounded random per seed.
     */
    static final class Calvus {
        final long seed;
        final List<Component> comps = new ArrayList<>();
        final List<Bubble> bubbles = new ArrayList<>();
        final List<float[]> towerMeta = new ArrayList<>(); // per component: {comp, crownStart, crownEnd, softCrown, height}
        final float hp;
        static final float GAP = 70;

        Calvus(long seed) {
            this.seed = seed;
            hp = 1050 * (.94f + .12f * new java.util.Random(seed ^ 0x51A7L).nextFloat());
        }

        java.util.Random rng(int purpose) { return new java.util.Random(seed * 0x9E3779B97F4A7C15L + purpose * 0x632BE59BD9B4E019L); }

        Recipe build() {
            java.util.Random lay = rng(1);
            float theta = (float) (lay.nextFloat() * Math.PI);
            float ax = (float) Math.cos(theta), az = (float) Math.sin(theta);
            int side = lay.nextBoolean() ? 1 : -1;
            float rp = hp * (.30f + .04f * lay.nextFloat());
            // Primary: leans downwind (+x) with a crosswind component.
            float leanX = hp * (.14f + .10f * lay.nextFloat()), leanZ = hp * (.10f * (lay.nextFloat() * 2 - 1));
            tower("primary", Role.PRIMARY, 0, 0, 0, hp, rp * 1.35f, hp * (.16f + .03f * lay.nextFloat()), leanX, leanZ, 30, 6 + lay.nextInt(2), true, rng(10));
            // Attached secondaries along the (random) mass axis, either side of the primary.
            float h1 = hp * (.66f + .14f * lay.nextFloat()), r1 = hp * (.18f + .04f * lay.nextFloat());
            float d1 = (rp + r1) * (1.0f + .12f * lay.nextFloat());
            float s1x = ax * d1 * side, s1z = az * d1 * side;
            tower("secondary_1", Role.SECONDARY, 0, s1x, s1z, h1, r1 * 1.3f, r1 * .62f, leanX * .5f, leanZ * .4f, 28, 4, false, rng(11));
            float h2 = hp * (.44f + .16f * lay.nextFloat()), r2 = hp * (.14f + .04f * lay.nextFloat());
            float rot = (float) Math.toRadians((lay.nextFloat() * 2 - 1) * 40);
            float bx = -ax * side, bz = -az * side;
            float cr = (float) Math.cos(rot), sr = (float) Math.sin(rot);
            float d2 = (rp + r2) * (1.05f + .15f * lay.nextFloat());
            float s2x = (bx * cr - bz * sr) * d2, s2z = (bx * sr + bz * cr) * d2;
            tower("secondary_2", Role.SECONDARY, 0, s2x, s2z, h2, r2 * 1.3f, r2 * .64f, leanX * .4f, leanZ * .3f, 26, 3, false, rng(12));
            lowerMass(new float[][]{{0, 0, rp}, {s1x, s1z, r1}, {s2x, s2z, r2}, {s1x * 1.75f, s1z * 1.75f, r1 * .9f}}, ax, az, rng(20));
            int group = 1;
            // Detached developing cell (most seeds): perpendicular to the mass axis, held clear of everything.
            if (lay.nextFloat() < .8f) {
                float h3 = hp * (.32f + .16f * lay.nextFloat()), r3 = hp * (.11f + .04f * lay.nextFloat());
                int ps = lay.nextBoolean() ? 1 : -1;
                float dirx = -az * ps + ax * .3f * side, dirz = ax * ps + az * .3f * side;
                float n = (float) Math.hypot(dirx, dirz);
                placeDetached("developing_cell", Role.SECONDARY, group++, dirx / n, dirz / n, rp + r3 + GAP, h3, r3, r3 * .7f, 28, rng(13));
            }
            int assoc = 2 + lay.nextInt(3);
            for (int i = 0; i < assoc; i++) {
                float ang = (float) (lay.nextFloat() * Math.PI * 2);
                float h = hp * (.13f + .16f * lay.nextFloat()), r = hp * (.075f + .05f * lay.nextFloat());
                placeDetached("cumulus_" + (i + 1), Role.ASSOCIATED, group++, (float) Math.cos(ang), (float) Math.sin(ang), hp * (.9f + .5f * lay.nextFloat()), h, r, r * .72f, 22, rng(30 + i));
            }
            lumps(rng(40));
            return new Recipe("calvus_system", seed, List.copyOf(comps), List.copyOf(bubbles), 0, 8, 55, hp, 1, 0);
        }

        /** Detached component: pushed outward along (dx,dz) from the origin until it clears every other group. */
        void placeDetached(String name, Role role, int group, float dx, float dz, float start, float h, float r, float rc, float blend, java.util.Random random) {
            long state = random.nextLong();
            for (float dist = start; dist < 3000; dist += 25) {
                int mark = bubbles.size();
                tower(name, role, group, dx * dist, dz * dist, h, r * 1.25f, rc, h * .08f * dx, h * .08f * dz, blend, 2, false, new java.util.Random(state));
                if (clear(mark, group)) return;
                bubbles.subList(mark, bubbles.size()).clear();
                comps.remove(comps.size() - 1);
                towerMeta.remove(towerMeta.size() - 1);
            }
        }

        /** Minimum surface gap between the new bubbles and every other group, allowing for lump protrusion. */
        boolean clear(int mark, int group) {
            for (int i = mark; i < bubbles.size(); i++) {
                Bubble a = bubbles.get(i);
                for (int j = 0; j < mark; j++) {
                    Bubble b = bubbles.get(j);
                    if (comps.get(b.comp()).group() == group) continue;
                    float d = (float) Math.sqrt(sq(a.x - b.x) + sq(a.y - b.y) + sq(a.z - b.z)) - a.maxR() * 1.18f - b.maxR() * 1.18f;
                    if (d < GAP) return false;
                }
            }
            return true;
        }

        /**
         * A convective cell as a 3D cluster of rising turrets (T260 phase 2): a leaning main updraft turret reaching
         * {@code h} with radius {@code crownR}, plus turrets of decreasing height standing progressively further out at
         * irregular azimuths, out to a footprint of about {@code baseR}. Each turret is a stack of rounded thermals that
         * wander slightly. Broad base, narrow off-centre summit, staircase of domes and several summits from every side
         * come from the 3D arrangement, never from a lofted outline. {@code extra} = turrets besides the main one.
         */
        int tower(String name, Role role, int group, float x0, float z0, float h, float baseR, float crownR, float leanX, float leanZ,
                  float blend, int extra, boolean primary, java.util.Random r) {
            int comp = comps.size();
            comps.add(new Component(name, role, group, blend, x0, z0, h));
            int crownStart = -1, crownEnd = -1;
            float golden = (float) (Math.PI * (3 - Math.sqrt(5)));
            float a0 = (float) (r.nextFloat() * Math.PI * 2);
            for (int k = 0; k <= extra; k++) {
                float u = extra == 0 ? 0 : k / (float) extra;
                float th = k == 0 ? h : h * (.86f - .50f * u) * (.92f + .16f * r.nextFloat());
                float tr = crownR * (k == 0 ? 1 : (1.0f + .25f * u) * (.85f + .3f * r.nextFloat()));
                float dist = k == 0 ? 0 : (crownR * .75f + (baseR - tr - crownR * .75f) * (float) Math.sqrt(u)) * (.85f + .3f * r.nextFloat());
                dist = Math.max(dist, k == 0 ? 0 : crownR * .7f);
                if (k <= 2 && k >= 1 && extra >= 4) {
                    // Two thin near-summit turrets beside the main updraft (~137 degrees apart): their own summits and
                    // notches from most directions while the crown stays narrow.
                    th = h * (.80f + .09f * r.nextFloat() - (k - 1) * .07f);
                    tr = crownR * (.56f + .10f * r.nextFloat());
                    dist = crownR * (1.75f + .30f * r.nextFloat());
                }
                th = Math.max(th, tr * 1.6f);
                float az = a0 + k * golden + (r.nextFloat() - .5f) * .7f;
                float bx = x0 + (float) Math.cos(az) * dist, bz = z0 + (float) Math.sin(az) * dist;
                // Lean: the main updraft leans fully; outer turrets share part of it (sheared cell).
                float lf = k == 0 ? 1 : .45f * (th / h);
                float wx = 0, wz = 0;
                int n = Math.max(2, Math.round((th - tr * .4f) / (tr * .62f)));
                for (int i = 0; i < n; i++) {
                    float t = n == 1 ? 1 : i / (float) (n - 1);
                    float rad = tr * (1.12f - .22f * t) * (.9f + .2f * r.nextFloat());
                    float ry = rad * (.74f + .12f * r.nextFloat());
                    float y = ry * .45f + (th - ry - ry * .45f) * t;
                    float ang = (float) (r.nextFloat() * Math.PI * 2);
                    wx = wx * .5f + (float) Math.cos(ang) * .20f * rad;
                    wz = wz * .5f + (float) Math.sin(ang) * .20f * rad;
                    float lt = (float) Math.pow(Math.max(0, y) / h, 1.5) * lf;
                    float cx = bx + leanX * lt + wx, cz = bz + leanZ * lt + wz;
                    boolean top = i == n - 1;
                    if (k == 0 && i == n - 2) crownStart = bubbles.size();
                    bubbles.add(new Bubble(cx, y, cz, rad * (.88f + .24f * r.nextFloat()), ry, rad * (.88f + .24f * r.nextFloat()), comp, top && primary ? 4 : 0));
                    if (k == 0 && top) crownEnd = bubbles.size();
                }
            }
            towerMeta.add(new float[]{comp, crownStart, crownEnd, primary ? 1 : 0, h, -1, -1});
            return comp;
        }

        /** Broad irregular lower mass under the attached cells: flattened mounds along and around the mass axis. */
        void lowerMass(float[][] cells, float ax, float az, java.util.Random r) {
            int comp = comps.size();
            comps.add(new Component("lower_mass", Role.LOWER_MASS, 0, 30, 0, 0, hp * .2f));
            int n = 20 + r.nextInt(8);
            float px = -az, pz = ax;
            for (int i = 0; i < n; i++) {
                float pick = r.nextFloat();
                float[] c = cells[pick < .10f ? 0 : pick < .42f ? 1 : pick < .74f ? 3 : 2];
                // Around each attached cell base, biased along the mass axis, with lateral arms for an irregular planform.
                float along = (r.nextFloat() * 2 - 1) * c[2] * 1.25f, across = (r.nextFloat() * 2 - 1) * c[2] * 1.05f;
                if (r.nextFloat() < .2f) along *= 1.4f;
                float x = c[0] + ax * along + px * across, z = c[1] + az * along + pz * across;
                float rad = hp * (.10f + .09f * r.nextFloat());
                float ry = rad * (.62f + .25f * r.nextFloat());
                float y = ry * (.25f + .35f * r.nextFloat());
                bubbles.add(new Bubble(x, y, z, rad * (.85f + .3f * r.nextFloat()), ry, rad * (.85f + .3f * r.nextFloat()), comp, 0));
            }
            towerMeta.add(new float[]{comp, -1, -1, 0, hp * .2f, -1, -1});
        }

        /** Cauliflower lumps on exposed macro surfaces (tier 1), small lumps on the upper growth region (tier 2). */
        void lumps(java.util.Random r) {
            List<Bubble> macro = new ArrayList<>(bubbles);
            Compact all = new Compact(new Recipe("", 0, comps, macro, 0, 0, 0, 0, 1, 0), macro, -1);
            List<Bubble> tier1 = new ArrayList<>();
            for (int i = 0; i < macro.size(); i++) {
                Bubble p = macro.get(i);
                float[] meta = towerMeta.get(p.comp());
                boolean crown = meta[3] > 0 && ((i >= meta[1] && i < meta[2]) || (i >= meta[5] && i < meta[6]));
                boolean mass = comps.get(p.comp()).role() == Role.LOWER_MASS;
                float r0 = Math.max(p.rx(), p.rz());
                int count = Math.round((crown ? 5 : mass ? 9 : 7) + (crown ? 3 : 7) * Math.min(1.5f, r0 / 220f));
                for (int k = 0; k < count * 3 && count > 0; k++) {
                    double el = Math.asin(Math.min(1, mass ? .25 + .75 * r.nextDouble() : -.25 + 1.2 * r.nextDouble()));
                    double az = r.nextDouble() * Math.PI * 2;
                    float dx = (float) (Math.cos(el) * Math.cos(az)), dy = (float) Math.sin(el), dz = (float) (Math.cos(el) * Math.sin(az));
                    float sx = p.x + p.rx * dx, sy = p.y + p.ry * dy, sz = p.z + p.rz * dz;
                    if (sy < 6) continue;
                    float lr = r0 * (crown ? .36f + .10f * r.nextFloat() : .30f + .14f * r.nextFloat());
                    // Exposed (not buried by other macro bubbles) and not in a crease between macro bubbles.
                    if (all.distance(sx, sy, sz) < -.12f * lr) continue;
                    if (creased(macro, i, sx, sy, sz, .45f * lr)) continue;
                    float f = crown ? .80f : .90f;
                    tier1.add(new Bubble(p.x + (sx - p.x) * f, p.y + (sy - p.y) * f, p.z + (sz - p.z) * f, lr * (.9f + .2f * r.nextFloat()),
                            lr * (.82f + .14f * r.nextFloat()), lr * (.9f + .2f * r.nextFloat()), p.comp(), crown ? 3 : 1));
                    if (--count == 0) break;
                }
            }
            bubbles.addAll(tier1);
            // Tier 2: small lumps on the upper part of active (non-crown, non-mass) towers.
            List<Bubble> tier2 = new ArrayList<>();
            for (Bubble l : tier1) {
                if (l.tier() != 1) continue;
                Component c = comps.get(l.comp());
                if (c.role() == Role.LOWER_MASS || l.y() < c.top() * .42f) continue;
                int count = 2 + r.nextInt(3);
                for (int k = 0; k < count; k++) {
                    double el = Math.asin(.05 + .9 * r.nextDouble()), az = r.nextDouble() * Math.PI * 2;
                    float dx = (float) (Math.cos(el) * Math.cos(az)), dy = (float) Math.sin(el), dz = (float) (Math.cos(el) * Math.sin(az));
                    float sx = l.x + l.rx * dx, sy = l.y + l.ry * dy, sz = l.z + l.rz * dz;
                    float sr = l.rx * (.34f + .12f * r.nextFloat());
                    if (all.distance(sx, sy, sz) < -.6f * sr) continue;
                    tier2.add(new Bubble(l.x + (sx - l.x) * .8f, l.y + (sy - l.y) * .8f, l.z + (sz - l.z) * .8f, sr, sr * .85f, sr, l.comp(), 2));
                }
            }
            bubbles.addAll(tier2);
        }

        /** True when the surface point lies within {@code margin} of another macro bubble's surface (a crease/valley). */
        static boolean creased(List<Bubble> macro, int self, float x, float y, float z, float margin) {
            for (int j = 0; j < macro.size(); j++) {
                if (j == self) continue;
                Bubble b = macro.get(j);
                float d = ellipsoid(x - b.x, y - b.y, z - b.z, b.rx, b.ry, b.rz);
                if (Math.abs(d) < margin) return true;
            }
            return false;
        }

        static float sq(float v) { return v * v; }
    }
}
