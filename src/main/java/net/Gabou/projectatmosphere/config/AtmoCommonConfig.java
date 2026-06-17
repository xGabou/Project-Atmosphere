package net.Gabou.projectatmosphere.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class AtmoCommonConfig {
    public static final ModConfigSpec COMMON_SPEC;

    public static final ModConfigSpec.IntValue CLOUD_RENDER_DISTANCE;
    public static final ModConfigSpec.BooleanValue FORCE_SHARED_EXECUTOR;

    public static final ModConfigSpec.BooleanValue ENABLE_TORNADOES;
    public static final ModConfigSpec.BooleanValue ENABLE_STORM_DEBRIS;
    public static final ModConfigSpec.IntValue MAX_STORM_DEBRIS_PER_CHUNK;
    public static final ModConfigSpec.BooleanValue AUTO_REPAIR_GLASS;
    public static final ModConfigSpec.BooleanValue DAMAGE_GLASS_ON_TORNADO;

    public static final ModConfigSpec.DoubleValue TORNADO_CHECK_INTERVAL_SEC;
    public static final ModConfigSpec.DoubleValue TORNADO_BASE_SPAWN_RADIUS_M;
    public static final ModConfigSpec.DoubleValue TORNADO_MIN_TEMP_CONTRAST_C;
    public static final ModConfigSpec.DoubleValue TORNADO_HUMIDITY_MIN_PERCENT;
    public static final ModConfigSpec.DoubleValue TORNADO_PRESSURE_GRADIENT_GAIN;
    public static final ModConfigSpec.DoubleValue TORNADO_PRESSURE_GRADIENT_CAP;
    public static final ModConfigSpec.DoubleValue TORNADO_SHEAR_MIN_SPEED_DIFF_MPS;
    public static final ModConfigSpec.DoubleValue TORNADO_SHEAR_MIN_DIR_DIFF_DEG;
    public static final ModConfigSpec.DoubleValue TORNADO_STORM_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue TORNADO_RISK_MIN_TO_CONSIDER;
    public static final ModConfigSpec.DoubleValue TORNADO_BASE_TRIGGER_CHANCE;
    public static final ModConfigSpec.DoubleValue TORNADO_LAPSE_RATE_C_PER_100M;
    public static final ModConfigSpec.DoubleValue TORNADO_ALOFT_DELTA_H_M;
    public static final ModConfigSpec.DoubleValue TORNADO_INTENSITY_MIN;
    public static final ModConfigSpec.DoubleValue TORNADO_INTENSITY_MAX;
    public static final ModConfigSpec.IntValue TORNADO_CELL_COOLDOWN_MINUTES;

    public static final ModConfigSpec.BooleanValue DISPLAY_UNITS_IMPERIAL;
    public static final ModConfigSpec.DoubleValue STORM_SEVERITY_BOOSTER;

    public static final ModConfigSpec.DoubleValue WIND_BASE_RETARGET_SEC;
    public static final ModConfigSpec.DoubleValue WIND_DIR_RETARGET_SEC;
    public static final ModConfigSpec.DoubleValue WIND_GUST_MEAN_SEC;
    public static final ModConfigSpec.DoubleValue WIND_GUST_DECAY_MPS;
    public static final ModConfigSpec.DoubleValue WIND_STORM_GUST_MULT;
    public static final ModConfigSpec.DoubleValue WIND_PUSH_THRESHOLD_MPS;
    public static final ModConfigSpec.DoubleValue WIND_PUSH_RAMP_MPS;
    public static final ModConfigSpec.DoubleValue WIND_PLAYER_PUSH_SCALE;
    public static final ModConfigSpec.DoubleValue WIND_ENTITY_PUSH_SCALE;
    public static final ModConfigSpec.DoubleValue WIND_PARTICLE_BEND_STRENGTH;

    public static final ModConfigSpec.DoubleValue PLAYER_WIND_THRESHOLD_MPS;
    public static final ModConfigSpec.DoubleValue PLAYER_MAX_GUST_BPT;
    public static final ModConfigSpec.DoubleValue PLAYER_GUST_CHANCE_SCALE;
    public static final ModConfigSpec.DoubleValue PLAYER_GUST_CHANCE_DIVIDER;
    public static final ModConfigSpec.DoubleValue PLAYER_GUST_STRENGTH_SCALE;
    public static final ModConfigSpec.IntValue PLAYER_GUST_DURATION_MIN;
    public static final ModConfigSpec.IntValue PLAYER_GUST_DURATION_MAX;
    public static final ModConfigSpec.DoubleValue PLAYER_GUST_ANGLE_VARIANCE_DEG;
    public static final ModConfigSpec.DoubleValue PLAYER_GUST_EXTREME_THRESHOLD_MPS;
    public static final ModConfigSpec.DoubleValue PLAYER_GUST_EXTREME_CHANCE_MULT;
    public static final ModConfigSpec.DoubleValue PLAYER_GUST_EXTREME_STRENGTH_MULT;

    public static final ModConfigSpec.BooleanValue WORLD_EFFECTS_ENABLED;
    public static final ModConfigSpec.IntValue WORLD_EFFECT_SAMPLES_PER_PLAYER;
    public static final ModConfigSpec.IntValue WORLD_EFFECT_SAMPLE_RADIUS;
    public static final ModConfigSpec.DoubleValue CLOUD_BURN_PREVENT_THRESHOLD;
    public static final ModConfigSpec.DoubleValue CLOUD_FIRE_DAMP_THRESHOLD;
    public static final ModConfigSpec.IntValue CLOUD_FIRE_DAMP_TICKS;
    public static final ModConfigSpec.DoubleValue FIRE_EXTINGUISH_BASE_CHANCE;
    public static final ModConfigSpec.DoubleValue CAULDRON_FILL_BASE_CHANCE;

    public enum CloudMode {
        FULL,
        HYBRID,
        VANILLA,
        NATIVE,
        SIMPLE_CLOUDS
    }

    public enum DimensionFilterMode {
        WHITELIST,
        BLACKLIST
    }

    public static final ModConfigSpec.EnumValue<CloudMode> CLOUD_MODE;
    public static final ModConfigSpec.EnumValue<DimensionFilterMode> CLOUD_DIMENSION_FILTER_MODE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CLOUD_DIMENSION_IDS;
    public static final ModConfigSpec.IntValue NATIVE_CLOUD_SPAWN_HEIGHT;
    public static final ModConfigSpec.BooleanValue ENABLE_CLOUD_MOVEMENT;
    public static final ModConfigSpec.BooleanValue FREEZE_CLOUD_MOVEMENT;
    public static final ModConfigSpec.DoubleValue CLOUD_WIND_DRIFT_SCALE;
    public static final ModConfigSpec.BooleanValue FOG_ENABLED;
    public static final ModConfigSpec.IntValue FOG_SYNC_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue FOG_HUMIDITY_START_PERCENT;
    public static final ModConfigSpec.DoubleValue FOG_HUMIDITY_FULL_PERCENT;
    public static final ModConfigSpec.DoubleValue FOG_WET_BIOME_BASE_STRENGTH;
    public static final ModConfigSpec.DoubleValue FOG_RAIN_BOOST;
    public static final ModConfigSpec.DoubleValue FOG_NEAR_DISTANCE;
    public static final ModConfigSpec.DoubleValue FOG_FAR_DISTANCE;
    public static final ModConfigSpec.DoubleValue FOG_COLOR_BLEND;
    public static final ModConfigSpec.DoubleValue FOG_WET_BIOME_DOWNFALL_MIN;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FOG_WET_BIOME_IDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FOG_WET_BIOME_KEYWORDS;
    public static final ModConfigSpec.BooleanValue AUTH_STRICT_OFFLINE_UUID_REJECT;
    public static final ModConfigSpec.BooleanValue AUTH_KICK_ON_FAILURE;
    public static final ModConfigSpec.IntValue AUTH_CHALLENGE_TIMEOUT_TICKS;
    public static final ModConfigSpec.BooleanValue EVENTS_ENABLED;

    public static final ModConfigSpec.BooleanValue TELEMETRY_ENABLED;
    public static final ModConfigSpec.IntValue TELEMETRY_RETENTION_DAYS;

    public static final ModConfigSpec.BooleanValue DEBUG_MODE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("performance");
        FORCE_SHARED_EXECUTOR = builder
                .comment("Force use of shared executor for all async tasks, regardless of CPU count")
                .define("forceSharedExecutor", false);
        CLOUD_RENDER_DISTANCE = builder
                .comment("Maximum distance in blocks to render clouds; higher values impact performance")
                .defineInRange("cloudRenderDistance", 2000, 100, Integer.MAX_VALUE);
        builder.pop();

        builder.push("display");
        DISPLAY_UNITS_IMPERIAL = builder
                .comment("Display values in imperial units (F, mph, inHg) instead of metric (C, m/s, hPa)")
                .define("imperialUnits", false);
        builder.pop();

        builder.push("storms");
        STORM_SEVERITY_BOOSTER = builder
                .comment("Global multiplier for storm severity (affects wind speed and precipitation intensity)")
                .defineInRange("stormSeverityBooster", 3.2D, 0.5D, 8.0D);
        EVENTS_ENABLED = builder
                .comment("Enable storm and weather event spawning/processing")
                .define("eventsEnabled", true);
        ENABLE_TORNADOES = builder
                .comment("Enable tornado spawning and commands")
                .define("enableTornadoes", true);
        ENABLE_STORM_DEBRIS = builder
                .comment("Enable random debris spawning during storms")
                .define("enableStormDebris", false);
        MAX_STORM_DEBRIS_PER_CHUNK = builder
                .comment("Maximum number of storm debris items allowed per chunk")
                .defineInRange("maxStormDebrisPerChunk", 10, 0, Integer.MAX_VALUE);
        AUTO_REPAIR_GLASS = builder
                .comment("Automatically repair tornado-damaged glass after 5 minutes of no new damage")
                .define("autoRepairGlass", true);
        DAMAGE_GLASS_ON_TORNADO = builder
                .comment("Enable glass damage when a tornado passes over it")
                .define("damageGlassOnTornado", true);

        builder.push("tornado");
        TORNADO_CHECK_INTERVAL_SEC = builder
                .comment("Seconds between tornado spawn checks")
                .defineInRange("checkIntervalSec", 60d, 1d, 3600d);
        TORNADO_BASE_SPAWN_RADIUS_M = builder
                .comment("Base radius around a biome center to spawn a tornado")
                .defineInRange("baseSpawnRadiusM", 64d, 1d, 512d);
        TORNADO_MIN_TEMP_CONTRAST_C = builder
                .comment("Minimum surface vs aloft temperature contrast for risk")
                .defineInRange("minTempContrastC", 6d, 0d, 100d);
        TORNADO_HUMIDITY_MIN_PERCENT = builder
                .comment("Minimum humidity percentage for risk")
                .defineInRange("humidityMinPercent", 65d, 0d, 100d);
        TORNADO_PRESSURE_GRADIENT_GAIN = builder
                .comment("Multiplier for pressure gradient contribution")
                .defineInRange("pressureGradientGain", 10d, 0d, 100d);
        TORNADO_PRESSURE_GRADIENT_CAP = builder
                .comment("Maximum pressure gradient contribution")
                .defineInRange("pressureGradientCap", 3d, 0d, 100d);
        TORNADO_SHEAR_MIN_SPEED_DIFF_MPS = builder
                .comment("Minimum wind speed difference for shear")
                .defineInRange("shearMinSpeedDiffMps", 5d, 0d, 100d);
        TORNADO_SHEAR_MIN_DIR_DIFF_DEG = builder
                .comment("Minimum wind direction difference for shear")
                .defineInRange("shearMinDirDiffDeg", 45d, 0d, 360d);
        TORNADO_STORM_MULTIPLIER = builder
                .comment("Risk multiplier during storms")
                .defineInRange("stormMultiplier", 1.5d, 0d, 10d);
        TORNADO_RISK_MIN_TO_CONSIDER = builder
                .comment("Minimum computed risk to consider spawning")
                .defineInRange("riskMinToConsider", 4d, 0d, 100d);
        TORNADO_BASE_TRIGGER_CHANCE = builder
                .comment("Base chance per risk point to trigger a tornado")
                .defineInRange("baseTriggerChance", 0.05d, 0d, 1d);
        TORNADO_LAPSE_RATE_C_PER_100M = builder
                .comment("Temperature lapse rate per 100m for aloft proxy")
                .defineInRange("lapseRateCPer100m", 0.65d, 0d, 10d);
        TORNADO_ALOFT_DELTA_H_M = builder
                .comment("Height difference in meters for aloft temperature proxy")
                .defineInRange("aloftDeltaHM", 1500d, 0d, 10000d);
        TORNADO_INTENSITY_MIN = builder
                .comment("Minimum tornado intensity")
                .defineInRange("intensityMin", 0.4d, 0d, 1d);
        TORNADO_INTENSITY_MAX = builder
                .comment("Maximum tornado intensity")
                .defineInRange("intensityMax", 1d, 0d, 1d);
        TORNADO_CELL_COOLDOWN_MINUTES = builder
                .comment("Cooldown in minutes before a cell can spawn another tornado")
                .defineInRange("cellCooldownMinutes", 20, 0, Integer.MAX_VALUE);
        builder.pop();

        builder.pop(); // storms

        builder.push("wind");
        WIND_BASE_RETARGET_SEC = builder
                .comment("Seconds between base wind retarget")
                .defineInRange("baseRetargetSec", 60d, 1d, 600d);
        WIND_DIR_RETARGET_SEC = builder
                .comment("Seconds between direction retarget")
                .defineInRange("dirRetargetSec", 90d, 1d, 600d);
        WIND_GUST_MEAN_SEC = builder
                .comment("Average gust duration in seconds")
                .defineInRange("gustMeanSec", 15d, 1d, 600d);
        WIND_GUST_DECAY_MPS = builder
                .comment("Gust decay speed in m/s per second")
                .defineInRange("gustDecayMps", 1.0d, 0d, 100d);
        WIND_STORM_GUST_MULT = builder
                .comment("Multiplier for gust speed during storms")
                .defineInRange("stormGustMult", 2.0d, 0d, 10d);
        WIND_PUSH_THRESHOLD_MPS = builder
                .comment("Minimum wind speed to push entities")
                .defineInRange("pushThresholdMps", 6.0d, 0d, 100d);
        WIND_PUSH_RAMP_MPS = builder
                .comment("Wind speed ramp around the threshold for smoothing (m/s)")
                .defineInRange("pushRampMps", 5.0d, 0d, 100d);
        WIND_PLAYER_PUSH_SCALE = builder
                .comment("Push scale applied to players")
                .defineInRange("playerPushScale", 0.013333333333d, 0d, 1d);
        WIND_ENTITY_PUSH_SCALE = builder
                .comment("Push scale applied to other entities")
                .defineInRange("entityPushScale", 0.01d, 0d, 1d);
        WIND_PARTICLE_BEND_STRENGTH = builder
                .comment("Blend strength for steering wind-bent particles per tick")
                .defineInRange("particleBendStrength", 0.08d, 0d, 1d);

        PLAYER_WIND_THRESHOLD_MPS = builder
                .comment("Minimum base wind speed (m/s) before player gusts can occur")
                .defineInRange("playerWindThresholdMps", 11.1d, 0d, 100d);
        PLAYER_MAX_GUST_BPT = builder
                .comment("Maximum per-tick gust impulse for players (blocks per tick)")
                .defineInRange("playerMaxGustBpt", 0.002d, 0d, 0.2d);
        PLAYER_GUST_CHANCE_SCALE = builder
                .comment("Scale for gust chance once wind exceeds the threshold")
                .defineInRange("playerGustChanceScale", 0.03d, 0d, 1d);
        PLAYER_GUST_CHANCE_DIVIDER = builder
                .comment("Divider for excess wind when computing gust chance (m/s)")
                .defineInRange("playerGustChanceDivider", 15d, 1d, 100d);
        PLAYER_GUST_STRENGTH_SCALE = builder
                .comment("Strength scale applied to excess wind for player gusts")
                .defineInRange("playerGustStrengthScale", 0.006666666667d, 0d, 1d);
        PLAYER_GUST_DURATION_MIN = builder
                .comment("Minimum gust duration for players (ticks)")
                .defineInRange("playerGustDurationMin", 10, 1, 200);
        PLAYER_GUST_DURATION_MAX = builder
                .comment("Maximum gust duration for players (ticks)")
                .defineInRange("playerGustDurationMax", 40, 1, 200);
        PLAYER_GUST_ANGLE_VARIANCE_DEG = builder
                .comment("Angle variance applied to gust direction (degrees)")
                .defineInRange("playerGustAngleVarianceDeg", 10d, 0d, 45d);
        PLAYER_GUST_EXTREME_THRESHOLD_MPS = builder
                .comment("Wind speed at which extreme gust multipliers kick in (m/s)")
                .defineInRange("playerGustExtremeThresholdMps", 30d, 0d, 100d);
        PLAYER_GUST_EXTREME_CHANCE_MULT = builder
                .comment("Multiplier applied to gust chance in extreme winds")
                .defineInRange("playerGustExtremeChanceMult", 2.5d, 1d, 10d);
        PLAYER_GUST_EXTREME_STRENGTH_MULT = builder
                .comment("Multiplier applied to max gust strength in extreme winds")
                .defineInRange("playerGustExtremeStrengthMult", 2.0d, 1d, 10d);
        builder.pop();

        builder.push("worldEffects");
        WORLD_EFFECTS_ENABLED = builder
                .comment("Enable Project Atmosphere world effects (cloud burn suppression, rain extinguish, cauldron filling)")
                .define("enabled", true);
        WORLD_EFFECT_SAMPLES_PER_PLAYER = builder
                .comment("Random sample count per player per tick for weather world effects")
                .defineInRange("samplesPerPlayerPerTick", 12, 0, 128);
        WORLD_EFFECT_SAMPLE_RADIUS = builder
                .comment("Sampling radius in blocks around each player")
                .defineInRange("sampleRadiusBlocks", 64, 8, 512);
        CLOUD_BURN_PREVENT_THRESHOLD = builder
                .comment("Cloud cover threshold above which sun-burning mobs stop igniting")
                .defineInRange("cloudBurnPreventionThreshold", 0.75d, 0d, 1d);
        CLOUD_FIRE_DAMP_THRESHOLD = builder
                .comment("Cloud cover threshold above which burning mobs cool down faster")
                .defineInRange("cloudFireDampThreshold", 0.90d, 0d, 1d);
        CLOUD_FIRE_DAMP_TICKS = builder
                .comment("Extra fire ticks removed per tick when clouds are very dense")
                .defineInRange("cloudFireDampTicks", 4, 0, 200);
        FIRE_EXTINGUISH_BASE_CHANCE = builder
                .comment("Base chance per sample to extinguish fire/campfires when raining (scaled by rain intensity)")
                .defineInRange("fireExtinguishBaseChance", 0.12d, 0d, 1d);
        CAULDRON_FILL_BASE_CHANCE = builder
                .comment("Base chance per sample to fill cauldrons when raining (scaled by rain intensity)")
                .defineInRange("cauldronFillBaseChance", 0.06d, 0d, 1d);
        builder.pop();

        builder.push("clouds");
        CLOUD_MODE = builder
                .comment("Select how Project Atmosphere cloud rendering should behave")
                .defineEnum("cloudMode", CloudMode.FULL);
        CLOUD_DIMENSION_FILTER_MODE = builder
                .comment("Controls whether cloudDimensionIds is used as an allow-list or deny-list for PA cloud/weather ownership")
                .defineEnum("cloudDimensionFilterMode", DimensionFilterMode.WHITELIST);
        CLOUD_DIMENSION_IDS = builder
                .comment("Dimension ids used by cloudDimensionFilterMode. Default allows PA clouds/weather only in the Overworld.")
                .defineListAllowEmpty("cloudDimensionIds",
                        List.of("minecraft:overworld"),
                        value -> value instanceof String);
        NATIVE_CLOUD_SPAWN_HEIGHT = builder
                .comment("Fixed Y height used by native Project Atmosphere cloud spawning")
                .defineInRange("nativeCloudSpawnHeight", 256, -2048, 4096);
        ENABLE_CLOUD_MOVEMENT = builder
                .comment("Enable native Project Atmosphere cloud drift from regional wind")
                .define("enableCloudMovement", true);
        FREEZE_CLOUD_MOVEMENT = builder
                .comment("Freeze native Project Atmosphere cloud movement for debugging and screenshots")
                .define("freezeCloudMovement", false);
        CLOUD_WIND_DRIFT_SCALE = builder
                .comment("Blocks-per-tick drift multiplier applied to wind speed in m/s for native PA clouds")
                .defineInRange("cloudWindDriftScale", 0.035d, 0.0d, 1.0d);
        builder.pop();

        builder.push("fog");
        FOG_ENABLED = builder
                .comment("Enable humidity-driven dynamic fog rendering on the client")
                .define("enabled", true);
        FOG_SYNC_INTERVAL_TICKS = builder
                .comment("Ticks between lightweight server->client atmosphere sync updates used by fog and sky-effect compatibility sampling")
                .defineInRange("syncIntervalTicks", 20, 1, 200);
        FOG_HUMIDITY_START_PERCENT = builder
                .comment("Humidity percentage where dynamic fog starts to form")
                .defineInRange("humidityStartPercent", 72d, 0d, 100d);
        FOG_HUMIDITY_FULL_PERCENT = builder
                .comment("Humidity percentage where the humidity contribution reaches full strength")
                .defineInRange("humidityFullPercent", 96d, 0d, 100d);
        FOG_WET_BIOME_BASE_STRENGTH = builder
                .comment("Base fog strength added by moisture-heavy biomes such as swamps or rainforests")
                .defineInRange("wetBiomeBaseStrength", 0.18d, 0d, 1d);
        FOG_RAIN_BOOST = builder
                .comment("Additional fog strength contributed by active rain intensity")
                .defineInRange("rainBoost", 0.22d, 0d, 1d);
        FOG_NEAR_DISTANCE = builder
                .comment("Near fog plane used when dynamic fog is fully saturated")
                .defineInRange("nearDistance", 0.5d, 0d, 64d);
        FOG_FAR_DISTANCE = builder
                .comment("Far fog plane used when dynamic fog is fully saturated")
                .defineInRange("farDistance", 72d, 4d, 512d);
        FOG_COLOR_BLEND = builder
                .comment("Color tint blend applied by dynamic fog")
                .defineInRange("colorBlend", 0.45d, 0d, 1d);
        FOG_WET_BIOME_DOWNFALL_MIN = builder
                .comment("Biome downfall value that starts contributing wet-biome fog weighting")
                .defineInRange("wetBiomeDownfallMin", 0.75d, 0d, 1d);
        FOG_WET_BIOME_IDS = builder
                .comment("Explicit biome ids that should always count as moisture-heavy for fog")
                .defineListAllowEmpty("wetBiomeIds",
                        List.of("minecraft:swamp", "minecraft:mangrove_swamp"),
                        value -> value instanceof String);
        FOG_WET_BIOME_KEYWORDS = builder
                .comment("Biome path keywords that should count as moisture-heavy for fog")
                .defineListAllowEmpty("wetBiomeKeywords",
                        List.of("swamp", "marsh", "bog", "fen", "rainforest", "jungle", "mangrove", "wetland", "bayou"),
                        value -> value instanceof String);
        builder.pop();

        builder.push("telemetry");
        TELEMETRY_ENABLED = builder
                .comment("Enable lightweight telemetry collection for diagnostics")
                .define("enableTelemetry", true);
        TELEMETRY_RETENTION_DAYS = builder
                .comment("Number of days to retain exported telemetry archives before pruning")
                .defineInRange("telemetryRetentionDays", 14, 0, 365);
        builder.pop();

        builder.push("auth");
        AUTH_STRICT_OFFLINE_UUID_REJECT = builder
                .comment("Reject offline UUID v3 identities during the launcher auth check")
                .define("strictOfflineUuidReject", true);
        AUTH_KICK_ON_FAILURE = builder
                .comment("Kick players who fail or timeout the launcher auth challenge")
                .define("kickOnFailure", true);
        AUTH_CHALLENGE_TIMEOUT_TICKS = builder
                .comment("Ticks before a pending launcher auth challenge times out")
                .defineInRange("challengeTimeoutTicks", 200, 1, Integer.MAX_VALUE);
        builder.pop();

        builder.push("debug");
        DEBUG_MODE = builder
                .comment("Enable debug mode for verbose logging and diagnostics")
                .define("debugMode", false);
        builder.pop();

        COMMON_SPEC = builder.build();
    }

    private AtmoCommonConfig() { }
}

