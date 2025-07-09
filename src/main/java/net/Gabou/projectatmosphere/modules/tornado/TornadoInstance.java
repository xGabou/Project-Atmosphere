package net.Gabou.projectatmosphere.modules.tornado;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class TornadoInstance {
    public final Vec3 position;
    public final long spawnTime;
    public final float radius;

    public TornadoInstance(Vec3 position, float radius) {
        this.position = position;
        this.radius = radius;
        this.spawnTime = System.currentTimeMillis();
    }

    public float getLifetimeSeconds() {
        return (System.currentTimeMillis() - spawnTime) / 1000f;
    }
}
