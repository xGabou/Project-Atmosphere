package net.Gabou.projectatmosphere.clouds.field;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Stateless deterministic cloudlet layout generator. Cloudlet identity and
 * local shape stay stable because they derive only from field seed + id.
 */
public final class CloudletLayout {
    private CloudletLayout() {
    }

    public static List<Cloudlet> generate(CloudFieldSnapshot snapshot) {
        if (snapshot == null || !snapshot.hasVisibleClouds()) {
            return List.of();
        }

        int count = snapshot.dynamicCloudletCount();
        if (count <= 0) {
            return List.of();
        }

        List<Cloudlet> cloudlets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            cloudlets.add(generate(snapshot, CloudletId.of(i)));
        }
        return List.copyOf(cloudlets);
    }

    public static Cloudlet generate(CloudFieldSnapshot snapshot, CloudletId id) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot cannot be null");
        }
        if (id == null) {
            throw new IllegalArgumentException("id cannot be null");
        }

        long seed = id.mixedSeed(snapshot.seed());
        float windAngle = windAngle(snapshot, seed);
        Layout layout = switch (snapshot.morphologyFamily()) {
            case PUFF -> cumulusLayout(snapshot, id, seed);
            case TOWER -> towerLayout(snapshot, id, seed);
            case STORM_ANVIL -> stormLayout(snapshot, id, seed, windAngle, false);
            case SHEET -> sheetLayout(snapshot, id, seed, windAngle);
            case CELLULAR_SHEET -> cellularSheetLayout(snapshot, id, seed, windAngle);
            case FILAMENT -> filamentLayout(snapshot, id, seed, windAngle);
            case SPIRAL_STORM -> stormLayout(snapshot, id, seed, windAngle, true);
            case DEBUG -> cumulusLayout(snapshot, id, seed);
        };

        float vertical = snapshot.baseY()
                + (snapshot.topY() - snapshot.baseY()) * layout.height01();
        float densityScale = layout.densityScale() * lerp(0.86F, 1.08F, unit(seed, 6));
        float coverageWeight = layout.coverageScale() * lerp(0.76F, 1.00F, unit(seed, 7))
                * snapshot.lodBand().detailScale()
                * snapshot.hydrationProgress();
        long coherentAgeTicks = Math.max(0L, snapshot.fieldAgeTicks() - Math.round(unit(seed, 8) * 2400.0F));

        Vec3 localOffset = new Vec3(
                layout.offsetX(),
                vertical - snapshot.center().y(),
                layout.offsetZ()
        );

        return new Cloudlet(
                id,
                localOffset,
                Math.max(1.0F, snapshot.radius() * layout.radiusScale()),
                layout.verticalSpanFraction(),
                densityScale,
                coverageWeight,
                coherentAgeTicks,
                layout.horizontalAspect(),
                layout.orientationRadians(),
                layout.role()
        );
    }

    private static Layout cumulusLayout(CloudFieldSnapshot snapshot, CloudletId id, long seed) {
        float development = snapshot.verticalDevelopment();
        float towerBlend = development * development * (3.0F - 2.0F * development);
        if (id.value() == 0) {
            return new Layout(
                    0.0F, 0.0F, lerp(0.34F, 0.46F, towerBlend),
                    lerp(0.32F, 0.28F, towerBlend),
                    lerp(0.68F, 0.86F, towerBlend),
                    lerp(1.08F, 1.13F, towerBlend), 1.00F,
                    lerp(0.84F, 0.74F, towerBlend), seedAngle(seed), CloudletRole.CORE
            );
        }

        float angle = unit(seed, 1) * (float) (Math.PI * 2.0);
        float radial01 = (float) Math.sqrt(unit(seed, 2));
        float ring = radial01 * snapshot.radius() * lerp(0.56F, 0.38F, towerBlend);
        float puffHeight = 0.12F + unit(seed, 3) * 0.66F;
        float towerHeight = 0.14F + unit(seed, 3) * 0.76F;
        float height01 = lerp(puffHeight, towerHeight, towerBlend);
        height01 *= 1.0F - radial01 * 0.28F;
        float radiusScale = lerp(0.18F, 0.29F, unit(seed, 4)) * lerp(1.08F, 0.72F, height01);
        return new Layout(
                (float) Math.cos(angle) * ring,
                (float) Math.sin(angle) * ring,
                Math.max(0.08F, height01),
                radiusScale,
                lerp(0.30F, 0.56F, unit(seed, 5)),
                1.0F,
                0.90F,
                lerp(0.72F, 0.94F, unit(seed, 9)),
                seedAngle(seed),
                CloudletRole.LOBE
        );
    }

    private static Layout towerLayout(CloudFieldSnapshot snapshot, CloudletId id, long seed) {
        if (id.value() == 0) {
            return new Layout(
                    0.0F, 0.0F, 0.48F,
                    0.28F, 0.90F,
                    1.14F, 1.00F,
                    0.72F, seedAngle(seed), CloudletRole.TOWER
            );
        }
        if (id.value() == 1) {
            return new Layout(
                    0.0F, 0.0F, 0.14F,
                    0.46F, 0.24F,
                    1.08F, 1.00F,
                    0.82F, seedAngle(seed), CloudletRole.BASE
            );
        }

        float height01 = 0.14F + unit(seed, 3) * 0.76F;
        float angle = unit(seed, 1) * (float) (Math.PI * 2.0);
        float ring = (float) Math.sqrt(unit(seed, 2))
                * snapshot.radius() * lerp(0.34F, 0.16F, height01);
        return new Layout(
                (float) Math.cos(angle) * ring,
                (float) Math.sin(angle) * ring,
                height01,
                lerp(0.16F, 0.27F, unit(seed, 4)) * lerp(1.08F, 0.68F, height01),
                lerp(0.32F, 0.58F, unit(seed, 5)),
                1.06F, 0.92F,
                lerp(0.62F, 0.84F, unit(seed, 9)),
                seedAngle(seed), CloudletRole.TOWER
        );
    }

    private static Layout stormLayout(
            CloudFieldSnapshot snapshot,
            CloudletId id,
            long seed,
            float windAngle,
            boolean spiral
    ) {
        float windX = (float) Math.cos(windAngle);
        float windZ = (float) Math.sin(windAngle);
        float crossX = -windZ;
        float crossZ = windX;
        if (id.value() == 0) {
            float asymmetry = spiral ? snapshot.radius() * 0.10F : 0.0F;
            return new Layout(
                    crossX * asymmetry, crossZ * asymmetry, spiral ? 0.42F : 0.38F,
                    spiral ? 0.23F : 0.24F, spiral ? 0.52F : 0.46F,
                    0.70F, 0.76F,
                    spiral ? 0.74F : 0.78F,
                    windAngle + (spiral ? 0.35F : 0.0F), CloudletRole.CORE
            );
        }
        if (id.value() == 1) {
            float offset = spiral ? snapshot.radius() * 0.12F : 0.0F;
            return new Layout(
                    -crossX * offset, -crossZ * offset, 0.10F,
                    spiral ? 0.35F : 0.31F, spiral ? 0.16F : 0.14F,
                    0.78F, 0.82F,
                    spiral ? 0.76F : 0.84F,
                    windAngle - (spiral ? 0.28F : 0.0F), CloudletRole.BASE
            );
        }
        if (id.value() == 2) {
            float spread = snapshot.radius() * lerp(0.08F, 0.15F, snapshot.anvilStrength());
            return new Layout(
                    windX * spread, windZ * spread, 0.78F,
                    lerp(0.46F, 0.56F, snapshot.anvilStrength()),
                    lerp(0.12F, 0.10F, snapshot.anvilStrength()),
                    0.62F, 0.70F,
                    spiral ? 0.30F : 0.36F,
                    windAngle, CloudletRole.ANVIL
            );
        }
        if (id.value() == 3 || id.value() == 4) {
            boolean upper = id.value() == 4;
            float along = snapshot.radius() * (upper ? 0.05F : 0.02F);
            float across = snapshot.radius()
                    * (upper ? -0.04F : 0.05F)
                    * (spiral ? 1.35F : 1.0F);
            return new Layout(
                    windX * along + crossX * across,
                    windZ * along + crossZ * across,
                    upper ? 0.72F : 0.55F,
                    upper ? 0.19F : 0.24F,
                    upper ? 0.32F : 0.42F,
                    upper ? 0.68F : 0.74F,
                    upper ? 0.70F : 0.76F,
                    upper ? 0.72F : 0.78F,
                    windAngle + (upper ? -0.16F : 0.13F)
                            + (spiral ? 0.18F : 0.0F),
                    CloudletRole.TOWER
            );
        }

        boolean upperOutflow = unit(seed, 10) < lerp(0.06F, 0.16F, snapshot.anvilStrength());
        if (upperOutflow) {
            // Secondary outflow must remain a thin, attached part of the main
            // anvil. The former 0.14..0.22 aspect combined with a 0.12..0.20
            // field-height span produced one-to-two-texel vertical blades in
            // the exact severe-layer maps. Keep the deterministic role count,
            // but broaden, thin and pull these lobes back into the primary
            // wind-aligned support.
            float along = lerp(0.06F, 0.26F, unit(seed, 2)) * snapshot.radius();
            float across = (unit(seed, 1) * 2.0F - 1.0F) * snapshot.radius() * 0.055F;
            return new Layout(
                    windX * along + crossX * across,
                    windZ * along + crossZ * across,
                    lerp(0.75F, 0.83F, unit(seed, 3)),
                    lerp(0.17F, 0.24F, unit(seed, 4)),
                    lerp(0.08F, 0.13F, unit(seed, 5)),
                    0.46F, 0.50F,
                    lerp(0.40F, 0.58F, unit(seed, 9)),
                    windAngle, CloudletRole.ANVIL
            );
        }

        float height01 = lerp(0.16F, 0.78F, unit(seed, 3));
        float spiralAngle = unit(seed, 1) * (float) (Math.PI * 2.0)
                + (spiral ? height01 * 2.2F : 0.0F);
        float ring = (float) Math.sqrt(unit(seed, 2))
                * snapshot.radius() * lerp(0.34F, 0.12F, height01);
        if (height01 < 0.30F) {
            return new Layout(
                    (float) Math.cos(spiralAngle) * ring,
                    (float) Math.sin(spiralAngle) * ring,
                    height01,
                    lerp(0.16F, 0.26F, unit(seed, 4)),
                    lerp(0.16F, 0.24F, unit(seed, 5)),
                    0.82F, 0.72F,
                    lerp(0.68F, 0.86F, unit(seed, 9)),
                    spiralAngle, CloudletRole.BASE
            );
        }
        return new Layout(
                (float) Math.cos(spiralAngle) * ring,
                (float) Math.sin(spiralAngle) * ring,
                height01,
                lerp(0.13F, 0.22F, unit(seed, 4)) * lerp(1.04F, 0.76F, height01),
                lerp(0.24F, 0.40F, unit(seed, 5)),
                0.92F, 0.74F,
                lerp(0.62F, 0.82F, unit(seed, 9)),
                spiralAngle, CloudletRole.TOWER
        );
    }

    private static Layout sheetLayout(
            CloudFieldSnapshot snapshot,
            CloudletId id,
            long seed,
            float windAngle
    ) {
        boolean nimbostratus = "nimbostratus".equals(snapshot.cloudTypeId());
        if (id.value() == 0) {
            return new Layout(
                    0.0F, 0.0F, nimbostratus ? 0.45F : 0.50F,
                    nimbostratus ? 0.54F : 0.48F,
                    nimbostratus ? 0.72F : 0.56F,
                    nimbostratus ? 1.16F : 0.92F,
                    1.00F,
                    nimbostratus ? 0.90F : 0.94F,
                    windAngle, CloudletRole.SHEET_TILE
            );
        }
        float angle = unit(seed, 1) * (float) (Math.PI * 2.0);
        float ring = (float) Math.sqrt(unit(seed, 2)) * snapshot.radius() * 0.72F;
        return new Layout(
                (float) Math.cos(angle) * ring,
                (float) Math.sin(angle) * ring,
                (nimbostratus ? 0.44F : 0.50F)
                        + (unit(seed, 3) - 0.5F) * (nimbostratus ? 0.16F : 0.14F),
                lerp(nimbostratus ? 0.34F : 0.30F, nimbostratus ? 0.49F : 0.43F, unit(seed, 4)),
                lerp(nimbostratus ? 0.58F : 0.44F, nimbostratus ? 0.80F : 0.64F, unit(seed, 5)),
                nimbostratus ? 1.10F : 0.88F,
                nimbostratus ? 0.96F : 0.90F,
                lerp(0.78F, 0.96F, unit(seed, 9)),
                windAngle + (unit(seed, 10) - 0.5F) * 0.20F,
                CloudletRole.SHEET_TILE
        );
    }

    private static Layout cellularSheetLayout(
            CloudFieldSnapshot snapshot,
            CloudletId id,
            long seed,
            float windAngle
    ) {
        if (id.value() == 0) {
            return new Layout(
                    0.0F, 0.0F, 0.42F,
                    0.36F, 0.58F,
                    1.02F, 0.92F,
                    0.86F, windAngle, CloudletRole.SHEET_TILE
            );
        }
        float angle = unit(seed, 1) * (float) (Math.PI * 2.0);
        float ring = (float) Math.sqrt(unit(seed, 2)) * snapshot.radius() * 0.76F;
        return new Layout(
                (float) Math.cos(angle) * ring,
                (float) Math.sin(angle) * ring,
                0.34F + unit(seed, 3) * 0.24F,
                lerp(0.22F, 0.35F, unit(seed, 4)),
                lerp(0.42F, 0.62F, unit(seed, 5)),
                0.98F, lerp(0.70F, 0.96F, unit(seed, 7)),
                lerp(0.72F, 0.92F, unit(seed, 9)),
                seedAngle(seed), CloudletRole.LOBE
        );
    }

    private static Layout filamentLayout(
            CloudFieldSnapshot snapshot,
            CloudletId id,
            long seed,
            float windAngle
    ) {
        float windX = (float) Math.cos(windAngle);
        float windZ = (float) Math.sin(windAngle);
        float crossX = -windZ;
        float crossZ = windX;
        float along = id.value() == 0
                ? 0.0F
                : (unit(seed, 2) * 2.0F - 1.0F) * snapshot.radius() * 0.76F;
        float band = (float) ((id.value() % 3) - 1) * snapshot.radius() * 0.12F;
        float wave = (float) Math.sin(along / Math.max(snapshot.radius(), 1.0F) * 4.2F + seedAngle(seed))
                * snapshot.radius() * 0.06F;
        return new Layout(
                windX * along + crossX * (band + wave),
                windZ * along + crossZ * (band + wave),
                0.48F + (unit(seed, 3) - 0.5F) * 0.10F,
                id.value() == 0 ? 0.64F : lerp(0.34F, 0.52F, unit(seed, 4)),
                lerp(0.20F, 0.34F, unit(seed, 5)),
                0.56F, lerp(0.62F, 0.88F, unit(seed, 7)),
                lerp(0.08F, 0.16F, unit(seed, 9)),
                windAngle + (unit(seed, 10) - 0.5F) * 0.14F,
                CloudletRole.FILAMENT
        );
    }

    private static float windAngle(CloudFieldSnapshot snapshot, long seed) {
        Vec3 wind = snapshot.windVector();
        if (wind.horizontalDistanceSqr() <= 1.0E-8D) {
            return seedAngle(seed);
        }
        return (float) Math.atan2(wind.z(), wind.x());
    }

    private static float seedAngle(long seed) {
        return unit(seed, 11) * (float) (Math.PI * 2.0);
    }

    private static float unit(long seed, int salt) {
        long value = seed + (long) salt * 0x9E3779B97F4A7C15L;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (float) ((value >>> 40) & 0xFFFFFFL) / (float) 0xFFFFFF;
    }

    private static float lerp(float min, float max, float t) {
        return min + (max - min) * Math.max(0.0F, Math.min(1.0F, t));
    }

    public enum CloudletRole {
        CORE,
        LOBE,
        BASE,
        TOWER,
        ANVIL,
        SHEET_TILE,
        FILAMENT
    }

    private record Layout(
            float offsetX,
            float offsetZ,
            float height01,
            float radiusScale,
            float verticalSpanFraction,
            float densityScale,
            float coverageScale,
            float horizontalAspect,
            float orientationRadians,
            CloudletRole role
    ) {
    }

    public record Cloudlet(
            CloudletId id,
            Vec3 localOffset,
            float horizontalRadius,
            float verticalScale,
            float densityScale,
            float coverageWeight,
            long coherentAgeTicks,
            float horizontalAspect,
            float orientationRadians,
            CloudletRole role
    ) {
        public Vec3 worldCenter(CloudFieldSnapshot snapshot) {
            return snapshot.center().add(localOffset);
        }
    }
}
