package net.Gabou.projectatmosphere.modules.atmosphere;

import net.Gabou.projectatmosphere.async.PoolType;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CycloneManager {
    private static final List<Cyclone> ACTIVE_CYCLONES = new CopyOnWriteArrayList<>();
    private static final long COOLDOWN_TICKS = 24000L * 2;
    private static long lastSpawnTick = -COOLDOWN_TICKS;
    private static long lastMidnightTick = -1L;

    private CycloneManager() {
    }

    public static void initialize(ServerLevel level) {
        ACTIVE_CYCLONES.clear();
        lastSpawnTick = level.getDayTime();
        lastMidnightTick = -1L;
        spawnInitialCyclones(level);
    }

    public static void update(ServerLevel level) {
        if (AtmosphericStateRegistry.isEmpty()) {
            return;
        }

        long dayTime = level.getDayTime();
        if (dayTime % 24000L == 0 && lastMidnightTick != dayTime) {
            onMidnight(level);
            lastMidnightTick = dayTime;
        }

        List<RegionAtmosphereState> snapshot = AtmosphericStateRegistry.snapshot();
        for (Cyclone cyclone : new ArrayList<>(ACTIVE_CYCLONES)) {
            AsyncAtmosphereService.runWithCallback(
                    PoolType.WEATHER,
                    () -> cyclone.tick(snapshot, dayTime),
                    result -> {
                        if (result == null) {
                            return;
                        }
                        applyCyclone(result);
                        if (result.remove()) {
                            ACTIVE_CYCLONES.remove(cyclone);
                        }
                    }
            );
        }
    }

    public static void onMidnight(ServerLevel level) {
        if (ACTIVE_CYCLONES.size() >= 4) {
            return;
        }
        long now = level.getDayTime();
        if (!cooldownPassed(now)) {
            return;
        }
        // Precompute nearby region candidates
        List<RegionAtmosphereState> candidates = findNearbyStates(level);

        if (candidates.isEmpty()) {
            return; // no valid region nearby -> skip all
        }
        RandomSource random = createRandom(level);
        spawnCyclone(level, candidates, random);
    }

    private static boolean cooldownPassed(long now) {
        return now - lastSpawnTick >= COOLDOWN_TICKS;
    }

    private static void spawnInitialCyclones(ServerLevel level) {
        RandomSource random = createRandom(level);
        int count = 3 + random.nextInt(6);

        // Precompute nearby region candidates
        List<RegionAtmosphereState> candidates = findNearbyStates(level);

        if (candidates.isEmpty()) {
            return; // no valid region nearby -> skip all
        }

        for (int i = 0; i < count; i++) {
            spawnCyclone(level, candidates, random);
        }
    }


    private static void spawnCyclone(ServerLevel level, List<RegionAtmosphereState> candidates, RandomSource random) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        // Pick one candidate directly
        RegionAtmosphereState state = candidates.get(random.nextInt(candidates.size()));

        float radius = 180f + random.nextFloat() * 140f;
        float intensity = 0.4f + random.nextFloat() * 0.4f;
        float pressureDrop = 5f + random.nextFloat() * 10f;
        long lifetime = 24000L + random.nextInt(24000);

        ACTIVE_CYCLONES.add(new Cyclone(
                new Vec2(state.getPosition().getX(), state.getPosition().getZ()),
                radius,
                intensity,
                pressureDrop,
                lifetime
        ));

        lastSpawnTick = level.getDayTime();
    }

    private static RandomSource createRandom(ServerLevel level) {
        long seed = level.getSeed() ^ (level.getDayTime() * 31L);
        return RandomSource.create(seed);
    }
    private static List<RegionAtmosphereState> findNearbyStates(ServerLevel level) {
        List<BlockPos> players = level.players().stream()
                .map(ServerPlayer::blockPosition)
                .toList();

        if (players.isEmpty()) return List.of();

        final double MAX_DIST_SQ = 5000d * 5000d;

        return AtmosphericStateRegistry.snapshot().stream()
                .filter(state -> {
                    BlockPos pos = state.getPosition();
                    if (pos == null) return false;

                    for (BlockPos player : players) {
                        double dx = pos.getX() - player.getX();
                        double dz = pos.getZ() - player.getZ();
                        if ((dx * dx + dz * dz) <= MAX_DIST_SQ) {
                            return true;
                        }
                    }
                    return false;
                })
                .toList();
    }



    private static final class Cyclone {
        private Vec2 center;
        private float radius;
        private float intensity;
        private final float corePressureDrop;
        private long lifetimeTicks;
        private int counter = 0;

        private Cyclone(Vec2 center, float radius, float intensity, float corePressureDrop, long lifetimeTicks) {
            this.center = center;
            this.radius = radius;
            this.intensity = intensity;
            this.corePressureDrop = corePressureDrop;
            this.lifetimeTicks = lifetimeTicks;
        }

        private CycloneStep tick(List<RegionAtmosphereState> snapshot, long gameTime) {
            List<CycloneDelta> deltas = List.of();
            if (counter++ % 20 == 0) {
                deltas = applyEffects(snapshot);
            }
            drift(snapshot, gameTime);
            lifetimeTicks--;
            intensity *= 0.9995f;
            if (lifetimeTicks <= 0 || intensity < 0.05f) {
                return new CycloneStep(true, deltas);
            }
            radius = Mth.clamp(radius + (intensity - 0.5f) * 0.8f, 120f, 420f);
            return new CycloneStep(false, deltas);
        }

        private List<CycloneDelta> applyEffects(List<RegionAtmosphereState> states) {
            List<CycloneDelta> deltas = new ArrayList<>();
            if (states.isEmpty()) {
                return deltas;
            }
            float maxPressureDrop = Math.min(12f, corePressureDrop);
            for (RegionAtmosphereState state : states) {
                double dx = state.getPosition().getX() - center.x;
                double dz = state.getPosition().getZ() - center.y;
                double distance = Math.sqrt(dx * dx + dz * dz);
                float influence = (float) (1d - (distance / radius));
                if (influence <= 0f) {
                    influence = 0f;
                }
                float scaledInfluence = influence * intensity;
                float pressureDelta = Mth.clamp(-maxPressureDrop * scaledInfluence, -20f, 0f);
                float humidityDelta = Mth.clamp(scaledInfluence, 0f, 0.6f);
                float temperatureDelta = Mth.clamp(-scaledInfluence * 2f, -8f, 0f);
                float rainCeil = Mth.clamp(scaledInfluence, 0f, 1f);
                float cloudCeil = Mth.clamp(state.getCloudCover() + scaledInfluence * 0.25f, 0f, 1f);
                deltas.add(new CycloneDelta(state.getKey(), temperatureDelta, humidityDelta, pressureDelta, rainCeil, cloudCeil));
            }
            return deltas;
        }

        private void drift(List<RegionAtmosphereState> states, long gameTime) {
            RegionAtmosphereState nearest = findNearest(states, center.x, center.y);
            if (nearest == null) {
                return;
            }
            WindVector wind = ForecastOrchestrator.getWind(nearest.getKey(), gameTime);
            float speed = Math.max(0.05f, wind.baseSpeed() * 0.02f);
            float angle = wind.angleRadians();
            float dx = (float) Math.sin(angle) * speed;
            float dz = (float) Math.cos(angle) * speed;
            center = center.add(new Vec2(dx, dz));
        }

        private RegionAtmosphereState findNearest(List<RegionAtmosphereState> states, double x, double z) {
            RegionAtmosphereState nearest = null;
            double best = Double.MAX_VALUE;
            for (RegionAtmosphereState state : states) {
                double dist = state.distanceTo(x, z);
                if (dist < best) {
                    best = dist;
                    nearest = state;
                }
            }
            return nearest;
        }
    }

    private static void applyCyclone(CycloneStep step) {
        if (step.deltas().isEmpty()) {
            return;
        }
        for (CycloneDelta delta : step.deltas()) {
            RegionAtmosphereState state = AtmosphericStateRegistry.getState(delta.key());
            if (state == null) {
                continue;
            }
            state.adjustTemperature(delta.temperatureDelta());
            state.adjustHumidity(delta.humidityDelta());
            state.adjustPressure(delta.pressureDelta());
            state.setRainIntensity(Math.min(1f, Math.max(state.getRainIntensity(), delta.rainCeil())));
            state.setCloudCover(Math.min(1f, Math.max(state.getCloudCover(), delta.cloudCeil())));
        }
    }

    private record CycloneStep(boolean remove, List<CycloneDelta> deltas) {
    }

    private record CycloneDelta(RegionInstanceKey key,
                                float temperatureDelta,
                                float humidityDelta,
                                float pressureDelta,
                                float rainCeil,
                                float cloudCeil) {
    }
}
