package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

/** Immutable identity/geometry generation successfully adopted by the renderer. */
public final class StormRenderSnapshot {
    public static final StormRenderSnapshot EMPTY = new StormRenderSnapshot(
            0L, 0L, 0L, 0.0D, 0.0D, 0.0F, new StormLobeDescriptor[0]
    );

    private final long sessionGeneration;
    private final long topologyGeneration;
    private final long gridSignature;
    private final double originX;
    private final double originZ;
    private final float extent;
    private final StormLobeDescriptor[] descriptors;

    public StormRenderSnapshot(
            long sessionGeneration,
            long topologyGeneration,
            long gridSignature,
            double originX,
            double originZ,
            float extent,
            StormLobeDescriptor[] descriptors
    ) {
        this.sessionGeneration = sessionGeneration;
        this.topologyGeneration = topologyGeneration;
        this.gridSignature = gridSignature;
        this.originX = originX;
        this.originZ = originZ;
        this.extent = extent;
        this.descriptors = descriptors == null
                ? new StormLobeDescriptor[0]
                : descriptors.clone();
    }

    public long sessionGeneration() { return sessionGeneration; }
    public long topologyGeneration() { return topologyGeneration; }
    public long gridSignature() { return gridSignature; }
    public double originX() { return originX; }
    public double originZ() { return originZ; }
    public float extent() { return extent; }
    public int descriptorCount() { return descriptors.length; }
    public StormLobeDescriptor[] descriptors() { return descriptors.clone(); }
    StormLobeDescriptor[] descriptorsUnsafe() { return descriptors; }
}
