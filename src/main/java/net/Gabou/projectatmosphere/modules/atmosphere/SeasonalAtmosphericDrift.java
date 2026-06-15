package net.Gabou.projectatmosphere.modules.atmosphere;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.seasons.SeasonSnapshot;
import net.Gabou.projectatmosphere.seasons.SeasonStage;
import net.Gabou.projectatmosphere.seasons.SeasonTimeHelper;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

import java.util.Objects;
import java.util.Set;

/**
 * Applies provider-agnostic season pressure to the mutable atmosphere layer.
 * Forecast data remains the immutable baseline; this only nudges live state.
 */
public final class SeasonalAtmosphericDrift {
    private static final int VERSION = 1;
    private static final int TICK_INTERVAL = 200;

    private static final float ACTIVE_TEMPERATURE_RATE = 0.025f;
    private static final float PASSIVE_TEMPERATURE_RATE = 0.008f;
    private static final float ACTIVE_HUMIDITY_RATE = 0.018f;
    private static final float PASSIVE_HUMIDITY_RATE = 0.006f;
    private static final float ACTIVE_PRESSURE_RATE = 0.014f;
    private static final float PASSIVE_PRESSURE_RATE = 0.004f;
    private static final float ACTIVE_CLOUD_WATER_RATE = 0.012f;
    private static final float PASSIVE_CLOUD_WATER_RATE = 0.004f;

    private static SeasonSnapshot currentSnapshot = SeasonSnapshot.neutral();
    private static SeasonalModifier currentModifier = SeasonalModifier.neutral();
    private static long lastTick = Long.MIN_VALUE;
    private static long lastTransitionGameTime = Long.MIN_VALUE;
    private static boolean initialized;

    private SeasonalAtmosphericDrift() {
    }

    public static synchronized void reset() {
        currentSnapshot = SeasonSnapshot.neutral();
        currentModifier = SeasonalModifier.neutral();
        lastTick = Long.MIN_VALUE;
        lastTransitionGameTime = Long.MIN_VALUE;
        initialized = false;
    }

    public static synchronized void onSeasonChanged(ServerLevel level) {
        if (level == null) {
            return;
        }
        SeasonSnapshot snapshot = safeSnapshot(level);
        applySnapshot(snapshot, level.getGameTime(), true);
    }

    public static synchronized void tick(ServerLevel level) {
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        if (now - lastTick < TICK_INTERVAL) {
            return;
        }
        lastTick = now;

        SeasonSnapshot snapshot = safeSnapshot(level);
        if (!initialized || !sameSeasonIdentity(currentSnapshot, snapshot)) {
            applySnapshot(snapshot, now, true);
        } else {
            currentSnapshot = snapshot;
            currentModifier = SeasonalModifier.forSnapshot(snapshot);
        }

        applyDrift(level, currentModifier);
    }

    public static synchronized float sunlightMultiplier() {
        return currentModifier.sunlightMultiplier();
    }

    public static synchronized CompoundTag savePersistentState() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", VERSION);
        tag.putBoolean("Initialized", initialized);
        tag.putString("ProviderId", currentSnapshot.providerId().toString());
        tag.putString("Stage", currentSnapshot.stage().name());
        tag.putFloat("Progress", currentSnapshot.progress());
        tag.putFloat("SnapshotTemperatureOffset", currentSnapshot.temperatureOffset());
        tag.putLong("LastTick", lastTick);
        tag.putLong("LastTransitionGameTime", lastTransitionGameTime);
        tag.put("Modifier", currentModifier.save());
        return tag;
    }

    public static synchronized void loadPersistentState(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            reset();
            return;
        }
        initialized = tag.getBoolean("Initialized");
        ResourceLocation providerId = parseProvider(tag.getString("ProviderId"));
        SeasonStage stage = parseStage(tag.getString("Stage"));
        float progress = Mth.clamp(tag.getFloat("Progress"), 0f, 1f);
        float temperatureOffset = tag.getFloat("SnapshotTemperatureOffset");
        currentSnapshot = new SeasonSnapshot(providerId, stage, progress, temperatureOffset);
        currentModifier = tag.contains("Modifier", Tag.TAG_COMPOUND)
                ? SeasonalModifier.load(tag.getCompound("Modifier"))
                : SeasonalModifier.forSnapshot(currentSnapshot);
        lastTick = tag.getLong("LastTick");
        lastTransitionGameTime = tag.getLong("LastTransitionGameTime");
    }

    private static void applySnapshot(SeasonSnapshot snapshot, long gameTime, boolean logTransition) {
        SeasonSnapshot previous = currentSnapshot;
        currentSnapshot = snapshot == null ? SeasonSnapshot.neutral() : snapshot;
        currentModifier = SeasonalModifier.forSnapshot(currentSnapshot);
        initialized = true;
        lastTransitionGameTime = gameTime;
        if (logTransition && !sameSeasonIdentity(previous, currentSnapshot)) {
            ProjectAtmosphere.LOGGER.info(
                    "[Atmosphere] Seasonal drift target changed: {} -> {} via provider {}.",
                    previous.stage(),
                    currentSnapshot.stage(),
                    currentSnapshot.providerId()
            );
        }
    }

    private static void applyDrift(ServerLevel level, SeasonalModifier modifier) {
        if (modifier == null || modifier.stage() == SeasonStage.NEUTRAL) {
            return;
        }
        long dayTime = level.getDayTime();
        Set<RegionInstanceKey> active = AtmosphericStateRegistry.getActiveStates();
        for (RegionAtmosphereState state : AtmosphericStateRegistry.snapshot()) {
            if (state == null || state.getRegionId() == null) {
                continue;
            }
            boolean activeRegion = active.contains(state.getRegionId());
            applyToState(state, modifier, dayTime, activeRegion);
        }
    }

    private static void applyToState(RegionAtmosphereState state, SeasonalModifier modifier, long dayTime, boolean active) {
        float temperatureTarget = state.getTargetTemperature(dayTime)
                + modifier.temperatureOffsetC()
                + currentSnapshot.temperatureOffset();
        float humidityTarget = Mth.clamp(
                state.getTargetHumidity(dayTime) * modifier.humidityMultiplier() + modifier.humidityOffset(),
                0f,
                1.2f
        );
        float pressureTarget = Mth.clamp(
                state.getTargetPressure(dayTime) + modifier.pressureOffsetHpa(),
                900f,
                1080f
        );
        float cloudWaterTarget = cloudWaterTarget(state, humidityTarget, modifier);

        if (active) {
            state.adjustTemperature(moveToward(state.getTemperature(), temperatureTarget, ACTIVE_TEMPERATURE_RATE, 0.25f));
            state.adjustHumidity(moveToward(state.getHumidity(), humidityTarget, ACTIVE_HUMIDITY_RATE, 0.006f));
            state.adjustPressure(moveToward(state.getPressure(), pressureTarget, ACTIVE_PRESSURE_RATE, 0.20f));
            state.adjustCloudWater(moveToward(state.getCloudWater(), cloudWaterTarget, ACTIVE_CLOUD_WATER_RATE, 0.004f));
        } else {
            state.adjustTemperature(moveToward(state.getTemperature(), temperatureTarget, PASSIVE_TEMPERATURE_RATE, 0.08f));
            state.adjustHumidity(moveToward(state.getHumidity(), humidityTarget, PASSIVE_HUMIDITY_RATE, 0.002f));
            state.adjustPressure(moveToward(state.getPressure(), pressureTarget, PASSIVE_PRESSURE_RATE, 0.06f));
            state.adjustCloudWater(moveToward(state.getCloudWater(), cloudWaterTarget, PASSIVE_CLOUD_WATER_RATE, 0.0015f));
        }
    }

    private static float cloudWaterTarget(RegionAtmosphereState state, float humidityTarget, SeasonalModifier modifier) {
        float capacity = modifier.cloudWaterCapacity();
        float current = state.getCloudWater();
        float target = Math.min(current, capacity);
        if (modifier.cloudWaterBias() > 0f && state.getHumidity() > humidityTarget) {
            float surplus = state.getHumidity() - humidityTarget;
            target = Math.min(capacity, current + surplus * modifier.cloudWaterBias());
        }
        return Mth.clamp(target, 0f, 1.2f);
    }

    private static float moveToward(float current, float target, float rate, float maxStep) {
        if (!Float.isFinite(current) || !Float.isFinite(target)) {
            return 0f;
        }
        return Mth.clamp((target - current) * rate, -maxStep, maxStep);
    }

    private static SeasonSnapshot safeSnapshot(ServerLevel level) {
        SeasonSnapshot snapshot = SeasonTimeHelper.snapshot(level);
        return snapshot == null ? SeasonSnapshot.neutral() : snapshot;
    }

    private static boolean sameSeasonIdentity(SeasonSnapshot a, SeasonSnapshot b) {
        if (a == null || b == null) {
            return false;
        }
        return a.stage() == b.stage() && Objects.equals(a.providerId(), b.providerId());
    }

    private static ResourceLocation parseProvider(String value) {
        try {
            if (value != null && !value.isBlank()) {
                int separator = value.indexOf(':');
                if (separator > 0 && separator < value.length() - 1) {
                    return ResourceLocation.fromNamespaceAndPath(value.substring(0, separator), value.substring(separator + 1));
                }
            }
        } catch (RuntimeException ignored) {
        }
        return SeasonSnapshot.neutral().providerId();
    }

    private static SeasonStage parseStage(String value) {
        try {
            if (value != null && !value.isBlank()) {
                return SeasonStage.valueOf(value);
            }
        } catch (RuntimeException ignored) {
        }
        return SeasonStage.NEUTRAL;
    }

    private record SeasonalModifier(
            SeasonStage stage,
            float temperatureOffsetC,
            float humidityMultiplier,
            float humidityOffset,
            float pressureOffsetHpa,
            float cloudWaterCapacity,
            float cloudWaterBias,
            float sunlightMultiplier
    ) {
        static SeasonalModifier neutral() {
            return new SeasonalModifier(SeasonStage.NEUTRAL, 0f, 1f, 0f, 0f, 0.34f, 0f, 1f);
        }

        static SeasonalModifier forSnapshot(SeasonSnapshot snapshot) {
            SeasonStage stage = snapshot == null ? SeasonStage.NEUTRAL : snapshot.stage();
            float progress = snapshot == null ? 0f : Mth.clamp(snapshot.progress(), 0f, 1f);
            float strength = stage == SeasonStage.NEUTRAL
                    ? 0f
                    : 0.75f + 0.25f * Mth.sin(progress * (float) Math.PI);
            return switch (stage) {
                case SPRING -> scale(new SeasonalModifier(stage, 2.0f, 1.05f, 0.03f, -0.2f, 0.36f, 0.04f, 0.95f), strength);
                case SUMMER -> scale(new SeasonalModifier(stage, 6.0f, 1.08f, 0.04f, -1.2f, 0.42f, 0.08f, 1.08f), strength);
                case AUTUMN -> scale(new SeasonalModifier(stage, -2.0f, 0.96f, -0.02f, 0.6f, 0.30f, -0.02f, 0.88f), strength);
                case WINTER -> scale(new SeasonalModifier(stage, -8.0f, 0.82f, -0.06f, 1.5f, 0.22f, -0.06f, 0.72f), strength);
                case NEUTRAL -> neutral();
            };
        }

        static SeasonalModifier scale(SeasonalModifier base, float strength) {
            float humidityMultiplier = 1f + (base.humidityMultiplier() - 1f) * strength;
            float cloudCapacity = Mth.lerp(strength, neutral().cloudWaterCapacity(), base.cloudWaterCapacity());
            float sunlight = 1f + (base.sunlightMultiplier() - 1f) * strength;
            return new SeasonalModifier(
                    base.stage(),
                    base.temperatureOffsetC() * strength,
                    humidityMultiplier,
                    base.humidityOffset() * strength,
                    base.pressureOffsetHpa() * strength,
                    cloudCapacity,
                    base.cloudWaterBias() * strength,
                    sunlight
            );
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Stage", stage.name());
            tag.putFloat("TemperatureOffsetC", temperatureOffsetC);
            tag.putFloat("HumidityMultiplier", humidityMultiplier);
            tag.putFloat("HumidityOffset", humidityOffset);
            tag.putFloat("PressureOffsetHpa", pressureOffsetHpa);
            tag.putFloat("CloudWaterCapacity", cloudWaterCapacity);
            tag.putFloat("CloudWaterBias", cloudWaterBias);
            tag.putFloat("SunlightMultiplier", sunlightMultiplier);
            return tag;
        }

        static SeasonalModifier load(CompoundTag tag) {
            if (tag == null || tag.isEmpty()) {
                return neutral();
            }
            return new SeasonalModifier(
                    parseStage(tag.getString("Stage")),
                    tag.getFloat("TemperatureOffsetC"),
                    tag.contains("HumidityMultiplier", Tag.TAG_FLOAT) ? tag.getFloat("HumidityMultiplier") : 1f,
                    tag.getFloat("HumidityOffset"),
                    tag.getFloat("PressureOffsetHpa"),
                    tag.contains("CloudWaterCapacity", Tag.TAG_FLOAT) ? tag.getFloat("CloudWaterCapacity") : neutral().cloudWaterCapacity(),
                    tag.getFloat("CloudWaterBias"),
                    tag.contains("SunlightMultiplier", Tag.TAG_FLOAT) ? tag.getFloat("SunlightMultiplier") : 1f
            );
        }
    }
}
