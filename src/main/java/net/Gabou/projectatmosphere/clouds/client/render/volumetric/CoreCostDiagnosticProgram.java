package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

/**
 * Selects which separately linked build of the volumetric cloud raymarch a
 * frame binds.
 *
 * <p>T161 splits one monolithic program into two. Both are compiled from the
 * same {@code cloud_atmosphere_volume.fsh}; they differ only in whether the
 * dormant diagnostic selectors are uniforms or compile-time constants.
 *
 * <p>{@link #LEAN_FINAL} is what ordinary rendering uses. Its diagnostic,
 * oracle, trace, legacy and alternate-output paths are statically unreachable,
 * so the driver eliminates them before allocating registers instead of keeping
 * their control flow live inside the march loop. It can only be bound on frames
 * whose uploads would have matched those constants exactly - see
 * {@code VolumetricCloudRenderer.leanFinalEligible()}.
 *
 * <p>{@link #DIAGNOSTIC_MONOLITH} is the unmodified program. Nothing was
 * removed from it: every historical diagnostic campaign still selects it and
 * still drives the same uniforms it always did. It is deliberately never the
 * silent fallback for a FINAL frame, because binding it there would restore the
 * full cost this task exists to remove without changing the image.
 *
 * <p>The experimental evidence for the split is {@code e301494} on
 * {@code experiment/core-cost}: on one PLAY_VIS_NEAR/Ultra fixture the
 * specialized program rendered a pixel-identical image at 2.984x the speed of
 * the monolith. That branch is evidence, not an implementation to merge.
 */
public enum CoreCostDiagnosticProgram {
    /** The unmodified program retaining every dormant diagnostic path. */
    DIAGNOSTIC_MONOLITH("diagnostic_monolith"),
    /** The compile-time-specialized normal renderer used by FINAL frames. */
    LEAN_FINAL("lean_final"),
    /**
     * T140 diagnostic: the lean renderer plus a conservative whole-pixel
     * rejection oracle. Rays that provably cannot reach cloud return the
     * renderer's own no-cloud result instead of marching. Diagnostic only - the
     * oracle is guarded by PA_T140_ORACLE, which FINAL never defines.
     */
    T140_PIXEL_ORACLE("t140_pixel_oracle"),
    /**
     * T140 diagnostic: renders the oracle's verdict rather than the scene, so
     * potential cloud coverage can be counted per fixture from the captured
     * image. Opaque where the pixel could reach cloud, transparent where it
     * provably could not.
     */
    T140_MASK("t140_mask"),
    /** T140 diagnostic: the oracle applied per 8x8 tile instead of per pixel. */
    T140_TILE8("t140_tile8"),
    /** T140 diagnostic: the oracle applied per 16x16 tile instead of per pixel. */
    T140_TILE16("t140_tile16");

    private final String serializedName;

    CoreCostDiagnosticProgram(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    /**
     * True for the programs that produce a normal cloud frame, and so may
     * publish this frame as the next frame's history.
     *
     * <p>The pixel and tile oracles qualify: they reject only rays that
     * provably find no density, and emit exactly the no-cloud result the
     * renderer itself emits for such a ray, so their output is the production
     * image. Keeping them here also keeps the temporal state identical across
     * the T140 arms, which is what makes their timings comparable. The mask
     * program renders a classification, not a scene, and must never become
     * history.
     */
    public boolean normalProductionOutput() {
        return this == LEAN_FINAL || this == T140_PIXEL_ORACLE
                || this == T140_TILE8 || this == T140_TILE16;
    }

    public static CoreCostDiagnosticProgram parse(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        for (CoreCostDiagnosticProgram program : values()) {
            if (program.serializedName.equals(normalized)) {
                return program;
            }
        }
        return null;
    }
}
