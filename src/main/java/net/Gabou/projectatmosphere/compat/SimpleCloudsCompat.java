package net.Gabou.projectatmosphere.compat;

import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import dev.nonamecrackers2.simpleclouds.api.common.cloud.spawning.SpawnInfo;
import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudGenerator;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudSpawningConfig;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import dev.nonamecrackers2.simpleclouds.common.world.ServerCloudManager;
import dev.nonamecrackers2.simpleclouds.common.world.SpawnRegion;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.manager.SimpleCloudSpawner;
import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.WeatherSampler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.BiasedToBottomInt;
import net.minecraft.world.phys.Vec2;
import org.joml.Vector2i;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static net.Gabou.projectatmosphere.manager.SimpleCloudSpawner.calculateDewPoint;
import static net.Gabou.projectatmosphere.manager.SimpleCloudSpawner.determineCloudSeverity;

public class SimpleCloudsCompat {

    public static ServerCloudManager cloudManager;
    public static CloudGenerator generator;

    public static RandomSource random = RandomSource.create();
    public static CloudSpawningConfig spawnConfig;

    public static final int SCALE = SimpleCloudsConstants.CLOUD_SCALE;

    public static final int MIN_RADIUS = Math.round(5000F/ SCALE);
    public static final int MAX_RADIUS = Math.round(9429F / SCALE);

    public static void init(ServerLevel level) {
        cloudManager = (ServerCloudManager) CloudManager.get(level);
        generator = cloudManager.getCloudGenerator();
        spawnConfig = generator.getSpawnConfig().get();
    }

    public static boolean isInit = false;

    public static void spawnCloudInBiome(String cloudId, BiomeInstanceKey key, ServerLevel level, @Nullable CloudRegion dummy, WindVector windVector) {


        if (!isInit) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] SimpleClouds is not ready yet, cannot spawn cloud: {}", cloudId);
            return;
        }


        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(SimpleCloudsMod.MODID, cloudId);
        CloudSpawningConfig.Info info = spawnConfig.getWeightInfo(rl);
        if (info == null) {
            ProjectAtmosphere.LOGGER.warn("[Atmosphere] Unknown cloud type: {}", cloudId);
            return;
        }
        ProjectAtmosphere.LOGGER.info("[Atmosphere] Spawning cloud: " + cloudId);
        List<SpawnRegion> Region = generator.getSpawnRegions();
        SpawnRegion targetRegion = Region.iterator().next();


        float x = targetRegion.x() + 0.5F;
        float z = targetRegion.z() + 0.5F;
        Optional<CloudRegion> region;
        if (dummy != null) {
            region = generator.spawnCloud(() -> info, spawnConfig.getSpawnInterval().sample(random), spawnConfig.getMaxRegions(), level,
                    (spawnInfo, playerX, playerZ, realX, realZ, rand, grow) ->
                            regionDummy(dummy)
            );
        } else {
            region = generator.spawnCloud(() -> info, spawnConfig.getSpawnInterval().sample(random), spawnConfig.getMaxRegions(), level,
                    (spawnInfo, playerX, playerZ, realX, realZ, rand, grow) ->
                            createRegion(spawnInfo, key, level, rand, windVector, generator)
            );
        }

        region.ifPresentOrElse(
                r -> ProjectAtmosphere.LOGGER.info("[Atmosphere] Spawned {} at {}, {} in {}", cloudId, x, z, key.biomeType()),
                () -> ProjectAtmosphere.LOGGER.warn("[Atmosphere] Failed to spawn {} in {}", cloudId, key.biomeType())
        );
    }

    public static Optional<CloudRegion> regionDummy(CloudRegion region) {
        return Optional.of(region);
    }


    public static Optional<CloudRegion> createRegion(
            SpawnInfo info,
            BiomeInstanceKey biomeKey,
            ServerLevel level,
            RandomSource random,
            WindVector wind,
            CloudGenerator generator
    ) {
        float x = biomeKey.samplePos().getX();
        float z = biomeKey.samplePos().getZ();
        float windAngleRad = wind.angleRadians();
        float dx = (float) Math.sin(windAngleRad);
        float dz = (float) Math.cos(windAngleRad);
        Vec2 direction = new Vec2(dx, dz).normalized();
        float rotation = windAngleRad + (float) Math.PI;


        
        Optional<CloudRegion> region = generator.createRegion(info, 10, 10, x, z, random, true);
        if (region.isEmpty()) {
            return Optional.empty();
        }
        CloudRegion cloudRegion = region.get();
        cloudRegion.setMovementDirection(direction);
        cloudRegion.setRotation(rotation);
        cloudRegion.setMaxSpeed(cloudRegion.getMaxSpeed() + wind.baseSpeed() * 0.01F);
        float acc = cloudRegion.getAccelerationFactor();
        cloudRegion.setAccelerationFactor(acc * wind.baseSpeed() * 0.005F);
        cloudRegion.setRadius(ProjectAtmosphere.DEFAULT_REGION_RADIUS);

        return Optional.of(cloudRegion);
    }

    public static void doInitialGenWithWeather(int x, int z, ServerLevel level) {
        SpawnRegion region = new SpawnRegion(x, z, SimpleCloudsConstants.SPAWN_RADIUS);
        CloudSpawningConfig config = spawnConfig;

        if (generator.getCloudsInRegion(region).size() > config.getMaxInitialRegions())
            return;

        for (int i = 0; i < config.getMaxInitialRegions(); i++) {
            int sharedRadius = BiasedToBottomInt.of(MIN_RADIUS,MAX_RADIUS).sample(random) ;
            for (int j = 0; j < SimpleCloudsConstants.SPAWN_ATTEMPTS; j++) {
                Vector2i pos;
                if(generator.getClouds().isEmpty())
                {
                    sharedRadius = 200;
                    pos = new Vector2i(x, z);
                }
                else {
                    pos = SpawnRegion.getRandomPointInRegion(region, random);
                }


                if (generator.getCloudsInRegion(region).size() >= config.getMaxInitialRegions())
                    return;

                boolean intersectsOther = generator.getSpawnRegions().stream()
                        .filter(r -> r != region)
                        .anyMatch(r -> r.includesPoint(pos.x, pos.y));
                if (intersectsOther)
                    continue;

                
                Set<BiomeInstanceKey> keys = WeatherSampler.sampleBiomesInArea(pos.x,pos.y,sharedRadius,level);
                WeatherSampler.WeatherStats stats = WeatherSampler.computeWeatherStats(keys, level, level.getGameTime());
                if (stats == null)
                    continue;

                String cloudId = CloudLibrary.getCloudIdFromSeverity(

                        determineCloudSeverity(
                                stats.temperature(),
                                stats.humidity(),
                                stats.pressure(),
                                calculateDewPoint(stats.temperature(), stats.humidity()),stats.stormChance(),level
                        ));
                ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(SimpleCloudsMod.MODID,cloudId);
                CloudSpawningConfig.Info selected = config.getWeightInfo(rl);
                if (selected == null)
                    return;
                Optional<CloudRegion> cloudFormation = createRegion(
                        selected,
                        new BiomeInstanceKey(stats.dominantBiome(), stats.pos()),
                        level,
                        random,
                        stats.windVector(),
                        generator
                );

                int finalSharedRadius = sharedRadius;
                cloudFormation.ifPresent(cf -> {
                    cf.setRadius(finalSharedRadius);
                    generator.addCloud(cf, CloudGenerator.Order.USE_WEIGHT);
                    isInit = true;
                });

                break;
            }
        }
    }






}
