package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

/** Pure temporal-identity contract; interpolation and advection are intentionally absent. */
final class VolumetricHistoryValidity {
    private VolumetricHistoryValidity() {
    }

    static boolean canRetain(Key previous, Key current) {
        return previous != null && current != null && previous.equals(current);
    }

    record Key(
            long worldGeneration,
            long dimensionGeneration,
            long ownerGeneration,
            long resourceGeneration,
            long topologyGeneration,
            long resolutionGeneration
    ) {
        static final Key EMPTY = new Key(0L, 0L, 0L, 0L, 0L, 0L);

        static Key nativeFrame(
                long worldGeneration,
                long dimensionGeneration,
                long ownerGeneration,
                long resourceGeneration,
                long topologyGeneration,
                long resolutionGeneration
        ) {
            return new Key(
                    worldGeneration,
                    dimensionGeneration,
                    ownerGeneration,
                    resourceGeneration,
                    topologyGeneration,
                    resolutionGeneration
            );
        }
    }
}
