package net.Gabou.projectatmosphere.manager;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.Gabou.projectatmosphere.modules.core.WeatherType;
import net.Gabou.projectatmosphere.seasons.SeasonTimeHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class AtmosphereCloudRegionTracker {
    private static final List<CloudRegion> CLOUD_REGIONS = new ArrayList<>();

    private AtmosphereCloudRegionTracker() {
    }

    static void reset(ServerLevel level) {
        CLOUD_REGIONS.clear();
        if (level != null) {
            CLOUD_REGIONS.addAll(CloudManager.get(level).getClouds());
        }
    }

    static void clear() {
        CLOUD_REGIONS.clear();
    }

    static void reconcile(ServerLevel level) {
        if (level == null) {
            return;
        }
        if (CLOUD_REGIONS.isEmpty()) {
            return;
        }
        List<CloudRegion> activeRegions = new ArrayList<>(CloudManager.get(level).getClouds());
        for (CloudRegion cloudRegion : new ArrayList<>(CLOUD_REGIONS)) {
            if (cloudRegion instanceof net.Gabou.projectatmosphere.util.ICloudRegionId id) {
                boolean stillActive = activeRegions.stream()
                        .filter(r -> r instanceof net.Gabou.projectatmosphere.util.ICloudRegionId)
                        .map(r -> (net.Gabou.projectatmosphere.util.ICloudRegionId) r)
                        .anyMatch(r -> r.projectatmosphere$getId() == id.projectatmosphere$getId());
                if (!stillActive) {
                    queueRemove(cloudRegion);
                }
            }
        }
    }

    static void pollQueue(ServerLevel level) {
        if (CloudRegionQueue.isEmpty()) {
            CloudRegionQueue.shuffle();
            return;
        }
        CloudRegionQueue.Entry entry;
        while ((entry = CloudRegionQueue.poll()) != null) {
            switch (entry.type()) {
                case ADD -> handleQueue(level, entry.region());
                case REMOVE -> handleRemove(level, entry.region());
            }
        }
    }

    static List<CloudRegion> getCloudRegions() {
        return Collections.unmodifiableList(CLOUD_REGIONS);
    }

    static void queueAdd(CloudRegion cloudRegion) {
        CloudRegionQueue.enqueueAdd(cloudRegion);
    }

    static void queueRemove(CloudRegion cloudRegion) {
        CloudRegionQueue.enqueueRemove(cloudRegion);
    }

    private static void handleQueue(ServerLevel level, CloudRegion cloudRegion) {
        if (CLOUD_REGIONS.contains(cloudRegion)) {
            return;
        }
        CLOUD_REGIONS.add(cloudRegion);
        if (WeatherType.isRainy(cloudRegion.getCloudTypeId())) {
            SeasonTimeHelper.onRainStarted(level, cloudRegion);
        }
    }

    private static void handleRemove(ServerLevel level, CloudRegion cloudRegion) {
        if (CloudManager.get(level).getClouds().contains(cloudRegion) && !CLOUD_REGIONS.contains(cloudRegion)) {
            return;
        }
        CLOUD_REGIONS.remove(cloudRegion);
        if (WeatherType.isRainy(cloudRegion.getCloudTypeId())) {
            SeasonTimeHelper.onRainEnded(level, cloudRegion);
        }
    }
}
