package net.Gabou.projectatmosphere.modules.weather;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

public final class StormSeverityScale {
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 7;
    public static final int SUPERCELL_LEVEL = 7;

    private StormSeverityScale() {
    }

    public static int clamp(int level) {
        return Mth.clamp(level, MIN_LEVEL, MAX_LEVEL);
    }

    public static float toNormalized(int level) {
        return (clamp(level) - MIN_LEVEL) / (float) (MAX_LEVEL - MIN_LEVEL);
    }

    public static int fromNormalized(float normalized) {
        return clamp(1 + Mth.floor(Mth.clamp(normalized, 0.0F, 1.0F) * 6.999F));
    }

    public static int fromWeatherPhase(RegionalWeatherPhase phase) {
        return switch (phase) {
            case CALM -> 1;
            case CLOUDY -> 2;
            case RAIN -> 3;
            case THUNDER -> 5;
            case SEVERE -> 6;
            case CYCLONE -> 7;
        };
    }

    public static int resolve(ServerLevel level, RegionInstanceKey key, long tick) {
        if (key == null) {
            return MIN_LEVEL;
        }

        int phaseLevel = fromWeatherPhase(ServerWeatherStateResolver.resolve(level, key, tick));
        int cloudLevel = sampleCloudLevel(level, key.center());
        RegionAtmosphereState state = AtmosphericStateRegistry.getState(key);
        if (state == null) {
            return clamp(Math.max(phaseLevel, cloudLevel));
        }

        float rain = Mth.clamp(state.getRainIntensity(), 0.0F, 1.0F);
        float cloud = Mth.clamp(state.getCloudCover(), 0.0F, 1.0F);
        float water = Mth.clamp(state.getCloudWater(), 0.0F, 1.2F);
        float wind = Mth.clamp(state.getWindStrength() / 26.0F, 0.0F, 1.0F);
        float lowPressure = Mth.clamp((1013.25F - state.getPressure()) / 50.0F, 0.0F, 1.0F);
        float chance = Mth.clamp(ForecastOrchestrator.getCurrentStormChance(key, tick), 0.0F, 1.0F);
        WindVector dynamicWind = state.getWind();
        float gust = dynamicWind == null ? 0.0F : Mth.clamp(Math.max(dynamicWind.baseSpeed(), dynamicWind.gustSpeed()) / 34.0F, 0.0F, 1.0F);

        float severityScore = rain * 0.28F
                + cloud * 0.15F
                + water * 0.12F
                + wind * 0.14F
                + lowPressure * 0.12F
                + chance * 0.12F
                + gust * 0.15F;

        int derived = fromNormalized(severityScore);
        return clamp(Math.max(Math.max(phaseLevel, cloudLevel), derived));
    }

    public static int sampleCloudLevel(ServerLevel level, BlockPos pos) {
        int strongest = MIN_LEVEL;
        for (CloudRegion region : CloudManager.get(level).getClouds()) {
            double dx = region.getWorldX() - pos.getX();
            double dz = region.getWorldZ() - pos.getZ();
            double radius = region.getRadius();
            if (dx * dx + dz * dz <= radius * radius) {
                strongest = Math.max(strongest, CloudLibrary.getSeverityFromRessourceLocation(region.getCloudTypeId()));
            }
        }
        return clamp(strongest);
    }
}
