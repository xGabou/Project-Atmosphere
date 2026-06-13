package net.Gabou.projectatmosphere.command.tree.service;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.api.WindVectorApi;
import net.Gabou.projectatmosphere.clouds.network.CloudRegionSyncManager;
import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudServices;
import net.Gabou.projectatmosphere.clouds.simulation.CloudGroupSpawner;
import net.Gabou.projectatmosphere.clouds.simulation.CloudRegionManager;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import net.Gabou.projectatmosphere.command.tree.util.PaCommandMessages;
import net.Gabou.projectatmosphere.command.tree.util.PaCommandSupport;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.Gabou.projectatmosphere.modules.temperature.command.TemperatureCommandHelper;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CommandCloudService {
    private static final Map<String, String> NATIVE_CLOUD_ALIASES = Map.ofEntries(
            Map.entry("cumulus", "cumulus_humilis"),
            Map.entry("small_cumulus", "cumulus_humilis"),
            Map.entry("stratus", "stratus_nebulosus"),
            Map.entry("heavy_stratus", "stratus_nebulosus"),
            Map.entry("overcast", "nimbostratus"),
            Map.entry("dense_stratocumulus", "stratocumulus"),
            Map.entry("thicker_stratocumulus", "stratocumulus"),
            Map.entry("cumulonimbus", "cumulonimbus_calvus"),
            Map.entry("simpleclouds:cumulonimbus", "cumulonimbus_calvus"),
            Map.entry("severe_cumulonimbus", "cumulonimbus_capillatus"),
            Map.entry("custom_cumulonimbus", "cumulonimbus_calvus"),
            Map.entry("dark_wall", "cumulonimbus_capillatus"),
            Map.entry("cookie", "cumulonimbus_calvus"),
            Map.entry("tsegrus", "cumulonimbus_calvus"),
            Map.entry("dense_tsegrus", "cumulonimbus_capillatus"),
            Map.entry("stronger_stratus", "nimbostratus"),
            Map.entry("severe_nimbostratus", "nimbostratus")
    );

    private CommandCloudService() {
    }

    public static int spawnCloud(CommandSourceStack source, String cloudId) {
        ServerPlayer player = PaCommandSupport.requirePlayer(source, "Cloud spawning is only available to players.");
        if (player == null) {
            return 0;
        }

        ServerLevel level = player.serverLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            source.sendFailure(Component.literal("Cloud spawning is only available in the Overworld."));
            return 0;
        }

        BlockPos pos = player.blockPosition();
        RegionInstanceKey regionKey = RegionInstanceKey.from(pos);
        WindVectorApi.WindSample sample = WindVectorApi.getOrFallback(regionKey, level.getGameTime());
        net.Gabou.projectatmosphere.modules.core.WindVector wind =
                net.Gabou.projectatmosphere.modules.core.WindVector.fromBase(
                        sample.speedMps(),
                        (float) Math.toRadians(sample.directionDeg())
                );

        if (AtmosphereCloudServices.isSimpleCloudsLoaded()) {
            CloudRegion region = SimpleCloudsCompat.spawnCloudInRegion(cloudId, regionKey, level, null, wind);
            if (region == null) {
                source.sendFailure(Component.literal("Failed to create Simple Clouds cloud '" + cloudId + "'."));
                return 0;
            }
            CloudRegionSyncManager.syncPlayer(player);
            PaCommandMessages.success(
                    source,
                    true,
                    "Cloud spawned",
                    "Cloud: " + cloudId,
                    "Region: " + regionKey,
                    "Wind: " + PaCommandSupport.formatWind(wind),
                    "Result: Simple Clouds region created"
            );
            return 1;
        }

        CloudRegionState state = spawnNativeCloud(level, pos, cloudId);
        if (state == null) {
            source.sendFailure(Component.literal("Failed to create native PA cloud '" + cloudId + "'."));
            return 0;
        }

        CloudRegionSyncManager.syncPlayer(player);
        PaCommandMessages.success(
                source,
                true,
                "Cloud spawned",
                "Requested: " + cloudId,
                "Resolved: " + state.getCloudTypeId(),
                "Region: " + regionKey,
                "Wind: " + PaCommandSupport.formatWind(wind),
                "Result: native PA region created"
        );
        return 1;
    }

    public static int spawnRain(CommandSourceStack source, int intensity) {
        String cloudId = AtmosphereCloudServices.isSimpleCloudsLoaded()
                ? CloudLibrary.getRandomRainCloud(intensity, true)
                : CloudTypeRegistry.getRandomRainCloud(intensity);
        return spawnCloud(source, cloudId);
    }

    public static int spawnThunder(CommandSourceStack source, int intensity) {
        String cloudId = AtmosphereCloudServices.isSimpleCloudsLoaded()
                ? CloudLibrary.getRandomThunderCloud(intensity)
                : CloudTypeRegistry.getRandomThunderCloud(intensity);
        return spawnCloud(source, cloudId);
    }

    public static int spawnSnowstorm(CommandSourceStack source, boolean overwrite) {
        ServerPlayer player = PaCommandSupport.requirePlayer(source, "Snowstorm spawning is only available to players.");
        if (player == null) {
            return 0;
        }

        ServerLevel level = player.serverLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            source.sendFailure(Component.literal("Snowstorm clouds can only be spawned in the Overworld."));
            return 0;
        }
        if (net.Gabou.projectatmosphere.seasons.SeasonTimeHelper.stage(level) != net.Gabou.projectatmosphere.seasons.SeasonStage.WINTER && !overwrite) {
            source.sendFailure(Component.literal("It is not winter. Use overwrite to force the spawn."));
            return 0;
        }

        String cloudId = AtmosphereCloudServices.isSimpleCloudsLoaded()
                ? CloudLibrary.getSnowstormCloudId()
                : "nimbostratus";
        return spawnCloud(source, cloudId);
    }

    public static int sendCloudCount(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        int storedCount = CloudRegionManager.getInstance().getCloudRegionCount(level);
        int activeRenderDataCount = CloudRegionManager.getInstance().getActiveRenderData(level).size();
        PaCommandMessages.success(
                source,
                false,
                "Cloud count",
                "Stored: " + storedCount,
                "Active render data: " + activeRenderDataCount
        );
        return 1;
    }

    public static int sendCloudList(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        List<String> lines = CloudRegionManager.getInstance().describeCloudRegions(level);
        if (lines.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[Project Atmosphere]\nAction: Cloud list\nResult: no saved cloud regions"), false);
            return 1;
        }

        StringBuilder message = new StringBuilder("[Project Atmosphere]\nAction: Cloud list");
        message.append("\nSaved regions: ").append(lines.size());
        int limit = Math.min(lines.size(), 8);
        for (int i = 0; i < limit; i++) {
            message.append("\n").append(i + 1).append(". ").append(lines.get(i));
        }
        if (lines.size() > limit) {
            message.append("\n... ").append(lines.size() - limit).append(" more");
        }
        source.sendSuccess(() -> Component.literal(message.toString()), false);
        return 1;
    }

    public static int clearClouds(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        CloudRegionManager.getInstance().clearCloudRegions(level);
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            CloudRegionSyncManager.syncPlayer(player);
        }
        PaCommandMessages.success(source, true, "Cloud regions cleared");
        return 1;
    }

    public static int clearInactiveClouds(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        int removed = CloudRegionManager.getInstance().clearInactiveCloudRegions(level);
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            CloudRegionSyncManager.syncPlayer(player);
        }
        PaCommandMessages.success(
                source,
                true,
                "Inactive cloud regions cleared",
                "Removed: " + removed
        );
        return 1;
    }

    public static int syncClouds(CommandSourceStack source) {
        ServerPlayer player = PaCommandSupport.requirePlayer(source, "Cloud sync is only available to players.");
        if (player == null) {
            return 0;
        }
        CloudRegionSyncManager.syncPlayer(player);
        PaCommandMessages.success(source, false, "Cloud sync sent");
        return 1;
    }

    public static int setCloudMovementFrozen(CommandSourceStack source, boolean frozen) {
        AtmoCommonConfig.FREEZE_CLOUD_MOVEMENT.set(frozen);
        try {
            saveCommonConfigForMod(ProjectAtmosphere.MODID);
        } catch (Exception exception) {
            ProjectAtmosphere.LOGGER.warn("Failed to save cloud movement freeze setting", exception);
        }

        PaCommandMessages.success(
                source,
                true,
                "Cloud movement " + (frozen ? "frozen" : "unfrozen"),
                "Native PA cloud drift is now " + (frozen ? "paused" : "active")
        );
        return 1;
    }

    private static void saveCommonConfigForMod(String modId) {
        var set = ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.COMMON);
        if (set == null) {
            return;
        }
        for (ModConfig config : set) {
            if (config.getModId().equals(modId)) {
                config.save();
                return;
            }
        }
    }

    public static boolean spawnWeatherCloudAtSource(CommandSourceStack source, String cloudId) {
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        RegionInstanceKey regionKey = RegionInstanceKey.from(pos);
        net.Gabou.projectatmosphere.modules.core.WindVector wind =
                net.Gabou.projectatmosphere.modules.core.WindVector.fromBase(1.0F, 0.0F);

        if (AtmosphereCloudServices.isSimpleCloudsLoaded()) {
            CloudRegion region = SimpleCloudsCompat.spawnCloudInRegion(cloudId, regionKey, level, null, wind);
            if (region == null) {
                return false;
            }
        } else if (spawnNativeCloud(level, pos, cloudId) == null) {
            return false;
        }

        ServerPlayer player = source.getPlayer();
        if (player != null) {
            CloudRegionSyncManager.syncPlayer(player);
        }
        return true;
    }

    public static String resolveNativeCloudTypeId(String requestedId) {
        if (requestedId == null || requestedId.isBlank()) {
            return CloudTypeRegistry.DEFAULT_CLOUD_TYPE_ID;
        }

        String normalized = requestedId.trim().toLowerCase(Locale.ROOT);
        String alias = NATIVE_CLOUD_ALIASES.get(normalized);
        if (alias != null) {
            return alias;
        }

        String path = normalized;
        int separator = normalized.indexOf(':');
        if (separator >= 0 && separator + 1 < normalized.length()) {
            path = normalized.substring(separator + 1);
            alias = NATIVE_CLOUD_ALIASES.get(path);
            if (alias != null) {
                return alias;
            }
        }

        return CloudTypeRegistry.getOrDefault(path).getId();
    }

    private static CloudRegionState spawnNativeCloud(ServerLevel level, BlockPos pos, String cloudId) {
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return null;
        }

        String cloudTypeId = resolveNativeCloudTypeId(cloudId);
        return CloudGroupSpawner.spawnRequestedCloud(level, pos, cloudTypeId);
    }

}
