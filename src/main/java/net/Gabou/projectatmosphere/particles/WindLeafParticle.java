package net.Gabou.projectatmosphere.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

public class WindLeafParticle extends TextureSheetParticle {

    private final SpriteSet sprites;

    protected WindLeafParticle(ClientLevel world, double x, double y, double z,
                               double xSpeed, double ySpeed, double zSpeed,
                               SpriteSet sprites) {
        super(world, x, y, z, xSpeed, ySpeed, zSpeed);
        this.sprites = sprites;
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        this.gravity = 0.01f;
        this.friction = 0.9f;
        this.lifetime = 40 + this.random.nextInt(30); // 2.5 to 3.5 seconds
        this.setSize(0.1F, 0.1F);
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(sprites);
        // Optional: add some rotation or extra sway
        this.yd -= 0.002; // subtle downward drift
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
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
            return new WindLeafParticle(world, x, y, z, xSpeed, ySpeed, zSpeed, spriteSet);
        }
    }
}
