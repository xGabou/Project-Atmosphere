package net.Gabou.projectatmosphere.modules.tornado;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import net.Gabou.projectatmosphere.modules.core.WindVector;

public class TornadoInstance {

    public Vec3 position;
    public final long spawnTime;
    public final float radius;
    public final WindVector wind;

    private float angularSpeed = 0.15f; 
    private long lastDemolitionCheck = 0L;
    private final long demolitionIntervalMs = 1000L; 

    public TornadoInstance(Vec3 position, float radius, WindVector wind) {
        this(position, radius, wind, 0.15f);
    }

    public TornadoInstance(Vec3 position, float radius, WindVector wind, float angularSpeed) {
        this.position = position;
        this.radius = radius;
        this.wind = wind;
        this.angularSpeed = angularSpeed;
        this.spawnTime = System.currentTimeMillis();
    }

    public float getLifetimeSeconds() {
        return (System.currentTimeMillis() - spawnTime) / 1000f;
    }

    public float getTwist() {
        long elapsedMs = System.currentTimeMillis() - spawnTime;
        float elapsedTicks = elapsedMs / 50.0f;
        return elapsedTicks * angularSpeed;
    }

    /**
     * Called each tick from tornado manager. Server handles demolition,
     * client relies on separate rendering logic.
     */
    public void tick(Level level) {
        if (level.isClientSide) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastDemolitionCheck >= demolitionIntervalMs) {
            lastDemolitionCheck = now;
            playDemolitionSound(level);
            demolishBlocks((ServerLevel) level);
        }
    }

    private void demolishBlocks(ServerLevel level) {
        BlockPos center = BlockPos.containing(position);
        int intRadius = Mth.ceil(radius);
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-intRadius, -1, -intRadius),
                center.offset(intRadius, intRadius, intRadius))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) {
                level.destroyBlock(pos, false);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        5, 0.2, 0.2, 0.2, 0.05);
            }
        }
    }

    private void playDemolitionSound(Level level) {
        BlockPos center = BlockPos.containing(position);

        level.playLocalSound(
                center.getX(), center.getY(), center.getZ(),
                SoundEvents.GENERIC_EXPLODE,
                SoundSource.WEATHER,
                2.0f, 
                0.5f + level.getRandom().nextFloat() * 0.4f, 
                false
        );
    }
}
