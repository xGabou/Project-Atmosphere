package net.Gabou.projectatmosphere.clouds.frontend.debug;

import net.Gabou.projectatmosphere.clouds.frontend.CloudRenderSnapshot;
import net.minecraft.world.phys.Vec3;

/**
 * Crée les snapshots utilisés uniquement par le rendu debug.
 * Cette classe ne modifie jamais les snapshots live courants.
 */
public final class CloudDebugSnapshotFactory {
    private static final Vec3 DEFAULT_CENTER = new Vec3(0.0D, 110.0D, 0.0D);

    private static final float DEBUG_RADIUS = 16.0F;
    private static final float DEBUG_HALF_HEIGHT = 8.0F;

    private static final float DEBUG_DENSITY = 0.65F;
    private static final float DEBUG_COVERAGE = 0.80F;
    private static final float DEBUG_EDGE_SOFTNESS = 0.25F;

    private static final int DEBUG_COLOR = 0xFFFF5555;

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
                DEBUG_COLOR
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
                debugColorOrTint
        );
    }
}
