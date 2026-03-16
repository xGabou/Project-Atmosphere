package net.Gabou.projectatmosphere.data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.server.level.ServerLevel;

public final class TornadoStorageManager {
    private static final Map<String, Long> COOLDOWNS = new ConcurrentHashMap<>();

    private TornadoStorageManager() {}

    public static void load(ServerLevel level) {
        // Placeholder for future persistence
    }

    public static void save(ServerLevel level) {
        // Placeholder for future persistence
    }

    public static void setCooldown(BiomeInstanceKey key, long untilTick) {
        RegionInstanceKey regionKey = AtmosphericStateRegistry.resolveRegionKey(key);
        if (regionKey != null) {
            setCooldown(regionKey, untilTick);
            return;
        }
        COOLDOWNS.put(key.toString(), untilTick);
    }

    public static boolean isOnCooldown(BiomeInstanceKey key, long nowTick) {
        RegionInstanceKey regionKey = AtmosphericStateRegistry.resolveRegionKey(key);
        if (regionKey != null) {
            return isOnCooldown(regionKey, nowTick);
        }
        Long until = COOLDOWNS.get(key.toString());
        return until != null && nowTick < until;
    }

    public static void setCooldown(RegionInstanceKey key, long untilTick) {
        if (key == null) {
            return;
        }
        COOLDOWNS.put(key.toString(), untilTick);
    }

    public static boolean isOnCooldown(RegionInstanceKey key, long nowTick) {
        if (key == null) {
            return false;
        }
        Long until = COOLDOWNS.get(key.toString());
        return until != null && nowTick < until;
    }
}

