package net.Gabou.projectatmosphere.particles;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;

public class WindLeafParticle extends TextureSheetParticle {

    private final SpriteSet sprites;

    private static final double DESIRED_TRAVEL_BLOCKS = 40.0;
    protected WindLeafParticle(ClientLevel world, double x, double y, double z,
                               double xSpeed, double ySpeed, double zSpeed,
                               SpriteSet sprites) {
        super(world, x, y, z, xSpeed, ySpeed, zSpeed);
        this.sprites = sprites;
        this.gravity = 0.01f;
        this.friction = 0.9f;
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;

        // Random lifetime between 40-70 ticks (2–3.5 seconds)
        double vMag = Math.sqrt(this.xd * this.xd + this.zd * this.zd);
        if (vMag > 0) {
            // #ticks needed = distance / speedPerTick
            this.lifetime = (int) Math.ceil(DESIRED_TRAVEL_BLOCKS / vMag);
        } else {
            // fallback if somehow vx=0
            this.lifetime = 200; // ~10 sec
        }

        // optional: add a bit of random jitter (+/- 10%)
        int jitter = (int)(this.lifetime * 0.1);
        this.lifetime += this.random.nextInt(jitter * 2 + 1) - jitter;

        // size, initial sprite, etc…
        float size = 0.08f + this.random.nextFloat() * 0.05f;
        this.setSize(size, size);
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        // Animate particle sprite (if animated textures used)
        this.setSpriteFromAge(sprites);

        // Gentle downward drift adjustment (if desired)
        this.yd -= 0.002;

        // Optional fade-out at end-of-life
        if (this.age > this.lifetime * 0.7) {
            this.alpha = 1.0F - (float)(this.age - (this.lifetime * 0.7)) / (this.lifetime * 0.3F);
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    // Factory provider for particle creation
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet spriteSet;

        public Provider(SpriteSet spriteSet) {
            this.spriteSet = spriteSet;
        }


        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel world,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new WindLeafParticle(world, x, y, z, xSpeed, ySpeed, zSpeed, spriteSet);
        }
    }
}
