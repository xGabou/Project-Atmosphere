package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.type.CloudFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudShapeProfile;

/** Numerical anatomy, not image/pixel golden files. Runs without a Minecraft client. */
public final class StormMorphologyPrototypeSandbox {
    private static int assertions;

    public static void main(String[] args) {
        for (long seed : new long[]{20261008L, 20268927L, 20276846L}) {
            var calvus = compile("cumulonimbus_calvus", seed);
            var capillatus = compile("cumulonimbus_capillatus", seed);
            var supercell = compile("supercell", seed);
            check(calvus.signature() == compile("cumulonimbus_calvus", seed).signature(), "deterministic compile");
            check(calvus.filaments().isEmpty(), "calvus has no mature fibrous canopy");
            check(capillatus.filaments().size() > 0, "capillatus has directional fibres");
            check(capillatus.canopyLength() / capillatus.radius() > 2 * calvus.canopyLength() / calvus.radius(), "distinct canopy/crown ratios");
            check(supercell.wallDepth() > 0 && calvus.wallDepth() == 0, "organised supercell-only lowered base");
            for (var program : new StormMorphologyPrototype.Program[]{calvus, capillatus, supercell}) {
                check(program.widthAt(.48f) > program.radius() * .25f, "no disconnected thin waist");
                for (int i = 1; i < 18; i++) {
                    float y = program.height() * i / 20f;
                    check(program.density(program.axisX(i / 20f), y, 0, StormMorphologyPrototype.Scale.MACRO) > .5f, "continuous rising mass");
                }
                var b = program.bounds();
                for (var fibre : program.filaments()) {
                    check(fibre.length() / (2 * fibre.width()) > 10, "ice filament aspect ratio");
                    check(program.density(fibre.startX(), fibre.height(), fibre.startZ(), StormMorphologyPrototype.Scale.MACRO) > .01f,
                            "fibre root attached to continuous macro canopy");
                    for (int i = 0; i <= 24; i++) {
                        float v = i / 24f;
                        float x = fibre.startX() + fibre.length() * v;
                        float z = fibre.startZ() + fibre.fan() * (v * .7f + v * v * .3f);
                        float y = fibre.height() + fibre.rise() * v + program.height() * .015f * (float) Math.sin(v * Math.PI);
                        check(program.density(x, y, z, StormMorphologyPrototype.Scale.MESO) > .1f,
                                "continuous tapered curved filament spine");
                    }
                }
                for (int i = 0; i < 1000; i++) {
                    float x = mix(b.minX() - 20, b.maxX() + 20, unit(i * 13 + 1));
                    float y = mix(b.minY() - 20, b.maxY() + 20, unit(i * 17 + 5));
                    float z = mix(b.minZ() - 20, b.maxZ() + 20, unit(i * 23 + 7));
                    float d = program.density(x, y, z, StormMorphologyPrototype.Scale.MICRO);
                    check(Float.isFinite(d) && d >= 0 && d <= 1, "bounded density");
                    check(program.unboundedDistance(x, y, z, StormMorphologyPrototype.Scale.MICRO)
                            >= program.radius() * .0275f || b.contains(x, y, z), "unclipped support enclosed by bounds");
                }
            }
        }
        controls();
        remainingControls();
        commonProfileAnatomy();
        canopyTipEnclosure();
        organisedBaseAndBounds();
        attachedGrowthAxes();
        smoothUnionCertificate();
        rayIntervalLattice();
        anatomyMetrics();
        boolean rejected = false;
        try { StormMorphologyPrototype.compile("cirrus", CloudShapeProfile.DEFAULT, 1); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "reject unrelated family rather than silent generic fallback");
        rejected = false;
        try { StormMorphologyPrototype.compile("supercell", profile(0, Float.NaN), 1); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "reject non-finite geometry inputs");
        System.out.println("T255 PASS: " + assertions + " shape-only anatomy/control/bounds assertions; NOT native/GPU acceptance.");
    }

    private static void controls() {
        var neutral = StormMorphologyPrototype.compile("supercell", profile(-1, 0), 42);
        var tilt = StormMorphologyPrototype.compile("supercell", profile(0, .8f), 42);
        var shear = StormMorphologyPrototype.compile("supercell", profile(1, .8f), 42);
        var split = StormMorphologyPrototype.compile("supercell", profile(2, 1), 42);
        var narrow = StormMorphologyPrototype.compile("supercell", profile(3, 1), 42);
        var wall = StormMorphologyPrototype.compile("supercell", profile(4, 1), 42);
        check(tilt.axisX(.75f) > neutral.axisX(.75f) + neutral.radius() * .3f, "tilt changes tower axis");
        check(shear.axisX(.9f) > neutral.axisX(.9f) + neutral.radius() * .3f, "shear bends upper axis");
        check(narrow.widthAt(.5f) < neutral.widthAt(.5f) * .85f, "narrowing changes tower width");
        check(measuredWidth(narrow, .5f) < measuredWidth(neutral, .5f) * .85f, "narrowing changes evaluated width, not only metadata");
        check(split.secondaryOffset() > neutral.secondaryOffset() + neutral.radius() * .4f, "split creates secondary organised tower");
        check(split.density(split.secondaryX(), split.height() * .55f, split.secondaryOffset(), StormMorphologyPrototype.Scale.MACRO) > neutral.density(split.secondaryX(), split.height() * .55f, split.secondaryOffset(), StormMorphologyPrototype.Scale.MACRO) + .2f, "split changes actual volume");
        check(wall.wallDepth() > neutral.wallDepth() + neutral.radius() * .15f, "wall changes lower extent");
        check(wall.density(0, -wall.wallDepth() * .65f, 0, StormMorphologyPrototype.Scale.MACRO) > neutral.density(0, -wall.wallDepth() * .65f, 0, StormMorphologyPrototype.Scale.MACRO) + .2f, "wall changes actual lower volume");
        float tiltedEdge = tilt.axisX(.35f) + tilt.radius() * .55f;
        check(tilt.density(tiltedEdge, tilt.height() * .35f, 0, StormMorphologyPrototype.Scale.MACRO)
                > neutral.density(tiltedEdge, tilt.height() * .35f, 0, StormMorphologyPrototype.Scale.MACRO) + .15f,
                "tilt changes the lower tower, below the legitimate forward-flank shield");
        var changedControls = new StormMorphologyPrototype.Program[]{tilt, shear, split, narrow, wall};
        String[] names = {"tilt", "shear", "split", "narrowing", "wall"};
        for (int k = 0; k < changedControls.length; k++) {
            double l1 = 0;
            for (int iy = 0; iy < 40; iy++) for (int ix = 0; ix < 50; ix++) for (int iz = 0; iz < 17; iz++) {
                float x = -350 + ix * 35, y = -350 + iy * 30, z = -400 + iz * 50;
                l1 += Math.abs(neutral.density(x, y, z, StormMorphologyPrototype.Scale.MACRO)
                        - changedControls[k].density(x, y, z, StormMorphologyPrototype.Scale.MACRO));
            }
            check(l1 > 1, names[k] + " changes evaluated volume in fixed common coordinates");
            System.out.println("T255 macroControl=" + names[k] + " densityL1=" + l1);
        }
    }

    private static void remainingControls() {
        float[] inputs = {100, 25, 170, 9, 15, .8f, .2f, .3f, .6f, .5f, .8f, .5f, .6f, .8f};
        var baseline = fromInputs(inputs, 42);
        String[] fields = {"baseRadius", "baseOffset", "topOffset", "lobeCountMin", "lobeCountMax",
                "lobeStrength", "verticalTilt", "windShearStrength", "cellSplitStrength", "towerNarrowing",
                "anvilSpread", "baseFlattening", "edgeRaggedness", "stormWallStrength"};
        for (int field : new int[]{0, 1, 2, 3, 4, 5, 10, 11, 12}) {
            float[] changed = inputs.clone();
            changed[field] = switch (field) {
                case 0 -> 150; case 1 -> 80; case 2 -> 240; case 3 -> 15; case 4 -> 32;
                case 5, 10, 11, 12 -> 0;
                default -> throw new AssertionError();
            };
            var other = fromInputs(changed, 42);
            check(other.signature() != baseline.signature(), fields[field] + " packed content changes");
            double difference = 0;
            // Common world-coordinate sampling domain; NOT normalised by each model's own dimensions.
            for (int iy = 0; iy < 34; iy++) for (int ix = 0; ix < 50; ix++) for (int iz = 0; iz < 13; iz++) {
                float x = -350 + ix * 35, y = -300 + iy * 35, z = -300 + iz * 50;
                difference += Math.abs(baseline.density(x, y, z, StormMorphologyPrototype.Scale.MICRO)
                        - other.density(x, y, z, StormMorphologyPrototype.Scale.MICRO));
            }
            check(difference > .01, fields[field] + " must change actual evaluated geometry at fixed seed");
            System.out.println("T255 control=" + fields[field] + " densityL1=" + difference);
        }
        var many = fromInputs(new float[]{100, 25, 170, 32, 32, 1, 1, 1, 1, 1, 1, 1, 1, 1}, 9);
        check(many.sectors().size() == 32 && many.filaments().size() == 32, "authoring not truncated to old eight candidate slots");
        float[] copy = many.packed(); copy[0] = -1;
        check(many.packed()[0] > 0, "packed upload cannot mutate program");
        boolean immutable = false;
        try { many.sectors().clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "immutable meso program");
    }

    private static void commonProfileAnatomy() {
        // Hold all material AND shape settings identical: archetype must change anatomy itself.
        var p = profile(-1, 0);
        var a = StormMorphologyPrototype.compile("cumulonimbus_calvus", p, 9);
        var b = StormMorphologyPrototype.compile("cumulonimbus_capillatus", p, 9);
        var c = StormMorphologyPrototype.compile("supercell", p, 9);
        check(a.signature() != b.signature() && b.signature() != c.signature(), "archetype is explicit");
        float x = b.axisX(.88f) + b.canopyLength() * .72f;
        check(b.density(x, b.height() * .92f, 0, StormMorphologyPrototype.Scale.MACRO)
                > a.density(x, a.height() * .92f, 0, StormMorphologyPrototype.Scale.MACRO) + .4f,
                "same settings: mature canopy vs developing crown");
        check(c.canopyLength() > b.canopyLength() && a.filaments().isEmpty(), "same settings: distinct macro and meso models");
    }

    private static void canopyTipEnclosure() {
        var p = compile("cumulonimbus_capillatus", 20261008L);
        float u = -p.radius() * .65f / p.canopyLength();
        float z = p.radius() * .026f;
        // Probe the vanishing fan edge: outside-support sag must not create a downward tail.
        float oldSagY = p.height() * .87f + p.height() * .07f * Math.max(-.25f, u)
                - p.radius() * .05f * z * z;
        float x = p.axisX(.88f) - p.radius() * .65f;
        check(oldSagY < -p.radius() * .3f, "regression probe lies below the non-wall condensation base");
        check(p.unboundedDistance(x, oldSagY, z, StormMorphologyPrototype.Scale.MESO)
                >= p.radius() * .0275f, "vanishing canopy edge cannot create unbounded downward support");
    }

    private static void organisedBaseAndBounds() {
        var shape = profile(-1, 0);
        var ordinary = StormMorphologyPrototype.compile("cumulonimbus_capillatus", shape, 42);
        var organised = StormMorphologyPrototype.compile("supercell", shape, 42);
        check(measuredWidth(organised, .02f) > measuredWidth(ordinary, .02f) * 1.4f,
                "identical controls: supercell has an organised broad asymmetric foot");
        var wall = StormMorphologyPrototype.compile("supercell", profile(4, 1), 42);
        float centre = lowerExtent(wall, -wall.radius() * .12f, -wall.radius() * .16f);
        float edge = lowerExtent(wall, wall.radius() * .40f, -wall.radius() * .16f);
        check(edge > centre + wall.wallDepth() * .3f, "lowered shared base is a bowl, not a flat-ended plug");
        var supercell = compile("supercell", 20261008L);
        check(supercell.bounds().maxX() - supercell.bounds().minX() < 6000,
                "anatomy-derived bounds do not retain symmetric empty upwind reach");
        for (int n = 0; n < 24; n++) {
            float sign = n % 2 == 0 ? 1 : -1;
            var p = fromInputs(new float[]{n % 3 == 0 ? 8 : 200, 80, n % 3 == 0 ? 400 : 120,
                    32, 32, 1, sign, n % 4 < 2 ? sign : -sign, 1, 1, 1, 1, 1, 1}, n);
            var b = p.bounds();
            float[] packed = p.packed();
            check(packed.length == 48 + p.growthAxes().size() * 8 + p.filaments().size() * 8,
                    "complete twelve-texel hierarchy header and variable-length features");
            check(packed[48] == p.growthAxes().get(0).directionX() && packed[15] == StormMorphologyPrototype.VERSION,
                    "feature offsets and interpretation version agree");
            for (int i = 0; i < 1000; i++) {
                float x = mix(b.minX() - 100, b.maxX() + 100, unit(i * 13 + n));
                float y = mix(b.minY() - 100, b.maxY() + 100, unit(i * 17 + n));
                float z = mix(b.minZ() - 100, b.maxZ() + 100, unit(i * 23 + n));
                check(p.unboundedDistance(x, y, z, StormMorphologyPrototype.Scale.MICRO)
                        >= Math.max(1, p.radius() * .0275f) || b.contains(x, y, z),
                        "extreme signed tilt/shear/32-feature support enclosed without clipping");
                boolean candidate = false;
                for (var group : p.componentBounds()) candidate |= group.contains(x, y, z);
                check(p.unboundedDistance(x, y, z, StormMorphologyPrototype.Scale.MICRO)
                        >= Math.max(1, p.radius() * .0275f) || candidate,
                        "complete component hierarchy never rejects occupied unaccelerated geometry");
            }
        }
    }

    private static void attachedGrowthAxes() {
        var p = compile("cumulonimbus_calvus", 20261008L);
        check(p.growthAxes().size() == p.sectors().size(), "authored growth organisation compiles to complete swept axes");
        for (var axis : p.growthAxes()) {
            float x = p.axisX(axis.start()) + axis.directionX() * axis.rootRadius();
            float z = axis.directionZ() * axis.rootRadius();
            check(p.density(x, axis.start() * p.height(), z, StormMorphologyPrototype.Scale.MACRO) > .5f,
                    "every major growth axis roots inside coherent macro mass");
            for (int i = 0; i <= 24; i++) {
                float u = i / 24f;
                float t = axis.start() + (axis.tip() - axis.start()) * u;
                float ease = u * u * (3 - 2 * u);
                float radius = axis.rootRadius() + (axis.tipRadius() - axis.rootRadius()) * ease;
                check(p.density(p.axisX(t) + axis.directionX() * radius, t * p.height(),
                        axis.directionZ() * radius, StormMorphologyPrototype.Scale.MESO) > .1f,
                        "unequal swept turret stays continuous from root to rounded tip");
            }
        }
        var cap = compile("cumulonimbus_capillatus", 20261008L);
        float distal = cap.axisX(.88f) + cap.canopyLength() * .97f;
        check(cap.density(distal, cap.height() * .93f, cap.radius(), StormMorphologyPrototype.Scale.MACRO) > .3f,
                "mature fan retains a broad distal front, not an elliptical paddle tip");
    }

    private static float lowerExtent(StormMorphologyPrototype.Program p, float x, float z) {
        for (float y = p.bounds().minY(); y < p.height(); y += p.radius() / 100) {
            if (p.density(x, y, z, StormMorphologyPrototype.Scale.MACRO) > .5f) return y;
        }
        throw new AssertionError("missing lower base support");
    }

    private static void smoothUnionCertificate() {
        // If all primitive distances >=m and k<=K, a>=m-K is invariant.
        // For a=m-delta, the next deficit is <=delta+(K-delta)^2/(4K)<=K.
        // This certifies complete support bounds; it does NOT certify an SDF.
        float maximumK = .22f, distance = 0;
        for (int i = 0; i < 20000; i++) {
            float k = i % 3 == 0 ? maximumK : .08f;
            float primitive = i % 5 == 0 ? .03f : 0;
            float h = Math.max(k - Math.abs(distance - primitive), 0) / k;
            distance = Math.min(distance, primitive) - h * h * k * .25f;
            check(distance >= -maximumK - 1e-6f, "smooth-min enclosure allowance independent of authored count");
        }
    }

    private static void anatomyMetrics() {
        // Measured anatomy diagnostics; no screenshot golden or subjective pass threshold.
        for (String id : new String[]{"cumulonimbus_calvus", "cumulonimbus_capillatus", "supercell"}) {
            for (long seed : new long[]{20261008L, 20268927L, 20276846L}) {
                var p = compile(id, seed);
                var b = p.bounds();
                int macroOccupied = 0, mesoOccupied = 0, added = 0, exposedAxes = 0;
                for (int ix = 0; ix < 60; ix++) for (int iy = 0; iy < 40; iy++) for (int iz = 0; iz < 32; iz++) {
                    float x = mix(b.minX(), b.maxX(), (ix + .5f) / 60);
                    float y = mix(b.minY(), b.maxY(), (iy + .5f) / 40);
                    float z = mix(b.minZ(), b.maxZ(), (iz + .5f) / 32);
                    boolean macro = p.density(x, y, z, StormMorphologyPrototype.Scale.MACRO) > .5f;
                    boolean meso = p.density(x, y, z, StormMorphologyPrototype.Scale.MESO) > .5f;
                    if (macro) macroOccupied++;
                    if (meso) mesoOccupied++;
                    if (meso && !macro) added++;
                }
                for (var axis : p.growthAxes()) {
                    boolean exposed = false;
                    for (int i = 4; i <= 19; i++) {
                        float u = i / 20f, t = axis.start() + (axis.tip() - axis.start()) * u;
                        float ease = u * u * (3 - 2 * u);
                        float radial = axis.rootRadius() + (axis.tipRadius() - axis.rootRadius()) * ease;
                        float cap = (float) Math.sqrt(Math.max(0, 1 - Math.pow(u, 8)));
                        radial += axis.radius() * (.5f + 2 * u * (1 - u)) * cap * .85f;
                        float x = p.axisX(t) + axis.directionX() * radial, z = axis.directionZ() * radial;
                        exposed |= p.density(x, t * p.height(), z, StormMorphologyPrototype.Scale.MACRO) < .01f
                                && p.density(x, t * p.height(), z, StormMorphologyPrototype.Scale.MESO) > .5f;
                    }
                    if (exposed) exposedAxes++;
                }
                check(macroOccupied > 0 && mesoOccupied >= macroOccupied, "meso decorates occupied coherent macro anatomy");
                System.out.printf(java.util.Locale.ROOT,
                        "T255_ANATOMY type=%s seed=%d macroVoxels=%d mesoVoxels=%d mesoAddedFraction=%.5f exposedAxes=%d/%d width15=%.3f width50=%.3f width85=%.3f leanOverHeight=%.5f canopyOverRadius=%.5f NOT_realism_acceptance%n",
                        id, seed, macroOccupied, mesoOccupied, added / (double) mesoOccupied,
                        exposedAxes, p.growthAxes().size(), measuredWidth(p, .15f), measuredWidth(p, .50f),
                        measuredWidth(p, .85f), (p.axisX(.9f) - p.axisX(.1f)) / p.height(), p.canopyLength() / p.radius());
            }
        }
    }

    private static void rayIntervalLattice() {
        for (String id : new String[]{"cumulonimbus_calvus", "cumulonimbus_capillatus", "supercell"}) {
            for (long seed : new long[]{20261008L, 20268927L, 20276846L}) {
                var p = compile(id, seed);
                var b = p.bounds();
                for (int side = 0; side < 6; side++) for (int probe = 0; probe < 5; probe++) {
                    double[] aim = {(b.minX() + b.maxX()) * .5, p.height() * (.1 + probe * .19),
                            p.radius() * (probe - 2) * .3};
                    double[] direction = {0, 0, 0};
                    direction[side / 2] = side % 2 == 0 ? 1 : -1;
                    // Includes exact parallel slabs and slightly oblique rays.
                    if (probe == 4) direction[(side / 2 + 1) % 3] = .02;
                    double norm = Math.sqrt(direction[0] * direction[0] + direction[1] * direction[1] + direction[2] * direction[2]);
                    double[] origin = new double[3];
                    for (int axis = 0; axis < 3; axis++) {
                        direction[axis] /= norm;
                        origin[axis] = aim[axis] - direction[axis] * 6000;
                    }
                    double[] whole = rayBox(origin, direction, b, 0, 12000);
                    if (whole[1] < whole[0]) continue;
                    var intervals = new java.util.ArrayList<double[]>();
                    for (var group : p.componentBounds()) {
                        double[] interval = rayBox(origin, direction, group, whole[0], whole[1]);
                        if (interval[1] >= interval[0]) intervals.add(interval);
                    }
                    intervals.sort(java.util.Comparator.comparingDouble(v -> v[0]));
                    int intervalIndex = 0, visited = 0;
                    double reference = 0, accelerated = 0;
                    double initial = whole[0] + .731 * 4;
                    int count = (int) Math.ceil((whole[1] - initial) / 4);
                    for (int sample = 0; sample < count; sample++) {
                        double t = initial + sample * 4;
                        double centre = t + .5 * Math.min(4, whole[1] - t);
                        reference += p.density((float) (origin[0] + direction[0] * centre),
                                (float) (origin[1] + direction[1] * centre), (float) (origin[2] + direction[2] * centre),
                                StormMorphologyPrototype.Scale.MESO);
                    }
                    for (int sample = 0; sample < count; sample++) {
                        double t = initial + sample * 4;
                        double centre = t + .5 * Math.min(4, whole[1] - t);
                        while (intervalIndex < intervals.size() && centre > intervals.get(intervalIndex)[1]) intervalIndex++;
                        if (intervalIndex >= intervals.size()) break;
                        if (centre < intervals.get(intervalIndex)[0]) {
                            // Whole integer lattice jump, never restart at the box boundary.
                            sample += Math.max(0, (int) Math.ceil((intervals.get(intervalIndex)[0] - centre) / 4));
                            if (sample >= count) break;
                            t = initial + sample * 4;
                            centre = t + .5 * Math.min(4, whole[1] - t);
                        }
                        accelerated += p.density((float) (origin[0] + direction[0] * centre),
                                (float) (origin[1] + direction[1] * centre), (float) (origin[2] + direction[2] * centre),
                                StormMorphologyPrototype.Scale.MESO);
                        visited++;
                    }
                    check(Math.abs(reference - accelerated) < 1e-6, "component traversal preserves occupied samples and original ray lattice");
                    check(visited <= count, "complete hierarchy does not add reference samples");
                }
            }
        }
    }

    private static double[] rayBox(double[] origin, double[] direction, StormMorphologyPrototype.Bounds b,
                                   double low, double high) {
        double[] lo = {b.minX(), b.minY(), b.minZ()}, hi = {b.maxX(), b.maxY(), b.maxZ()};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(direction[axis]) < 1e-8) {
                if (origin[axis] < lo[axis] || origin[axis] > hi[axis]) high = -1;
            } else {
                double a = (lo[axis] - origin[axis]) / direction[axis], c = (hi[axis] - origin[axis]) / direction[axis];
                low = Math.max(low, Math.min(a, c)); high = Math.min(high, Math.max(a, c));
            }
        }
        return new double[]{low, high};
    }

    private static float measuredWidth(StormMorphologyPrototype.Program p, float height) {
        int occupied = 0;
        float stride = p.radius() / 40;
        for (int i = -80; i <= 80; i++) {
            if (p.density(p.axisX(height) + i * stride, p.height() * height, 0,
                    StormMorphologyPrototype.Scale.MACRO) > .5f) occupied++;
        }
        return occupied * stride;
    }

    private static StormMorphologyPrototype.Program fromInputs(float[] v, long seed) {
        return StormMorphologyPrototype.compile("supercell", new CloudShapeProfile("test/controls",
                v[0], v[1], v[2], (int) v[3], (int) v[4], v[5], v[6], v[7], v[8], v[9], v[10], v[11], v[12], v[13]), seed);
    }

    private static StormMorphologyPrototype.Program compile(String id, long seed) {
        return StormMorphologyPrototype.compile(id, CloudShapeProfile.defaultFor(id, CloudFamily.CUMULONIMBUS, null), seed);
    }

    private static CloudShapeProfile profile(int control, float value) {
        return new CloudShapeProfile("prototype/test", 100, 25, 170, 9, 15, .8f,
                control == 0 ? value : 0, control == 1 ? value : 0, control == 2 ? value : 0,
                control == 3 ? value : 0, .8f, .7f, .6f, control == 4 ? value : 0);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    private static float unit(int seed) { return ((seed * 1103515245 + 12345) & 0xffff) / 65535f; }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
}
