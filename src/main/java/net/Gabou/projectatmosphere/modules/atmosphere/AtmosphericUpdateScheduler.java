package net.Gabou.projectatmosphere.modules.atmosphere;

import net.Gabou.projectatmosphere.async.PoolType;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.telemetry.TelemetryCollector;
import net.Gabou.projectatmosphere.telemetry.TelemetryModels.AnomalyMarker;
import net.Gabou.projectatmosphere.telemetry.TelemetryModels.AtmosphereCouplingSample;
import net.Gabou.projectatmosphere.telemetry.TelemetryModels.HumidityBudgetSample;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.ocean.OceanBasinManager;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Centralizes atmospheric state updates.
 * - Active regions (within 1000 blocks of players) refresh every 20 ticks.
 * - Passive regions refresh in small batches every 100 ticks via a round-robin queue.
 * Heavy computations run on the async weather pool and apply clamped deltas on the main thread.
 */
public final class AtmosphericUpdateScheduler {
    private static final int ACTIVE_INTERVAL_TICKS = 20;
    private static final int PASSIVE_INTERVAL_TICKS = 100;
    private static final int PASSIVE_BATCH_SIZE = 1000;

    private static final float COOLING_SCALE = 3f;
    private static final float TEMPERATURE_TARGET_RESTORE = 0.04f;
    private static final float TEMPERATURE_GUARD_THRESHOLD_C = 6f;
    private static final float TEMPERATURE_GUARD_EXCESS_FACTOR = 0.15f;
    private static final float TEMPERATURE_GUARD_MAX_DELTA = 3f;
    private static final float PRESSURE_RESTORE = 1.5f;
    private static final float PRESSURE_TARGET_RESTORE = 0.015f;
    private static final float PRESSURE_GUARD_THRESHOLD_HPA = 8f;
    private static final float PRESSURE_GUARD_EXCESS_FACTOR = 0.12f;
    private static final float PRESSURE_GUARD_MAX_DELTA = 2.5f;
    private static final float CYCLONE_CLOUD_FLOOR_DECAY_ACTIVE = 0.02f;
    private static final float CYCLONE_RAIN_FLOOR_DECAY_ACTIVE = 0.03f;
    private static final float CYCLONE_CLOUD_FLOOR_DECAY_PASSIVE = 0.008f;
    private static final float CYCLONE_RAIN_FLOOR_DECAY_PASSIVE = 0.012f;
    private static final float RAIN_FADE = 0.01f;

    private static final AtomicBoolean ACTIVE_IN_FLIGHT = new AtomicBoolean();
    private static final AtomicBoolean PASSIVE_IN_FLIGHT = new AtomicBoolean();
    private static final ArrayDeque<RegionInstanceKey> PASSIVE_QUEUE = new ArrayDeque<>();
    private static final Set<String> REPORTED_ANOMALIES = ConcurrentHashMap.newKeySet();

    private static long lastActiveTick = -ACTIVE_INTERVAL_TICKS;
    private static long lastPassiveTick = 0L;

    private AtmosphericUpdateScheduler() {
    }

    public static void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (now - lastActiveTick >= ACTIVE_INTERVAL_TICKS) {
            lastActiveTick = now;
            rebuildActiveAsync(level, active -> scheduleActive(level, active));
        }
        if (now - lastPassiveTick >= PASSIVE_INTERVAL_TICKS) {
            lastPassiveTick = now;
            schedulePassive(level);
        }
    }

    private static void scheduleActive(ServerLevel level, Set<RegionInstanceKey> activeKeys) {
        if (!ACTIVE_IN_FLIGHT.compareAndSet(false, true)) {
            return;
        }
        if (activeKeys.isEmpty()) {
            ACTIVE_IN_FLIGHT.set(false);
            return;
        }
        long dayTime = level.getDayTime();
        List<StateView> snapshot = snapshotStates(activeKeys, dayTime);
        if (snapshot.isEmpty()) {
            ACTIVE_IN_FLIGHT.set(false);
            return;
        }
        float daylight = baseDaylightCurve(dayTime);
        float seasonal = seasonalTilt(dayTime);
        String dimensionId = level.dimension().location().toString();
        AsyncAtmosphereService.runWithCallback(
                PoolType.WEATHER,
                () -> computeDeltas(snapshot, daylight, seasonal, UpdateMode.ACTIVE),
                deltas -> {
                    try {
                        applyDeltas(deltas, dayTime, dimensionId, UpdateMode.ACTIVE);
                    } finally {
                        ACTIVE_IN_FLIGHT.set(false);
                    }
                }
        );
    }

    private static void rebuildActiveAsync(ServerLevel level, java.util.function.Consumer<Set<RegionInstanceKey>> consumer) {
        List<BlockPos> players = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            players.add(player.blockPosition());
        }
        Map<RegionInstanceKey, RegionAtmosphereState> states = AtmosphericStateRegistry.getStatesAsMap();
        AsyncAtmosphereService.runWithCallback(
                PoolType.WEATHER,
                () -> computeActiveStates(players, states.keySet()),
                active -> {
                    AtmosphericStateRegistry.replaceActiveStates(active);
                    if (consumer != null) {
                        consumer.accept(new HashSet<>(active));
                    }
                }
        );
    }

    private static Set<RegionInstanceKey> computeActiveStates(List<BlockPos> players, Set<RegionInstanceKey> keys) {
        if (players.isEmpty() || keys.isEmpty()) {
            return Set.of();
        }
        int radius = 1000;
        int r2 = radius * radius;
        Set<RegionInstanceKey> active = new HashSet<>();
        for (RegionInstanceKey key : keys) {
            if (key == null) {
                continue;
            }
            for (BlockPos playerPos : players) {
                if (key.contains(playerPos) || isWithinRegionRadius(key, playerPos, r2)) {
                    active.add(key);
                    break;
                }
            }
        }
        return active;
    }

    private static boolean isWithinRegionRadius(RegionInstanceKey key, BlockPos pos, int radiusSquared) {
        int size = key.regionSize();
        int minX = key.regionX() * size;
        int minZ = key.regionZ() * size;
        int maxX = minX + size - 1;
        int maxZ = minZ + size - 1;
        int px = pos.getX();
        int pz = pos.getZ();
        int dx = 0;
        int dz = 0;
        if (px < minX) {
            dx = minX - px;
        } else if (px > maxX) {
            dx = px - maxX;
        }
        if (pz < minZ) {
            dz = minZ - pz;
        } else if (pz > maxZ) {
            dz = pz - maxZ;
        }
        return (dx * dx + dz * dz) <= radiusSquared;
    }

    private static void schedulePassive(ServerLevel level) {
        if (!PASSIVE_IN_FLIGHT.compareAndSet(false, true)) {
            return;
        }
        Set<RegionInstanceKey> activeKeys = new HashSet<>(AtmosphericStateRegistry.getActiveStates());
        refillQueue(activeKeys);
        List<RegionInstanceKey> batchKeys = pollBatch(activeKeys);
        if (batchKeys.isEmpty()) {
            PASSIVE_IN_FLIGHT.set(false);
            return;
        }
        long dayTime = level.getDayTime();
        List<StateView> snapshot = snapshotStates(batchKeys, dayTime);
        if (snapshot.isEmpty()) {
            PASSIVE_IN_FLIGHT.set(false);
            return;
        }
        float daylight = baseDaylightCurve(dayTime);
        float seasonal = seasonalTilt(dayTime);
        String dimensionId = level.dimension().location().toString();
        AsyncAtmosphereService.runWithCallback(
                PoolType.WEATHER,
                () -> computeDeltas(snapshot, daylight, seasonal, UpdateMode.PASSIVE),
                deltas -> {
                    try {
                        applyDeltas(deltas, dayTime, dimensionId, UpdateMode.PASSIVE);
                    } finally {
                        PASSIVE_IN_FLIGHT.set(false);
                    }
                }
        );
    }

    private static void refillQueue(Set<RegionInstanceKey> activeKeys) {
        if (!PASSIVE_QUEUE.isEmpty()) {
            return;
        }
        Map<RegionInstanceKey, RegionAtmosphereState> states = AtmosphericStateRegistry.getStatesAsMap();
        for (RegionInstanceKey key : states.keySet()) {
            if (key == null || activeKeys.contains(key)) {
                continue;
            }
            PASSIVE_QUEUE.addLast(key);
        }
    }

    private static List<RegionInstanceKey> pollBatch(Set<RegionInstanceKey> activeKeys) {
        List<RegionInstanceKey> batch = new ArrayList<>(PASSIVE_BATCH_SIZE);
        while (!PASSIVE_QUEUE.isEmpty() && batch.size() < PASSIVE_BATCH_SIZE) {
            RegionInstanceKey key = PASSIVE_QUEUE.pollFirst();
            if (key != null && !activeKeys.contains(key)) {
                batch.add(key);
            }
        }
        return batch;
    }

    private static List<StateView> snapshotStates(Collection<RegionInstanceKey> keys, long dayTime) {
        Map<RegionInstanceKey, RegionAtmosphereState> states = AtmosphericStateRegistry.getStatesAsMap();
        List<StateView> views = new ArrayList<>(keys.size());
        for (RegionInstanceKey key : keys) {
            RegionAtmosphereState state = states.get(key);
            if (state == null || state.getPosition() == null) {
                continue;
            }
            views.add(buildStateView(key, state, dayTime));
        }
        return views;
    }

    private static StateView buildStateView(RegionInstanceKey key, RegionAtmosphereState state, long dayTime) {
        return new StateView(
                key,
                state.getTemperature(),
                state.getHumidity(),
                state.getPressure(),
                state.getCloudCover(),
                state.getCloudWater(),
                state.getSunlight(),
                state.getRainIntensity(),
                state.getTargetTemperature(dayTime),
                state.getTargetHumidity(dayTime),
                state.getTargetPressure(dayTime),
                state.getBiomeSunlightMultiplier(),
                state.getBaselineMinTemperature(),
                state.getBaselineMaxTemperature(),
                state.getDominantBiome() == null ? "unknown" : state.getDominantBiome().toString()
        );
    }

    private static List<StateDelta> computeDeltas(List<StateView> snapshot, float daylight, float seasonal, UpdateMode mode) {
        List<StateDelta> deltas = new ArrayList<>(snapshot.size());
        for (StateView view : snapshot) {
            float sunlightFactor = daylight * seasonal * view.sunlightMultiplier();
            sunlightFactor *= Math.max(0f, 1f - view.cloudCover());
            sunlightFactor = Mth.clamp(sunlightFactor, 0f, 1f);

            float baselineSpan = Math.max(0.001f, view.baselineMax() - view.baselineMin());
            float baseTarget = Mth.lerp(sunlightFactor, view.baselineMin(), view.baselineMax());
            float rainPenalty = view.rainIntensity() * baselineSpan * 0.15f;
            float adjustedTarget = baseTarget - rainPenalty;
            float blendedTarget = Mth.lerp(mode.blend(), view.temperature(), adjustedTarget);
            float sunlightTemperatureDelta = (blendedTarget - view.temperature()) * mode.scale();
            float temperatureForecastRestore = (view.targetTemperature() - view.temperature()) * TEMPERATURE_TARGET_RESTORE * mode.scale();
            float temperatureDeviation = view.targetTemperature() - view.temperature();
            float temperatureGuardDelta = 0f;
            float temperatureDeviationAbs = Math.abs(temperatureDeviation);
            if (temperatureDeviationAbs > TEMPERATURE_GUARD_THRESHOLD_C) {
                float excess = temperatureDeviationAbs - TEMPERATURE_GUARD_THRESHOLD_C;
                float guardMagnitude = Math.min(TEMPERATURE_GUARD_MAX_DELTA, excess * TEMPERATURE_GUARD_EXCESS_FACTOR);
                temperatureGuardDelta = Math.signum(temperatureDeviation) * guardMagnitude * mode.scale();
            }

            float clampedRain = Math.min(1f, view.rainIntensity());
            float rainTemperatureDelta = -clampedRain * COOLING_SCALE * mode.scale();
            float rainPressureDelta = clampedRain * PRESSURE_RESTORE * mode.scale();
            float pressureForecastRestore = (view.targetPressure() - view.pressure()) * PRESSURE_TARGET_RESTORE * mode.scale();
            float pressureDeviation = view.targetPressure() - view.pressure();
            float pressureGuardDelta = 0f;
            float pressureDeviationAbs = Math.abs(pressureDeviation);
            if (pressureDeviationAbs > PRESSURE_GUARD_THRESHOLD_HPA) {
                float excess = pressureDeviationAbs - PRESSURE_GUARD_THRESHOLD_HPA;
                float guardMagnitude = Math.min(PRESSURE_GUARD_MAX_DELTA, excess * PRESSURE_GUARD_EXCESS_FACTOR);
                pressureGuardDelta = Math.signum(pressureDeviation) * guardMagnitude * mode.scale();
            }
            float oceanFlux = 0f;
            float windTransport = 0f;
            if (mode.transportAccumulationTicks() > 0f) {
                oceanFlux = OceanBasinManager.estimateHumidityFlux(view.key(), view.humidity()) * mode.transportAccumulationTicks();
                windTransport = WindVector.estimateHumidityTransport(view.key()) * mode.transportAccumulationTicks();
            }

            HumidityBudget humidityBudget = HumidityBudgetService.compute(
                    view.humidity(),
                    view.targetHumidity(),
                    sunlightFactor,
                    view.cloudCover(),
                    view.rainIntensity(),
                    view.dominantBiomeId(),
                    oceanFlux,
                    windTransport,
                    mode.scale(),
                    mode.humidityBudgetScale()
            );
            float humidityDelta = humidityBudget.netDelta();
            CloudWaterExchange cloudWaterExchange = CloudWaterService.compute(
                    Mth.clamp(view.humidity() + humidityDelta, 0f, 1.2f),
                    view.targetHumidity(),
                    view.cloudWater(),
                    view.cloudCover(),
                    view.rainIntensity()
            );
            humidityDelta += cloudWaterExchange.humidityDelta();

            float temperatureDelta = clampDelta(
                    sunlightTemperatureDelta + rainTemperatureDelta + temperatureForecastRestore + temperatureGuardDelta,
                    -20f,
                    20f
            );
            humidityDelta = clampDelta(humidityDelta, -0.35f, 0.35f);
            float cloudWaterDelta = clampDelta(cloudWaterExchange.cloudWaterDelta(), -0.08f, 0.08f);
            float pressureDelta = clampDelta(rainPressureDelta + pressureForecastRestore + pressureGuardDelta, -15f, 15f);

            float rainFade = RAIN_FADE * mode.rainFadeScale();

            deltas.add(new StateDelta(
                    view.key(),
                    temperatureDelta,
                    humidityDelta,
                    pressureDelta,
                    sunlightFactor,
                    rainFade,
                    mode.relaxFactor(),
                    view.targetTemperature(),
                    view.targetHumidity(),
                    view.targetPressure(),
                    humidityBudget,
                    cloudWaterDelta,
                    cloudWaterExchange,
                    view.dominantBiomeId()
            ));
        }
        return deltas;
    }

    private static void applyDeltas(List<StateDelta> deltas, long dayTime, String dimensionId, UpdateMode mode) {
        for (StateDelta delta : deltas) {
            RegionAtmosphereState state = AtmosphericStateRegistry.getState(delta.key());
            if (state == null) {
                continue;
            }
            float humidityBefore = state.getHumidity();
            float cloudWaterBefore = state.getCloudWater();
            float temperatureBefore = state.getTemperature();
            float pressureBefore = state.getPressure();
            state.setSunlight(delta.sunlight());
            state.adjustTemperature(delta.temperatureDelta());
            state.adjustHumidity(delta.humidityDelta());
            state.adjustCloudWater(delta.cloudWaterDelta());
            state.adjustPressure(delta.pressureDelta());
            if (mode == UpdateMode.ACTIVE) {
                state.decayCycloneVisualFloor(CYCLONE_CLOUD_FLOOR_DECAY_ACTIVE, CYCLONE_RAIN_FLOOR_DECAY_ACTIVE);
            } else {
                state.decayCycloneVisualFloor(CYCLONE_CLOUD_FLOOR_DECAY_PASSIVE, CYCLONE_RAIN_FLOOR_DECAY_PASSIVE);
            }
            if (delta.rainFade() > 0f) {
                state.dampenRain(delta.rainFade());
            }
            if (delta.relaxFactor() > 0f) {
                state.relaxTemperatureAndPressureTowardBase(delta.relaxFactor());
            }
            if (mode == UpdateMode.ACTIVE) {
                state.recordDailySnapshot(dayTime);
            }
            AtmosphericTelemetryReporter.recordFor(state, delta, temperatureBefore, pressureBefore, humidityBefore, cloudWaterBefore, dayTime, dimensionId, mode);
        }
    }

    private static void recordAtmosphereCoupling(RegionAtmosphereState state,
                                                 StateDelta delta,
                                                 float temperatureBefore,
                                                 float pressureBefore,
                                                 float humidityBefore,
                                                 long dayTime,
                                                 String dimensionId,
                                                 UpdateMode mode) {
        RegionInstanceKey key = state.getRegionId();
        if (key == null) {
            return;
        }
        TelemetryCollector.get().recordAtmosphereCouplingSample(new AtmosphereCouplingSample(
                dayTime / 24000L,
                Math.floorMod(dayTime, 24000L),
                dimensionId,
                key.toString(),
                key.regionX(),
                key.regionZ(),
                key.regionSize(),
                delta.dominantBiomeId(),
                mode.name().toLowerCase(Locale.ROOT),
                delta.targetTemperature(),
                delta.targetPressure(),
                delta.targetHumidity(),
                temperatureBefore,
                state.getTemperature(),
                pressureBefore,
                state.getPressure(),
                humidityBefore,
                state.getHumidity(),
                state.getCloudCover(),
                state.getRainIntensity(),
                delta.temperatureDelta(),
                delta.pressureDelta()
        ));
    }

    private static void recordHumidityBudget(RegionAtmosphereState state,
                                             StateDelta delta,
                                             float humidityBefore,
                                             float cloudWaterBefore,
                                             long dayTime,
                                             String dimensionId,
                                             UpdateMode mode) {
        RegionInstanceKey key = state.getRegionId();
        if (key == null) {
            return;
        }
        HumidityBudget budget = delta.humidityBudget();
        CloudWaterExchange exchange = delta.cloudWaterExchange();
        TelemetryCollector.get().recordHumidityBudgetSample(new HumidityBudgetSample(
                dayTime / 24000L,
                Math.floorMod(dayTime, 24000L),
                dimensionId,
                key.toString(),
                key.regionX(),
                key.regionZ(),
                key.regionSize(),
                delta.dominantBiomeId(),
                mode.name().toLowerCase(Locale.ROOT),
                delta.targetHumidity(),
                humidityBefore,
                state.getHumidity(),
                cloudWaterBefore,
                state.getCloudWater(),
                state.getCloudCover(),
                state.getRainIntensity(),
                budget.solarDrying(),
                budget.biomeEvaporation(),
                budget.oceanFlux(),
                budget.rainExchange(),
                budget.windTransport(),
                budget.forecastRestore(),
                budget.precipitationSink(),
                exchange.condensation(),
                exchange.reEvaporation(),
                exchange.precipitationDraw(),
                delta.humidityDelta()
        ));
    }

    private static void recordAnomalies(RegionAtmosphereState state, long dayTime) {
        float temperature = state.getTemperature();
        String temperatureKey = state.getRegionId() + ":temperature_outlier";
        if ((temperature < -80f || temperature > 80f) && REPORTED_ANOMALIES.add(temperatureKey)) {
            TelemetryCollector.get().recordAnomaly(new AnomalyMarker(
                    Instant.now(),
                    "temperature_outlier",
                    state.getRegionId().toString(),
                    Map.of("temperature", temperature)
            ));
        }

        float targetTemperature = state.getTargetTemperature(dayTime);
        float temperatureDeviation = Math.abs(targetTemperature - state.getTemperature());
        boolean hiddenTemperatureDrift = temperatureDeviation > TEMPERATURE_GUARD_THRESHOLD_C
                && state.getCloudCover() < 0.1f
                && state.getRainIntensity() < 0.05f;
        String driftKey = state.getRegionId() + ":temperature_drift_from_target";
        if (hiddenTemperatureDrift && REPORTED_ANOMALIES.add(driftKey)) {
            TelemetryCollector.get().recordAnomaly(new AnomalyMarker(
                    Instant.now(),
                    "temperature_drift_from_target",
                    state.getRegionId().toString(),
                    Map.of(
                            "temperature", state.getTemperature(),
                            "targetTemperature", targetTemperature,
                            "temperatureDeviation", temperatureDeviation,
                            "cloudCover", state.getCloudCover(),
                            "rainIntensity", state.getRainIntensity()
                    )
            ));
        }

        float targetPressure = state.getTargetPressure(dayTime);
        float pressureDeviation = Math.abs(targetPressure - state.getPressure());
        boolean hiddenPressureDrift = pressureDeviation > PRESSURE_GUARD_THRESHOLD_HPA
                && state.getCloudCover() < 0.05f
                && state.getRainIntensity() < 0.02f;
        String pressureKey = state.getRegionId() + ":pressure_drift_no_visible_weather";
        if (hiddenPressureDrift && REPORTED_ANOMALIES.add(pressureKey)) {
            TelemetryCollector.get().recordAnomaly(new AnomalyMarker(
                    Instant.now(),
                    "pressure_drift_no_visible_weather",
                    state.getRegionId().toString(),
                    Map.of(
                            "pressure", state.getPressure(),
                            "targetPressure", targetPressure,
                            "pressureDeviation", pressureDeviation,
                            "cloudCover", state.getCloudCover(),
                            "rainIntensity", state.getRainIntensity()
                    )
            ));
        }
    }

    private static float baseDaylightCurve(long dayTime) {
        long time = dayTime % 24000L;
        float dayProgress = time / 12000f;
        float daylight = (float) Math.sin(Math.PI * dayProgress);
        daylight = Mth.clamp(daylight, 0f, 1f);
        return daylight * daylight;
    }

    private static float seasonalTilt(long dayTime) {
        long day = dayTime / 24000L;
        float seasonProgress = (day % 96L) / 96f;
        return 0.7f + 0.3f * Mth.cos(seasonProgress * (float) (Math.PI * 2));
    }

    private static float clampDelta(float value, float min, float max) {
        return Mth.clamp(value, min, max);
    }

    record StateView(
            RegionInstanceKey key,
            float temperature,
            float humidity,
            float pressure,
            float cloudCover,
            float cloudWater,
            float sunlight,
            float rainIntensity,
            float targetTemperature,
            float targetHumidity,
            float targetPressure,
            float sunlightMultiplier,
            float baselineMin,
            float baselineMax,
            String dominantBiomeId
    ) {
    }

    record StateDelta(
            RegionInstanceKey key,
            float temperatureDelta,
            float humidityDelta,
            float pressureDelta,
            float sunlight,
            float rainFade,
            float relaxFactor,
            float targetTemperature,
            float targetHumidity,
            float targetPressure,
            HumidityBudget humidityBudget,
            float cloudWaterDelta,
            CloudWaterExchange cloudWaterExchange,
            String dominantBiomeId
    ) {
    }

    enum UpdateMode {
        ACTIVE(1f, 0.0005f, 0.6f, 1f, 1f, ACTIVE_INTERVAL_TICKS),
        PASSIVE(0.35f, 0.0002f, 0.45f, 0.5f, 0.45f, 0f);

        private final float scale;
        private final float relaxFactor;
        private final float blend;
        private final float rainFadeScale;
        private final float humidityBudgetScale;
        private final float transportAccumulationTicks;

        UpdateMode(float scale, float relaxFactor, float blend, float rainFadeScale, float humidityBudgetScale,
                   float transportAccumulationTicks) {
            this.scale = scale;
            this.relaxFactor = relaxFactor;
            this.blend = blend;
            this.rainFadeScale = rainFadeScale;
            this.humidityBudgetScale = humidityBudgetScale;
            this.transportAccumulationTicks = transportAccumulationTicks;
        }

        float scale() {
            return scale;
        }

        float relaxFactor() {
            return relaxFactor;
        }

        float blend() {
            return blend;
        }

        float rainFadeScale() {
            return rainFadeScale;
        }

        float humidityBudgetScale() {
            return humidityBudgetScale;
        }

        float transportAccumulationTicks() {
            return transportAccumulationTicks;
        }
    }
}
