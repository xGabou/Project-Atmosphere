package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

/** Copied, immutable primitive input safe for the client CPU worker. */
public final class StormGeometryBuildInput {
    private final long sessionGeneration;
    private final long worldGeneration;
    private final long dimensionGeneration;
    private final long ownerGeneration;
    private final long resourceGeneration;
    private final long mapGeneration;
    private final long requestGeneration;
    private final long topologySignature;
    private final long gridSignature;
    private final double cameraX;
    private final double cameraZ;
    private final double originX;
    private final double originZ;
    private final float extent;
    private final float detailDistance;
    private final float transitionWidth;
    private final StormLobeDescriptor[] descriptors;

    public StormGeometryBuildInput(
            long sessionGeneration,
            long requestGeneration,
            long topologySignature,
            long gridSignature,
            double cameraX,
            double cameraZ,
            double originX,
            double originZ,
            float extent,
            StormLobeDescriptor[] descriptors
    ) {
        this(
                sessionGeneration,
                sessionGeneration,
                sessionGeneration,
                sessionGeneration,
                sessionGeneration,
                gridSignature,
                requestGeneration,
                topologySignature,
                gridSignature,
                cameraX,
                cameraZ,
                originX,
                originZ,
                extent,
                extent,
                0.0F,
                descriptors
        );
    }

    public StormGeometryBuildInput(
            long sessionGeneration,
            long worldGeneration,
            long dimensionGeneration,
            long ownerGeneration,
            long resourceGeneration,
            long mapGeneration,
            long requestGeneration,
            long topologySignature,
            long gridSignature,
            double cameraX,
            double cameraZ,
            double originX,
            double originZ,
            float extent,
            float detailDistance,
            float transitionWidth,
            StormLobeDescriptor[] descriptors
    ) {
        if (!Double.isFinite(cameraX) || !Double.isFinite(cameraZ)
                || !Double.isFinite(originX) || !Double.isFinite(originZ)
                || !Float.isFinite(extent) || extent <= 0.0F
                || !Float.isFinite(detailDistance) || detailDistance < 0.0F
                || !Float.isFinite(transitionWidth) || transitionWidth < 0.0F) {
            throw new IllegalArgumentException("invalid storm build domain");
        }
        this.sessionGeneration = sessionGeneration;
        this.worldGeneration = worldGeneration;
        this.dimensionGeneration = dimensionGeneration;
        this.ownerGeneration = ownerGeneration;
        this.resourceGeneration = resourceGeneration;
        this.mapGeneration = mapGeneration;
        this.requestGeneration = requestGeneration;
        this.topologySignature = topologySignature;
        this.gridSignature = gridSignature;
        this.cameraX = cameraX;
        this.cameraZ = cameraZ;
        this.originX = originX;
        this.originZ = originZ;
        this.extent = extent;
        this.detailDistance = detailDistance;
        this.transitionWidth = transitionWidth;
        this.descriptors = descriptors == null
                ? new StormLobeDescriptor[0]
                : descriptors.clone();
    }

    public long sessionGeneration() { return sessionGeneration; }
    public long worldGeneration() { return worldGeneration; }
    public long dimensionGeneration() { return dimensionGeneration; }
    public long ownerGeneration() { return ownerGeneration; }
    public long resourceGeneration() { return resourceGeneration; }
    public long mapGeneration() { return mapGeneration; }
    public long requestGeneration() { return requestGeneration; }
    public long topologySignature() { return topologySignature; }
    public long gridSignature() { return gridSignature; }
    public double cameraX() { return cameraX; }
    public double cameraZ() { return cameraZ; }
    public double originX() { return originX; }
    public double originZ() { return originZ; }
    public float extent() { return extent; }
    public float detailDistance() { return detailDistance; }
    public float transitionWidth() { return transitionWidth; }
    public StormLobeDescriptor[] descriptors() { return descriptors.clone(); }
    StormLobeDescriptor[] descriptorsUnsafe() { return descriptors; }
}
