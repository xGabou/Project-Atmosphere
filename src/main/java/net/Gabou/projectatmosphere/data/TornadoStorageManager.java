package net.Gabou.projectatmosphere.data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
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
        COOLDOWNS.put(key.toString(), untilTick);
    }

    public static boolean isOnCooldown(BiomeInstanceKey key, long nowTick) {
        Long until = COOLDOWNS.get(key.toString());
        return until != null && nowTick < until;
    }
}

