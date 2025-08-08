package net.Gabou.projectatmosphere.modules.tornado;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import net.Gabou.projectatmosphere.modules.core.WindVector;

public class TornadoInstance {

    public Vec3 position;
    public final long spawnTime;
    public final float radius;
    public final WindVector wind;

    private float angularSpeed = 0.15f; // ~0.15 rad/tick
    private long lastDemolitionCheck = 0L;
    private final long demolitionIntervalMs = 1000L; // Check every 1 sec

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
     * Called each tick from tornado manager to handle sound & destruction
     */
    public void tick(Level level) {
        long now = System.currentTimeMillis();

        if (now - lastDemolitionCheck >= demolitionIntervalMs) {
            lastDemolitionCheck = now;

            // Sound effect
            playDemolitionSound(level);

            // Future: Add block destruction, particles, debris here
        }
    }

    private void playDemolitionSound(Level level) {
        BlockPos center = BlockPos.containing(position);

        level.playLocalSound(
                center.getX(), center.getY(), center.getZ(),
                SoundEvents.GENERIC_EXPLODE,
                SoundSource.WEATHER,
                2.0f, // Volume
                0.5f + level.getRandom().nextFloat() * 0.4f, // Pitch variation
                false
        );
    }
}
