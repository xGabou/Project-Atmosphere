package net.Gabou.projectatmosphere.clouds.simulation;

import java.util.Locale;
import java.util.UUID;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.StormBodyEvaluator;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.StormBodySkeleton;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.StormLobeDescriptor;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.StormLobeSpatialIndex;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeDefinition;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * T240. The production storm's anatomy guards: the V2 template's skeleton and
 * the continuous body it produces, measured on the body itself.
 *
 * <p>They replace, for the production template, the T127 / T133 role-diameter
 * and ratio bands of the legacy lobe template (still enforced on template 0
 * by {@link CloudMorphologyTopologySandbox}). Those bands described the lobe
 * union's silhouette - core 0.45-0.50 of base, anvil union 1150-1450 blocks,
 * the anvil crown above the tower crown - i.e. the mushroom; none of them
 * constrains the continuous body, whose form comes from the skeleton
 * equations. What must hold for the body, and is checked here over 128
 * deterministic plans, each spawned as the client builds it (per-stage radius
 * scale and aspect, VolumetricRenderCell):
 * <ul>
 * <li>a complete skeleton, deterministic per seed;</li>
 * <li>a broad, low lower mass (base height / footprint);</li>
 * <li>an exposed updraft (base crown to deck bottom, over storm height) whose
 * body width stays near-constant (no neck, no cone, no root bulge);</li>
 * <li>a thin deck (mean thickness / length) reaching far downwind of the
 * updraft (downwind / upwind reach), with bounded cross drift;</li>
 * <li>one body: the updraft axis is solid (distance below -band) from the
 * base to mid-deck;</li>
 * <li>the published conservative AABB encloses every point within the band;</li>
 * <li>the distance's Lipschitz constants stay within the bounds the shared
 * field and the rain gate dilate by.</li>
 * </ul>
 */
public final class StormBodyAnatomySandbox {
    // Client per-stage scale and aspect (VolumetricRenderCell, STORM_ANVIL).
    private static final float[] RADIUS_SCALE = {1.00F, 1.18F, 1.28F, 1.00F};
    private static final float[] ASPECT = {0.90F, 0.90F, 0.82F, 0.58F};
    private static final float[] EDGE_SOFTNESS = {0.52F, 0.48F, 0.44F, 0.56F};
    private static final float[] DENSITY = {0.90F, 0.97F, 0.92F, 0.80F};

    private static final boolean DEBUG_LIPSCHITZ = false;
    private static int debugPrinted;
    private static final boolean DEBUG_ENCLOSURE = false;

    private StormBodyAnatomySandbox() {
    }

    public static void main(String[] args) {
        validate();
    }

    static void validate() {
        int previous = CloudMorphologyGenerators.stormTemplateVersion;
        try {
            CloudMorphologyGenerators.stormTemplateVersion =
                    CloudMorphologyGenerators.PRODUCTION_STORM_TEMPLATE;
            run();
        } finally {
            CloudMorphologyGenerators.stormTemplateVersion = previous;
        }
    }

    private static void run() {
        CloudTypeDefinition definition = CloudTypeRegistry.getOrDefault("cumulonimbus_calvus");
        Range height = new Range();
        Range baseAspect = new Range();
        Range exposedUpdraft = new Range();
        Range columnWidthRatio = new Range();
        Range deckAspect = new Range();
        Range downwindReach = new Range();
        Range crossDrift = new Range();
        Range axisCore = new Range();
        Range lipschitzH = new Range();
        Range lipschitzV = new Range();
        int enclosureMisses = 0;
        int enclosureSamples = 0;
        float[] texels = new float[StormLobeSpatialIndex.DESCRIPTOR_FLOATS];
        float[] repeat = new float[StormLobeSpatialIndex.DESCRIPTOR_FLOATS];
        StormBodyEvaluator.Sample sample = new StormBodyEvaluator.Sample();
        for (int plan = 0; plan < 128; plan++) {
            long seed = 0x5432343041414E41L + plan * 0x9E3779B9L;
            int count = spawn(definition, seed, texels);
            spawn(definition, seed, repeat);
            for (int i = 0; i < texels.length; i++) {
                if (Float.floatToIntBits(texels[i]) != Float.floatToIntBits(repeat[i])) {
                    throw new IllegalStateException("storm body skeleton is not deterministic plan=" + plan);
                }
            }
            int o = StormBodySkeleton.offset(0);
            if (texels[o + StormBodySkeleton.VALID] <= 0.0F) {
                throw new IllegalStateException("production storm plan has no body skeleton plan=" + plan
                        + " members=" + count);
            }
            double floor = texels[o + StormBodySkeleton.FLOOR_Y];
            double baseHeight = texels[o + StormBodySkeleton.BASE_HEIGHT];
            double deckBottom = texels[o + StormBodySkeleton.DECK_BOTTOM];
            double deckTop = texels[o + StormBodySkeleton.DECK_TOP];
            double deckMid = texels[o + StormBodySkeleton.DECK_MID];
            double band = texels[o + StormBodySkeleton.BAND];
            double deckLo = texels[o + StormBodySkeleton.DECK_LO];
            double deckHi = texels[o + StormBodySkeleton.DECK_HI];
            double footprint = texels[o + StormBodySkeleton.BASE_UP] + texels[o + StormBodySkeleton.BASE_DOWN];
            height.accept(deckTop - floor);
            baseAspect.accept(baseHeight / footprint);
            exposedUpdraft.accept((deckBottom - (floor + baseHeight)) / (deckTop - floor));
            double span = Math.max(deckTop - deckBottom, 20.0D);
            deckAspect.accept(span * 0.625D / (deckHi - deckLo));
            downwindReach.accept(deckHi / Math.max(-deckLo, 1.0D));
            crossDrift.accept(Math.abs(texels[o + StormBodySkeleton.DECK_CROSS_MEAN])
                    / texels[o + StormBodySkeleton.DECK_HALF_WIDTH]);

            // Body width of the exposed column, measured on the body, at 25 /
            // 50 / 75% of the span between the base crown and the deck bottom.
            double crown = floor + baseHeight;
            double widest = 0.0D;
            double narrowest = Double.POSITIVE_INFINITY;
            for (double f : new double[] {0.25D, 0.50D, 0.75D}) {
                double y = crown + f * (deckBottom - crown);
                double width = columnHalfWidth(texels, o, y, sample);
                widest = Math.max(widest, width);
                narrowest = Math.min(narrowest, width);
            }
            columnWidthRatio.accept(widest / Math.max(narrowest, 1.0D));

            // One body: the updraft axis is solid from the base into the deck.
            double worstAxis = Double.NEGATIVE_INFINITY;
            for (double y = floor + 0.15D * baseHeight; y <= deckMid; y += 8.0D) {
                double t = Math.max(0.0D, Math.min(1.0D,
                        (y - texels[o + StormBodySkeleton.TOWER_LO_Y])
                                / Math.max(texels[o + StormBodySkeleton.TOWER_HI_Y]
                                        - texels[o + StormBodySkeleton.TOWER_LO_Y], 1.0D)));
                double ax = texels[o + StormBodySkeleton.TOWER_LO_X] + t
                        * (texels[o + StormBodySkeleton.TOWER_HI_X] - texels[o + StormBodySkeleton.TOWER_LO_X]);
                double az = texels[o + StormBodySkeleton.TOWER_LO_Z] + t
                        * (texels[o + StormBodySkeleton.TOWER_HI_Z] - texels[o + StormBodySkeleton.TOWER_LO_Z]);
                StormBodyEvaluator.evaluate(texels, o, ax, y, az, sample);
                worstAxis = Math.max(worstAxis, sample.distance + sample.band);
            }
            axisCore.accept(worstAxis);

            // AABB enclosure and Lipschitz constants on random points.
            java.util.Random random = new java.util.Random(seed);
            double minX = texels[o + StormBodySkeleton.AABB_MIN_X];
            double maxX = texels[o + StormBodySkeleton.AABB_MAX_X];
            double minZ = texels[o + StormBodySkeleton.AABB_MIN_Z];
            double maxZ = texels[o + StormBodySkeleton.AABB_MAX_Z];
            double minY = texels[o + StormBodySkeleton.AABB_MIN_Y];
            double maxY = texels[o + StormBodySkeleton.AABB_MAX_Y];
            double h = 2.0D;
            for (int i = 0; i < 4000; i++) {
                double x = minX - 300.0D + random.nextDouble() * (maxX - minX + 600.0D);
                double y = minY - 150.0D + random.nextDouble() * (maxY - minY + 300.0D);
                double z = minZ - 300.0D + random.nextDouble() * (maxZ - minZ + 600.0D);
                StormBodyEvaluator.evaluate(texels, o, x, y, z, sample);
                double d = sample.distance;
                if (d <= sample.band) {
                    enclosureSamples++;
                    if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) {
                        enclosureMisses++;
                        if (DEBUG_ENCLOSURE) {
                            double rx = x - texels[o + StormBodySkeleton.ORIGIN_X];
                            double rz = z - texels[o + StormBodySkeleton.ORIGIN_Z];
                            System.out.printf(Locale.ROOT, "MISS d=%.1f band=%.1f region=%d w=%.0f c=%.0f y-floor=%.0f outX=%.0f outZ=%.0f outY=%.0f deckTop-floor=%.0f%n",
                                    d, sample.band, sample.region,
                                    rx * texels[o + StormBodySkeleton.WIND_X] + rz * texels[o + StormBodySkeleton.WIND_Z],
                                    -rx * texels[o + StormBodySkeleton.WIND_Z] + rz * texels[o + StormBodySkeleton.WIND_X],
                                    y - floor, Math.max(minX - x, x - maxX), Math.max(minZ - z, z - maxZ),
                                    Math.max(minY - y, y - maxY), deckTop - floor);
                            System.out.printf(Locale.ROOT, "  deckLo=%.0f deckHi=%.0f wind=(%.3f,%.3f) crossMean=%.0f halfW=%.0f tower=%.0f%n",
                                    deckLo, deckHi,
                                    texels[o + StormBodySkeleton.WIND_X], texels[o + StormBodySkeleton.WIND_Z], texels[o + StormBodySkeleton.DECK_CROSS_MEAN],
                                    texels[o + StormBodySkeleton.DECK_HALF_WIDTH], texels[o + StormBodySkeleton.TOWER_RADIUS]);
                        }
                    }
                }
                if (Math.abs(d) > 200.0D) {
                    continue;
                }
                double dx = distance(texels, o, x + h, y, z, sample) - distance(texels, o, x - h, y, z, sample);
                double dz = distance(texels, o, x, y, z + h, sample) - distance(texels, o, x, y, z - h, sample);
                double dy = distance(texels, o, x, y + h, z, sample) - distance(texels, o, x, y - h, z, sample);
                lipschitzH.accept(Math.hypot(dx, dz) / (2.0D * h));
                if (DEBUG_LIPSCHITZ && (Math.hypot(dx, dz) / (2.0D * h) > 1.4D || Math.abs(dy) / (2.0D * h) > 1.5D) && debugPrinted < 25) {
                    debugPrinted++;
                    double rx = x - texels[o + StormBodySkeleton.ORIGIN_X];
                    double rz = z - texels[o + StormBodySkeleton.ORIGIN_Z];
                    double ww = rx * texels[o + StormBodySkeleton.WIND_X] + rz * texels[o + StormBodySkeleton.WIND_Z];
                    double cc = -rx * texels[o + StormBodySkeleton.WIND_Z] + rz * texels[o + StormBodySkeleton.WIND_X];
                    StormBodyEvaluator.evaluate(texels, o, x, y, z, sample);
                    System.out.printf(Locale.ROOT, "LIP gH=%.2f gV=%.2f d=%.1f region=%d w=%.0f c=%.0f y-floor=%.0f deckLo=%.0f deckHi=%.0f deckBottom-floor=%.0f%n",
                            Math.hypot(dx, dz) / (2.0D * h), Math.abs(dy) / (2.0D * h), d, sample.region, ww, cc, y - floor, deckLo, deckHi, deckBottom - floor);
                }
                lipschitzV.accept(Math.abs(dy) / (2.0D * h));
            }
            // A band never smaller than a physical minimum.
            if (band < 24.0D || band > 48.0D) {
                throw new IllegalStateException("storm body band outside 24-48 blocks: " + band);
            }
        }

        System.out.printf(Locale.ROOT,
                "T240_BODY_ANATOMY plans=128 height=%s baseAspect=%s exposedUpdraft=%s"
                        + " columnWidthRatio=%s deckAspect=%s downwindReach=%s crossDrift=%s"
                        + " axisCoreWorst(d+band)=%s lipschitzH=%s lipschitzV=%s"
                        + " enclosureMisses=%d/%d%n",
                height, baseAspect, exposedUpdraft, columnWidthRatio, deckAspect, downwindReach,
                crossDrift, axisCore, lipschitzH, lipschitzV, enclosureMisses, enclosureSamples);

        requireRange("storm body height", height, 640.0D, 860.0D);
        // T241: the lower mass is the tower's flared foot (1.3 Rt of mean reach),
        // not a wide saucer, so its aspect rose from ~0.2 to ~0.28 - still a
        // flat foot at about 3.5 : 1, never a ball.
        requireRange("storm body base aspect (height / along-wind footprint)", baseAspect, 0.0D, 0.35D);
        requireRange("storm body exposed updraft (of storm height)", exposedUpdraft, 0.40D, 0.75D);
        requireRange("storm body column width max / min (25-75%)", columnWidthRatio, 1.0D, 1.25D);
        requireRange("storm body deck aspect (mean thickness / length)", deckAspect, 0.0D, 0.15D);
        requireRange("storm body downwind / upwind deck reach", downwindReach, 3.0D, 1.0E9D);
        requireRange("storm body deck cross drift / half-width", crossDrift, 0.0D, 0.25D);
        requireRange("storm body axis core (d + band, < 0 = solid)", axisCore, -1.0E9D, 0.0D);
        requireRange("storm body horizontal Lipschitz", lipschitzH, 0.0D,
                StormBodyEvaluator.LIPSCHITZ_HORIZONTAL);
        requireRange("storm body vertical Lipschitz", lipschitzV, 0.0D,
                StormBodyEvaluator.LIPSCHITZ_VERTICAL);
        if (enclosureMisses != 0) {
            throw new IllegalStateException("storm body AABB misses " + enclosureMisses
                    + " points within the band");
        }
        System.out.println("T240_BODY_ANATOMY|PASSED");
    }

    /** Spawns plan {@code seed} as the client sees it; returns the member count. */
    private static int spawn(CloudTypeDefinition definition, long seed, float[] texels) {
        java.util.Arrays.fill(texels, 0.0F);
        RandomSource random = RandomSource.create(seed);
        CloudMorphologyGenerators.SpawnPlan plan = CloudMorphologyGenerators.createSpawnPlan(definition, random);
        Vec3 origin = new Vec3(0.0D, 136.0D + plan.baseDrop(), 0.0D);
        UUID group = UUID.nameUUIDFromBytes(("t240-body-" + seed).getBytes());
        int count = Math.min(plan.clusterCount(), StormLobeSpatialIndex.MAX_LOBES);
        for (int index = 0; index < count; index++) {
            Vec3 center = index == 0 ? origin
                    : CloudMorphologyGenerators.createClusterCenter(origin, plan, index, random);
            CloudMorphologyGenerators.StormLobeSpec spec =
                    CloudMorphologyGenerators.stormLobeSpec(plan, index, (float) center.y);
            int role = switch (spec.stage()) {
                case BASE -> 0;
                case CORE -> 1;
                case TOWER -> 2;
                case ANVIL -> 3;
                default -> throw new IllegalStateException("unexpected storm stage " + spec.stage());
            };
            float major = Math.max(4.0F, plan.radius() * spec.radiusMultiplier() * RADIUS_SCALE[role]);
            StormLobeDescriptor descriptor = new StormLobeDescriptor(
                    UUID.nameUUIDFromBytes(("t240-member-" + seed + "-" + index).getBytes()),
                    group, index, plan.clusterCount(), 0,
                    StormLobeDescriptor.Role.values()[role],
                    center.x, center.z, spec.baseY(), spec.topY(), major, major * ASPECT[role],
                    0.0F, 1.0F, 0.0F, 0.0F, DENSITY[role], EDGE_SOFTNESS[role],
                    0.37F, 0.62F, 1.0F, 1.0F);
            descriptor.writeTexels(texels, index * StormLobeDescriptor.FLOATS_PER_DESCRIPTOR);
        }
        StormBodySkeleton.writeAll(texels, count);
        return count;
    }

    /** Half-width of the body across the wind through the updraft axis at height y. */
    private static double columnHalfWidth(float[] s, int o, double y, StormBodyEvaluator.Sample sample) {
        double loY = s[o + StormBodySkeleton.TOWER_LO_Y];
        double hiY = s[o + StormBodySkeleton.TOWER_HI_Y];
        double t = Math.max(0.0D, Math.min(1.0D, (y - loY) / Math.max(hiY - loY, 1.0D)));
        double ax = s[o + StormBodySkeleton.TOWER_LO_X] + t * (s[o + StormBodySkeleton.TOWER_HI_X] - s[o + StormBodySkeleton.TOWER_LO_X]);
        double az = s[o + StormBodySkeleton.TOWER_LO_Z] + t * (s[o + StormBodySkeleton.TOWER_HI_Z] - s[o + StormBodySkeleton.TOWER_LO_Z]);
        double crossX = -s[o + StormBodySkeleton.WIND_Z];
        double crossZ = s[o + StormBodySkeleton.WIND_X];
        double width = 0.0D;
        for (int sign = -1; sign <= 1; sign += 2) {
            double r = 0.0D;
            while (r < 2000.0D) {
                StormBodyEvaluator.evaluate(s, o, ax + sign * r * crossX, y, az + sign * r * crossZ, sample);
                if (sample.distance > 0.0D) {
                    break;
                }
                r += 2.0D;
            }
            width += r;
        }
        return 0.5D * width;
    }

    private static double distance(float[] s, int o, double x, double y, double z,
            StormBodyEvaluator.Sample sample) {
        StormBodyEvaluator.evaluate(s, o, x, y, z, sample);
        return sample.distance;
    }

    private static void requireRange(String label, Range range, double minimum, double maximum) {
        if (!(range.min >= minimum && range.max <= maximum)) {
            throw new IllegalStateException(label + " outside " + minimum + ".." + maximum + ": " + range);
        }
    }

    private static final class Range {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        void accept(double value) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%.3f..%.3f", min, max);
        }
    }
}
