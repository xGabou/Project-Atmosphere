package net.Gabou.projectatmosphere.manager;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.entity.CloudEntity;
import net.Gabou.projectatmosphere.entity.SmallNormalCloud1Entity;
import net.Gabou.projectatmosphere.modules.humidity.manager.HumidityManager;
import net.Gabou.projectatmosphere.modules.humidity.util.HumidityProfileManager;
import net.Gabou.projectatmosphere.modules.pressure.util.PressureProfileManager;
import net.Gabou.projectatmosphere.modules.temperature.util.TemperatureProfileManager;
import net.Gabou.projectatmosphere.registry.EntityRegistrar;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Set;

public class CloudSpawner {
    private static final int SPAWN_INTERVAL_TICKS = 20 * 15; // Every 15 seconds
    private static long lastSpawnTick = 0;
    private static final int cloudY = 190; // Fixed Y coordinate for cloud spawn
    private static final int CLOUDS_PER_PLAYER = 30; // How many clouds per playerz
    private static final float SEA_LEVEL_PRESSURE = 1013.25f; // hPa, standard sea level pressure

    /**
     * Attempts to spawn clouds in the given level based on the spawn chance.
     *
     * @param level The server level where clouds may be spawned.
     */
    public static void trySpawnClouds(ServerLevel level) {
        long gameTime = level.getGameTime();
        if (gameTime - lastSpawnTick < SPAWN_INTERVAL_TICKS) {
            return;
        }
        lastSpawnTick = gameTime;

        float spawnChance = WeatherManager.getCloudSpawnChance(level);
        if (level.random.nextFloat() <= spawnChance) {
            spawnCloudsForAllPlayers(level);

    }
    /**
     * Spawns a cloud for the given player in the specified level.
     *
     * @param player The player for whom the cloud will be spawned.
     * @param level  The server level where the cloud will be spawned.
     */
    public static void spawnCloudForPlayer(ServerPlayer player, ServerLevel level) {
        spawnCloud(level, player, getRadiusBlocks(level));
    }

    /**
     * Spawns clouds for all players in the given level.
     *
     * @param level The server level where the clouds will be spawned.
     */

    private static void spawnCloudsForAllPlayers(ServerLevel level) {
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return;
        }

        for (ServerPlayer player : players) {
            float spawnChance = calculateCloudSpawnChance(level,player);
            final int radiusBlocks = getRadiusBlocks(level);
                for (int i = 0; i < CLOUDS_PER_PLAYER*spawnChance; i++) {
                    spawnCloud(level, player, radiusBlocks);
                }

        }
    }

    /**
     * Spawns a cloud entity at a random position around the player.
     *
     * @param level        The server level where the cloud will be spawned.
     * @param player       The player around whom the cloud will be spawned.
     * @param radiusBlocks The radius in blocks within which to spawn the cloud.
     */

    private static void spawnCloud(ServerLevel level, ServerPlayer player, int radiusBlocks) {
        double x = player.getX() + (level.random.nextDouble() - 0.5) * radiusBlocks;
        double z = player.getZ() + (level.random.nextDouble() - 0.5) * radiusBlocks;

        BlockPos pos = new BlockPos((int) x, cloudY, (int) z);

        if (!level.hasChunkAt(pos)) {
            ProjectAtmosphere.LOGGER.warn("Skipped cloud spawn at unloaded position for player " + player.getName().getString() + ": " + pos);
            return;
        }

        EntityType<CloudEntity> type = EntityRegistrar.CLOUD_ENTITY.get();
        CloudEntity cloud = new CloudEntity(type, level);
        cloud.setPos(x, cloudY, z);
        level.addFreshEntity(cloud);
        cloud.setSize(1.5f);
        ProjectAtmosphere.LOGGER.info("Spawned cloud for player " + player.getName().getString() + " at " + x + ", " + cloudY + ", " + z);


        x = player.getX() + (level.random.nextDouble() - 0.5) * radiusBlocks;
        z = player.getZ() + (level.random.nextDouble() - 0.5) * radiusBlocks;
        EntityType<SmallNormalCloud1Entity> type2 = EntityRegistrar.SMALL_NORMAL_CLOUD_1_ENTITY.get();
        SmallNormalCloud1Entity cloud2 = new SmallNormalCloud1Entity(type2, level);
        cloud2.setPos(x, cloudY, z);
        cloud2.setSize(3.5f);
        level.addFreshEntity(cloud2);
        ProjectAtmosphere.LOGGER.info("Spawned cloud for player " + player.getName().getString() + " at " + x + ", " + cloudY + ", " + z);

    }

    private static int getRadiusBlocks(ServerLevel level) {
        return level.getServer().getPlayerList().getViewDistance() * 16;
    }
}
