package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.type.CloudShapeProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;

/**
 * T255 experimental shape-only compiler/evaluator. Not adopted by production.
 * Local coordinates: X downwind, Y up, Z crosswind, condensation level Y=0.
 * Macro anatomy is a continuous growth envelope, not a row of overlapping balls.
 * Meso sectors sculpt that envelope; ice ribbons attach to a mature canopy.
 */
public final class StormMorphologyPrototype {
    public static final int VERSION = 4;
    public enum Archetype { CALVUS, CAPILLATUS, SUPERCELL }
    public enum Scale { MACRO, MESO, MICRO }

    public record GrowthSector(float angle, float height, float span, float strength) {}
    /** Attached swept growth axis: unequal birth/tip heights and changing cross-section, not a ball. */
    public record GrowthAxis(float directionX, float directionZ, float start, float tip,
                             float rootRadius, float tipRadius, float radius) {}
    /** A curved tapering ribbon, parameterised by downwind progress, not a stretched blob. */
    public record Filament(float startX, float length, float startZ, float fan,
                           float height, float rise, float width, float depth) {}
    public record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        public boolean contains(float x, float y, float z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }

    private StormMorphologyPrototype() {}

    public static Program compile(String typeId, CloudShapeProfile shape, long seed) {
        Archetype archetype = switch (Objects.requireNonNull(typeId, "typeId")) {
            case "cumulonimbus_calvus" -> Archetype.CALVUS;
            case "cumulonimbus_capillatus" -> Archetype.CAPILLATUS;
            case "supercell" -> Archetype.SUPERCELL;
            default -> throw new IllegalArgumentException("Not an experimental storm archetype: " + typeId);
        };
        Objects.requireNonNull(shape, "shape");
        float[] controls = {shape.getBaseRadius(), shape.getBaseOffset(), shape.getTopOffset(),
                shape.getLobeStrength(), shape.getVerticalTilt(), shape.getWindShearStrength(),
                shape.getCellSplitStrength(), shape.getTowerNarrowing(), shape.getAnvilSpread(),
                shape.getBaseFlattening(), shape.getEdgeRaggedness(), shape.getStormWallStrength()};
        for (float value : controls) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite morphology input");
        }
        SplittableRandom random = new SplittableRandom(seed);
        int count = random.nextInt(shape.getLobeCountMin(), shape.getLobeCountMax() + 1);
        List<GrowthSector> sectors = new ArrayList<>(count);
        // Stratified height and irrational angular spacing avoid equal-radius lobe chains.
        for (int i = 0; i < count; i++) {
            sectors.add(new GrowthSector((float) (i * 2.39996323 + random.nextDouble(-.35, .35)),
                    (float) (.14 + .73 * (i + random.nextDouble(.1, .9)) / count),
                    (float) random.nextDouble(.09, .23), (float) random.nextDouble(.55, 1)));
        }
        return new Program(archetype, shape, sectors, random);
    }

    public static final class Program {
        private final Archetype archetype;
        private final float radius, height, baseDepth, tilt, shear, narrowing, spread;
        private final float flattening, raggedness, split, wall, structure;
        private final float canopyLength, wallDepth, secondaryOffset;
        private final float bodyWidthBound, supportMargin, fibreMinY, fibreMaxY;
        private final List<GrowthSector> sectors;
        private final List<GrowthAxis> growthAxes;
        private final List<Filament> filaments;
        private final Bounds bounds;
        private final List<Bounds> componentBounds;
        private final long signature;

        private Program(Archetype archetype, CloudShapeProfile shape,
                        List<GrowthSector> sectors, SplittableRandom random) {
            this.archetype = archetype;
            radius = shape.getBaseRadius() * 2.5f;
            height = shape.getTopOffset() * 4;
            baseDepth = shape.getBaseOffset() * 2;
            tilt = shape.getVerticalTilt();
            shear = shape.getWindShearStrength();
            narrowing = shape.getTowerNarrowing();
            spread = shape.getAnvilSpread();
            flattening = shape.getBaseFlattening();
            raggedness = shape.getEdgeRaggedness();
            split = shape.getCellSplitStrength();
            // Explicit legacy migration: lowering is not applicable to calvus/capillatus.
            wall = archetype == Archetype.SUPERCELL ? shape.getStormWallStrength() : 0;
            structure = shape.getLobeStrength();
            this.sectors = List.copyOf(sectors);
            List<GrowthAxis> axes = new ArrayList<>();
            for (GrowthSector s : sectors) {
                float birth = Math.max(.02f, s.height() - s.span() * 1.8f);
                float tip = Math.min(1.12f, s.height() + s.span());
                axes.add(new GrowthAxis((float) Math.cos(s.angle()), (float) Math.sin(s.angle()), birth, tip,
                        widthAt(birth) * .40f, widthAt(tip) * (.85f + .20f * s.strength()),
                        radius * (.16f + .20f * s.strength()) * (float) Math.sqrt(structure)));
            }
            growthAxes = List.copyOf(axes);
            canopyLength = radius * switch (archetype) {
                case CALVUS -> .35f + .65f * spread;
                case CAPILLATUS -> 1.4f + 2.7f * spread;
                case SUPERCELL -> 1.8f + 3.2f * spread;
            };
            wallDepth = archetype == Archetype.SUPERCELL ? radius * .9f * wall : 0;
            secondaryOffset = radius * split * (archetype == Archetype.SUPERCELL ? 1.3f : .45f);
            List<Filament> fibres = new ArrayList<>();
            if (archetype != Archetype.CALVUS) {
                int fibreCount = Math.max(4, sectors.size());
                int parents = (fibreCount + 2) / 3;
                float parentFan = 0, rootX = 0, rootZ = 0, rootY = 0;
                for (int i = 0; i < fibreCount; i++) {
                    if (i % 3 == 0) {
                        // Each parent grows several branches from one attached root, not a uniform comb.
                        parentFan = (float) ((i / 3 + .5) / parents * 2 - 1 + random.nextDouble(-.12, .12));
                        float start = (float) random.nextDouble(.12, .35);
                        rootX = axisX(.88f) + canopyLength * start;
                        rootZ = parentFan * radius * .45f;
                        float fanCap = (float) Math.sqrt(smooth(-radius * .65f, radius * .18f, canopyLength * start));
                        float cross = radius * (.70f + (1 + spread) * smooth(-.1f, .58f, start)) * fanCap;
                        float sweep = archetype == Archetype.SUPERCELL ? radius * .45f * shear * smooth(.12f, 1, start) : 0;
                        float sag = clamp((rootZ - sweep) / Math.max(cross, 1), -1, 1);
                        float half = radius * .18f * (1 - .66f * smooth(.05f, 1.1f, start)) * fanCap;
                        rootY = height * .87f + height * .07f * start - radius * .05f * sag * sag + half * .45f;
                    }
                    float bundleWidth = radius * (float) random.nextDouble(.06, .10);
                    float branchFan = parentFan + (float) random.nextDouble(-.20, .20);
                    fibres.add(new Filament(rootX,
                            Math.max(bundleWidth * 22, canopyLength * (float) random.nextDouble(.65, 1.05)), rootZ,
                            branchFan * radius * (1 + spread) - rootZ, rootY,
                            height * (float) random.nextDouble(.035, .10) + shear * height * .04f,
                            bundleWidth, radius * .035f));
                }
            }
            filaments = List.copyOf(fibres);
            float widthBound = radius * 1.2f, topBound = height;
            for (GrowthAxis axis : growthAxes) {
                widthBound = Math.max(widthBound, Math.max(axis.rootRadius(), axis.tipRadius()) + axis.radius());
                topBound = Math.max(topBound, height * axis.tip());
            }
            bodyWidthBound = widthBound;
            // Every smooth union can lower min(distance) by at most k/4. Include feather and micro.
            supportMargin = Math.max(2, radius * .055f) * .5f + radius * .022f * raggedness
                    + radius * (.04f + .055f + .05f + .035f + growthAxes.size() * .02f + filaments.size() * .00625f) + 2;
            float a = height * .9f * tilt, b = height * .38f * shear;
            float axisMin = Math.min(0, a + b), axisMax = Math.max(0, a + b);
            if (b != 0) {
                float turning = -a / (2 * b);
                if (turning > 0 && turning < 1) {
                    float value = axisX(turning);
                    axisMin = Math.min(axisMin, value); axisMax = Math.max(axisMax, value);
                }
            }
            float footX = archetype == Archetype.SUPERCELL ? 1.55f : 1;
            float footZ = archetype == Archetype.SUPERCELL ? 1.3f : 1;
            float minX = axisMin - bodyWidthBound * footX - radius * .22f;
            float maxX = axisMax + bodyWidthBound * footX;
            float minZ = -bodyWidthBound * footZ, maxZ = Math.max(bodyWidthBound * footZ, secondaryOffset + radius * .55f);
            float maxY = topBound;
            if (archetype == Archetype.CALVUS) {
                float crownX = axisX(.80f) + canopyLength * .25f;
                float crownRadius = radius * .82f + canopyLength * .35f;
                minX = Math.min(minX, crownX - crownRadius); maxX = Math.max(maxX, crownX + crownRadius);
            } else {
                minX = Math.min(minX, axisX(.88f) - radius * .65f);
                maxX = Math.max(maxX, axisX(.88f) + canopyLength);
                float canopyWidth = radius * (1.7f + spread);
                float sweep = archetype == Archetype.SUPERCELL ? radius * .45f * Math.abs(shear) : 0;
                minZ = Math.min(minZ, -canopyWidth - sweep); maxZ = Math.max(maxZ, canopyWidth + sweep);
            }
            float fibresLow = Float.POSITIVE_INFINITY, fibresHigh = Float.NEGATIVE_INFINITY;
            for (Filament f : filaments) {
                minX = Math.min(minX, f.startX()); maxX = Math.max(maxX, f.startX() + f.length());
                minZ = Math.min(minZ, Math.min(f.startZ(), f.startZ() + f.fan()) - f.width());
                maxZ = Math.max(maxZ, Math.max(f.startZ(), f.startZ() + f.fan()) + f.width());
                fibresLow = Math.min(fibresLow, f.height() + Math.min(0, f.rise()) - f.depth());
                fibresHigh = Math.max(fibresHigh, f.height() + Math.max(0, f.rise()) + height * .015f + f.depth());
            }
            fibreMinY = fibresLow; fibreMaxY = fibresHigh;
            maxY = Math.max(maxY, fibresHigh);
            // Enclosure certificate, not an SDF: repeated smooth-mins with k<=K cannot
            // fall below min(primitive distances)-K, regardless of authored feature count.
            // Keep the older larger evaluator-pruning allowance for unchanged GPU equation parity.
            float occupancyMargin = radius * .22f + Math.max(2, radius * .055f) * .5f
                    + radius * .022f * raggedness + 2;
            float towerMinX = axisMin - bodyWidthBound * footX - radius * .22f;
            float towerMaxX = axisMax + bodyWidthBound * footX;
            if (archetype == Archetype.CALVUS) {
                float crownX = axisX(.80f) + canopyLength * .25f;
                float crownRadius = radius * .82f + canopyLength * .35f;
                towerMinX = Math.min(towerMinX, crownX - crownRadius);
                towerMaxX = Math.max(towerMaxX, crownX + crownRadius);
            }
            List<Bounds> groups = new ArrayList<>();
            groups.add(new Bounds(towerMinX - occupancyMargin * footX, -baseDepth - wallDepth - occupancyMargin,
                    -bodyWidthBound * footZ - occupancyMargin * footZ, towerMaxX + occupancyMargin * footX,
                    topBound + occupancyMargin, Math.max(bodyWidthBound * footZ, secondaryOffset + radius * .55f) + occupancyMargin * footZ));
            if (archetype != Archetype.CALVUS) {
                float canopyWidth = radius * (1.7f + spread) + (archetype == Archetype.SUPERCELL ? radius * .45f * Math.abs(shear) : 0);
                groups.add(new Bounds(axisX(.88f) - radius * .65f - occupancyMargin, canopyMinY() - occupancyMargin,
                        -canopyWidth - occupancyMargin, axisX(.88f) + canopyLength + occupancyMargin,
                        canopyMaxY() + occupancyMargin, canopyWidth + occupancyMargin));
                float iceMinX = Float.POSITIVE_INFINITY, iceMaxX = Float.NEGATIVE_INFINITY;
                float iceMinZ = Float.POSITIVE_INFINITY, iceMaxZ = Float.NEGATIVE_INFINITY;
                for (Filament f : filaments) {
                    iceMinX = Math.min(iceMinX, f.startX()); iceMaxX = Math.max(iceMaxX, f.startX() + f.length());
                    iceMinZ = Math.min(iceMinZ, Math.min(f.startZ(), f.startZ() + f.fan()) - f.width());
                    iceMaxZ = Math.max(iceMaxZ, Math.max(f.startZ(), f.startZ() + f.fan()) + f.width());
                }
                groups.add(new Bounds(iceMinX - occupancyMargin, fibreMinY - occupancyMargin, iceMinZ - occupancyMargin,
                        iceMaxX + occupancyMargin, fibreMaxY + occupancyMargin, iceMaxZ + occupancyMargin));
            }
            componentBounds = List.copyOf(groups);
            Bounds whole = groups.get(0);
            for (Bounds g : groups) whole = new Bounds(Math.min(whole.minX(), g.minX()), Math.min(whole.minY(), g.minY()),
                    Math.min(whole.minZ(), g.minZ()), Math.max(whole.maxX(), g.maxX()), Math.max(whole.maxY(), g.maxY()), Math.max(whole.maxZ(), g.maxZ()));
            bounds = whole;
            long hash = 0xcbf29ce484222325L;
            for (float value : packed()) hash = (hash ^ Float.floatToIntBits(value)) * 0x100000001b3L;
            signature = hash;
        }

        public Archetype archetype() { return archetype; }
        public float radius() { return radius; }
        public float height() { return height; }
        public float canopyLength() { return canopyLength; }
        public float wallDepth() { return wallDepth; }
        public float secondaryOffset() { return secondaryOffset; }
        public float secondaryX() { return axisX(.55f) - radius * .22f; }
        public List<GrowthSector> sectors() { return sectors; }
        public List<GrowthAxis> growthAxes() { return growthAxes; }
        public List<Filament> filaments() { return filaments; }
        public Bounds bounds() { return bounds; }
        public List<Bounds> componentBounds() { return componentBounds; }
        public long signature() { return signature; }

        public float axisX(float fraction) {
            float t = clamp(fraction, 0, 1);
            return height * (.9f * tilt * t + .38f * shear * t * t);
        }

        /** Continuous broad foot, narrowed trunk, upper growth crown. No level/row discontinuities. */
        public float widthAt(float fraction) {
            float t = clamp(fraction, 0, 1);
            // Narrowing follows rising growth, not a sinusoidal hourglass waist.
            float trunk = smooth(.10f, .70f, t);
            float crown = smooth(.62f, .88f, t);
            float upper = archetype == Archetype.CALVUS ? .20f : .08f;
            return radius * (1 - .16f * t - .26f * narrowing * trunk + upper * crown);
        }

        /** Positive distance outside; this is NOT a certified SDF for sphere tracing. */
        public float distance(float x, float y, float z, Scale scale) {
            if (!bounds.contains(x, y, z)) return radius;
            return unboundedDistance(x, y, z, scale);
        }

        /** Inspection hook: bypass bounds rejection so enclosure tests cannot pass tautologically. */
        public float unboundedDistance(float x, float y, float z, Scale scale) {
            float t = clamp(y / height, 0, 1);
            float axis = axisX(t);
            float dx = x - axis;
            float q = hypot(dx, z);
            if (archetype == Archetype.SUPERCELL) {
                float foot = compact(t / .38f);
                q = hypot(dx / (1 + .55f * foot), z / (1 + .30f * foot));
            }
            float width = widthAt(t);
            float floor = -baseDepth + (1 - flattening) * radius * .12f * (q / radius) * (q / radius);
            if (wallDepth > 0) {
                // Lower the common condensation surface into an attached off-centre bowl.
                // No separate flat-ended cylindrical primitive.
                float lowerQ = hypot(x + radius * .12f, z + radius * .16f) / (radius * .65f);
                floor -= wallDepth * compact(lowerQ);
            }
            float top = height - radius * .43f * (q / Math.max(width, 1)) * (q / Math.max(width, 1));
            float body = Math.max(q - width, Math.max(floor - y, y - top));
            if (scale != Scale.MACRO) {
                for (GrowthAxis axisGrowth : growthAxes) {
                    if (axisGrowth.radius() <= 0) continue;
                    float v = clamp((y / height - axisGrowth.start()) / (axisGrowth.tip() - axisGrowth.start()), 0, 1);
                    float radial = mix(axisGrowth.rootRadius(), axisGrowth.tipRadius(), smooth(0, 1, v));
                    float cap = (float) Math.sqrt(Math.max(0, 1 - v * v * v * v * v * v * v * v));
                    float growthRadius = axisGrowth.radius() * (.5f + 2 * v * (1 - v)) * cap;
                    float growth = Math.max(hypot(x - axis - axisGrowth.directionX() * radial,
                                    z - axisGrowth.directionZ() * radial) - growthRadius,
                            Math.max(axisGrowth.start() * height - y, y - axisGrowth.tip() * height));
                    body = union(body, growth, radius * .08f);
                }
            }

            if (secondaryOffset > 0) {
                // Secondary growth shares the lower mass and diverges upward, not an isolated blob.
                float diverge = smooth(.08f, .48f, t);
                float sx = axis - radius * .22f * diverge;
                float sz = secondaryOffset * diverge;
                float secondaryWidth = widthAt(t) * (.55f - .18f * t) * split;
                float branch = Math.max(hypot(x - sx, z - sz) - secondaryWidth,
                        Math.max(floor - y, y - height * (.60f + .18f * split)));
                body = union(body, branch, radius * .16f);
            }

            if (archetype == Archetype.CALVUS) {
                // Smooth developing shoulder only; no mature anvil sheet or ice filaments.
                float shoulder = ellipsoid(x - axisX(.80f) - canopyLength * .25f,
                        y - height * .79f, z, radius * .82f + canopyLength * .35f,
                        height * .15f, radius * .76f);
                body = union(body, shoulder, radius * .20f);
            } else {
                float cx = x - axisX(.88f);
                float u = cx / canopyLength;
                // A coherent spreading canopy with curved upper/lower surfaces and tapering edge.
                // Broad swept fan: only the windward attachment contracts to zero.
                // The distal front remains broad instead of closing into an elliptical paddle tip.
                float fanCap = (float) Math.sqrt(smooth(-radius * .65f, radius * .18f, cx));
                float cross = radius * (.70f + (1 + spread) * smooth(-.1f, .58f, u)) * fanCap;
                float sweep = archetype == Archetype.SUPERCELL ? radius * .45f * shear * smooth(.12f, 1, u) : 0;
                float cz = z - sweep;
                float sag = clamp(cz / Math.max(cross, 1), -1, 1);
                float cy = height * .87f + height * .07f * clamp(u, -.25f, 1.2f)
                        - radius * .05f * sag * sag;
                float half = radius * .18f * (1 - .66f * smooth(.05f, 1.1f, u)) * fanCap;
                float canopy = Math.max(ellipse(cz, y - cy, Math.max(1, cross), Math.max(1, half)),
                        Math.max(-radius * .65f - cx, cx - canopyLength + radius * .12f * sag * sag));
                body = union(body, canopy, radius * .22f);
                if (archetype == Archetype.SUPERCELL) {
                    // Forward-flank cloud shield is organised deep mass, not a thicker material on the ordinary anvil.
                    float shieldWidth = radius * (1.0f + .85f * spread) * fanCap;
                    float shieldBottom = height * (.47f + .35f * clamp(u, 0, 1));
                    float shieldTop = cy;
                    float shield = Math.max(ellipse(cz, y - (shieldTop + shieldBottom) * .5f,
                                    Math.max(1, shieldWidth), Math.max(1, (shieldTop - shieldBottom) * .5f)),
                            Math.max(-radius * .40f - cx, cx - canopyLength * .82f));
                    if (shieldTop > shieldBottom) body = union(body, shield, radius * .14f);
                }
                if (scale != Scale.MACRO) {
                    for (Filament f : filaments) {
                        float v = clamp((x - f.startX()) / f.length(), 0, 1);
                        float fz = f.startZ() + f.fan() * (v * .7f + v * v * .3f);
                        float fy = f.height() + f.rise() * v + height * .015f * (float) Math.sin(v * Math.PI);
                        float taper = .25f + .75f * (1 - v);
                        float ribbon = Math.max(ellipse(z - fz, y - fy, f.width() * taper, f.depth() * taper),
                                Math.max(f.startX() - x, x - f.startX() - f.length()));
                        body = union(body, ribbon, radius * .025f);
                    }
                }
            }
            if (scale == Scale.MICRO) {
                // Bounded surface displacement only: never creates the tower/crown/canopy.
                float micro = (float) (Math.sin(x / 17 + Math.sin(z / 31))
                        * Math.sin(y / 23 + Math.sin(x / 41)) * Math.cos(z / 19));
                body += micro * radius * .022f * raggedness;
            }
            return body;
        }

        public float density(float x, float y, float z, Scale scale) {
            return clamp(.5f - distance(x, y, z, scale) / Math.max(2, radius * .055f), 0, 1);
        }

        private float canopyMinY() {
            float value = Math.min(height * .8525f - radius * .23f, height * .8525f - radius * .05f - 1);
            return archetype == Archetype.SUPERCELL ? Math.min(value, height * .47f) : value;
        }
        private float canopyMaxY() { return height * .954f + Math.max(1, radius * .18f); }

        /** 12 header/hierarchy texels, two per swept growth axis, two per ice bundle. */
        public float[] packed() {
            float[] data = new float[48 + growthAxes.size() * 8 + filaments.size() * 8];
            float[] header = {radius, height, baseDepth, archetype.ordinal(), tilt, shear, narrowing, spread,
                    flattening, raggedness, split, wall, structure, sectors.size(), filaments.size(), VERSION};
            System.arraycopy(header, 0, data, 0, header.length);
            data[16] = bodyWidthBound;
            data[17] = height;
            for (GrowthAxis axis : growthAxes) data[17] = Math.max(data[17], height * axis.tip());
            data[18] = canopyMinY(); data[19] = canopyMaxY();
            data[20] = filaments.isEmpty() ? 0 : fibreMinY;
            data[21] = filaments.isEmpty() ? 0 : fibreMaxY;
            data[22] = supportMargin;
            data[23] = componentBounds.size();
            for (int i = 0; i < componentBounds.size(); i++) {
                var b = componentBounds.get(i);
                int o = 24 + i * 8;
                data[o] = b.minX(); data[o + 1] = b.minY(); data[o + 2] = b.minZ();
                data[o + 4] = b.maxX(); data[o + 5] = b.maxY(); data[o + 6] = b.maxZ();
            }
            int p = 48;
            for (GrowthAxis axis : growthAxes) {
                data[p++] = axis.directionX(); data[p++] = axis.directionZ(); data[p++] = axis.start(); data[p++] = axis.tip();
                data[p++] = axis.rootRadius(); data[p++] = axis.tipRadius(); data[p++] = axis.radius(); data[p++] = 0;
            }
            for (Filament f : filaments) {
                data[p++] = f.startX(); data[p++] = f.length(); data[p++] = f.startZ(); data[p++] = f.fan();
                data[p++] = f.height(); data[p++] = f.rise(); data[p++] = f.width(); data[p++] = f.depth();
            }
            return data;
        }
    }

    private static float ellipsoid(float x, float y, float z, float rx, float ry, float rz) {
        float norm = (float) Math.sqrt(x * x / (rx * rx) + y * y / (ry * ry) + z * z / (rz * rz));
        return norm == 0 ? -Math.min(rx, Math.min(ry, rz)) : (norm - 1) * (float) Math.sqrt(x * x + y * y + z * z) / norm;
    }
    private static float ellipse(float a, float b, float ra, float rb) {
        float norm = hypot(a / ra, b / rb);
        return norm == 0 ? -Math.min(ra, rb) : (norm - 1) * hypot(a, b) / norm;
    }
    private static float compact(float t) { float v = Math.max(0, 1 - t * t); return v * v; }
    private static float union(float a, float b, float k) { float h = Math.max(k - Math.abs(a - b), 0) / k; return Math.min(a, b) - h * h * k * .25f; }
    private static float hypot(float x, float z) { return (float) Math.sqrt(x * x + z * z); }
    private static float smooth(float a, float b, float v) { float t = clamp((v - a) / (b - a), 0, 1); return t * t * (3 - 2 * t); }
    private static float clamp(float v, float a, float b) { return Math.max(a, Math.min(b, v)); }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
}
