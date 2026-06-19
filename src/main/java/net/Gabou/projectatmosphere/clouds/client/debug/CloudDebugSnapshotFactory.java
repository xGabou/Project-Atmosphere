package net.Gabou.projectatmosphere.clouds.client.debug;

import net.Gabou.projectatmosphere.clouds.client.CloudRenderSnapshot;
import net.Gabou.projectatmosphere.clouds.type.CloudMaterialProfile;
import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudShapeProfile;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import net.Gabou.projectatmosphere.modules.weather.PrecipitationTier;
import net.Gabou.projectatmosphere.modules.weather.StormVisualTier;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Crée les snapshots utilisés uniquement par le rendu debug.
 * Cette classe ne modifie jamais les snapshots live courants.
 */
public final class CloudDebugSnapshotFactory {
    private static final UUID DEBUG_REGION_ID = new UUID(0L, 0L);
    private static final UUID DEBUG_CLUSTER_ID = new UUID(0L, 1L);
    private static final Vec3 DEFAULT_CENTER = new Vec3(0.0D, 110.0D, 0.0D);

    private static final float DEBUG_RADIUS = 16.0F;
    private static final float DEBUG_HALF_HEIGHT = 8.0F;

    private static final float DEBUG_DENSITY = 0.65F;
    private static final float DEBUG_COVERAGE = 0.80F;
    private static final float DEBUG_EDGE_SOFTNESS = 0.25F;
    private static final float DEBUG_VERTICAL_THICKNESS = 1.0F;
    private static final float DEBUG_EDGE_EROSION_STRENGTH = 0.25F;
    private static final float DEBUG_TOP_SOFTNESS = 0.20F;
    private static final float DEBUG_BASE_SOFTNESS = 0.18F;
    private static final float DEBUG_BASE_DARKNESS = 0.25F;
    private static final float DEBUG_NOISE_SCALE = 0.020F;
    private static final float DEBUG_DETAIL_NOISE_SCALE = 0.105F;
    private static final float DEBUG_EROSION_NOISE_SCALE = 0.118F;
    private static final float DEBUG_DENSITY_MULTIPLIER = 1.0F;
    private static final float DEBUG_COVERAGE_MULTIPLIER = 1.0F;
    private static final float DEBUG_HEIGHT_SQUASH = 1.0F;
    private static final float DEBUG_TOWER_STRENGTH = 0.0F;
    private static final float DEBUG_ANVIL_STRENGTH = 0.0F;
    private static final float DEBUG_PRECIPITATION_CORE_STRENGTH = 0.0F;

    private static final int DEBUG_COLOR = 0xFFFF5555;
    private static final int DEBUG_CLOUD_SEED = 1337;

    private CloudDebugSnapshotFactory() {
    }

    /**
     * Crée un snapshot debug factice au centre par défaut.
     *
     * @return snapshot debug factice
     */
    public static CloudRenderSnapshot createFakeSnapshot() {
        return createFakeSnapshot(Vec3.ZERO, DEFAULT_CENTER);
    }

    /**
     * Crée un snapshot debug factice autour du centre demandé.
     *
     * @param center centre monde du snapshot debug
     * @return snapshot debug factice
     */
    public static CloudRenderSnapshot createFakeSnapshot(Vec3 center) {
        return createFakeSnapshot(Vec3.ZERO, center);
    }

    /**
     * Crée un snapshot debug factice avec une caméra et un centre explicites.
     *
     * @param cameraPosition position caméra utilisée par le debug
     * @param center centre monde du snapshot debug
     * @return snapshot debug factice
     */
    public static CloudRenderSnapshot createFakeSnapshot(Vec3 cameraPosition, Vec3 center) {
        Vec3 safeCameraPosition = cameraPosition != null ? cameraPosition : Vec3.ZERO;
        Vec3 safeCenter = center != null ? center : DEFAULT_CENTER;

        float cloudBaseY = (float) safeCenter.y() - DEBUG_HALF_HEIGHT;
        float cloudTopY = (float) safeCenter.y() + DEBUG_HALF_HEIGHT;

        return new CloudRenderSnapshot(
                true,
                DEBUG_REGION_ID,
                DEBUG_CLUSTER_ID,
                "minecraft:overworld",
                0L,
                0.0F,
                safeCameraPosition,
                safeCenter,
                safeCenter,
                Vec3.ZERO,
                DEBUG_RADIUS,
                cloudBaseY,
                cloudTopY,
                DEBUG_DENSITY,
                DEBUG_COVERAGE,
                DEBUG_EDGE_SOFTNESS,
                0.0F,
                0.0F,
                0,
                20 * 60 * 10,
                1.0F,
                0.0F,
                DEBUG_RADIUS,
                DEBUG_COVERAGE,
                DEBUG_DENSITY,
                DEBUG_RADIUS,
                0L,
                0L,
                0.0F,
                CloudTypeRegistry.DEFAULT_CLOUD_TYPE_ID,
                CloudTypeRegistry.DEFAULT_CLOUD_TYPE_ID,
                CloudMorphologyFamily.DEBUG,
                0,
                DEBUG_VERTICAL_THICKNESS,
                DEBUG_EDGE_EROSION_STRENGTH,
                DEBUG_TOP_SOFTNESS,
                DEBUG_BASE_SOFTNESS,
                DEBUG_BASE_DARKNESS,
                DEBUG_NOISE_SCALE,
                DEBUG_DETAIL_NOISE_SCALE,
                DEBUG_EROSION_NOISE_SCALE,
                DEBUG_DENSITY_MULTIPLIER,
                DEBUG_COVERAGE_MULTIPLIER,
                DEBUG_HEIGHT_SQUASH,
                DEBUG_TOWER_STRENGTH,
                DEBUG_ANVIL_STRENGTH,
                DEBUG_PRECIPITATION_CORE_STRENGTH,
                DEBUG_CLOUD_SEED,
                DEBUG_COLOR,
                CloudMaterialProfile.DEFAULT,
                CloudShapeProfile.DEFAULT,
                StormVisualTier.CLOUDY,
                PrecipitationTier.NONE,
                0.45F,
                0.0F
        );
    }

    /**
     * Crée une variante debug d'un snapshot existant avec une couleur dédiée.
     *
     * @param base snapshot source
     * @param debugColorOrTint couleur debug ARGB
     * @return snapshot debug dérivé
     */
    public static CloudRenderSnapshot createDebugSnapshot(CloudRenderSnapshot base, int debugColorOrTint) {
        if (base == null) {
            base = createFakeSnapshot();
        }

        return new CloudRenderSnapshot(
                base.isEnabled(),
                base.getRegionId(),
                base.getClusterId(),
                base.getDimension(),
                base.getWorldTime(),
                base.getPartialTick(),
                base.getCameraPosition(),
                base.getRegionCenter(),
                base.getPreviousRegionCenter(),
                base.getVelocity(),
                base.getRegionRadius(),
                base.getCloudBaseY(),
                base.getCloudTopY(),
                base.getDensity(),
                base.getCoverage(),
                base.getEdgeSoftness(),
                base.getWindOffsetX(),
                base.getWindOffsetZ(),
                base.getAgeTicks(),
                base.getLifetimeTicks(),
                base.getGrowth(),
                base.getDecay(),
                base.getTargetRadius(),
                base.getTargetCoverage(),
                base.getTargetDensity(),
                base.getSpawnRadius(),
                base.getLastMotionTick(),
                base.getLastGrowthTick(),
                base.getLastGrowthRate(),
                base.getCloudTypeId(),
                base.getPreviousCloudTypeId(),
                base.getMorphologyFamily(),
                base.getCloudTypeTicks(),
                base.getVerticalThickness(),
                base.getEdgeErosionStrength(),
                base.getTopSoftness(),
                base.getBaseSoftness(),
                base.getBaseDarkness(),
                base.getNoiseScale(),
                base.getDetailNoiseScale(),
                base.getErosionNoiseScale(),
                base.getDensityMultiplier(),
                base.getCoverageMultiplier(),
                base.getHeightSquash(),
                base.getTowerStrength(),
                base.getAnvilStrength(),
                base.getPrecipitationCoreStrength(),
                base.getCloudSeed(),
                debugColorOrTint,
                base.getMaterialProfile(),
                base.getShapeProfile(),
                base.getStormVisualTier(),
                base.getPrecipitationTier(),
                base.getShadowContribution(),
                base.getLightningInfluence()
        );
    }
}
