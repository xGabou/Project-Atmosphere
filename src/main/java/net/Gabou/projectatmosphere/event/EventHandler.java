package net.Gabou.projectatmosphere.event;


import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudGenerator;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import dev.nonamecrackers2.simpleclouds.common.world.ServerCloudManager;
import net.Gabou.projectatmosphere.client.ClientSyncLock;
import net.Gabou.projectatmosphere.manager.SimpleCloudSpawner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.Gabou.projectatmosphere.ProjectAtmosphere;

@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID)
public class EventHandler {

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (!event.level.isClientSide && event.phase == TickEvent.Phase.END) {
            if (event.level instanceof ServerLevel serverLevel) {
                if (serverLevel.players().isEmpty()) return;

                ServerPlayer player = serverLevel.players().get(0);
                if (!ClientSyncLock.isPlayerReady(player.getUUID())) return;

                ServerCloudManager cloudManager = (ServerCloudManager) CloudManager.get(serverLevel);
                CloudGenerator generator = cloudManager.getCloudGenerator();
                if(generator.getTicksTillNextGen()<= 0)
                {
                    SimpleCloudSpawner.trySpawnClouds(serverLevel,generator);
                }

            }
        }
    }
}
