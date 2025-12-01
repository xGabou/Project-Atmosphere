package net.Gabou.projectatmosphere.particles;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;

public class WindLeafParticle extends TextureSheetParticle {

    private final SpriteSet sprites;

    private static final double DESIRED_TRAVEL_BLOCKS = 40.0;
    protected WindLeafParticle(ClientLevel world, double x, double y, double z,
                               double xSpeed, double ySpeed, double zSpeed,
                               SpriteSet sprites) {
        super(world, x, y, z, xSpeed, ySpeed, zSpeed);
        this.sprites = sprites;

        
        this.gravity = 0.002f;
        this.friction = 0.98f;

        this.xd = xSpeed;
        this.yd = ySpeed * 0.3f + (random.nextFloat() * 0.02f); 
        this.zd = zSpeed;

        this.lifetime = 200; 
        this.setSize(0.1f, 0.1f);
        try {
            this.setSpriteFromAge(sprites);
        } catch (Throwable ignored) {
            // SpriteSet may not be ready yet; will retry in tick()
        }

        
        this.roll = random.nextFloat() * (float)Math.PI * 2.0f; 
        this.oRoll = this.roll;
    }


    @Override
    public void tick() {
        super.tick();
        // Retry sprite binding in case atlas loaded after construction
        try {
            this.setSpriteFromAge(this.sprites);
        } catch (Throwable ignored) {
        }


        Vec3 push = WindParticlePusher.computeWindPush(this.level, new Vec3(this.x, this.y, this.z));
        if (push.lengthSqr() > 0.0) {
            this.xd += push.x;
            this.yd += push.y;
            this.zd += push.z;
        }


        this.oRoll = this.roll;
        this.roll += 0.02f + random.nextFloat() * 0.01f;

        
        if (this.age % 5 == 0) {
            this.yd += (random.nextFloat() - 0.5f) * 0.003f;
        }

        
        if (this.age > this.lifetime - 40) {
            this.alpha = (this.lifetime - this.age) / 40.0f;
        }
    }
//    @Override
//    public float getQuadSize(float partialTicks) {
//        float flutter = (float) Math.sin((this.age + partialTicks) * 0.2f) * 0.1f;
//        return super.getQuadSize(partialTicks) + flutter;
//    }



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
            return new WindLeafParticle(world, x, y, z, xSpeed, ySpeed, zSpeed, spriteSet);
        }
    }
}
