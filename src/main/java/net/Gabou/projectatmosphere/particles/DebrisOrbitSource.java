package net.Gabou.projectatmosphere.particles;

import net.minecraft.world.phys.Vec3;

/** Backend-neutral live transform used by client debris particles. */
public interface DebrisOrbitSource {
    DebrisOrbitSource NONE = new DebrisOrbitSource() {
        @Override
        public Vec3 renderPosition(float partialTick) {
            return Vec3.ZERO;
        }

        @Override
        public float renderBottomY(float partialTick) {
            return 0.0F;
        }

        @Override
        public float twist() {
            return 0.0F;
        }

        @Override
        public float lifetimeSeconds() {
            return 0.0F;
        }

        @Override
        public boolean isAlive() {
            return false;
        }
    };

    Vec3 renderPosition(float partialTick);

    float renderBottomY(float partialTick);

    float twist();

    float lifetimeSeconds();

    boolean isAlive();
}
