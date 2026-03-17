package net.Gabou.projectatmosphere.client.loading;

import net.Gabou.projectatmosphere.client.ClientSyncLock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

public final class ClientForecastLoadingLifecycle {
    private ClientForecastLoadingLifecycle() {
    }

    @SubscribeEvent
    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientSyncLock.clear();
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
        ClientForecastLoadingWorkQueue.reset();
        ForecastLoadingState.reset("client_logout");
    }
}
