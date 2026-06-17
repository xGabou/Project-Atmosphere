package net.Gabou.projectatmosphere.client.loading;

import net.Gabou.projectatmosphere.client.ClientSyncLock;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

public final class IntegratedForecastLoadingBridge {
    private IntegratedForecastLoadingBridge() {
    }

    public static void update(ForecastLoadingStage stage, String subtext, Float progress, String source) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> apply(stage, subtext, progress, source));
    }

    private static void apply(ForecastLoadingStage stage, String subtext, Float progress, String source) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getSingleplayerServer() == null) {
            return;
        }

        ClientSyncLock.setReadyForLocalPlayer(false);
        ForecastLoadingState.Snapshot snapshot = ForecastLoadingState.snapshot();
        if (!snapshot.active()) {
            ForecastLoadingState.start(stage, null, subtext, progress, source);
            return;
        }

        ForecastLoadingState.update(stage, null, subtext, progress, source);
    }
}
