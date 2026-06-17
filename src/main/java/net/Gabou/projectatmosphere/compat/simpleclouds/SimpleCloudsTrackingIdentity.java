package net.Gabou.projectatmosphere.compat.simpleclouds;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import net.Gabou.projectatmosphere.util.ICloudRegionId;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds PA tracking identities for Simple Clouds regions without assuming that
 * Simple Clouds exposes a globally unique per-cloud instance id.
 */
public final class SimpleCloudsTrackingIdentity {
    private static final int CAPTURE_TIME_BUCKET_TICKS = 1200;
    private static final Map<CloudRegion, Entry> ENTRIES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<String, Integer> BASE_COUNTS = new ConcurrentHashMap<>();

    private SimpleCloudsTrackingIdentity() {
    }

    public static Entry resolve(CloudRegion region, ServerLevel level) {
        if (region == null) {
            return Entry.missing();
        }
        synchronized (ENTRIES) {
            Entry existing = ENTRIES.get(region);
            if (existing != null) {
                return existing;
            }
            Entry created = create(region, level);
            ENTRIES.put(region, created);
            return created;
        }
    }

    public static void clear() {
        synchronized (ENTRIES) {
            ENTRIES.clear();
        }
        BASE_COUNTS.clear();
    }

    private static Entry create(CloudRegion region, ServerLevel level) {
        int sourceId = extractSourceId(region);
        String typeId = normalizeType(region.getCloudTypeId());
        int seaLevel = level == null ? 0 : level.getSeaLevel();
        RegionInstanceKey nearestRegion = RegionInstanceKey.from(BlockPos.containing(region.getWorldX(), seaLevel, region.getWorldZ()));
        long gameTime = level == null ? 0L : level.getGameTime();
        long timeBucket = Math.floorDiv(gameTime, CAPTURE_TIME_BUCKET_TICKS);
        String base = "sc|id=" + sourceId
                + "|type=" + typeId
                + "|region=" + nearestRegion.regionX() + "," + nearestRegion.regionZ()
                + "|born=" + timeBucket;
        int index = BASE_COUNTS.merge(base, 1, Integer::sum) - 1;
        String key = index == 0 ? base : base + "|idx=" + index;
        return new Entry(key, sourceId, typeId, nearestRegion, timeBucket, index);
    }

    private static int extractSourceId(CloudRegion region) {
        if (region instanceof ICloudRegionId accessor) {
            return accessor.projectatmosphere$getId();
        }
        return System.identityHashCode(region);
    }

    private static String normalizeType(ResourceLocation type) {
        if (type == null) {
            return "unknown";
        }
        return type.toString().toLowerCase(Locale.ROOT).replace('|', '_').replace(' ', '_');
    }

    public record Entry(
            String trackingKey,
            int sourceId,
            String typeId,
            RegionInstanceKey nearestRegion,
            long timeBucket,
            int formationIndex
    ) {
        private static Entry missing() {
            return new Entry("sc|missing", 0, "unknown", new RegionInstanceKey(0, 0), 0L, 0);
        }
    }
}
