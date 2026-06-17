package net.Gabou.projectatmosphere.modules.seasonaltrees.transport;

import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.seasonaltrees.core.SeedPayload;
import net.Gabou.projectatmosphere.modules.seasonaltrees.core.SeasonalTreesCore;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class WindSeedTransport implements SeasonalTreesSeedTransport {
    private final List<SeedParticle> particles = new ArrayList<>();

    @Override
    public boolean isEnabled() {
        return AtmoCommonConfig.SEASONAL_TREES_WIND_TRANSPORT_ENABLED.get();
    }

    @Override
    public boolean offerSeed(ServerLevel level, SeedPayload payload) {
        if (!isEnabled()) {
            return false;
        }
        int maxSeeds = AtmoCommonConfig.SEASONAL_TREES_MAX_ACTIVE_SEEDS.get();
        if (particles.size() >= maxSeeds) {
            return false;
        }
        BlockPos source = payload.sourcePos();
        Vec3 pos = new Vec3(source.getX() + 0.5d, source.getY() + 1.2d, source.getZ() + 0.5d);
        Vec3 velocity = initialWindVelocity(level, source);
        int lifetime = AtmoCommonConfig.SEASONAL_TREES_SEED_LIFETIME_TICKS.get();
        particles.add(new SeedParticle(pos, velocity, payload, lifetime));
        return true;
    }

    @Override
    public void tick(ServerLevel level) {
        if (particles.isEmpty()) {
            return;
        }
        long tick = level.getGameTime();
        double windScale = AtmoCommonConfig.SEASONAL_TREES_SEED_BASE_SPEED.get();
        Iterator<SeedParticle> iterator = particles.iterator();
        while (iterator.hasNext()) {
            SeedParticle particle = iterator.next();
            if (particle.age >= particle.lifetime) {
                tryPlant(level, particle);
                iterator.remove();
                continue;
            }
            if (!level.hasChunkAt(BlockPos.containing(particle.position))) {
                iterator.remove();
                continue;
            }
            WindVector wind = ForecastOrchestrator.getWind(BlockPos.containing(particle.position), tick);
            float speed = wind.gustSpeed() > 0f ? wind.gustSpeed() : wind.baseSpeed();
            double angle = wind.angleRadians();
            Vec3 windVel = new Vec3(-Math.sin(angle), 0.0d, Math.cos(angle)).scale(speed * windScale);

            particle.velocity = particle.velocity.add(windVel.scale(0.08d));
            particle.velocity = particle.velocity.add(0.0d, -0.003d, 0.0d);

            particle.position = particle.position.add(particle.velocity);
            particle.age++;

            if (isOnGround(level, particle.position)) {
                tryPlant(level, particle);
                iterator.remove();
            }
        }
    }

    @Override
    public int getActiveSeedCount() {
        return particles.size();
    }

    private Vec3 initialWindVelocity(ServerLevel level, BlockPos pos) {
        WindVector wind = ForecastOrchestrator.getWind(pos, level.getGameTime());
        float speed = wind.gustSpeed() > 0f ? wind.gustSpeed() : wind.baseSpeed();
        double angle = wind.angleRadians();
        double scale = AtmoCommonConfig.SEASONAL_TREES_SEED_BASE_SPEED.get();
        return new Vec3(-Math.sin(angle), 0.03d, Math.cos(angle)).scale(speed * scale);
    }

    private boolean isOnGround(ServerLevel level, Vec3 pos) {
        BlockPos below = new BlockPos(Mth.floor(pos.x), Mth.floor(pos.y - 0.1d), Mth.floor(pos.z));
        return level.getBlockState(below).isSolid();
    }

    private void tryPlant(ServerLevel level, SeedParticle particle) {
        BlockPos landing = new BlockPos(Mth.floor(particle.position.x), Mth.floor(particle.position.y), Mth.floor(particle.position.z));
        SeasonalTreesCore.tryPlantSeedAt(level, landing, particle.payload);
    }

    private static final class SeedParticle {
        private Vec3 position;
        private Vec3 velocity;
        private final SeedPayload payload;
        private final int lifetime;
        private int age;

        private SeedParticle(Vec3 position, Vec3 velocity, SeedPayload payload, int lifetime) {
            this.position = position;
            this.velocity = velocity;
            this.payload = payload;
            this.lifetime = lifetime;
        }
    }
}
