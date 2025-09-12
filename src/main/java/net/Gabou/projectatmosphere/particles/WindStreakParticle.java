package net.Gabou.projectatmosphere.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;

public class WindStreakParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    protected WindStreakParticle(ClientLevel world, double x, double y, double z,
                                 double xSpeed, double ySpeed, double zSpeed,
                                 SpriteSet sprites) {
        super(world, x, y, z, xSpeed, ySpeed, zSpeed);
        this.sprites = sprites;

        // No gravity, very low friction so it keeps its wind velocity
        this.gravity = 0.0f;
        this.friction = 0.99f;

        // Initial velocity = wind direction
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;

        this.lifetime = 20 + random.nextInt(20); // streak lives for 1–2 seconds
        this.setSize(0.05f, 0.2f); // thin, tall rectangle
        this.setSpriteFromAge(sprites);

        this.alpha = 0.0f; // start invisible, fade in
    }

    @Override
    public void tick() {
        super.tick();

        // Fade in first quarter of life
        if (this.age < this.lifetime / 4) {
            this.alpha = (float)this.age / (this.lifetime / 4f);
        }
        // Fade out last quarter of life
        else if (this.age > this.lifetime * 3 / 4) {
            this.alpha = (this.lifetime - this.age) / (this.lifetime / 4f);
        }

        // Add small wobble to make streaks feel alive
        this.xd += (random.nextFloat() - 0.5f) * 0.001f;
        this.zd += (random.nextFloat() - 0.5f) * 0.001f;
    }

    @Override
    public float getQuadSize(float partialTicks) {
        // Stretch particle forward in its movement direction
        return 0.3f + ((float) age / lifetime) * 0.2f;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet spriteSet;

        public Provider(SpriteSet spriteSet) {
            this.spriteSet = spriteSet;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel world,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new WindStreakParticle(world, x, y, z, xSpeed, ySpeed, zSpeed, spriteSet);
        }
    }
}
