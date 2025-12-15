package net.Gabou.projectatmosphere.telemetry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.atmosphere.CloudManager;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.telemetry.TelemetryModels.DominantBiomeOccupancy;
import net.Gabou.projectatmosphere.telemetry.TelemetryModels.OccupiedChunk;
import net.Gabou.projectatmosphere.telemetry.TelemetryModels.PlayerExperienceSample;
import net.Gabou.projectatmosphere.telemetry.TelemetryCollector;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID)
public final class ServerTelemetrySampler {
    private static final Map<Long, Long> CHUNK_OCCUPANCY_TICKS = new LinkedHashMap<>();
    private static final List<OccupiedChunk> TOP_BUFFER = new ArrayList<>(5);
    private static final long SERVER_SESSION_START_MS = System.currentTimeMillis();

    private ServerTelemetrySampler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.side.isClient() || event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!AtmoCommonConfig.TELEMETRY_ENABLED.get()) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
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
    }

    private static void recordPlayerSample(ServerLevel level, ServerPlayer player, BlockPos pos, long gameTime) {
        BiomeInstanceKey key = new BiomeInstanceKey(AtmosphereUtils.getBiomeLocation(pos, level), pos);
        float temperature = ForecastOrchestrator.getCurrentTemperature(key, gameTime);
        float humidity = ForecastOrchestrator.getCurrentHumidity(key, gameTime);
        float pressure = ForecastOrchestrator.getCurrentPressure(key, gameTime);
        WindVector wind = ForecastOrchestrator.getCurrentWind(key, gameTime);
        boolean isRaining = CloudManager.get(level).isRainingAt(pos);
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

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
