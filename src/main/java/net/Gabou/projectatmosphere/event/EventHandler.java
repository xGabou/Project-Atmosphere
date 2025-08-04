package net.Gabou.projectatmosphere.event;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudGenerator;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import dev.nonamecrackers2.simpleclouds.common.world.ServerCloudManager;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.blocks.BlockManager;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.Gabou.projectatmosphere.manager.SimpleCloudSpawner;
import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


import static net.Gabou.projectatmosphere.ProjectAtmosphere.LOGGER;

@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID)
public class EventHandler {

    private static final int MIN_TICKS_BETWEEN_DUST_SPAWN = 5000;
    // Increase delay between tempest effects to reduce how often
    // cochonerie (debris) spawns
    private static final int MIN_TICKS_BETWEEN_TEMPESTA = 2000;

    private static int tickCounter = 0;

    private static int ticksSinceLastCloudSpawn = 0;

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide || !(event.level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!AtmosphereManager.isInitialGenerationDone) {
            return;
        }
        if (serverLevel.players().isEmpty()) {
            return;
        }

        if (!serverLevel.dimension().equals(Level.OVERWORLD)) return;

        ServerPlayer player = serverLevel.players().get(0);

        ServerCloudManager cloudManager = (ServerCloudManager) CloudManager.get(serverLevel);
        CloudGenerator generator = cloudManager.getCloudGenerator();
        AtmosphereManager.tick(serverLevel);
        if (generator.getTicksTillNextGen() <= 0 && ticksSinceLastCloudSpawn % 1000 == 0) {
            SimpleCloudSpawner.trySpawnClouds(serverLevel, generator);
            ticksSinceLastCloudSpawn = 0;
        }

        if (tickCounter % MIN_TICKS_BETWEEN_DUST_SPAWN == 0) {
            BlockManager.spawnDust(serverLevel, player.blockPosition());
        }

        if (tickCounter % MIN_TICKS_BETWEEN_TEMPESTA == 0) {

            final int cloudY = cloudManager.getCloudHeight();

            for (CloudRegion region : generator.getClouds()) {
                int severity = CloudLibrary.getSeverityFromRessourceLocation(region.getCloudTypeId());
                if (severity > 5) {
                    BlockPos pos = new BlockPos((int) region.getPosX(), cloudY, (int) region.getPosZ());
                    BlockManager.simulateTempesta(serverLevel, pos, (int) region.getRadius());
                }
            }


        }
        ticksSinceLastCloudSpawn++;
        tickCounter++;
    }
}
