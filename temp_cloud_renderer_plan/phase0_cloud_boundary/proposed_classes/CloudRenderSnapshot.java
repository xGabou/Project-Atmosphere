/**
 * Temporary proposal skeleton for the Phase 0 cloud renderer boundary.
 * TODO: move into the real source tree only after the boundary is approved.
 */
public final class CloudRenderSnapshot {
    /** Whether the fake or future cloud should render. */
    private final boolean enabled;

    /** Dimension identifier for the cloud snapshot. */
    private final String dimension;

    /** World time captured for debug or future interpolation. */
    private final long worldTime;

    /** Partial tick used for client-side interpolation. */
    private final float partialTick;

    /** Camera position used for render-relative placement. */
    private final Object cameraPosition;

    /** World-space center of the cloud region. */
    private final Object regionCenter;

    /** Radius of the visible cloud region. */
    private final float regionRadius;

    /** Lower Y bound of the cloud volume. */
    private final float cloudBaseY;

    /** Upper Y bound of the cloud volume. */
    private final float cloudTopY;

    /** Overall density or opacity control. */
    private final float density;

    /** Coverage amount for later cloud rendering. */
    private final float coverage;

    /** Edge softness for later cloud rendering. */
    private final float edgeSoftness;

    /** Fake horizontal offset on the X axis. */
    private final float windOffsetX;

    /** Fake horizontal offset on the Z axis. */
    private final float windOffsetZ;

    /** Debug tint for the fake cloud. */
    private final int debugColorOrTint;

    /**
     * Creates an immutable cloud render snapshot.
     *
     * @param enabled whether the cloud is visible
     * @param dimension dimension identifier
     * @param worldTime current world time
     * @param partialTick client partial tick
     * @param cameraPosition camera position
     * @param regionCenter cloud center
     * @param regionRadius cloud radius
     * @param cloudBaseY cloud base height
     * @param cloudTopY cloud top height
     * @param density density value
     * @param coverage coverage value
     * @param edgeSoftness edge softness value
     * @param windOffsetX x-axis wind offset
     * @param windOffsetZ z-axis wind offset
     * @param debugColorOrTint debug tint value
     */
    public CloudRenderSnapshot(boolean enabled, String dimension, long worldTime, float partialTick, Object cameraPosition, Object regionCenter, float regionRadius, float cloudBaseY, float cloudTopY, float density, float coverage, float edgeSoftness, float windOffsetX, float windOffsetZ, int debugColorOrTint) {
        this.enabled = enabled;
        this.dimension = dimension;
        this.worldTime = worldTime;
        this.partialTick = partialTick;
        this.cameraPosition = cameraPosition;
        this.regionCenter = regionCenter;
        this.regionRadius = regionRadius;
        this.cloudBaseY = cloudBaseY;
        this.cloudTopY = cloudTopY;
        this.density = density;
        this.coverage = coverage;
        this.edgeSoftness = edgeSoftness;
        this.windOffsetX = windOffsetX;
        this.windOffsetZ = windOffsetZ;
        this.debugColorOrTint = debugColorOrTint;
    }

    /** Returns whether the cloud is enabled. */
    public boolean isEnabled() {
        return enabled;
    }

    /** Returns the dimension identifier. */
    public String getDimension() {
        return dimension;
    }

    /** Returns the captured world time. */
    public long getWorldTime() {
        return worldTime;
    }

    /** Returns the partial tick. */
    public float getPartialTick() {
        return partialTick;
    }

    /** Returns the camera position object. */
    public Object getCameraPosition() {
        return cameraPosition;
    }

    /** Returns the region center object. */
    public Object getRegionCenter() {
        return regionCenter;
    }

    /** Returns the region radius. */
    public float getRegionRadius() {
        return regionRadius;
    }

    /** Returns the cloud base Y. */
    public float getCloudBaseY() {
        return cloudBaseY;
    }

    /** Returns the cloud top Y. */
    public float getCloudTopY() {
        return cloudTopY;
    }

    /** Returns the density value. */
    public float getDensity() {
        return density;
    }

    /** Returns the coverage value. */
    public float getCoverage() {
        return coverage;
    }

    /** Returns the edge softness value. */
    public float getEdgeSoftness() {
        return edgeSoftness;
    }

    /** Returns the x-axis wind offset. */
    public float getWindOffsetX() {
        return windOffsetX;
    }

    /** Returns the z-axis wind offset. */
    public float getWindOffsetZ() {
        return windOffsetZ;
    }

    /** Returns the debug tint value. */
    public int getDebugColorOrTint() {
        return debugColorOrTint;
    }
}
