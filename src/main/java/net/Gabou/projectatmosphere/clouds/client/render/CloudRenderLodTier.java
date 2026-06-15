package net.Gabou.projectatmosphere.clouds.client.render;

/**
 * Client-side PA cloud visibility tiers. These tiers only affect render cost
 * and visual simplification; they do not mutate cloud simulation state.
 */
public enum CloudRenderLodTier {
    NEAR(0, 0.0F, 250.0F, 1.00F, 1.00F, 1.00F, 1.00F, 12),
    MEDIUM(1, 250.0F, 750.0F, 0.68F, 0.86F, 0.90F, 0.76F, 12),
    FAR(2, 750.0F, 1500.0F, 0.42F, 0.72F, 0.82F, 0.48F, 10),
    HORIZON(3, 1500.0F, Float.MAX_VALUE, 0.22F, 0.58F, 0.74F, 0.22F, 8);

    private final int order;
    private final float minDistance;
    private final float maxDistance;
    private final float raymarchStepScale;
    private final float densityScale;
    private final float coverageScale;
    private final float detailScale;
    private final int budget;

    CloudRenderLodTier(
            int order,
            float minDistance,
            float maxDistance,
            float raymarchStepScale,
            float densityScale,
            float coverageScale,
            float detailScale,
            int budget
    ) {
        this.order = order;
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        this.raymarchStepScale = raymarchStepScale;
        this.densityScale = densityScale;
        this.coverageScale = coverageScale;
        this.detailScale = detailScale;
        this.budget = budget;
    }

    public int getOrder() {
        return order;
    }

    public float getMinDistance() {
        return minDistance;
    }

    public float getMaxDistance() {
        return maxDistance;
    }

    public float getRaymarchStepScale() {
        return raymarchStepScale;
    }

    public float getDensityScale() {
        return densityScale;
    }

    public float getCoverageScale() {
        return coverageScale;
    }

    public float getDetailScale() {
        return detailScale;
    }

    public int getBudget() {
        return budget;
    }

    public static CloudRenderLodTier forDistance(float distance) {
        float clamped = Math.max(0.0F, distance);
        if (clamped < MEDIUM.minDistance) {
            return NEAR;
        }
        if (clamped < FAR.minDistance) {
            return MEDIUM;
        }
        if (clamped < HORIZON.minDistance) {
            return FAR;
        }
        return HORIZON;
    }
}
