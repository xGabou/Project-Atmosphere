package net.Gabou.projectatmosphere.modules.weather;

import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

public final class ServerWeatherStateResolver {
    private ServerWeatherStateResolver() {
    }

    public static RegionalWeatherPhase resolve(ServerLevel level, RegionInstanceKey key, long tick) {
        if (key == null) {
            return RegionalWeatherPhase.CALM;
        }
        RegionAtmosphereState state = AtmosphericStateRegistry.getState(key);
        if (state == null) {
            float stormChance = ForecastOrchestrator.getCurrentStormChance(key, tick);
            if (stormChance >= 0.7F) {
                return RegionalWeatherPhase.SEVERE;
            }
            if (stormChance >= 0.45F) {
                return RegionalWeatherPhase.THUNDER;
            }
            if (stormChance >= 0.2F) {
                return RegionalWeatherPhase.RAIN;
            }
            return RegionalWeatherPhase.CLOUDY;
        }

        float rain = Mth.clamp(state.getRainIntensity(), 0.0F, 1.0F);
        float cloud = Mth.clamp(state.getCloudCover(), 0.0F, 1.0F);
        float cloudWater = Mth.clamp(state.getCloudWater(), 0.0F, 1.2F);
        float wind = Math.max(0.0F, state.getWindStrength());
        float lowPressure = Mth.clamp((1013.25F - state.getPressure()) / 45.0F, 0.0F, 1.0F);
        float cycloneFloor = Math.max(state.getCycloneCloudFloor(), state.getCycloneRainFloor());
        float stormChance = ForecastOrchestrator.getCurrentStormChance(key, tick);
        WindVector dynamicWind = state.getWind();
        float gust = dynamicWind == null ? 0.0F : Math.max(dynamicWind.baseSpeed(), dynamicWind.gustSpeed());

        float severity = rain * 0.30F
                + cloud * 0.18F
                + cloudWater * 0.12F
                + Mth.clamp(wind / 18.0F, 0.0F, 1.0F) * 0.12F
                + lowPressure * 0.08F
                + stormChance * 0.15F
                + cycloneFloor * 0.20F
                + Mth.clamp((gust - 20.0F) / 40.0F, 0.0F, 1.0F) * 0.10F;

        if (cycloneFloor >= 0.45F || severity >= 0.95F) {
            return RegionalWeatherPhase.CYCLONE;
        }
        if (severity >= 0.70F) {
            return RegionalWeatherPhase.SEVERE;
        }
        if (severity >= 0.42F) {
            return RegionalWeatherPhase.THUNDER;
        }
        if (severity >= 0.18F) {
            return RegionalWeatherPhase.RAIN;
        }
        if (cloud >= 0.18F || cloudWater >= 0.10F) {
            return RegionalWeatherPhase.CLOUDY;
        }
        return RegionalWeatherPhase.CALM;
    }
}
