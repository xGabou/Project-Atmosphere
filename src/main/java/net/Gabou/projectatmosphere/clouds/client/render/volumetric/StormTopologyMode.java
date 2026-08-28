package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

/** T119 diagnostic selector; compact is the only production default. */
public enum StormTopologyMode {
    COMPACT(0, "compact"),
    LEGACY_SCAN(1, "legacy_scan");

    private final int shaderId;
    private final String serializedName;

    StormTopologyMode(int shaderId, String serializedName) {
        this.shaderId = shaderId;
        this.serializedName = serializedName;
    }

    public int shaderId() {
        return shaderId;
    }

    public String serializedName() {
        return serializedName;
    }
}
