package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import net.Gabou.projectatmosphere.clouds.cell.CloudCell;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldSnapshot;
import net.Gabou.projectatmosphere.clouds.field.CloudletLayout;
import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyFamily;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Minimal render-side view of one cloud cell for weather-map splatting and
 * funnel rendering. Producible from real synced CloudCells or, as a migration
 * fallback, from legacy CloudFieldSnapshots.
 */
public record VolumetricRenderCell(
        double x,
        double z,
        float baseY,
        float topY,
        float radiusMajor,
        float radiusMinor,
        float orientationRadians,
        float density,
        float edgeSoftness,
        float energy,
        int cloudProfile,
        int morphologyFamily,
        float verticalDevelopment,
        float humidity,
        float anvilStrength,
        float precipitationIntensity,
        float lifecycleStage,
        float seed01,
        float funnelStrength,
        float funnelGroundY,
        float rotation
) {
    public static VolumetricRenderCell fromCell(CloudCell cell) {
        return new VolumetricRenderCell(
                cell.x(),
                cell.z(),
                cell.baseY(),
                cell.topY(),
                cell.radiusMajor(),
                cell.radiusMinor(),
                cell.orientationRadians(),
                cell.density(),
                cell.edgeSoftness(),
                cell.energy(),
                profileFor(cell.classification()),
                morphologyFor(cell.classification()).ordinal(),
                Mth.clamp(cell.verticalExtentRatio(), 0.0F, 1.0F),
                cell.density(),
                cell.classification() == net.Gabou.projectatmosphere.clouds.cell.CloudCellClassification.CUMULONIMBUS
                        ? cell.energy() : 0.0F,
                cell.classification() == net.Gabou.projectatmosphere.clouds.cell.CloudCellClassification.CUMULONIMBUS
                        ? cell.energy() : 0.0F,
                lifecycleFor(cell.phase()),
                (cell.seed() & 0xFFFFL) / 65535.0F,
                cell.funnelStrength(),
                cell.funnelGroundY(),
                cell.rotation()
        );
    }

    public static VolumetricRenderCell fromFieldSnapshot(CloudFieldSnapshot snapshot) {
        float effectiveDensity = snapshot.effectiveDensity();
        float effectiveCoverage = snapshot.effectiveCoverage();
        return new VolumetricRenderCell(
                snapshot.center().x(),
                snapshot.center().z(),
                snapshot.baseY(),
                snapshot.topY(),
                Math.max(4.0F, snapshot.radius()),
                Math.max(4.0F, snapshot.radius() * 0.82F),
                (snapshot.seed() % 628L) * 0.01F,
                Math.min(1.0F, effectiveDensity * (0.55F + 0.45F * effectiveCoverage) * Math.max(0.4F, snapshot.hydrationProgress())),
                1.0F - effectiveCoverage * 0.6F,
                snapshot.stormPotential(),
                profileFor(snapshot),
                snapshot.morphologyFamily().ordinal(),
                snapshot.verticalDevelopment(),
                snapshot.humidityInfluence(),
                snapshot.anvilStrength(),
                snapshot.precipitationIntensity(),
                snapshot.lifecycleStage(),
                (snapshot.seed() & 0xFFFFL) / 65535.0F,
                0.0F,
                0.0F,
                0.0F
        );
    }

    public static VolumetricRenderCell fromFieldCloudlet(
            CloudFieldSnapshot snapshot,
            CloudletLayout.Cloudlet cloudlet
    ) {
        Vec3 center = cloudlet.worldCenter(snapshot);
        // Modest expansion: enough for neighboring cloudlets to overlap into
        // one mass, not so much that they merge into a giant flat card.
        float radius = Math.max(6.0F, cloudlet.horizontalRadius() * 1.22F);
        float verticalRadius = radius * Math.max(0.65F, cloudlet.verticalScale() * 1.08F);
        float baseY = Math.max(snapshot.baseY(), (float) center.y() - verticalRadius * 0.82F);
        float topY = Math.min(snapshot.topY(), (float) center.y() + verticalRadius * 0.98F);
        if (topY <= baseY + 1.0F) {
            topY = Math.min(snapshot.topY(), baseY + Math.max(2.0F, verticalRadius));
        }

        long seed = cloudlet.id().mixedSeed(snapshot.seed());
        float effectiveDensity = Math.min(1.0F,
                snapshot.effectiveDensity() * cloudlet.densityScale()
                        * (0.88F + 0.48F * cloudlet.coverageWeight()) * 1.28F);
        return new VolumetricRenderCell(
                center.x(),
                center.z(),
                baseY,
                topY,
                radius,
                Math.max(5.0F, radius * 0.78F),
                (seed & 0x3FFL) * 0.006135923F,
                effectiveDensity,
                0.42F,
                snapshot.stormPotential(),
                profileFor(snapshot),
                snapshot.morphologyFamily().ordinal(),
                snapshot.verticalDevelopment(),
                snapshot.humidityInfluence(),
                snapshot.anvilStrength(),
                snapshot.precipitationIntensity(),
                snapshot.lifecycleStage(),
                (seed & 0xFFFFL) / 65535.0F,
                0.0F,
                0.0F,
                0.0F
        );
    }

    private static int profileFor(String cloudTypeId, CloudMorphologyFamily morphology) {
        String id = cloudTypeId == null ? "" : cloudTypeId.toLowerCase(Locale.ROOT);
        if (id.contains("supercell") || id.contains("hurricane")
                || morphology == CloudMorphologyFamily.SPIRAL_STORM) {
            return 7;
        }
        if (id.contains("cumulonimbus") || morphology == CloudMorphologyFamily.STORM_ANVIL) {
            return 4;
        }
        if (id.contains("nimbostratus")) {
            return 5;
        }
        if (id.contains("stratocumulus") || morphology == CloudMorphologyFamily.CELLULAR_SHEET) {
            return 2;
        }
        if (id.contains("stratus") || morphology == CloudMorphologyFamily.SHEET) {
            return 1;
        }
        if (id.contains("cirrus") || morphology == CloudMorphologyFamily.FILAMENT) {
            return 6;
        }
        if (id.contains("cumulus") || morphology == CloudMorphologyFamily.PUFF
                || morphology == CloudMorphologyFamily.TOWER) {
            return 3;
        }
        return 0;
    }

    /** Returns the stable shader profile encoded for one canonical field snapshot. */
    public static int profileFor(CloudFieldSnapshot snapshot) {
        return profileFor(snapshot.cloudTypeId(), snapshot.morphologyFamily());
    }

    private static int profileFor(net.Gabou.projectatmosphere.clouds.cell.CloudCellClassification classification) {
        return switch (classification) {
            case STRATUS -> 1;
            case STRATOCUMULUS -> 2;
            case CUMULUS_HUMILIS, CUMULUS_MEDIOCRIS, CUMULUS_CONGESTUS -> 3;
            case CUMULONIMBUS -> 4;
            case CIRRIFORM -> 6;
            case UNCLASSIFIED -> 0;
        };
    }

    private static CloudMorphologyFamily morphologyFor(
            net.Gabou.projectatmosphere.clouds.cell.CloudCellClassification classification
    ) {
        return switch (classification) {
            case STRATUS -> CloudMorphologyFamily.SHEET;
            case STRATOCUMULUS -> CloudMorphologyFamily.CELLULAR_SHEET;
            case CUMULUS_CONGESTUS -> CloudMorphologyFamily.TOWER;
            case CUMULONIMBUS -> CloudMorphologyFamily.STORM_ANVIL;
            case CIRRIFORM -> CloudMorphologyFamily.FILAMENT;
            case CUMULUS_HUMILIS, CUMULUS_MEDIOCRIS, UNCLASSIFIED -> CloudMorphologyFamily.PUFF;
        };
    }

    private static float lifecycleFor(
            net.Gabou.projectatmosphere.clouds.cell.CloudCellLifecyclePhase phase
    ) {
        return switch (phase) {
            case FORMING -> 0.20F;
            case MATURE -> 0.50F;
            case DISSIPATING -> 0.85F;
        };
    }
}
