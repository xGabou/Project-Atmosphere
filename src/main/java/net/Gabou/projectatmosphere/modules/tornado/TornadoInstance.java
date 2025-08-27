package net.Gabou.projectatmosphere.modules.tornado;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.TintedGlassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.Gabou.projectatmosphere.modules.core.WindVector;

public class TornadoInstance {

    private static final int DEBRIS_RANGE_EXTENSION = 5;
    public static final double AMBIENT_WIND_INFLUENCE_EXTENSION= 15;
    public static final double WIND_SPEED_SCALING_FACTOR= 0.05;
    public static final double WIND_EFFECT_VERTICAL_MAX_OFFSET = 50;
    public static final double WIND_EFFECT_VERTICAL_MIN_OFFSET= -5;
    public Vec3 position;
    public final long spawnTime;
    public final float radius;
    public final WindVector wind;

    private float angularSpeed = 0.15f; 
    private long lastDemolitionCheck = 0L;
    private final long demolitionIntervalMs = 1000L;
    private final long ambientWindIntervalMs = 2000L;
    private long lastAmbientWindCheck = 0L;


    private final TornadoLevel level;


    public TornadoLevel getLevel() {
        return level;
    }

    public double getSuctionRadius() {
        return level.getBaseDamage() * 2;
    }

    public double getDamageMultiplier() {
        return level.getBaseDamage();
    }

    public TornadoInstance(Vec3 position, float radius, WindVector wind) {
        this(position, radius, wind, 0.05f);
    }

    public TornadoInstance(Vec3 position, float radius, WindVector wind, float angularSpeed) {
        this.position = position;
        this.radius = radius;
        this.wind = wind;
        this.angularSpeed = angularSpeed;
        this.spawnTime = System.currentTimeMillis();
        this.level = TornadoLevel.fromWindSpeed(wind.baseSpeed());
    }

    public float getLifetimeSeconds() {
        return (System.currentTimeMillis() - spawnTime) / 1000f;
    }

    public float getTwist() {
        long elapsedMs = System.currentTimeMillis() - spawnTime;
        float elapsedTicks = elapsedMs / 100.0f;
        return Mth.clamp(elapsedTicks * angularSpeed,0.5f,5.0f);
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

        boolean ambientDue = now - lastAmbientWindCheck >= ambientWindIntervalMs;
        boolean demolitionDue = now - lastDemolitionCheck >= demolitionIntervalMs;

        if (ambientDue || demolitionDue) {
            AsyncAtmosphereService.runStorm(() -> {
                try {
                    if (ambientDue) {
                        lastAmbientWindCheck = now;
                        applyAmbientWind(level);
                    }
                    if (demolitionDue) {
                        lastDemolitionCheck = now;
                        demolishBlocks((ServerLevel) level);
                        playDemolitionSound(level);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

    }


    private void applyAmbientWind(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        double influence = radius + AMBIENT_WIND_INFLUENCE_EXTENSION;
        AABB box = new AABB(
                position.x - influence, position.y - 5,
                position.z - influence, position.x + influence,
                position.x - influence, position.y + WIND_EFFECT_VERTICAL_MIN_OFFSET
        );

        double windSpeed = wind.gustSpeed() * WIND_SPEED_SCALING_FACTOR;
        double vx = Math.cos(wind.angleRadians()) * windSpeed;
        double vz = Math.sin(wind.angleRadians()) * windSpeed;

        for (Entity entity : serverLevel.getEntities(null, box)) {
            entity.push(vx, 0, vz);
            if(entity instanceof Player)
                ProjectAtmosphere.LOGGER.info("Pushed player by wind: vx=" + vx + ", vz=" + vz);
        }
    }

    private void demolishBlocks(ServerLevel level) {
        // Precompute once per tick/per tornado
        final long tick = level.getGameTime();
        final ServerLevel sLevel = (ServerLevel) level;
        final double innerSq = radius * radius;
        final double outerSq = (radius + 5) * (radius + 5);
        final double band = Math.max(1.0, outerSq - innerSq);
        final double invBand = 1.0 / band; // reuse
// Optional: time-slice to spread load (every 2 ticks per pos "bucket")
        final int sliceMod = 2;
        BlockPos center = BlockPos.containing(position);
        int intRadius = Mth.ceil(radius);
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-intRadius - DEBRIS_RANGE_EXTENSION, -1, -intRadius - DEBRIS_RANGE_EXTENSION),
                center.offset(intRadius + DEBRIS_RANGE_EXTENSION, intRadius+3, intRadius + DEBRIS_RANGE_EXTENSION))) {
            BlockState state = level.getBlockState(pos);
            double distSq = pos.distSqr(center);
            if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) {
                level.destroyBlock(pos, false);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        5, 0.2, 0.2, 0.2, 0.05);
            } else if (isGlass(state)) {
                // quick reject
                if (distSq > outerSq) return;

                // time-slice (hash by pos to spread work)
                if (((pos.asLong() ^ tick) & (sliceMod - 1)) != 0) return;

                // probability grows linearly from outer edge (≈0) to inner edge (≈pMax)
                // tune pMax; 0.35f means ~35% hit chance right at the core per slice tick
                final float pMax = 0.35f;
                final double t = Math.min(1.0, Math.max(0.0, (outerSq - distSq) * invBand)); // 0..1
                final float p = (float)(t * pMax);

                // one RNG + one branch
                if (sLevel.random.nextFloat() < p) {
                    // constant damage = 1; breaks over time; inner area just hits more often
                    GlassDamageManager.damageGlass(sLevel, pos, state, 1);
                }
            }
        }
    }

    private boolean isGlass(BlockState state) {
        return state.getBlock() instanceof GlassBlock
                || state.getBlock() instanceof StainedGlassBlock
                || state.getBlock() instanceof StainedGlassPaneBlock
                || state.getBlock() instanceof TintedGlassBlock;
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
