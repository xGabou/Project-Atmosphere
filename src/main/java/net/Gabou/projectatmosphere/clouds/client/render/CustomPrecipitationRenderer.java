package net.Gabou.projectatmosphere.clouds.client.render;

import net.Gabou.projectatmosphere.clouds.client.ClientLocalizedWeatherState;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.modules.weather.PrecipitationTier;
import net.Gabou.projectatmosphere.modules.weather.SnowTier;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

public final class CustomPrecipitationRenderer {
    private static volatile PrecipitationVisualState lastState = PrecipitationVisualState.NONE;

    private CustomPrecipitationRenderer() {
    }

    public static boolean renderSnowAndRain(
            @Nullable ClientLevel level,
            LightTexture lightTexture,
            float partialTick,
            double camX,
            double camY,
            double camZ
    ) {
        lastState = resolveState(level, partialTick);
        return false;
    }

    public static PrecipitationVisualState getLastState() {
        return lastState;
    }

    public static PrecipitationVisualState resolveState(@Nullable ClientLevel level, float partialTick) {
        if (level == null || !isEnabled()) {
            return PrecipitationVisualState.NONE;
        }

        ClientLocalizedWeatherState.Diagnostics diagnostics = ClientLocalizedWeatherState.getDiagnostics();
        float rainIntensity = Mth.clamp(ClientLocalizedWeatherState.getRainLevel(level, partialTick), 0.0F, 1.0F);
        float thunderIntensity = Mth.clamp(ClientLocalizedWeatherState.getThunderLevel(level, partialTick), 0.0F, 1.0F);
        PrecipitationTier rainTier = PrecipitationTier.fromRainIntensity(rainIntensity + thunderIntensity * 0.18F);
        BlockPos samplePos = diagnostics.samplePos();
        SnowTier snowTier = diagnostics.sample().snowing()
                ? SnowTier.resolve(-2.0F, Math.max(0.35F, rainIntensity), resolveWindProxy(rainIntensity, thunderIntensity), rainIntensity)
                : SnowTier.NONE;
        float windProxy = resolveWindProxy(rainIntensity, thunderIntensity);
        float slant = Mth.clamp(windProxy / 24.0F, 0.0F, 1.0F);
        float fogBoost = Math.max(rainTier.getFogBoost(), snowTier.getWhiteoutStrength() * 0.65F);
        float splashIntensity = snowTier == SnowTier.NONE ? rainTier.getSplashIntensity() : 0.0F;

        return new PrecipitationVisualState(
                snowTier == SnowTier.NONE ? rainTier : PrecipitationTier.NONE,
                snowTier,
                rainIntensity,
                thunderIntensity,
                slant * 0.55F,
                slant * 0.22F,
                fogBoost,
                splashIntensity,
                samplePos
        );
    }

    private static boolean isEnabled() {
        try {
            return AtmoCommonConfig.ENABLE_CUSTOM_PRECIPITATION_RENDERING.get();
        } catch (IllegalStateException exception) {
            return true;
        }
    }

    private static float resolveWindProxy(float rainIntensity, float thunderIntensity) {
        return 4.0F + rainIntensity * 8.0F + thunderIntensity * 12.0F;
    }
}
