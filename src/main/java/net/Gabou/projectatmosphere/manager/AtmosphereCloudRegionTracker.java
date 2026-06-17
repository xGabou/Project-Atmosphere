package net.Gabou.projectatmosphere.manager;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.Gabou.projectatmosphere.modules.core.WeatherType;
import net.Gabou.projectatmosphere.seasons.SeasonTimeHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AtmosphereCloudRegionTracker {
    private static final List<CloudRegion> CLOUD_REGIONS = new ArrayList<>();

    private AtmosphereCloudRegionTracker() {
    }

    public static void reset(ServerLevel level) {
        CLOUD_REGIONS.clear();
        if (level != null) {
            CLOUD_REGIONS.addAll(CloudManager.get(level).getClouds());
        }
    }

    public static void clear() {
        CLOUD_REGIONS.clear();
    }

    public static void reconcile(ServerLevel level) {
        if (level == null) {
            return;
        }
        if (CLOUD_REGIONS.isEmpty()) {
            return;
        }
        Set<Integer> activeIds = new HashSet<>();
        for (CloudRegion activeRegion : CloudManager.get(level).getClouds()) {
            if (activeRegion instanceof net.Gabou.projectatmosphere.util.ICloudRegionId id) {
                activeIds.add(id.projectatmosphere$getId());
            }
        }

        for (CloudRegion cloudRegion : new ArrayList<>(CLOUD_REGIONS)) {
            if (cloudRegion instanceof net.Gabou.projectatmosphere.util.ICloudRegionId id) {
                if (!activeIds.contains(id.projectatmosphere$getId())) {
                    queueRemove(cloudRegion);
                }
            }
        }
    }

    public static void pollQueue(ServerLevel level) {
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

    public static List<CloudRegion> getCloudRegions() {
        return Collections.unmodifiableList(CLOUD_REGIONS);
    }

    public static void queueAdd(CloudRegion cloudRegion) {
        CloudRegionQueue.enqueueAdd(cloudRegion);
    }

    public static void queueRemove(CloudRegion cloudRegion) {
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
