package net.Gabou.projectatmosphere.modules.tornado;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
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
import net.minecraft.world.level.chunk.LevelChunk;
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

    private final CloudRegion cloudRegion;

    private float angularSpeed = 0.15f;
    private long lastDemolitionCheck = 0L;
    private final long demolitionIntervalMs = 1000L;
    private final long ambientWindIntervalMs = 2000L;
    private long lastAmbientWindCheck = 0L;


    private final TornadoLevel level;


    public TornadoLevel getLevel() {
        return level;
    }

    public CloudRegion getCloudRegion() {
        return cloudRegion;
    }

    public double getSuctionRadius() {
        return level.getBaseDamage() * 2;
    }

    public double getDamageMultiplier() {
        return level.getBaseDamage();
    }

    public TornadoInstance(Vec3 position, float radius, WindVector wind,CloudRegion cloudRegion) {
        this(position, radius, wind, 0.05f,cloudRegion);
    }

    public TornadoInstance(Vec3 position, float radius, WindVector wind, float angularSpeed, CloudRegion cloudRegion) {
        this.position = position;
        this.radius = radius;
        this.wind = wind;
        this.angularSpeed = angularSpeed;
        this.spawnTime = System.currentTimeMillis();
        this.level = TornadoLevel.fromWindSpeed(wind.baseSpeed());
        this.cloudRegion = cloudRegion;

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
                        level.getServer().execute(()->playDemolitionSound(level));
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

    // worker thread seulement
    private void demolishBlocks(ServerLevel level) {
        final BlockPos center = BlockPos.containing(position);
        final int intRadius = Mth.ceil(radius);
        final double outerSq = (radius + 5) * (radius + 5);
        final double innerSq = radius * radius;
        final double band = Math.max(1.0, outerSq - innerSq);
        final double invBand = 1.0 / band;
        final BlockPos min = center.offset(-intRadius - DEBRIS_RANGE_EXTENSION, 0, -intRadius - DEBRIS_RANGE_EXTENSION);
        final BlockPos max = center.offset( intRadius + DEBRIS_RANGE_EXTENSION, 3 + intRadius,  intRadius + DEBRIS_RANGE_EXTENSION);

        it.unimi.dsi.fastutil.longs.LongArrayList toDestroy = new it.unimi.dsi.fastutil.longs.LongArrayList(2048);
        it.unimi.dsi.fastutil.longs.LongArrayList toDestroyGlass = new it.unimi.dsi.fastutil.longs.LongArrayList(2048);

        // lecture off thread avec checks stricts
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            // ne charge pas de chunk ici
            if (!level.isLoaded(pos)) continue;

            try {
                // récupère le chunk si déjà chargé sinon skip
                LevelChunk chunk = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, false);
                if (chunk == null) continue;

                // lecture état depuis le chunk existant
                BlockState state = chunk.getBlockState(pos);
                if (state.isAir()) continue;
                final double distSq = pos.distSqr(center);
                // ton test demandé hors main thread
                if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) {
                    toDestroy.add(pos.asLong());
                }
                else if (AtmosphereUtils.isGlass(state)) {
                    if (distSq > outerSq) continue;

                    final float pMax = 0.35f;
                    final double t = Mth.clamp((outerSq - distSq) * invBand, 0.0, 1.0);
                    final float p = (float) (t * pMax);

                    if (level.random.nextFloat() < p) {
                        toDestroyGlass.add(pos.asLong());
                    }
                }


            } catch (Throwable t) {
                // au moindre souci on ignore cette position
            }
        }

        if (toDestroy.isEmpty()) return;

        // destruction uniquement sur le thread serveur
        final int perTick = 256;
        this._destroyCursor = 0;
        level.getServer().execute(() -> processLeafLogDestruction(level, toDestroy, perTick));
        GlassDamageManager.damageGlass(level, toDestroyGlass);
    }

    // curseur pour le batching
    private int _destroyCursor = 0;

    // main thread seulement
    private void processLeafLogDestruction(ServerLevel level,
                                           it.unimi.dsi.fastutil.longs.LongArrayList list,
                                           int perTick) {
        if (_destroyCursor >= list.size()) { _destroyCursor = 0; return; }

        int end = Math.min(_destroyCursor + perTick, list.size());
        for (int i = _destroyCursor; i < end; i++) {
            BlockPos pos = BlockPos.of(list.getLong(i));
            if (!level.isLoaded(pos)) continue;

            BlockState state = level.getBlockState(pos);
            if (!(state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS))) continue;
            level.destroyBlock(pos, false);
        }

        _destroyCursor = end;
        if (_destroyCursor < list.size()) {
            level.getServer().execute(() -> processLeafLogDestruction(level, list, perTick));
        } else {
            _destroyCursor = 0;
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
