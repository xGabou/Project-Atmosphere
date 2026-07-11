package net.Gabou.projectatmosphere.clouds.client.render.depth;

/**
 * Immutable description of the scene depth made safe for cloud sampling.
 * The texture is detached from the framebuffer into which clouds composite.
 */
public record SceneDepthFrame(
        int textureId,
        int width,
        int height,
        int sourceFramebuffer,
        int copyFramebuffer,
        String source,
        boolean valid,
        boolean fallbackUsed,
        boolean detached
) {
    public static final SceneDepthFrame INVALID = new SceneDepthFrame(
            0, 0, 0, 0, 0, "unavailable", false, true, false
    );

    public String diagnostics() {
        return "source=" + source
                + " size=" + width + "x" + height
                + " sourceFbo=" + sourceFramebuffer
                + " copyFbo=" + copyFramebuffer
                + " texture=" + textureId
                + " valid=" + valid
                + " detached=" + detached
                + " fallback=" + fallbackUsed;
    }
}
