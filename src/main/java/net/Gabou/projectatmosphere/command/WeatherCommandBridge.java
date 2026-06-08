package net.Gabou.projectatmosphere.command;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import net.Gabou.projectatmosphere.clouds.network.CloudRegionSyncManager;
import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudServices;
import net.Gabou.projectatmosphere.clouds.simulation.CloudRegionManager;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionStateStore;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.Gabou.projectatmosphere.modules.temperature.command.TemperatureCommandHelper;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

public final class WeatherCommandBridge {

    private static final float WEATHER_REGION_RADIUS = 64.0F;
    private static final float WEATHER_REGION_BASE_Y_OFFSET = 80.0F;

    private WeatherCommandBridge() {
    }

    public static int setClear(CommandSourceStack source, int duration) {
        return apply(source, duration, WeatherKind.CLEAR);
    }

    public static int setRain(CommandSourceStack source, int duration) {
        return apply(source, duration, WeatherKind.RAIN);
    }

    public static int setThunder(CommandSourceStack source, int duration) {
        return apply(source, duration, WeatherKind.THUNDER);
    }

    private static int apply(CommandSourceStack source, int duration, WeatherKind kind) {
        ServerLevel level = source.getLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            source.sendFailure(Component.literal("Weather clouds can only be spawned in the Overworld."));
            return 0;
        }

        BlockPos sourcePos = BlockPos.containing(source.getPosition());
        RegionInstanceKey regionKey = RegionInstanceKey.from(sourcePos);
        int resolvedDuration = resolveDuration(source, duration, kind.durationProvider);

        if (AtmosphereCloudServices.isSimpleCloudsLoaded()) {
            String cloudId = switch (kind) {
                case CLEAR -> CloudLibrary.getCloudIdFromSeverity(1);
                case RAIN -> CloudLibrary.getRandomRainCloud(1, false);
                case THUNDER -> CloudLibrary.getRandomThunderCloud(1);
            };
            CloudRegion region = SimpleCloudsCompat.spawnCloudInRegion(
                    cloudId,
                    regionKey,
                    level,
                    null,
                    net.Gabou.projectatmosphere.modules.core.WindVector.fromBase(1.0F, 0.0F)
            );
            if (region == null) {
                source.sendFailure(Component.literal("Failed to spawn weather cloud."));
                return 0;
            }
            source.sendSuccess(() -> Component.literal("Spawned " + kind.messageName + " cloud in your region."), true);
            return 1;
        }

        CloudRegionState state = upsertNativeWeatherRegion(level, sourcePos, regionKey, kind, resolvedDuration);
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            CloudRegionSyncManager.syncPlayer(player);
        }
        if (state == null) {
            source.sendFailure(Component.literal("Failed to spawn weather cloud."));
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal("Spawned " + kind.messageName + " cloud in your region."),
                true
        );
        return 1;
    }

    private static int resolveDuration(CommandSourceStack source, int duration, IntProvider provider) {
        return duration == -1 ? provider.sample(source.getLevel().getRandom()) : duration;
    }

    private static CloudRegionState upsertNativeWeatherRegion(
            ServerLevel level,
            BlockPos sourcePos,
            RegionInstanceKey regionKey,
            WeatherKind kind,
            int durationTicks
    ) {
        CloudRegionState state = CloudRegionStateStore.getAll(level).stream()
                .filter(candidate -> candidate != null)
                .filter(candidate -> Objects.equals(candidate.getSourceRegionKey(), regionKey))
                .findFirst()
                .orElse(null);

        Vec3 center = new Vec3(sourcePos.getX(), sourcePos.getY() + WEATHER_REGION_BASE_Y_OFFSET, sourcePos.getZ());
        String cloudTypeId = kind.nativeCloudTypeId(durationTicks);
        float density = kind.density;
        float coverage = kind.coverage;
        float edgeSoftness = kind.edgeSoftness;

        if (state == null) {
            state = CloudRegionManager.getInstance().createCloudRegion(
                    level,
                    center,
                    WEATHER_REGION_RADIUS,
                    (float) center.y() - 8.0F,
                    (float) center.y() + 8.0F,
                    density,
                    coverage,
                    edgeSoftness,
                    regionKey
            );
        } else {
            state.setPreviousCenter(state.getCenter());
            state.setCenter(center);
            state.setRadius(WEATHER_REGION_RADIUS);
            state.setVerticalBounds((float) center.y() - 8.0F, (float) center.y() + 8.0F);
            state.setDensity(density);
            state.setCoverage(coverage);
            state.setEdgeSoftness(edgeSoftness);
            state.setActive(true);
            state.setCurrentRegionKey(regionKey);
            state.setAgeTicks(0);
            state.setLifetimeTicks(durationTicks);
            state.setGrowth(1.0F);
            state.setDecay(0.0F);
            state.setCloudTypeTicks(0);
        }

        state.setCloudTypeId(cloudTypeId);
        state.setLifetimeTicks(durationTicks);
        state.setCurrentRegionKey(regionKey);
        CloudRegionStateStore.markDirty(level);
        return state;
    }

    private enum WeatherKind {
        CLEAR("clear", 0.20F, 0.25F, 0.40F, tick -> CloudTypeRegistry.getClearWeatherCloudId(), ServerLevel.RAIN_DELAY),
        RAIN("rain", 0.72F, 0.82F, 0.32F, tick -> CloudTypeRegistry.getRandomRainCloud(tick >= 1200 ? 2 : 1), ServerLevel.RAIN_DURATION),
        THUNDER("thunder", 0.88F, 0.94F, 0.24F, tick -> CloudTypeRegistry.getRandomThunderCloud(tick >= 1200 ? 2 : 1), ServerLevel.THUNDER_DURATION);

        private final String messageName;
        private final float density;
        private final float coverage;
        private final float edgeSoftness;
        private final CloudIdPicker cloudIdPicker;
        private final IntProvider durationProvider;

        WeatherKind(String messageName,
                    float density,
                    float coverage,
                    float edgeSoftness,
                    CloudIdPicker cloudIdPicker,
                    IntProvider durationProvider) {
            this.messageName = messageName;
            this.density = density;
            this.coverage = coverage;
            this.edgeSoftness = edgeSoftness;
            this.cloudIdPicker = cloudIdPicker;
            this.durationProvider = durationProvider;
        }

        private String nativeCloudTypeId(int durationTicks) {
            return this.cloudIdPicker.pick(durationTicks);
        }
    }

    @FunctionalInterface
    private interface CloudIdPicker {
        String pick(int durationTicks);
    }
}
