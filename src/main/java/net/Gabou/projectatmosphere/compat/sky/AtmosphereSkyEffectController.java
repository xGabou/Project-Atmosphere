package net.Gabou.projectatmosphere.compat.sky;

import net.Gabou.projectatmosphere.compat.auroras.AuroraSeasonHelper;
import net.Gabou.projectatmosphere.client.atmosphere.AtmosphereClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class AtmosphereSkyEffectController {
    private static boolean rainbowEnabled;
    private static boolean auroraEnabled;
    private static ResourceKey<Level> lastDimension;

    private static AtmosphereSkySample lastSample = AtmosphereSkySample.NONE;

    private static float rainbowStrength;
    private static boolean rainbowActive;
    private static boolean rainbowActivationPulse;

    private static float auroraStrength;
    private static boolean auroraActive;

    private AtmosphereSkyEffectController() {
    }

    public static void setRainbowEnabled(boolean enabled) {
        rainbowEnabled = enabled;
        if (!enabled) {
            rainbowStrength = 0.0F;
            rainbowActive = false;
            rainbowActivationPulse = false;
        }
    }

    public static void setAuroraEnabled(boolean enabled) {
        auroraEnabled = enabled;
        if (!enabled) {
            auroraStrength = 0.0F;
            auroraActive = false;
        }
    }

    public static boolean isRainbowEnabled() {
        return rainbowEnabled;
    }

    public static boolean isAuroraEnabled() {
        return auroraEnabled;
    }

    public static void tick(Minecraft minecraft) {
        if ((!rainbowEnabled && !auroraEnabled) || minecraft.level == null || minecraft.player == null) {
            clear();
            return;
        }

        ResourceKey<Level> dimension = minecraft.level.dimension();
        if (lastDimension != null && !lastDimension.equals(dimension)) {
            clearVisualState();
        }
        lastDimension = dimension;

        lastSample = AtmosphereSkySampler.sample(minecraft, 0.0F);
        if (lastSample == AtmosphereSkySample.NONE) {
            rainbowActivationPulse = false;
            rainbowStrength = Mth.lerp(0.08F, rainbowStrength, 0.0F);
            auroraStrength = Mth.lerp(0.08F, auroraStrength, 0.0F);
            rainbowActive = rainbowEnabled && rainbowStrength >= 0.07F;
            auroraActive = auroraEnabled && auroraStrength >= 0.03F;
            return;
        }

        updateRainbowState(lastSample);
        updateAuroraState(minecraft, lastSample);
    }

    public static float getRainLevelOverride(float vanillaRainLevel) {
        if (!rainbowEnabled && !auroraEnabled) {
            return vanillaRainLevel;
        }
        return AtmosphereClientState.getRainIntensity();
    }

    public static double scaleRainbowBrightness(double vanillaBrightness) {
        return rainbowEnabled ? vanillaBrightness * rainbowStrength : vanillaBrightness;
    }

    public static float scaleAuroraBrightness(float vanillaBrightness) {
        return auroraEnabled ? vanillaBrightness * auroraStrength : vanillaBrightness;
    }

    public static boolean shouldRenderRainbow() {
        return rainbowEnabled && rainbowActive;
    }

    public static boolean consumeRainbowActivationPulse() {
        boolean pulse = rainbowActivationPulse;
        rainbowActivationPulse = false;
        return pulse;
    }

    public static float getRainbowStrength() {
        return rainbowStrength;
    }

    public static boolean isAuroraActive() {
        return auroraEnabled && auroraActive;
    }

    public static float getAuroraStrength() {
        return auroraStrength;
    }

    public static AtmosphereSkySample getLastSample() {
        return lastSample;
    }

    private static void updateRainbowState(AtmosphereSkySample sample) {
        if (!rainbowEnabled) {
            rainbowStrength = 0.0F;
            rainbowActive = false;
            rainbowActivationPulse = false;
            return;
        }

        float humidityFactor = SkyConditionMath.remapClamped(sample.humidityPercent(), 58.0F, 96.0F);
        float recentRainFactor = sample.recentRainFactor();
        float cloudBreakFactor = Mth.clamp(sample.cloudBreakup() * 0.72F + sample.clearingTrend() * 0.55F, 0.0F, 1.0F);
        float sunVisibilityFactor = SkyConditionMath.remapClamped(sample.sunVisibility(), 0.10F, 0.60F);
        float sunAngleFactor = SkyConditionMath.peakedFactor(sample.daylightFactor(), 0.42F, 0.34F);
        float activeRainPenalty = 1.0F - SkyConditionMath.remapClamped(sample.rainIntensity(), 0.05F, 0.24F);
        float targetStrength = 0.0F;

        if (sample.canSeeSky()) {
            targetStrength = humidityFactor
                    * recentRainFactor
                    * cloudBreakFactor
                    * sunVisibilityFactor
                    * sunAngleFactor
                    * activeRainPenalty;
        }

        float tracking = targetStrength > rainbowStrength ? 0.18F : 0.07F;
        rainbowStrength = Mth.lerp(tracking, rainbowStrength, targetStrength);

        boolean newActive = rainbowStrength >= 0.15F || (rainbowActive && rainbowStrength >= 0.07F);
        rainbowActivationPulse = !rainbowActive && newActive;
        rainbowActive = newActive;
    }

    private static void updateAuroraState(Minecraft minecraft, AtmosphereSkySample sample) {
        if (!auroraEnabled) {
            auroraStrength = 0.0F;
            auroraActive = false;
            return;
        }

        float seasonal = AuroraSeasonHelper.computeSeasonalFactor(minecraft.level);
        float thermal = AuroraSeasonHelper.computeTemperatureFactor(minecraft.level, minecraft.player.blockPosition());
        float nightGate = SkyConditionMath.remapClamped(sample.nightFactor(), 0.08F, 0.35F);
        float darknessGate = SkyConditionMath.reverseRemapClamped(sample.daylightFactor(), 0.06F, 0.22F);
        float cloudPenalty = 1.0F - SkyConditionMath.remapClamped(sample.cloudCover(), 0.30F, 0.92F) * 0.82F;
        float humidityHaze = SkyConditionMath.remapClamped(sample.humidityPercent(), 82.0F, 100.0F);
        float wetBiomePenalty = 1.0F - sample.wetBiomeFactor() * 0.12F;
        float clarity = sample.canSeeSky()
                ? Mth.clamp(sample.atmosphericClarity() * cloudPenalty * (1.0F - humidityHaze * 0.35F) * wetBiomePenalty, 0.0F, 1.0F)
                : 0.0F;
        float climateBoost = Mth.clamp(seasonal * thermal, 0.35F, 1.45F);
        float targetStrength = nightGate * darknessGate * clarity * climateBoost;

        float tracking = targetStrength > auroraStrength ? 0.12F : 0.06F;
        auroraStrength = Mth.lerp(tracking, auroraStrength, targetStrength);
        auroraActive = auroraStrength >= 0.08F || (auroraActive && auroraStrength >= 0.03F);
    }

    private static void clearVisualState() {
        lastSample = AtmosphereSkySample.NONE;
        rainbowStrength = 0.0F;
        rainbowActive = false;
        rainbowActivationPulse = false;
        auroraStrength = 0.0F;
        auroraActive = false;
    }

    private static void clear() {
        clearVisualState();
        lastDimension = null;
    }
}
