package net.Gabou.projectatmosphere.client.loading;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.client.ClientSyncLock;
import net.Gabou.projectatmosphere.client.hurricane.cache.ClientHurricaneStateCache;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID, value = Dist.CLIENT)
public final class ClientForecastLoadingLifecycle {
    private ClientForecastLoadingLifecycle() {
    }

    @SubscribeEvent
    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientSyncLock.clear();
        ClientHurricaneStateCache.clear();
        ClientForecastLoadingWorkQueue.reset();
        if (!ForecastLoadingState.snapshot().active()) {
            ForecastLoadingState.start(
                    ForecastLoadingStage.WAITING_FOR_SERVER,
                    null,
                    null,
                    null,
                    "client_login"
            );
        }
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientSyncLock.clear();
        ClientHurricaneStateCache.clear();
        ClientForecastLoadingWorkQueue.reset();
        ForecastLoadingState.reset("client_logout");
    }
}

