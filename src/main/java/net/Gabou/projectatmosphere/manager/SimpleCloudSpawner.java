package net.Gabou.projectatmosphere.manager;

import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudGenerator;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudSpawningConfig;
import dev.nonamecrackers2.simpleclouds.common.world.SpawnRegion;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.WeatherSampler;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.BiasedToBottomInt;
import org.joml.Vector2i;

import java.util.*;

import static net.Gabou.projectatmosphere.compat.SimpleCloudsCompat.MAX_RADIUS;
import static net.Gabou.projectatmosphere.compat.SimpleCloudsCompat.MIN_RADIUS;

public class SimpleCloudSpawner {
    // Constante pour l'intervalle de spawn des nuages en ticks.
    private static final int SPAWN_INTERVAL_TICKS = 24000; // 50 secondes (20 ticks par seconde)
    // Dernier tick de spawn pour éviter les spawns trop fréquents.
    private static long LAST_SPAWN_TICK = 0;

    // Pression moyenne en hPa (niveau de la mer)
    private static float PRESSION_MOYENNE = 1013.25f;

    private static int NB_MAX_CLOUDS_TYPES = 4;

    private static int currentViolence = 0;

    private static float DEW_GAP_MODIFIER = 1.0f; // Modificateur pour le gap de rosée
    private static float PRESSURE_MODIFIER = 1.0f; // Modificateur pour la pression
    private static float HUMIDITY_MODIFIER = 1.0f; // Modificateur pour l'humidité
    private static float TEMPERATURE_MODIFIER = 1.0f; // Modificateur pour la température

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
    public static void trySpawnClouds(ServerLevel level, CloudGenerator generator) {
        List<SpawnRegion> spawnRegions = generator.getSpawnRegions();
        RandomSource random = RandomSource.create();
        CloudSpawningConfig config = generator.getSpawnConfig().get();

        // 1. Determine how many new regions we can spawn
        int currentCount = generator.getClouds().size();
        int maxRegions = config.getMaxInitialRegions();
        int remaining = maxRegions - currentCount;
        if (remaining <= 0) return;

        int toSpawn = Mth.clamp(BiasedToBottomInt.of(1, 5).sample(random), 1, remaining); // spawn 1–5 clouds or up to the limit

        for (int i = 0; i < toSpawn; i++) {
            if (spawnRegions.isEmpty()) {
                System.out.println("[Atmosphere] No spawn regions available");
                return;
            }

            // 2. Pick a random region and a point inside it
            SpawnRegion region = spawnRegions.get(random.nextInt(spawnRegions.size()));
            int radius = BiasedToBottomInt.of(MIN_RADIUS, MAX_RADIUS).sample(random);
            Vector2i point = SpawnRegion.getRandomPointInRegion(region, random);

            // 3. Weather sampling for surrounding biomes
            Set<BiomeInstanceKey> sample = WeatherSampler.sampleBiomesInArea(point.x, point.y, radius, level);
            WeatherSampler.WeatherStats stats = WeatherSampler.computeWeatherStats(sample, level, level.getGameTime());
            if (stats == null) continue;

            // 4. Determine cloud ID
            String cloudId = CloudLibrary.getCloudIdFromSeverity(
                    determineCloudSeverity(
                            stats.temperature(),
                            stats.humidity(),
                            stats.pressure(),
                            calculateDewPoint(stats.temperature(), stats.humidity()),
                            stats.stormChance()
                    )
            );

            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(SimpleCloudsMod.MODID, cloudId);
            CloudSpawningConfig.Info info = config.getWeightInfo(rl);
            if (info == null) {
                System.out.println("[Atmosphere] Unknown cloud type: " + cloudId);
                continue;
            }

            // 5. Create dummy region for size control
            Optional<CloudRegion> dummyOpt = SimpleCloudsCompat.createRegion(
                    info,
                    new BiomeInstanceKey(stats.dominantBiome(), stats.pos()),
                    level,
                    random,
                    stats.windVector(),
                    generator
            );

            if (dummyOpt.isEmpty()) continue;

            CloudRegion dummy = dummyOpt.get();
            dummy.setRadius(radius);

            // 6. Spawn cloud with weather-based region
            SimpleCloudsCompat.spawnCloudInBiome(
                    cloudId,
                    new BiomeInstanceKey(stats.dominantBiome(), stats.pos()),
                    level,
                    dummy,
                    stats.windVector()
            );

            if (generator.getClouds().size() >= maxRegions) return;
        }
    }






    public static BlockPos getRandomPosInRegion(SpawnRegion region, RandomSource random, ServerLevel level) {
        Vector2i vec = SpawnRegion.getRandomPointInRegion(region, random);
        return new BlockPos(vec.x, level.getSeaLevel(), vec.y);
    }


    // Méthode pour spawn des nuages simples dans le niveau serveur.
    public static void spawnSimpleClouds(BiomeInstanceKey key, long dayTime,ServerLevel serverLevel) {

        float temperature = ForecastOrchestrator.getCurrentTemperature(key, dayTime); //En celcius
        float humidity = ForecastOrchestrator.getCurrentHumidity(key, dayTime); //En pourcentage
        float pressure = ForecastOrchestrator.getCurrentPressure(key, dayTime); //En hPa ou mb
        WindVector wind = ForecastOrchestrator.getCurrentWind(key);
        float stormChance = ForecastOrchestrator.getCurrentStormChance(key,dayTime); //En pourcentage

        float dewPoint = calculateDewPoint(temperature, humidity); //Point de rosée en Celsius

        currentViolence = determineCloudSeverity(temperature, humidity, pressure, dewPoint,stormChance);
        SimpleCloudsCompat.spawnCloudInBiome(CloudLibrary.getCloudIdFromSeverity(currentViolence), key,serverLevel,null,wind);

    }


    public static float calculateDewPoint(float temperature, float humidity) {
        // Formule de calcul du point de rosée (dewPoint) de la formule d'August-Roche-Magnus
        final float ACONST = 17.62f;
        final float BCONST = 243.12f;

        float result = (ACONST * temperature) / (BCONST + temperature) + (float) Math.log(humidity / 100.0f);
        return (BCONST * result) / (ACONST - result);
    }
    public static int determineCloudSeverity(float temperature, float humidity, float pressure,float dewPoint,float stormChance) {

        float dewGap = temperature - dewPoint; //Plus de dewGap est petit -> Plus il y a de nuages
        float pressureFactor = 1.0f - (pressure / PRESSION_MOYENNE);
        float humidityFactor = humidity / 100.0f;
        float tempIdealness = 1.0f - Math.abs(temperature - 15.0f) / 40.0f;

        float dewGapFactor = 1.0f - Math.min(dewGap, 10.0f) / 10.0f;

        float instability =
                (dewGapFactor * DEW_GAP_MODIFIER) +
                        (pressureFactor * PRESSURE_MODIFIER) +
                        (humidityFactor * HUMIDITY_MODIFIER) +
                        (tempIdealness * TEMPERATURE_MODIFIER);

        int severity = Math.round(instability*stormChance);
        return Math.max(1, Math.min(7, severity));
    }

    public static void spawnCloudForPlayer(ServerPlayer player, ServerLevel level) {
        BiomeInstanceKey key = new BiomeInstanceKey(AtmosphereUtils.getBiomeLocation(player.blockPosition(), level),player.blockPosition());
        SimpleCloudsCompat.spawnCloudInBiome("itty_bitty",key, level,null,ForecastOrchestrator.getCurrentWind(key));
    }

    public static void onPlayerJoined(ServerLevel world, Set<BiomeInstanceKey> biomeSamples) {
        long dayTime = world.getDayTime();

        for (BiomeInstanceKey key : biomeSamples) {
            spawnSimpleClouds(key, dayTime, world);
        }
    }

}
