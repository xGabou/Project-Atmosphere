package net.Gabou.projectatmosphere.modules.tornado;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import net.Gabou.projectatmosphere.api.common.cloud.region.ITornadoRegion;
import net.Gabou.projectatmosphere.api.common.cloud.region.TornadoDescriptor;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.Gabou.projectatmosphere.modules.core.WindVector;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class TornadoInstance {

    private static final int DEBRIS_RANGE_EXTENSION = 5;
    public static final double AMBIENT_WIND_INFLUENCE_EXTENSION= 15;
    public static final double WIND_SPEED_SCALING_FACTOR= 0.05;
    public static final double WIND_EFFECT_VERTICAL_MAX_OFFSET = 50;
    public static final double WIND_EFFECT_VERTICAL_MIN_OFFSET= -5;
    private final UUID id;
    public Vec3 position;
    public final long spawnTime;
    public final float radius;
    public final WindVector wind;
    private float visualBottomY;
    private float visualHeight;
    private boolean descriptorMissing;

    @Nullable
    private CloudRegion cloudRegion;

    private float angularSpeed = 0.15f;
    private long lastDemolitionCheck = 0L;
    private final long demolitionIntervalMs = 1000L;
    private final long ambientWindIntervalMs = 2000L;
    private long lastAmbientWindCheck = 0L;


    private final TornadoLevel level;


    public TornadoLevel getLevel() {
        return level;
    }

    @Nullable
    public CloudRegion getCloudRegion() {
        return this.cloudRegion;
    }

    public UUID getId() {
        return this.id;
    }

    public float getVisualBottomY() {
        return this.visualBottomY;
    }

    public float getVisualHeight() {
        return this.visualHeight;
    }

    public double getSuctionRadius() {
        return level.getBaseDamage() * 2;
    }

    public double getDamageMultiplier() {
        return level.getBaseDamage();
    }

    public TornadoInstance(Vec3 position, float radius, WindVector wind, @Nullable CloudRegion cloudRegion) {
        this(UUID.randomUUID(), position, radius, wind, 0.05f, (float)position.y, Math.max(96.0F, radius * 12.0F), cloudRegion);
    }

    public TornadoInstance(UUID id, Vec3 position, float radius, WindVector wind,
                           float visualBottomY, float visualHeight, @Nullable CloudRegion cloudRegion) {
        this(id, position, radius, wind, 0.05f, visualBottomY, visualHeight, cloudRegion);
    }

    public TornadoInstance(UUID id, Vec3 position, float radius, WindVector wind, float angularSpeed,
                           float visualBottomY, float visualHeight, @Nullable CloudRegion cloudRegion) {
        this.id = id;
        this.position = position;
        this.radius = radius;
        this.wind = wind;
        this.angularSpeed = angularSpeed;
        this.spawnTime = System.currentTimeMillis();
        this.level = TornadoLevel.fromWindSpeed(wind.baseSpeed());
        this.cloudRegion = cloudRegion;
        this.visualBottomY = visualBottomY;
        this.visualHeight = visualHeight;
    }

    public float getLifetimeSeconds() {
        return (System.currentTimeMillis() - spawnTime) / 1000f;
    }

    public float getTwist() {
        long elapsedMs = System.currentTimeMillis() - spawnTime;
        float elapsedTicks = elapsedMs / 100.0f;
        return Mth.clamp(elapsedTicks * angularSpeed,0.5f,5.0f);
    }

    public void setCloudRegion(@Nullable CloudRegion cloudRegion) {
        this.cloudRegion = cloudRegion;
    }

    public boolean isDescriptorMissing() {
        return this.descriptorMissing;
    }

    public boolean synchronizeWithDescriptor() {
        TornadoDescriptor descriptor = this.findDescriptor();
        if (descriptor == null) {
            this.descriptorMissing = this.cloudRegion instanceof ITornadoRegion;
            return false;
        }

        this.descriptorMissing = false;
        this.visualBottomY = descriptor.getBottomY();
        this.visualHeight = descriptor.getHeight();
        this.position = new Vec3(
                this.cloudRegion.getWorldX() + descriptor.getOffsetX(),
                descriptor.getBottomY(),
                this.cloudRegion.getWorldZ() + descriptor.getOffsetZ()
        );
        return true;
    }

    public void advanceByWind() {
        float speed = this.wind.baseSpeed() * 0.2f;
        this.position = this.position.add(
                Math.cos(this.wind.angleRadians()) * speed,
                0.0,
                Math.sin(this.wind.angleRadians()) * speed
        );
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
        double minY = position.y + WIND_EFFECT_VERTICAL_MIN_OFFSET;
        double maxY = position.y + WIND_EFFECT_VERTICAL_MAX_OFFSET;
        AABB box = new AABB(
                position.x - influence, minY,
                position.z - influence, position.x + influence,
                maxY, position.z + influence
        );

        // Base along-wind component (ambient)
        double ambientSpeed = Math.max(0.0, wind.gustSpeed()) * WIND_SPEED_SCALING_FACTOR;
        double ax = Math.cos(wind.angleRadians()) * ambientSpeed;
        double az = Math.sin(wind.angleRadians()) * ambientSpeed;

        for (Entity entity : serverLevel.getEntities(null, box)) {
            // Vector from entity to tornado center (horizontal)
            double dx = position.x - entity.getX();
            double dz = position.z - entity.getZ();
            double distSq = dx * dx + dz * dz;
            double dist = Math.sqrt(distSq);

            // Avoid div by zero; normalize inward vector
            double nx = dist > 1e-4 ? dx / dist : 0.0;
            double nz = dist > 1e-4 ? dz / dist : 0.0;

            // Suction strength scales with proximity, capped
            double suctionRadius = Math.max(4.0, this.getSuctionRadius());
            double proximity = Math.max(0.0, 1.0 - Math.min(dist, suctionRadius) / suctionRadius);

            // Tangential (swirl) component: perpendicular to inward vector
            double swirlDirX = -nz;
            double swirlDirZ = nx;

            // Scale factors — stronger than before; scales with tornado level
            double baseMag = 0.06 + this.getLevel().getMaxWindSpeed() * 0.02; // stronger baseline
            double suctionMag = baseMag * 2.0 * proximity; // inward
            double swirlMag = baseMag * 1.5 * Math.sqrt(proximity); // rotational swirl

            double vx = ax + nx * suctionMag + swirlDirX * swirlMag;
            double vz = az + nz * suctionMag + swirlDirZ * swirlMag;

            // Vertical lift increases near center
            double vy = 0.02 * proximity * (1.0 + this.getLevel().getMaxWindSpeed() * 0.02);

            entity.push(vx, vy, vz);
            if (entity instanceof Player) {
                entity.hurtMarked = true;
            }
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

        RandomSource random = RandomSource.create();
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

                    if (random.nextFloat() < p) {
                        toDestroyGlass.add(pos.asLong());
                    }
                }


            } catch (Throwable t) {
                // au moindre souci on ignore cette position
            }
        }

        // destruction uniquement sur le thread serveur
        final int perTick = 256;
        this._destroyCursor = 0;
        if (!toDestroy.isEmpty()) {
           AsyncAtmosphereService.runOnMainThread(
                   ()-> processLeafLogDestruction(level, toDestroy, perTick)
           );
        }
        if (!toDestroyGlass.isEmpty()) {
           GlassDamageManager.damageGlass(level, toDestroyGlass);
        }
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

    @Nullable
    private TornadoDescriptor findDescriptor() {
        if (!(this.cloudRegion instanceof ITornadoRegion tornadoRegion)) {
            return null;
        }
        return tornadoRegion.findTornado(this.id);
    }
}
