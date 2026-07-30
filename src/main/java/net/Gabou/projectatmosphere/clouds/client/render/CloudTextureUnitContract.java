package net.Gabou.projectatmosphere.clouds.client.render;

/**
 * One source of truth for texture units touched by the native cloud pass.
 * Minecraft 1.20.1 caches units 0..11; PA binds the remaining textures with
 * raw OpenGL and the render-state guard must capture every one of them.
 */
public final class CloudTextureUnitContract {
    public static final int MINECRAFT_WORKING_UNIT = 0;
    public static final int MAX_MINECRAFT_TRACKED_UNIT = 11;
    public static final int PUFF_CANDIDATE_UNIT = 12;
    public static final int BASE_NOISE_UNIT = 13;
    public static final int DETAIL_NOISE_UNIT = 14;
    public static final int MAX_PA_TOUCHED_UNIT = DETAIL_NOISE_UNIT;
    public static final int REQUIRED_FRAGMENT_TEXTURE_UNITS = MAX_PA_TOUCHED_UNIT + 1;

    private CloudTextureUnitContract() {
    }

    /** Pure invariant check used by the standalone renderer diagnostics. */
    public static void selfCheck() {
        if (MINECRAFT_WORKING_UNIT < 0
                || MINECRAFT_WORKING_UNIT > MAX_MINECRAFT_TRACKED_UNIT
                || PUFF_CANDIDATE_UNIT != MAX_MINECRAFT_TRACKED_UNIT + 1
                || BASE_NOISE_UNIT != PUFF_CANDIDATE_UNIT + 1
                || DETAIL_NOISE_UNIT != BASE_NOISE_UNIT + 1
                || REQUIRED_FRAGMENT_TEXTURE_UNITS != MAX_PA_TOUCHED_UNIT + 1) {
            throw new IllegalStateException("native cloud texture-unit contract is not contiguous");
        }
    }
}
