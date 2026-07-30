package net.Gabou.projectatmosphere.compat.rainbows;

import net.Gabou.projectatmosphere.compat.sky.AtmosphereSkyEffectController;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class RainbowWeatherTracker {
    private RainbowWeatherTracker() {
    }

    public static void setEnabled(boolean value) {
        AtmosphereSkyEffectController.setRainbowEnabled(value);
    }

    public static boolean isEnabled() {
        return AtmosphereSkyEffectController.isRainbowEnabled();
    }

    public static float getRainLevelOverride(float vanillaRainLevel) {
        return AtmosphereSkyEffectController.getRainLevelOverride(vanillaRainLevel);
    }

    public static double scaleBrightness(double vanillaBrightness) {
        return AtmosphereSkyEffectController.scaleRainbowBrightness(vanillaBrightness);
    }

    public static boolean shouldRender() {
        return AtmosphereSkyEffectController.shouldRenderRainbow();
    }

    public static boolean consumeActivationPulse() {
        return AtmosphereSkyEffectController.consumeRainbowActivationPulse();
    }

    public static float getVisualStrength() {
        return AtmosphereSkyEffectController.getRainbowStrength();
    }
}
