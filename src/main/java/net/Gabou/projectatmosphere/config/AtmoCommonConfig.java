package net.Gabou.projectatmosphere.config;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;




public class AtmoCommonConfig {
    public static final ForgeConfigSpec.IntValue CLOUD_RENDER_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue FORCE_SHARED_EXECUTOR;
    public static final ForgeConfigSpec.BooleanValue ENABLE_TORNADOES;
    public static final ForgeConfigSpec.BooleanValue ENABLE_STORM_DEBRIS;
    public static final ForgeConfigSpec.IntValue MAX_STORM_DEBRIS_PER_CHUNK;
    public static final ForgeConfigSpec.BooleanValue AUTO_REPAIR_GLASS;
    public static final ForgeConfigSpec.BooleanValue DAMAGE_GLASS_ON_TORNADO;
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




    public static final ForgeConfigSpec.DoubleValue WIND_BASE_RETARGET_SEC;
    public static final ForgeConfigSpec.DoubleValue WIND_DIR_RETARGET_SEC;
    public static final ForgeConfigSpec.DoubleValue WIND_GUST_MEAN_SEC;
    public static final ForgeConfigSpec.DoubleValue WIND_GUST_DECAY_MPS;
    public static final ForgeConfigSpec.DoubleValue WIND_STORM_GUST_MULT;
    public static final ForgeConfigSpec.DoubleValue WIND_PUSH_THRESHOLD_MPS;
    public static final ForgeConfigSpec.DoubleValue WIND_PLAYER_PUSH_SCALE;
    public static final ForgeConfigSpec.DoubleValue WIND_ENTITY_PUSH_SCALE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("performance");
        FORCE_SHARED_EXECUTOR = builder
                .comment("Force use of shared executor for all async tasks, regardless of CPU count")
                .define("forceSharedExecutor", false);
        CLOUD_RENDER_DISTANCE = builder
                .comment("Maximum distance in blocks to render clouds; higher values impact performance")
                .defineInRange("cloudRenderDistance", 2000, 100, Integer.MAX_VALUE);
        builder.pop();
        builder.push("storms");
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
        WIND_PLAYER_PUSH_SCALE = builder
                .comment("Push scale applied to players")
                .defineInRange("playerPushScale", 0.04d, 0d, 1d);
        WIND_ENTITY_PUSH_SCALE = builder
                .comment("Push scale applied to other entities")
                .defineInRange("entityPushScale", 0.03d, 0d, 1d);
        builder.pop();

        COMMON_SPEC = builder.build();
    }

    public static final ForgeConfigSpec COMMON_SPEC;
}

