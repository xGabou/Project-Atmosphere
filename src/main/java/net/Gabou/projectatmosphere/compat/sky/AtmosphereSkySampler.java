package net.Gabou.projectatmosphere.compat.sky;

import net.Gabou.projectatmosphere.client.atmosphere.AtmosphereClientState;
import net.Gabou.projectatmosphere.clouds.CloudWeatherSample;
import net.Gabou.projectatmosphere.clouds.WeatherCloudQueries;
import net.Gabou.projectatmosphere.clouds.api.CloudShadowMapAccess;
import net.Gabou.projectatmosphere.client.fog.FogBiomeClassifier;
import net.Gabou.projectatmosphere.compat.temperature.ClientTemperatureResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class AtmosphereSkySampler {
    private AtmosphereSkySampler() {
    }

    public static AtmosphereSkySample sample(Minecraft minecraft, float partialTick) {
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || !Level.OVERWORLD.equals(level.dimension())) {
            return AtmosphereSkySample.NONE;
        }

        BlockPos pos = minecraft.player.blockPosition();
        AtmosphereClientState.Snapshot snapshot = AtmosphereClientState.getSnapshot();
        float humidityPercent = snapshot.humidityPercent();
        float rainIntensity = snapshot.rainIntensity();
        float cloudCover = snapshot.cloudCover();
        CloudWeatherSample cloudWeather = WeatherCloudQueries.sampleAt(level, pos, false);
        rainIntensity = Math.max(rainIntensity, cloudWeather.rainStrength());
        cloudCover = Math.max(cloudCover, cloudWeather.cloudCoverStrength());
        float recentRainFactor = snapshot.recentRainFactor();
        float clearingTrend = snapshot.clearingTrend();
        float wetBiomeFactor = FogBiomeClassifier.computeWetBiomeFactor(level, pos);
        float temperatureC = ClientTemperatureResolver.getCelsius(level, pos);
        boolean canSeeSky = level.canSeeSky(pos.above());

        float daylightFactor = Mth.clamp((float) (Math.cos(level.getTimeOfDay(partialTick) * (Math.PI * 2.0D)) * 3.0D), 0.0F, 1.0F);
        float nightFactor = Mth.clamp(level.getStarBrightness(partialTick) * 2.0F, 0.0F, 1.0F);

        float sunVisibility = 0.0F;
        float atmosphericClarity = 0.0F;
        if (canSeeSky) {
            float cloudShadow = CloudShadowMapAccess.sampleShadowAt(pos.getX() + 0.5D, pos.getZ() + 0.5D);
            float sunOcclusion = Mth.clamp(1.0F - cloudCover * 0.82F - rainIntensity * 0.95F - cloudShadow * 0.70F, 0.0F, 1.0F);
            sunVisibility = daylightFactor * sunOcclusion;

            float humidityHaze = SkyConditionMath.remapClamped(humidityPercent, 74.0F, 100.0F);
            atmosphericClarity = Mth.clamp(1.0F - cloudCover * 0.75F - rainIntensity * 0.95F - humidityHaze * 0.25F - cloudShadow * 0.45F, 0.0F, 1.0F);
        }

        float cloudBreakup = SkyConditionMath.peakedFactor(cloudCover, 0.32F, 0.34F);
        cloudBreakup = Mth.clamp(cloudBreakup * 0.72F + clearingTrend * 0.68F, 0.0F, 1.0F);

        return new AtmosphereSkySample(
                humidityPercent,
                rainIntensity,
                cloudCover,
                recentRainFactor,
                clearingTrend,
                wetBiomeFactor,
                temperatureC,
                daylightFactor,
                nightFactor,
                sunVisibility,
                atmosphericClarity,
                cloudBreakup,
                canSeeSky
        );
    }
}
