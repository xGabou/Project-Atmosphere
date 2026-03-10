package net.Gabou.projectatmosphere.telemetry;

import java.util.*;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
import net.Gabou.projectatmosphere.modules.region.RegionForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.telemetry.TelemetryModels.DominantBiomeOccupancy;
import net.Gabou.projectatmosphere.telemetry.TelemetryModels.ChannelSummary;
import net.Gabou.projectatmosphere.telemetry.TelemetryModels.OccupiedChunk;
import net.Gabou.projectatmosphere.telemetry.TelemetryModels.PlayerExperienceSample;
import net.Gabou.projectatmosphere.telemetry.TelemetryModels.RegionForecastSample;
import net.Gabou.projectatmosphere.telemetry.TelemetryCollector;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = ProjectAtmosphere.MODID)
public final class ServerTelemetrySampler {
    private static final Map<Long, Long> CHUNK_OCCUPANCY_TICKS = new LinkedHashMap<>();
    private static final List<OccupiedChunk> TOP_BUFFER = new ArrayList<>(5);
    private static final long SERVER_SESSION_START_MS = System.currentTimeMillis();
    private static final int REGION_FORECAST_SAMPLE_INTERVAL_TICKS = 1200;
    private static long lastRegionForecastSampleTick = -REGION_FORECAST_SAMPLE_INTERVAL_TICKS;

    private ServerTelemetrySampler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!AtmoCommonConfig.TELEMETRY_ENABLED.get()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().isClientSide) {
            return;
        }

        ServerLevel level = player.serverLevel();
        long gameTime = level.getGameTime();
        BlockPos pos = player.blockPosition();

        long key = chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
        CHUNK_OCCUPANCY_TICKS.merge(key, 1L, Long::sum);

        if (gameTime % 200 == 0) {
            recordPlayerSample(level, player, pos, gameTime);
        }
        if (gameTime % 1200 == 0) {
            emitDominantBiomeOccupancy(gameTime);
        }
        if (gameTime - lastRegionForecastSampleTick >= REGION_FORECAST_SAMPLE_INTERVAL_TICKS) {
            lastRegionForecastSampleTick = gameTime;
            recordRegionForecasts(level, gameTime);
        }
    }

    private static void recordPlayerSample(ServerLevel level, ServerPlayer player, BlockPos pos, long gameTime) {
        BiomeInstanceKey key = new BiomeInstanceKey(AtmosphereUtils.getBiomeLocation(pos, level), pos);
        float temperature = ForecastOrchestrator.getCurrentTemperature(key, gameTime);
        float humidity = ForecastOrchestrator.getCurrentHumidity(key, gameTime);
        float pressure = ForecastOrchestrator.getCurrentPressure(key, gameTime);
        WindVector wind = ForecastOrchestrator.getWind(key, gameTime);
        boolean isRaining = level.isRainingAt(pos);
        boolean temperatureOutOfRange = temperature < -60f || temperature > 60f;

        PlayerExperienceSample sample = new PlayerExperienceSample(
                gameTime / 24000L,
                gameTime % 24000L,
                (System.currentTimeMillis() - SERVER_SESSION_START_MS) / 1000L,
                level.dimension().location().toString(),
                pos.getX() >> 4,
                pos.getZ() >> 4,
                key.biomeType().toString(),
                temperature,
                humidity,
                pressure,
                wind.baseSpeed(),
                wind.angleRadians(),
                level.isRaining(),
                level.isThundering(),
                isRaining ? "RAINING" : "CLEAR",
                temperatureOutOfRange,
                false,
                false
        );
        TelemetryCollector.get().recordPlayerSample(sample);
    }

    private static void emitDominantBiomeOccupancy(long gameTime) {
        if (CHUNK_OCCUPANCY_TICKS.isEmpty()) {
            return;
        }
        TOP_BUFFER.clear();
        CHUNK_OCCUPANCY_TICKS.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue(Comparator.naturalOrder()).reversed())
                .limit(5)
                .forEach(entry -> {
                    int cx = (int) (entry.getKey() >> 32);
                    int cz = (int) (entry.getKey().longValue());
                    long seconds = entry.getValue() / 20L;
                    TOP_BUFFER.add(new OccupiedChunk(cx, cz, seconds));
                });
        TelemetryCollector.get().recordDominantBiome(new DominantBiomeOccupancy(gameTime / 24000L, List.copyOf(TOP_BUFFER)));
    }

    private static void recordRegionForecasts(ServerLevel level, long gameTime) {
        RegionForecastOrchestrator orchestrator = ForecastOrchestrator.getRegionOrchestrator(level);
        if (orchestrator == null) {
            return;
        }
        var active = AtmosphericStateRegistry.getActiveStates();
        Set<RegionInstanceKey> targets = active.isEmpty()
                ? new java.util.HashSet<>(AtmosphericStateRegistry.getStatesAsMap().keySet())
                : new java.util.HashSet<>(active);
        if (targets.isEmpty()) {
            return;
        }

        long day = gameTime / 24000L;
        long timeOfDay = gameTime % 24000L;
        String dimensionId = level.dimension().location().toString();

        for (RegionInstanceKey key : targets) {
            ForecastRegion region = orchestrator.ensureLoaded(key);
            RegionAtmosphereState state = AtmosphericStateRegistry.getState(key);
            if (state == null) {
                continue;
            }

            ChannelSummary temperature = summarizeWeek(region.getTemperature());
            ChannelSummary humidity = summarizeWeek(region.getHumidity());
            ChannelSummary pressure = summarizeWeek(region.getPressure());

            WindVector expectedWind = ForecastOrchestrator.getForecastWind(key, gameTime);
            float expectedWindSpeed = Math.max(0f, expectedWind.baseSpeed());
            Float expectedWindDirection = Mth.wrapDegrees((float) Math.toDegrees(expectedWind.angleRadians()));

            WindVector currentWind = ForecastOrchestrator.getWind(key, gameTime);
            float currentWindSpeed = Math.max(0f, currentWind.baseSpeed());
            Float currentWindDirection = Mth.wrapDegrees((float) Math.toDegrees(currentWind.angleRadians()));

            int anchorChunkX = region.getAnchor() == null ? 0 : region.getAnchor().getX() >> 4;
            int anchorChunkZ = region.getAnchor() == null ? 0 : region.getAnchor().getZ() >> 4;
            String dominantBiome = selectDominantBiome(region.getBiomeWeights());

            RegionForecastSample sample = new RegionForecastSample(
                    day,
                    timeOfDay,
                    dimensionId,
                    key.toString(),
                    key.regionX(),
                    key.regionZ(),
                    key.regionSize(),
                    dominantBiome,
                    anchorChunkX,
                    anchorChunkZ,
                    temperature,
                    humidity,
                    pressure,
                    expectedWindSpeed,
                    expectedWindDirection,
                    state.getTemperature(),
                    state.getHumidity(),
                    state.getPressure(),
                    currentWindSpeed,
                    currentWindDirection,
                    state.getCloudCover(),
                    state.getRainIntensity()
            );
            TelemetryCollector.get().recordRegionForecastSample(sample);
        }
    }

    private static ChannelSummary summarizeWeek(float[][] curve) {
        if (curve == null || curve.length == 0) {
            return new ChannelSummary(0f, 0f);
        }
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (float[] day : curve) {
            if (day == null || day.length == 0) {
                continue;
            }
            for (float value : day) {
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }
        if (min == Float.MAX_VALUE) {
            return new ChannelSummary(0f, 0f);
        }
        return new ChannelSummary(min, max);
    }

    private static String selectDominantBiome(Map<ResourceLocation, Integer> weights) {
        if (weights == null || weights.isEmpty()) {
            return "unknown";
        }
        ResourceLocation best = null;
        int bestWeight = Integer.MIN_VALUE;
        for (Map.Entry<ResourceLocation, Integer> entry : weights.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > bestWeight) {
                best = entry.getKey();
                bestWeight = entry.getValue();
            }
        }
        return best == null ? "unknown" : best.toString();
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
