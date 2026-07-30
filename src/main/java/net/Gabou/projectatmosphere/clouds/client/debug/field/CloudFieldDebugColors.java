package net.Gabou.projectatmosphere.clouds.client.debug.field;

import net.Gabou.projectatmosphere.clouds.field.CloudLodBand;

/**
 * Small color palette for CloudField debug geometry.
 */
public final class CloudFieldDebugColors {
    public static final Color CENTER = new Color(1.00F, 1.00F, 1.00F, 1.00F);
    public static final Color PREVIOUS_CENTER = new Color(1.00F, 0.32F, 0.90F, 0.85F);
    public static final Color WIND = new Color(0.35F, 0.90F, 1.00F, 0.85F);
    public static final Color CLOUDLET = new Color(1.00F, 1.00F, 1.00F, 0.90F);

    private CloudFieldDebugColors() {
    }

    public static Color forLod(CloudLodBand lodBand, float hydrationProgress) {
        float alpha = 0.22F + clamp01(hydrationProgress) * 0.62F;
        return switch (lodBand == null ? CloudLodBand.HAZE : lodBand) {
            case DYNAMIC -> new Color(0.20F, 1.00F, 0.38F, alpha);
            case TRANSITION -> new Color(1.00F, 0.86F, 0.20F, alpha);
            case FAR_PROCEDURAL -> new Color(0.24F, 0.62F, 1.00F, Math.max(0.18F, alpha * 0.72F));
            case HAZE -> new Color(0.58F, 0.62F, 0.68F, Math.max(0.14F, alpha * 0.52F));
        };
    }

    public static Color cloudlet(float hydrationProgress) {
        float alpha = 0.20F + clamp01(hydrationProgress) * 0.80F;
        return new Color(CLOUDLET.red(), CLOUDLET.green(), CLOUDLET.blue(), alpha);
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    public record Color(float red, float green, float blue, float alpha) {
    }
}
