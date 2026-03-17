package net.Gabou.projectatmosphere.client.loading;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.client.BiomeClientTemperatureCache;
import net.Gabou.projectatmosphere.client.ClientSyncLock;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID, value = Dist.CLIENT)
public final class ClientForecastLoadingWorkQueue {
    private static final int BATCH_SIZE = 12;
    private static volatile PendingSnapshot pendingSnapshot;
    private static volatile boolean serverReadyReceived;
    private static volatile String readySource = "unknown";

    private ClientForecastLoadingWorkQueue() {
    }

    public static void queueForecastSnapshot(Map<ResourceLocation, float[]> forecastSnapshot, String source) {
        PendingSnapshot pending = new PendingSnapshot(forecastSnapshot, normalizeSource(source));
        pendingSnapshot = pending;
        serverReadyReceived = false;
        readySource = "unknown";
        ClientSyncLock.setReadyForLocalPlayer(false);
        BiomeClientTemperatureCache.clear();
        ForecastLoadingState.update(
                ForecastLoadingStage.BUILDING_LOCAL_FORECAST_CACHE,
                null,
                pending.progressText(),
                pending.progressValue(),
                pending.source
        );
    }

    public static boolean hasPendingWork() {
        return pendingSnapshot != null;
    }

    public static void onServerReady(String source) {
        serverReadyReceived = true;
        readySource = normalizeSource(source);
        flushReadyIfPossible();
    }

    public static void reset() {
        pendingSnapshot = null;
        serverReadyReceived = false;
        readySource = "unknown";
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        PendingSnapshot pending = pendingSnapshot;
        if (pending == null) {
            return;
        }

        pending.applyNextBatch();
        ForecastLoadingState.update(
                ForecastLoadingStage.BUILDING_LOCAL_FORECAST_CACHE,
                null,
                pending.progressText(),
                pending.progressValue(),
                pending.source
        );

        if (!pending.isComplete()) {
            return;
        }

        BiomeClientTemperatureCache.replaceDayForecasts(pending.targetForecasts);
        pendingSnapshot = null;
        ForecastLoadingState.update(
                ForecastLoadingStage.PREPARING_WEATHER_SYSTEMS,
                null,
                pending.finalizeText(),
                0.94F,
                pending.source + "_finalize"
        );
        flushReadyIfPossible();
    }

    private static void flushReadyIfPossible() {
        if (!serverReadyReceived || pendingSnapshot != null) {
            return;
        }

        ClientSyncLock.setReadyForLocalPlayer(true);
        ForecastLoadingState.markReady(readySource);
        serverReadyReceived = false;
        readySource = "unknown";
    }

    private static String normalizeSource(String source) {
        return source == null || source.isBlank() ? "unknown" : source;
    }

    private static final class PendingSnapshot {
        private final List<Map.Entry<ResourceLocation, float[]>> entries;
        private final Map<ResourceLocation, float[]> targetForecasts;
        private final int totalEntries;
        private final String source;
        private int appliedEntries;

        private PendingSnapshot(Map<ResourceLocation, float[]> forecastSnapshot, String source) {
            this.entries = new ArrayList<>(forecastSnapshot.entrySet());
            this.targetForecasts = new ConcurrentHashMap<>(Math.max(1, forecastSnapshot.size()));
            this.totalEntries = entries.size();
            this.source = source;
        }

        private void applyNextBatch() {
            int remaining = totalEntries - appliedEntries;
            int toApply = Math.min(BATCH_SIZE, remaining);
            for (int i = 0; i < toApply; i++) {
                Map.Entry<ResourceLocation, float[]> entry = entries.get(appliedEntries);
                targetForecasts.put(entry.getKey(), entry.getValue());
                appliedEntries++;
            }
        }

        private boolean isComplete() {
            return appliedEntries >= totalEntries;
        }

        private String progressText() {
            return appliedEntries + " / " + totalEntries + " biome profiles";
        }

        private String finalizeText() {
            return totalEntries > 0
                    ? "Finalized " + totalEntries + " biome profiles"
                    : "Finalizing forecast cache";
        }

        private float progressValue() {
            if (totalEntries <= 0) {
                return 0.74F;
            }
            float ratio = appliedEntries / (float) totalEntries;
            return 0.56F + (ratio * 0.32F);
        }
    }
}
