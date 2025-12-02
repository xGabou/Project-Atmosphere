package net.Gabou.projectatmosphere.modules.atmosphere;

import net.Gabou.projectatmosphere.async.PoolType;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final float HUMIDITY_DRAIN = 0.2f;
    private static final float PRESSURE_RESTORE = 1.5f;
    private static final float RAIN_FADE = 0.01f;

    private static final AtomicBoolean ACTIVE_IN_FLIGHT = new AtomicBoolean();
    private static final AtomicBoolean PASSIVE_IN_FLIGHT = new AtomicBoolean();
    private static final ArrayDeque<RegionInstanceKey> PASSIVE_QUEUE = new ArrayDeque<>();

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
        List<StateView> snapshot = snapshotStates(activeKeys);
        if (snapshot.isEmpty()) {
            ACTIVE_IN_FLIGHT.set(false);
            return;
        }
        float daylight = baseDaylightCurve(level.getSunAngle(1f));
        float seasonal = seasonalTilt(level);
        long dayTime = level.getDayTime();
        AsyncAtmosphereService.runWithCallback(
                PoolType.WEATHER,
                () -> computeDeltas(snapshot, daylight, seasonal, UpdateMode.ACTIVE),
                deltas -> {
                    try {
                        applyDeltas(deltas, dayTime, UpdateMode.ACTIVE);
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
        Map<RegionInstanceKey, RegionAtmosphereState> states = AtmosphericStateRegistry.getStatesAsMap();
        for (RegionInstanceKey key : keys) {
            RegionAtmosphereState state = states.get(key);
            if (state == null || state.getPosition() == null) {
                continue;
            }
            BlockPos sample = state.getPosition();
            for (BlockPos playerPos : players) {
                double dx = sample.getX() - playerPos.getX();
                double dz = sample.getZ() - playerPos.getZ();
                if ((dx * dx + dz * dz) <= r2) {
                    active.add(key);
                    break;
                }
            }
        }
        return active;
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
        List<StateView> snapshot = snapshotStates(batchKeys);
        if (snapshot.isEmpty()) {
            PASSIVE_IN_FLIGHT.set(false);
            return;
        }
        float daylight = baseDaylightCurve(level.getSunAngle(1f));
        float seasonal = seasonalTilt(level);
        long dayTime = level.getDayTime();
        AsyncAtmosphereService.runWithCallback(
                PoolType.WEATHER,
                () -> computeDeltas(snapshot, daylight, seasonal, UpdateMode.PASSIVE),
                deltas -> {
                    try {
                        applyDeltas(deltas, dayTime, UpdateMode.PASSIVE);
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

    private static List<StateView> snapshotStates(Collection<RegionInstanceKey> keys) {
        Map<RegionInstanceKey, RegionAtmosphereState> states = AtmosphericStateRegistry.getStatesAsMap();
        List<StateView> views = new ArrayList<>(keys.size());
        for (RegionInstanceKey key : keys) {
            RegionAtmosphereState state = states.get(key);
            if (state == null || state.getPosition() == null) {
                continue;
            }
            views.add(new StateView(
                    key,
                    state.getTemperature(),
                    state.getHumidity(),
                    state.getPressure(),
                    state.getCloudCover(),
                    state.getSunlight(),
                    state.getRainIntensity(),
                    state.getBiomeSunlightMultiplier(),
                    state.getBaselineMinTemperature(),
                    state.getBaselineMaxTemperature()
            ));
        }
        return views;
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

            float clampedRain = Math.min(1f, view.rainIntensity());
            float rainTemperatureDelta = -clampedRain * COOLING_SCALE * mode.scale();
            float rainHumidityDelta = -clampedRain * HUMIDITY_DRAIN * mode.scale();
            float rainPressureDelta = clampedRain * PRESSURE_RESTORE * mode.scale();

            float humidityDelta = ((view.rainIntensity() * 0.02f) - (sunlightFactor * 0.01f)) * mode.scale();
            humidityDelta += rainHumidityDelta;

            float temperatureDelta = clampDelta(sunlightTemperatureDelta + rainTemperatureDelta, -20f, 20f);
            humidityDelta = clampDelta(humidityDelta, -0.35f, 0.35f);
            float pressureDelta = clampDelta(rainPressureDelta, -15f, 15f);

            float rainFade = RAIN_FADE * mode.rainFadeScale();

            deltas.add(new StateDelta(
                    view.key(),
                    temperatureDelta,
                    humidityDelta,
                    pressureDelta,
                    sunlightFactor,
                    rainFade,
                    mode.relaxFactor()
            ));
        }
        return deltas;
    }

    private static void applyDeltas(List<StateDelta> deltas, long dayTime, UpdateMode mode) {
        for (StateDelta delta : deltas) {
            RegionAtmosphereState state = AtmosphericStateRegistry.getState(delta.key());
            if (state == null) {
                continue;
            }
            state.setSunlight(delta.sunlight());
            state.adjustTemperature(delta.temperatureDelta());
            state.adjustHumidity(delta.humidityDelta());
            state.adjustPressure(delta.pressureDelta());
            if (delta.rainFade() > 0f) {
                state.dampenRain(delta.rainFade());
            }
            if (delta.relaxFactor() > 0f) {
                state.relaxTowardBase(delta.relaxFactor());
            }
            if (mode == UpdateMode.ACTIVE) {
                state.recordDailySnapshot(dayTime);
            }
        }
    }

    private static float baseDaylightCurve(float sunAngle) {
        float cosine = (float) Math.cos(sunAngle);
        float daylight = Mth.clamp(cosine, 0f, 1f);
        return daylight * daylight;
    }

    private static float seasonalTilt(ServerLevel level) {
        long day = level.getDayTime() / 24000L;
        float seasonProgress = (day % 96L) / 96f;
        return 0.85f + 0.15f * Mth.cos(seasonProgress * (float) (Math.PI * 2));
    }

    private static float clampDelta(float value, float min, float max) {
        return Mth.clamp(value, min, max);
    }

    private record StateView(
            RegionInstanceKey key,
            float temperature,
            float humidity,
            float pressure,
            float cloudCover,
            float sunlight,
            float rainIntensity,
            float sunlightMultiplier,
            float baselineMin,
            float baselineMax
    ) {
    }

    private record StateDelta(
            RegionInstanceKey key,
            float temperatureDelta,
            float humidityDelta,
            float pressureDelta,
            float sunlight,
            float rainFade,
            float relaxFactor
    ) {
    }

    private enum UpdateMode {
        ACTIVE(1f, 0.0005f, 0.6f, 1f),
        PASSIVE(0.35f, 0.0002f, 0.45f, 0.5f);

        private final float scale;
        private final float relaxFactor;
        private final float blend;
        private final float rainFadeScale;

        UpdateMode(float scale, float relaxFactor, float blend, float rainFadeScale) {
            this.scale = scale;
            this.relaxFactor = relaxFactor;
            this.blend = blend;
            this.rainFadeScale = rainFadeScale;
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
    }
}
