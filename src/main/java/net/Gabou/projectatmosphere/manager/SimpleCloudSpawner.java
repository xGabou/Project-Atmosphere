package net.Gabou.projectatmosphere.manager;

import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudGenerator;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import dev.nonamecrackers2.simpleclouds.common.world.ServerCloudManager;
import dev.nonamecrackers2.simpleclouds.common.world.SpawnRegion;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.Gabou.projectatmosphere.modules.humidity.manager.HumidityManager;
import net.Gabou.projectatmosphere.modules.pressure.manager.PressureManager;
import net.Gabou.projectatmosphere.modules.temperature.manager.TemperatureManager;
import net.Gabou.projectatmosphere.modules.temperature.util.ForecastStorageManager;
import net.Gabou.projectatmosphere.modules.temperature.util.TemperatureProfileManager;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.WeatherSampler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.joml.Vector2i;

import java.util.List;
import java.util.Set;

public class SimpleCloudSpawner {
    // Constante pour l'intervalle de spawn des nuages en ticks.
    private static final int SPAWN_INTERVAL_TICKS = 1000; // 50 secondes (20 ticks par seconde)
    // Dernier tick de spawn pour éviter les spawns trop fréquents.
    private static long LAST_SPAWN_TICK = 0;

    // Pression moyenne en hPa (niveau de la mer)
    private static float PRESSION_MOYENNE = 1013.25f;

    private static int currentViolence = 0;

    public static int getCurrentViolence() {
        return currentViolence;
    }
    private static int setViolence(int violence) {
        if (violence < 0 || violence > 7) {
            throw new IllegalArgumentException("Violence must be between 0 and 7");
        }
        return violence;
    }

    // Méthode pour essayer de spawn des nuages dans le niveau serveur si l'intervalle de temps est respecté.
    public static void trySpawnClouds(ServerLevel serverLevel) {
        long gameTime = serverLevel.getGameTime();
        if (gameTime - LAST_SPAWN_TICK < SPAWN_INTERVAL_TICKS) {
            return;
        }
        LAST_SPAWN_TICK = gameTime;
        ServerCloudManager cloudManager = (ServerCloudManager) CloudManager.get(serverLevel);
        CloudGenerator generator = cloudManager.getCloudGenerator();

        for (SpawnRegion region : generator.getSpawnRegions()) {
            Set<BiomeInstanceKey> keys = WeatherSampler.sampleBiomesInRegion(region, serverLevel);
            WeatherSampler.WeatherStats stats = WeatherSampler.computeWeatherStats(keys, serverLevel, serverLevel.getDayTime());

            if (stats == null) continue;
            SimpleCloudsCompat.spawnCloudInBiome(
                    CloudLibrary.getCloudIdFromSeverity(
                            determineCloudSeverity(
                                    stats.temperature(), stats.humidity(), stats.pressure(),
                                    calculateDewPoint(stats.temperature(), stats.humidity()))),
                    new BiomeInstanceKey(
                            stats.dominantBiome(),
                            getRandomPosInRegion(region,RandomSource.create(),
                                    serverLevel)), serverLevel);
        }


    }
    public static BlockPos getRandomPosInRegion(SpawnRegion region, RandomSource random, ServerLevel level) {
        Vector2i vec = SpawnRegion.getRandomPointInRegion(region, random);
        return new BlockPos(vec.x, level.getSeaLevel(), vec.y);
    }


    // Méthode pour spawn des nuages simples dans le niveau serveur.
    public static void spawnSimpleClouds(BiomeInstanceKey key, long dayTime,ServerLevel serverLevel) {

        float temperature = TemperatureManager.getCurrentTemperature(key, dayTime); //En celcius
        float humidity = HumidityManager.getCurrentHumidity(key, dayTime); //En pourcentage
        float pressure = PressureManager.getCurrentPressure(key, dayTime); //En hPa ou mb

        float dewPoint = calculateDewPoint(temperature, humidity); //Point de rosée en Celsius

        currentViolence = determineCloudSeverity(temperature, humidity, pressure, dewPoint);
        SimpleCloudsCompat.spawnCloudInBiome(CloudLibrary.getCloudIdFromSeverity(currentViolence), key,serverLevel);

    }

    private static float calculateDewPoint(float temperature, float humidity) {
        // Formule de calcul du point de rosée (dewPoint) de la formule d'August-Roche-Magnus
        final float ACONST = 17.62f;
        final float BCONST = 243.12f;

        float result = (ACONST * temperature) / (BCONST + temperature) + (float) Math.log(humidity / 100.0f);
        return (BCONST * result) / (ACONST - result);
    }
    public static int determineCloudSeverity(float temperature, float humidity, float pressure,float dewPoint) {

        float dewGap = temperature - dewPoint; // smaller = more saturation → more clouds
        float pressureFactor = 1.0f - (pressure / PRESSION_MOYENNE); // low pressure = higher instability
        float humidityFactor = humidity / 100.0f;
        float tempIdealness = 1.0f - Math.abs(temperature - 15.0f) / 40.0f;

        // Normalize dewGap: ideal is 0–5, beyond 5 = less likely clouds
        float dewGapFactor = 1.0f - Math.min(dewGap, 10.0f) / 10.0f;

        // Combine all into instability score (weights are tweakable)
        float instability =
                (dewGapFactor * 0.4f) +
                        (pressureFactor * 0.3f) +
                        (humidityFactor * 0.2f) +
                        (tempIdealness * 0.1f);

        // Scale into [1–7] index
        int severity = Math.round(instability * 7);
        return Math.max(1, Math.min(7, severity));
    }

    public static void spawnCloudForPlayer(ServerPlayer player, ServerLevel level) {
        SimpleCloudsCompat.spawnCloudInBiome("itty_bitty",new BiomeInstanceKey(level.getBiome(player.blockPosition()).unwrapKey().get().location(),player.blockPosition()), level);
    }

    public static void onPlayerJoined(ServerLevel world, Set<BiomeInstanceKey> biomeSamples) {
        long dayTime = world.getDayTime();

        for (BiomeInstanceKey key : biomeSamples) {
            spawnSimpleClouds(key, dayTime, world);
        }
    }

}
