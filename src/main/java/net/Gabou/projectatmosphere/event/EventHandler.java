package net.Gabou.projectatmosphere.event;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudGenerator;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import dev.nonamecrackers2.simpleclouds.common.world.ServerCloudManager;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.blocks.BlockManager;
import net.Gabou.projectatmosphere.compat.CompatHandler;
import net.Gabou.projectatmosphere.compat.rainbows.RainbowRainBridge;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.Gabou.projectatmosphere.manager.SimpleCloudSpawner;
import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID)
public class EventHandler {

    private static final int MIN_TICKS_BETWEEN_DUST_SPAWN = 5000;

    private static final int MIN_TICKS_BETWEEN_TEMPESTA = 2000;

    private static int tickCounter = 0;

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

        ServerCloudManager cloudManager = (ServerCloudManager) CloudManager.get(serverLevel);
        CloudGenerator generator = cloudManager.getCloudGenerator();
        AtmosphereManager.tick(serverLevel);

        if (generator.getTicksTillNextGen() <= 0) {
            SimpleCloudSpawner.trySpawnClouds(serverLevel, generator);
        }

        if (CompatHandler.isRainbowsLoaded()) {
            RainbowRainBridge.sync(serverLevel, generator);
        }


        if (!AtmoCommonConfig.ENABLE_STORM_DEBRIS.get()) {
            return;
        } else if (tickCounter % MIN_TICKS_BETWEEN_TEMPESTA == 0) {

            final int cloudY = cloudManager.getCloudHeight();

            for (CloudRegion region : generator.getClouds()) {
                int severity = CloudLibrary.getSeverityFromRessourceLocation(region.getCloudTypeId());
                if (severity > 5) {
                    BlockPos pos = new BlockPos((int) region.getWorldX(), cloudY, (int) region.getWorldZ());
                    BlockManager.simulateTempesta(serverLevel, pos, (int) region.getRadius());
                }
            }


        }

        tickCounter++;
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (CompatHandler.isRainbowsLoaded() && level.dimension().equals(event.getTo())) {
            ServerCloudManager cloudManager = (ServerCloudManager) CloudManager.get(level);
            RainbowRainBridge.sendSnapshot(player, level, cloudManager.getCloudGenerator());
        }
    }

    public static void onRegenerate() {
        tickCounter = 0;
    }
}
