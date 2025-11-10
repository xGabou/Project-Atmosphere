package net.Gabou.projectatmosphere.compat.rainbows;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudGenerator;
import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.Gabou.projectatmosphere.network.NetworkHandler;
import net.Gabou.projectatmosphere.network.RainfallUpdatePacket;
import net.Gabou.projectatmosphere.util.WeatherType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RainbowRainBridge {

    private static final Map<ResourceKey<Level>, Float> LAST_LEVELS = new HashMap<>();
    private static final float EPSILON = 0.02f;

    private RainbowRainBridge() {
    }

    public static void sync(ServerLevel level, CloudGenerator generator) {
        float rainLevel = computeRainLevel(generator);
        ResourceKey<Level> dimension = level.dimension();
        Float previous = LAST_LEVELS.get(dimension);
        if (previous != null && Math.abs(previous - rainLevel) <= EPSILON) {
            return;
        }
        LAST_LEVELS.put(dimension, rainLevel);
        RainfallUpdatePacket packet = new RainfallUpdatePacket(dimension.location(), rainLevel);
        NetworkHandler.CHANNEL.send(PacketDistributor.DIMENSION.with(() -> dimension), packet);
    }

    public static void clear(ResourceKey<Level> dimension) {
        LAST_LEVELS.remove(dimension);
    }

    public static void sendSnapshot(ServerPlayer player, ServerLevel level, CloudGenerator generator) {
        ResourceKey<Level> dimension = level.dimension();
        float rainLevel = LAST_LEVELS.getOrDefault(dimension, computeRainLevel(generator));
        LAST_LEVELS.put(dimension, rainLevel);
        RainfallUpdatePacket packet = new RainfallUpdatePacket(dimension.location(), rainLevel);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    private static float computeRainLevel(CloudGenerator generator) {
        List<CloudRegion> clouds = generator.getClouds();
        if (clouds.isEmpty()) {
            return 0.0f;
        }
        float total = 0.0f;
        int rainyCount = 0;
        for (CloudRegion region : clouds) {
            ResourceLocation type = region.getCloudTypeId();
            if (WeatherType.isRainy(type)) {
                rainyCount++;
                int severity = CloudLibrary.getSeverityFromRessourceLocation(type);
                total += Math.max(1, severity) / 7.0f;
            }
        }
        if (rainyCount == 0) {
            return 0.0f;
        }
        return Mth.clamp(total / rainyCount, 0.0f, 1.0f);
    }
}
