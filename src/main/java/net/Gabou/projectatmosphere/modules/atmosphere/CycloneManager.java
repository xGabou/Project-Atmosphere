package net.Gabou.projectatmosphere.modules.atmosphere;

import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec2;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class CycloneManager {
    private static final List<Cyclone> ACTIVE_CYCLONES = new ArrayList<>();
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

        Iterator<Cyclone> it = ACTIVE_CYCLONES.iterator();
        while (it.hasNext()) {
            Cyclone cyclone = it.next();
            if (cyclone.tick(level)) {
                it.remove();
            }
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
        spawnCyclone(level);
    }

    private static boolean cooldownPassed(long now) {
        return now - lastSpawnTick >= COOLDOWN_TICKS;
    }

    private static void spawnInitialCyclones(ServerLevel level) {
        RandomSource random = level.random;
        int count = 3 + random.nextInt(2);
        for (int i = 0; i < count; i++) {
            spawnCyclone(level);
        }
    }

    private static void spawnCyclone(ServerLevel level) {
        RandomSource random = level.random;
        var stateOpt = AtmosphericStateRegistry.getRandomState(random);
        if (stateOpt.isEmpty()) {
            return;
        }
        RegionAtmosphereState state = stateOpt.get();
        float radius = 180f + random.nextFloat() * 140f;
        float intensity = 0.4f + random.nextFloat() * 0.4f;
        float pressureDrop = 5f + random.nextFloat() * 10f;
        long lifetime = 24000L + random.nextInt(24000);
        ACTIVE_CYCLONES.add(new Cyclone(new Vec2(state.getPosition().getX(), state.getPosition().getZ()), radius, intensity, pressureDrop, lifetime));
        lastSpawnTick = level.getDayTime();
    }

    private static final class Cyclone {
        private Vec2 center;
        private float radius;
        private float intensity;
        private final float corePressureDrop;
        private long lifetimeTicks;

        private Cyclone(Vec2 center, float radius, float intensity, float corePressureDrop, long lifetimeTicks) {
            this.center = center;
            this.radius = radius;
            this.intensity = intensity;
            this.corePressureDrop = corePressureDrop;
            this.lifetimeTicks = lifetimeTicks;
        }

        private boolean tick(ServerLevel level) {
            applyEffects();
            drift(level);
            lifetimeTicks--;
            intensity *= 0.9995f;
            if (lifetimeTicks <= 0 || intensity < 0.05f) {
                return true;
            }
            radius = Mth.clamp(radius + (intensity - 0.5f) * 0.8f, 120f, 420f);
            return false;
        }

        private void applyEffects() {
            List<RegionAtmosphereState> states = AtmosphericStateRegistry.snapshot();
            for (RegionAtmosphereState state : states) {
                double dx = state.getPosition().getX() - center.x;
                double dz = state.getPosition().getZ() - center.y;
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > radius) {
                    continue;
                }
                float influence = (float) (1d - (distance / radius));
                state.adjustPressure(-corePressureDrop * influence * intensity);
                state.adjustHumidity(influence * intensity * 0.05f);
                state.adjustTemperature(-influence * intensity * 0.5f);
                state.setRainIntensity(Math.max(state.getRainIntensity(), influence * intensity));
                state.setCloudCover(Math.min(1f, state.getCloudCover() + influence * 0.25f));
            }
        }

        private void drift(ServerLevel level) {
            RegionAtmosphereState nearest = AtmosphericStateRegistry.findNearest(center.x, center.y);
            if (nearest == null) {
                return;
            }
            WindVector wind = nearest.getWind();
            if (wind == null) {
                return;
            }
            float speed = Math.max(0.05f, wind.baseSpeed() * 0.02f);
            float angle = wind.angleRadians();
            float dx = (float) Math.sin(angle) * speed;
            float dz = (float) Math.cos(angle) * speed;
            center = center.add(new Vec2(dx,dz));
        }
    }
}
