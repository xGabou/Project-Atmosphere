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
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.Gabou.projectatmosphere.modules.tornado.GlassDamageManager;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID)
public class EventHandler {

    private static final int MIN_TICKS_BETWEEN_DUST_SPAWN = 5000;
    
    
    private static final int MIN_TICKS_BETWEEN_TEMPESTA = 2000;

    private static final int TICKS_BETWEEN_TORNADO_CHECK = 200;
    private static final float NATURAL_TORNADO_CHANCE = 0.01f;

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
        TornadoManager.tick(serverLevel);
        GlassDamageManager.tick(serverLevel);
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

        if (tickCounter % TICKS_BETWEEN_TORNADO_CHECK == 0) {
            for (CloudRegion region : generator.getClouds()) {
                int severity = CloudLibrary.getSeverityFromRessourceLocation(region.getCloudTypeId());
                if (severity >= 6 && serverLevel.random.nextFloat() < NATURAL_TORNADO_CHANCE) {
                    BlockPos spawnPos = new BlockPos((int) region.getPosX(), serverLevel.getSeaLevel(), (int) region.getPosZ());
                    BiomeInstanceKey key = AtmosphereUtils.getBiomeKey(serverLevel, spawnPos);
                    WindVector wind = ForecastOrchestrator.getCurrentWind(key, serverLevel.getGameTime());
                    TornadoManager.spawnServer(serverLevel, new Vec3(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ()), 4.0f, wind);
                }
            }
        }
        ticksSinceLastCloudSpawn++;
        tickCounter++;
    }
}
