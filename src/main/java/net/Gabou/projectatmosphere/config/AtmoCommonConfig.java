package net.Gabou.projectatmosphere.config;
import net.minecraftforge.common.ForgeConfigSpec;
import java.util.List;
public class AtmoCommonConfig {
    public enum CloudMode {
        FULL,
        HYBRID,
        VANILLA;

        public CloudMode next() {
            CloudMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public enum CloudRaymarchQuality {
        LOW(16, 0.50F),
        MEDIUM(32, 0.75F),
        HIGH(64, 1.00F);

        private final int raymarchSteps;
        private final float resolutionScale;

        CloudRaymarchQuality(int raymarchSteps, float resolutionScale) {
            this.raymarchSteps = raymarchSteps;
            this.resolutionScale = resolutionScale;
        }

        /**
         * Retourne le nombre d'etapes de raymarch associe a cette qualite.
         *
         * @return nombre d'etapes shader
         */
        public int getRaymarchSteps() {
            return raymarchSteps;
        }

        public float getResolutionScale() {
            return resolutionScale;
        }

        /**
         * Retourne la qualite suivante pour les boutons cyclables.
         *
         * @return prochaine qualite
         */
        public CloudRaymarchQuality next() {
            CloudRaymarchQuality[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public enum CloudDiagnosticsOverlayMode {
        OFF,
        BASIC,
        FULL;

        public CloudDiagnosticsOverlayMode next() {
            CloudDiagnosticsOverlayMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public static final ForgeConfigSpec.IntValue CLOUD_RENDER_DISTANCE;
    public static final ForgeConfigSpec.EnumValue<CloudRaymarchQuality> CLOUD_RAYMARCH_QUALITY;
    public static final ForgeConfigSpec.IntValue NATIVE_CLOUD_SPAWN_HEIGHT;
    public static final ForgeConfigSpec.BooleanValue FORCE_SHARED_EXECUTOR;
    public static final ForgeConfigSpec.BooleanValue DISPLAY_UNITS_IMPERIAL;
    public static final ForgeConfigSpec.EnumValue<CloudMode> CLOUD_MODE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_TORNADOES;
    public static final ForgeConfigSpec.BooleanValue ENABLE_TORNADO_DESTRUCTION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_STORM_DEBRIS;
    public static final ForgeConfigSpec.BooleanValue EVENTS_ENABLED;
    public static final ForgeConfigSpec.IntValue MAX_STORM_DEBRIS_PER_CHUNK;
    public static final ForgeConfigSpec.BooleanValue AUTO_REPAIR_GLASS;
    public static final ForgeConfigSpec.BooleanValue DAMAGE_GLASS_ON_TORNADO;
    public static final ForgeConfigSpec.BooleanValue ENABLE_HURRICANE_DESTRUCTION;
    public static final ForgeConfigSpec.DoubleValue HURRICANE_DESTRUCTION_STRENGTH;
    public static final ForgeConfigSpec.BooleanValue HURRICANE_DROP_BROKEN_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue HURRICANE_DAMAGE_TREES;
    public static final ForgeConfigSpec.DoubleValue TORNADO_CHECK_INTERVAL_SEC;
    public static final ForgeConfigSpec.DoubleValue TORNADO_BASE_SPAWN_RADIUS_M;
    public static final ForgeConfigSpec.DoubleValue TORNADO_MIN_TEMP_CONTRAST_C;
    public static final ForgeConfigSpec.DoubleValue TORNADO_HUMIDITY_MIN_PERCENT;
    public static final ForgeConfigSpec.DoubleValue TORNADO_PRESSURE_GRADIENT_GAIN;
    public static final ForgeConfigSpec.DoubleValue TORNADO_PRESSURE_GRADIENT_CAP;
    public static final ForgeConfigSpec.DoubleValue TORNADO_SHEAR_MIN_SPEED_DIFF_MPS;
    public static final ForgeConfigSpec.DoubleValue TORNADO_SHEAR_MIN_DIR_DIFF_DEG;
    public static final ForgeConfigSpec.DoubleValue TORNADO_STORM_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue TORNADO_RISK_MIN_TO_CONSIDER;
    public static final ForgeConfigSpec.DoubleValue TORNADO_BASE_TRIGGER_CHANCE;
    public static final ForgeConfigSpec.DoubleValue TORNADO_LAPSE_RATE_C_PER_100M;
    public static final ForgeConfigSpec.DoubleValue TORNADO_ALOFT_DELTA_H_M;
    public static final ForgeConfigSpec.DoubleValue TORNADO_INTENSITY_MIN;
    public static final ForgeConfigSpec.DoubleValue TORNADO_INTENSITY_MAX;
    public static final ForgeConfigSpec.IntValue TORNADO_CELL_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.BooleanValue TORNADO_ALLOW_LEGACY_FALLBACK;
    public static final ForgeConfigSpec.BooleanValue DISABLE_SIMPLE_CLOUDS_TORNADO_SSBO;
    public static final ForgeConfigSpec.BooleanValue TORNADO_DEBUG_LOGGING;
    public static final ForgeConfigSpec.DoubleValue TORNADO_RENDER_QUALITY;
    public static final ForgeConfigSpec.DoubleValue TORNADO_RENDER_DOWNSAMPLE;

    public static final ForgeConfigSpec.DoubleValue STORM_SEVERITY_BOOSTER;

    public static final ForgeConfigSpec.BooleanValue DEBUG_MODE;
    public static final ForgeConfigSpec.EnumValue<CloudDiagnosticsOverlayMode> CLOUD_DIAGNOSTICS_OVERLAY;




    public static final ForgeConfigSpec.DoubleValue WIND_BASE_RETARGET_SEC;
    public static final ForgeConfigSpec.DoubleValue WIND_DIR_RETARGET_SEC;
    public static final ForgeConfigSpec.DoubleValue WIND_GUST_MEAN_SEC;
    public static final ForgeConfigSpec.DoubleValue WIND_GUST_DECAY_MPS;
    public static final ForgeConfigSpec.DoubleValue WIND_STORM_GUST_MULT;
    public static final ForgeConfigSpec.DoubleValue WIND_PUSH_THRESHOLD_MPS;
    public static final ForgeConfigSpec.DoubleValue WIND_PUSH_RAMP_MPS;
    public static final ForgeConfigSpec.DoubleValue WIND_PLAYER_PUSH_SCALE;
    public static final ForgeConfigSpec.DoubleValue WIND_ENTITY_PUSH_SCALE;
    public static final ForgeConfigSpec.DoubleValue WIND_PARTICLE_BEND_STRENGTH;
    public static final ForgeConfigSpec.DoubleValue PLAYER_WIND_THRESHOLD_MPS;
    public static final ForgeConfigSpec.DoubleValue PLAYER_MAX_GUST_BPT;
    public static final ForgeConfigSpec.DoubleValue PLAYER_GUST_CHANCE_SCALE;
    public static final ForgeConfigSpec.DoubleValue PLAYER_GUST_CHANCE_DIVIDER;
    public static final ForgeConfigSpec.DoubleValue PLAYER_GUST_STRENGTH_SCALE;
    public static final ForgeConfigSpec.IntValue PLAYER_GUST_DURATION_MIN;
    public static final ForgeConfigSpec.IntValue PLAYER_GUST_DURATION_MAX;
    public static final ForgeConfigSpec.DoubleValue PLAYER_GUST_ANGLE_VARIANCE_DEG;
    public static final ForgeConfigSpec.DoubleValue PLAYER_GUST_EXTREME_THRESHOLD_MPS;
    public static final ForgeConfigSpec.DoubleValue PLAYER_GUST_EXTREME_CHANCE_MULT;
    public static final ForgeConfigSpec.DoubleValue PLAYER_GUST_EXTREME_STRENGTH_MULT;
    public static final ForgeConfigSpec.IntValue WIND_LEAF_PARTICLE_RADIUS_BLOCKS;
    public static final ForgeConfigSpec.IntValue WIND_LEAF_PARTICLE_SCAN_UP;
    public static final ForgeConfigSpec.IntValue WIND_LEAF_PARTICLE_SCAN_DOWN;
    public static final ForgeConfigSpec.IntValue WIND_LEAF_PARTICLE_ATTEMPTS_PER_TICK;
    public static final ForgeConfigSpec.DoubleValue WIND_LEAF_PARTICLE_CHANCE_PER_CANDIDATE;
    public static final ForgeConfigSpec.IntValue WIND_LEAF_PARTICLE_MIN_FOLIAGE_NEIGHBORS;
    public static final ForgeConfigSpec.BooleanValue WIND_LEAF_PARTICLE_REQUIRE_LOG_BELOW;
    public static final ForgeConfigSpec.IntValue WIND_LEAF_PARTICLE_MAX_LOG_SEARCH_DEPTH;
    public static final ForgeConfigSpec.BooleanValue SEASONAL_TREES_ENABLED;
    public static final ForgeConfigSpec.BooleanValue SEASONAL_TREES_DYNAMIC_TREES_ENABLED;
    public static final ForgeConfigSpec.BooleanValue SEASONAL_TREES_VANILLA_ENABLED;
    public static final ForgeConfigSpec.DoubleValue SEASONAL_TREES_LEAF_DROP_DAYS;
    public static final ForgeConfigSpec.DoubleValue SEASONAL_TREES_LEAF_REGROW_DAYS;
    public static final ForgeConfigSpec.DoubleValue SEASONAL_TREES_TRANSITION_COOLDOWN_DAYS;
    public static final ForgeConfigSpec.DoubleValue SEASONAL_TREES_TRANSITION_OFFSET_DAYS;
    public static final ForgeConfigSpec.DoubleValue SEASONAL_TREES_SPREAD_CHANCE_PER_DAY;
    public static final ForgeConfigSpec.IntValue SEASONAL_TREES_SPREAD_RADIUS_BLOCKS;
    public static final ForgeConfigSpec.DoubleValue SEASONAL_TREES_VIGOR_REGEN_PER_DAY;
    public static final ForgeConfigSpec.DoubleValue SEASONAL_TREES_VIGOR_MIN_FOR_SPREAD;
    public static final ForgeConfigSpec.IntValue SEASONAL_TREES_MAX_ACTIVE_SEEDS;
    public static final ForgeConfigSpec.IntValue SEASONAL_TREES_SEED_LIFETIME_TICKS;
    public static final ForgeConfigSpec.DoubleValue SEASONAL_TREES_SEED_BASE_SPEED;
    public static final ForgeConfigSpec.BooleanValue SEASONAL_TREES_WIND_TRANSPORT_ENABLED;
    public static final ForgeConfigSpec.IntValue SEASONAL_TREES_BUDGET_PER_TICK;
    public static final ForgeConfigSpec.IntValue SEASONAL_TREES_SCAN_BUDGET_PER_TICK;
    public static final ForgeConfigSpec.BooleanValue WORLD_EFFECTS_ENABLED;
    public static final ForgeConfigSpec.IntValue WORLD_EFFECT_SAMPLES_PER_PLAYER;
    public static final ForgeConfigSpec.IntValue WORLD_EFFECT_SAMPLE_RADIUS;
    public static final ForgeConfigSpec.DoubleValue CLOUD_BURN_PREVENT_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue CLOUD_FIRE_DAMP_THRESHOLD;
    public static final ForgeConfigSpec.IntValue CLOUD_FIRE_DAMP_TICKS;
    public static final ForgeConfigSpec.DoubleValue FIRE_EXTINGUISH_BASE_CHANCE;
    public static final ForgeConfigSpec.DoubleValue CAULDRON_FILL_BASE_CHANCE;
    public static final ForgeConfigSpec.BooleanValue FOG_ENABLED;
    public static final ForgeConfigSpec.DoubleValue FORECAST_DEVIATION_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue FOG_SYNC_INTERVAL_TICKS;
    public static final ForgeConfigSpec.DoubleValue FOG_HUMIDITY_START_PERCENT;
    public static final ForgeConfigSpec.DoubleValue FOG_HUMIDITY_FULL_PERCENT;
    public static final ForgeConfigSpec.DoubleValue FOG_WET_BIOME_BASE_STRENGTH;
    public static final ForgeConfigSpec.DoubleValue FOG_RAIN_BOOST;
    public static final ForgeConfigSpec.DoubleValue FOG_NEAR_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue FOG_FAR_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue FOG_COLOR_BLEND;
    public static final ForgeConfigSpec.DoubleValue FOG_WET_BIOME_DOWNFALL_MIN;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FOG_WET_BIOME_IDS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FOG_WET_BIOME_KEYWORDS;
    public static final ForgeConfigSpec.BooleanValue TELEMETRY_ENABLED;
    public static final ForgeConfigSpec.IntValue TELEMETRY_RETENTION_DAYS;
    public static final ForgeConfigSpec.BooleanValue AUTH_STRICT_OFFLINE_UUID_REJECT;
    public static final ForgeConfigSpec.BooleanValue AUTH_KICK_ON_FAILURE;
    public static final ForgeConfigSpec.IntValue AUTH_CHALLENGE_TIMEOUT_TICKS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("performance");
        FORCE_SHARED_EXECUTOR = builder
                .comment("Force use of shared executor for all async tasks, regardless of CPU count")
                .define("forceSharedExecutor", false);
        CLOUD_RENDER_DISTANCE = builder
                .comment("Maximum distance in blocks to render clouds; higher values impact performance")
                .defineInRange("cloudRenderDistance", 2000, 100, Integer.MAX_VALUE);
        CLOUD_RAYMARCH_QUALITY = builder
                .comment("Qualite du raymarch des nuages PA: LOW=16 etapes a 50%, MEDIUM=32 etapes a 75%, HIGH=64 etapes a 100%")
                .defineEnum("cloudRaymarchQuality", CloudRaymarchQuality.MEDIUM);
        builder.pop();

        builder.push("clouds");
        NATIVE_CLOUD_SPAWN_HEIGHT = builder
                .comment("Hauteur Y fixe utilisee par le spawn natif des nuages Project Atmosphere")
                .defineInRange("nativeCloudSpawnHeight", 256, -2048, 4096);
        builder.pop();

        builder.push("display");
        DISPLAY_UNITS_IMPERIAL = builder
                .comment("Display values in imperial units (F, mph, inHg) instead of metric (C, m/s, hPa)")
                .define("imperialUnits", false);
        CLOUD_MODE = builder
                .comment("Select how Project Atmosphere cloud rendering should behave")
                .defineEnum("cloudMode", CloudMode.FULL);
        builder.pop();

        builder.push("forecast");
        FORECAST_DEVIATION_MULTIPLIER = builder
                .comment("Multiplier applied to daily forecast variation")
                .defineInRange("forecastDeviationMultiplier", 1.0d, 0.0d, 3.0d);
        builder.pop();
        builder.push("storms");
        STORM_SEVERITY_BOOSTER = builder
                .comment("Global multiplier for storm severity calculations")
                .defineInRange("stormSeverityBooster", 3.2d, 0.5d, 28d);
        EVENTS_ENABLED = builder
                .comment("Enable storm and weather event spawning/processing")
                .define("eventsEnabled", true);
        ENABLE_TORNADOES = builder
                .comment("Enable tornado spawning and commands")
                .define("enableTornadoes", true);
        ENABLE_TORNADO_DESTRUCTION = builder
                .comment("Enable tornado block destruction and terrain scouring")
                .define("enableTornadoDestruction", true);
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
        ENABLE_HURRICANE_DESTRUCTION = builder
                .comment("Enable limited hurricane block destruction")
                .define("enableHurricaneDestruction", true);
        HURRICANE_DESTRUCTION_STRENGTH = builder
                .comment("Overall hurricane destruction strength. Higher values increase checks, break chance, and tree damage.")
                .defineInRange("hurricaneDestructionStrength", 1.0d, 0.0d, 3.0d);
        HURRICANE_DROP_BROKEN_BLOCKS = builder
                .comment("Drop items from blocks broken by hurricanes")
                .define("hurricaneDropBrokenBlocks", false);
        HURRICANE_DAMAGE_TREES = builder
                .comment("Allow hurricanes to damage leaves and occasionally break logs")
                .define("hurricaneDamageTrees", true);
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
        TORNADO_ALLOW_LEGACY_FALLBACK = builder
                .comment("Allow falling back to the legacy mesh tornado when the SimpleClouds shader pack lacks the CloudStorms SSBO. Leave false to require the shader-driven funnel.")
                .define("allowLegacyTornadoFallback", false);
        DISABLE_SIMPLE_CLOUDS_TORNADO_SSBO = builder
                .comment("Disable Project Atmosphere's Simple Clouds storm SSBO integration. Tornado cloud carving uses safer uniforms; hurricane cloud shaping falls back off when this is enabled.")
                .define("disableSimpleCloudsTornadoSSBO", false);
        TORNADO_DEBUG_LOGGING = builder
                .comment("Enable verbose tornado logging (SSBO detection, fallback decisions, command outcomes).")
                .define("debugTornadoLogging", false);
        TORNADO_RENDER_QUALITY = builder
                .comment("Client tornado volume quality multiplier. Lower values reduce tornado shader step count and detail for better FPS.")
                .defineInRange("renderQuality", 0.72d, 0.25d, 1.0d);
        TORNADO_RENDER_DOWNSAMPLE = builder
                .comment("Client tornado volume downsample factor. 1.0 renders at full resolution; higher values reduce shaded pixels before upsampling.")
                .defineInRange("renderDownsample", 2.5d, 1.0d, 4.0d);
        builder.pop();
        builder.pop();

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
                .comment("Soft-start range above the threshold where wind push ramps up")
                .defineInRange("pushRampMps", 8.0d, 0d, 100d);
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
        builder.push("leafParticles");
        WIND_LEAF_PARTICLE_RADIUS_BLOCKS = builder
                .comment("Sampling radius in blocks around the player for wind-driven leaf particles")
                .defineInRange("radiusBlocks", 32, 4, 128);
        WIND_LEAF_PARTICLE_SCAN_UP = builder
                .comment("Vertical scan range above the player for canopy sampling")
                .defineInRange("verticalScanUp", 12, 0, 64);
        WIND_LEAF_PARTICLE_SCAN_DOWN = builder
                .comment("Vertical scan range below the player for canopy sampling")
                .defineInRange("verticalScanDown", 12, 0, 64);
        WIND_LEAF_PARTICLE_ATTEMPTS_PER_TICK = builder
                .comment("Number of canopy sampling attempts per client tick")
                .defineInRange("attemptsPerTick", 2, 0, 32);
        WIND_LEAF_PARTICLE_CHANCE_PER_CANDIDATE = builder
                .comment("Chance per valid canopy candidate to spawn a particle")
                .defineInRange("chancePerCandidate", 0.03d, 0d, 1d);
        WIND_LEAF_PARTICLE_MIN_FOLIAGE_NEIGHBORS = builder
                .comment("Minimum foliage blocks required in a 3x3x3 neighborhood to accept a canopy candidate")
                .defineInRange("minFoliageNeighbors", 6, 1, 27);
        WIND_LEAF_PARTICLE_REQUIRE_LOG_BELOW = builder
                .comment("Require at least one log block below the canopy candidate")
                .define("requireLogBelow", true);
        WIND_LEAF_PARTICLE_MAX_LOG_SEARCH_DEPTH = builder
                .comment("Maximum depth to search for a log block below the canopy candidate")
                .defineInRange("maxLogSearchDepth", 8, 1, 32);
        builder.pop();

        builder.push("seasonalTrees");
        SEASONAL_TREES_ENABLED = builder
                .comment("Enable seasonal tree leaf transitions and spreading")
                .define("enabled", false);
        SEASONAL_TREES_DYNAMIC_TREES_ENABLED = builder
                .comment("Enable Dynamic Trees support (preferred)")
                .define("dynamicTreesEnabled", false);
        SEASONAL_TREES_VANILLA_ENABLED = builder
                .comment("Enable conservative vanilla tree support (off by default)")
                .define("vanillaEnabled", true);
        SEASONAL_TREES_LEAF_DROP_DAYS = builder
                .comment("In-game days for leaves to drop during autumn")
                .defineInRange("leafDropDays", 4.0d, 0.1d, 40d);
        SEASONAL_TREES_LEAF_REGROW_DAYS = builder
                .comment("In-game days for leaves to regrow during spring")
                .defineInRange("leafRegrowDays", 4.0d, 0.1d, 40d);
        SEASONAL_TREES_TRANSITION_COOLDOWN_DAYS = builder
                .comment("Cooldown in days before a tree can restart a season transition")
                .defineInRange("transitionCooldownDays", 2.0d, 0d, 40d);
        SEASONAL_TREES_TRANSITION_OFFSET_DAYS = builder
                .comment("Max random offset in days applied to per-tree seasonal transitions")
                .defineInRange("transitionOffsetDays", 2.0d, 0d, 20d);
        SEASONAL_TREES_SPREAD_CHANCE_PER_DAY = builder
                .comment("Chance per in-game day for mature trees to attempt spreading")
                .defineInRange("spreadChancePerDay", 0.02d, 0d, 1d);
        SEASONAL_TREES_SPREAD_RADIUS_BLOCKS = builder
                .comment("Baseline spread radius in blocks for local seed dispersal")
                .defineInRange("spreadRadiusBlocks", 12, 1, 128);
        SEASONAL_TREES_VIGOR_REGEN_PER_DAY = builder
                .comment("Base vigor regeneration per in-game day")
                .defineInRange("vigorRegenPerDay", 0.05d, 0d, 1d);
        SEASONAL_TREES_VIGOR_MIN_FOR_SPREAD = builder
                .comment("Minimum vigor required before a tree can spread")
                .defineInRange("vigorMinForSpread", 0.55d, 0d, 1d);
        SEASONAL_TREES_MAX_ACTIVE_SEEDS = builder
                .comment("Maximum number of active seed particles (wind transport) per world")
                .defineInRange("maxActiveSeeds", 256, 0, 10000);
        SEASONAL_TREES_SEED_LIFETIME_TICKS = builder
                .comment("Seed particle lifetime in ticks before attempting to plant")
                .defineInRange("seedLifetimeTicks", 400, 20, 72000);
        SEASONAL_TREES_SEED_BASE_SPEED = builder
                .comment("Base speed multiplier for wind-transported seeds")
                .defineInRange("seedBaseSpeed", 0.15d, 0d, 5d);
        SEASONAL_TREES_WIND_TRANSPORT_ENABLED = builder
                .comment("Enable wind-based seed transport when Project Atmosphere is installed")
                .define("windTransportEnabled", true);
        SEASONAL_TREES_BUDGET_PER_TICK = builder
                .comment("Maximum number of tree updates per tick")
                .defineInRange("budgetPerTick", 80, 1, 10000);
        SEASONAL_TREES_SCAN_BUDGET_PER_TICK = builder
                .comment("Maximum number of chunk scan steps per tick")
                .defineInRange("scanBudgetPerTick", 120, 1, 20000);
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
        CLOUD_DIAGNOSTICS_OVERLAY = builder
                .comment("Cloud render diagnostics overlay mode. F3+O cycles this in-game.")
                .defineEnum("cloudDiagnosticsOverlay", CloudDiagnosticsOverlayMode.OFF);
        builder.pop();

        COMMON_SPEC = builder.build();
    }

    public static final ForgeConfigSpec COMMON_SPEC;
}

