package net.Gabou.projectatmosphere.client.fog;

import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;

import java.util.List;
import java.util.Locale;

public final class FogBiomeClassifier {
    private FogBiomeClassifier() {
    }

    public static float computeWetBiomeFactor(LevelReader level, BlockPos pos) {
        if (level == null || pos == null) {
            return 0.0F;
        }

        Biome biome = level.getBiome(pos).value();
        float downfallThreshold = AtmoCommonConfig.FOG_WET_BIOME_DOWNFALL_MIN.get().floatValue();
        float factor = 0.0F;

        if (biome.getModifiedClimateSettings().hasPrecipitation()) {
            factor = Math.max(factor, remapClamped(biome.getModifiedClimateSettings().downfall(), downfallThreshold, 1.0F));
        }

        ResourceLocation biomeId = level.getBiome(pos).unwrapKey()
                .map(key -> key.location())
                .orElse(null);
        if (biomeId == null) {
            return Mth.clamp(factor, 0.0F, 1.0F);
        }

        String fullId = biomeId.toString().toLowerCase(Locale.ROOT);
        String path = biomeId.getPath().toLowerCase(Locale.ROOT);

        if (matchesExact(fullId, AtmoCommonConfig.FOG_WET_BIOME_IDS.get())) {
            return 1.0F;
        }
        if (matchesKeyword(fullId, path, AtmoCommonConfig.FOG_WET_BIOME_KEYWORDS.get())) {
            factor = Math.max(factor, 1.0F);
        }

        return Mth.clamp(factor, 0.0F, 1.0F);
    }

    public static float computeFallbackHumidityPercent(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return 0.0F;
        }

        Biome biome = level.getBiome(pos).value();
        float humidity = biome.getModifiedClimateSettings().downfall() * 100.0F;
        if (biome.getModifiedClimateSettings().hasPrecipitation() && isLocallyRaining(level, pos)) {
            humidity = Math.max(humidity, 82.0F);
        }
        return Mth.clamp(humidity, 0.0F, 100.0F);
    }

    public static float computeClientRainIntensity(Level level, BlockPos pos) {
        return isLocallyRaining(level, pos) ? 0.85F : 0.0F;
    }

    private static boolean isLocallyRaining(Level level, BlockPos pos) {
        try {
            return CloudManager.get(level).isRainingAt(pos);
        } catch (Exception ignored) {
            return level.isRainingAt(pos);
        }
    }

    private static boolean matchesExact(String fullId, List<? extends String> configuredIds) {
        for (String configured : configuredIds) {
            if (configured != null && fullId.equals(configured.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesKeyword(String fullId, String path, List<? extends String> keywords) {
        for (String keyword : keywords) {
            if (keyword == null) {
                continue;
            }
            String lowered = keyword.toLowerCase(Locale.ROOT).trim();
            if (lowered.isEmpty()) {
                continue;
            }
            if (path.contains(lowered) || fullId.contains(lowered)) {
                return true;
            }
        }
        return false;
    }

    private static float remapClamped(float value, float start, float end) {
        if (end <= start) {
            return value >= end ? 1.0F : 0.0F;
        }
        return Mth.clamp((value - start) / (end - start), 0.0F, 1.0F);
    }
}
