package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

/** Complete worker result. GPU adoption validates every generation token. */
public final class StormGeometryBuild {
    private final long sessionGeneration;
    private final long worldGeneration;
    private final long dimensionGeneration;
    private final long ownerGeneration;
    private final long resourceGeneration;
    private final long mapGeneration;
    private final long requestGeneration;
    private final long topologySignature;
    private final long gridSignature;
    private final double originX;
    private final double originZ;
    private final float extent;
    private final float detailDistance;
    private final float transitionWidth;
    private final StormLobeDescriptor[] selectedDescriptors;
    private final float[] descriptorTexels;
    private final float[] candidateTexels;
    private final int activeTiles;
    private final int overflowTiles;
    private final int maxCandidatesPerTile;
    private final int omittedGroups;
    private final long buildNanos;
    private final long completedNanos;

    public StormGeometryBuild(
            StormGeometryBuildInput input,
            StormLobeDescriptor[] selectedDescriptors,
            float[] descriptorTexels,
            float[] candidateTexels,
            int activeTiles,
            int overflowTiles,
            int maxCandidatesPerTile,
            int omittedGroups,
            long buildNanos
    ) {
        int selectedCount = selectedDescriptors == null ? 0 : selectedDescriptors.length;
        int descriptorFloatCount = StormLobeSpatialIndex.MAX_LOBES
                * StormLobeDescriptor.FLOATS_PER_DESCRIPTOR;
        int candidateFloatCount = StormLobeSpatialIndex.GRID_SIZE
                * StormLobeSpatialIndex.GRID_SIZE * 4;
        if (selectedCount > StormLobeSpatialIndex.MAX_LOBES
                || descriptorTexels == null || descriptorTexels.length != descriptorFloatCount
                || candidateTexels == null || candidateTexels.length != candidateFloatCount) {
            throw new IllegalArgumentException("invalid bounded storm build result");
        }
        this.sessionGeneration = input.sessionGeneration();
        this.worldGeneration = input.worldGeneration();
        this.dimensionGeneration = input.dimensionGeneration();
        this.ownerGeneration = input.ownerGeneration();
        this.resourceGeneration = input.resourceGeneration();
        this.mapGeneration = input.mapGeneration();
        this.requestGeneration = input.requestGeneration();
        this.topologySignature = input.topologySignature();
        this.gridSignature = input.gridSignature();
        this.originX = input.originX();
        this.originZ = input.originZ();
        this.extent = input.extent();
        this.detailDistance = input.detailDistance();
        this.transitionWidth = input.transitionWidth();
        this.selectedDescriptors = selectedDescriptors == null
                ? new StormLobeDescriptor[0]
                : selectedDescriptors.clone();
        this.descriptorTexels = descriptorTexels == null ? new float[0] : descriptorTexels;
        this.candidateTexels = candidateTexels == null ? new float[0] : candidateTexels;
        this.activeTiles = Math.max(0, activeTiles);
        this.overflowTiles = Math.max(0, overflowTiles);
        this.maxCandidatesPerTile = Math.max(0, maxCandidatesPerTile);
        this.omittedGroups = Math.max(0, omittedGroups);
        this.buildNanos = Math.max(0L, buildNanos);
        this.completedNanos = System.nanoTime();
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
    public double originX() { return originX; }
    public double originZ() { return originZ; }
    public float extent() { return extent; }
    public float detailDistance() { return detailDistance; }
    public float transitionWidth() { return transitionWidth; }
    public int descriptorCount() { return selectedDescriptors.length; }
    public int activeTiles() { return activeTiles; }
    public int overflowTiles() { return overflowTiles; }
    public int maxCandidatesPerTile() { return maxCandidatesPerTile; }
    public int omittedGroups() { return omittedGroups; }
    public long buildNanos() { return buildNanos; }
    public long completedNanos() { return completedNanos; }

    public boolean matchesLifecycleGeneration(long generation) {
        return matchesLifecycleGeneration(
                generation, generation, generation, generation, generation, mapGeneration
        );
    }

    public boolean matchesLifecycleGeneration(
            long expectedSession,
            long expectedWorld,
            long expectedDimension,
            long expectedOwner,
            long expectedResource,
            long expectedMap
    ) {
        return sessionGeneration == expectedSession
                && worldGeneration == expectedWorld
                && dimensionGeneration == expectedDimension
                && ownerGeneration == expectedOwner
                && resourceGeneration == expectedResource
                && mapGeneration == expectedMap;
    }
    public StormLobeDescriptor[] selectedDescriptors() { return selectedDescriptors.clone(); }
    StormLobeDescriptor[] selectedDescriptorsUnsafe() { return selectedDescriptors; }
    float[] descriptorTexelsUnsafe() { return descriptorTexels; }
    float[] candidateTexelsUnsafe() { return candidateTexels; }
}
