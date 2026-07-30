package net.Gabou.projectatmosphere.clouds.field;

/**
 * Distance bands for CloudField rendering. These values describe renderer
 * intent only; they do not mutate simulation state.
 */
public enum CloudLodBand {
    DYNAMIC(true, 1.00F, 1.00F, 0.00F),
    TRANSITION(true, 0.55F, 0.55F, 0.45F),
    FAR_PROCEDURAL(false, 0.00F, 0.24F, 1.00F),
    HAZE(false, 0.00F, 0.00F, 1.00F);

    private final boolean identifiableCloudlets;
    private final float cloudletFraction;
    private final float detailScale;
    private final float proceduralBlend;

    CloudLodBand(
            boolean identifiableCloudlets,
            float cloudletFraction,
            float detailScale,
            float proceduralBlend
    ) {
        this.identifiableCloudlets = identifiableCloudlets;
        this.cloudletFraction = cloudletFraction;
        this.detailScale = detailScale;
        this.proceduralBlend = proceduralBlend;
    }

    public boolean hasIdentifiableCloudlets() {
        return identifiableCloudlets;
    }

    public float cloudletFraction() {
        return cloudletFraction;
    }

    public float detailScale() {
        return detailScale;
    }

    public float proceduralBlend() {
        return proceduralBlend;
    }
}
