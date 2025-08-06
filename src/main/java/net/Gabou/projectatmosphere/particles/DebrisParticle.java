package net.Gabou.projectatmosphere.particles;

import net.Gabou.projectatmosphere.modules.tornado.TornadoInstance;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;

import java.lang.ref.WeakReference;

/**
 * Particle representing debris swirling around a tornado.
 */
public class DebrisParticle extends TextureSheetParticle {
    private final WeakReference<TornadoInstance> tornadoRef;
    private final double radius;
    private final double baseY;
    private final float angularSpeed;
    private final float startAngle;

    protected DebrisParticle(ClientLevel level, TornadoInstance tornado, double radius, double height, float angularSpeed) {
        super(level, tornado.position.x, tornado.position.y + height, tornado.position.z, 0, 0, 0);
        this.tornadoRef = new WeakReference<>(tornado);
        this.radius = radius;
        this.baseY = height;
        this.angularSpeed = angularSpeed;
        this.startAngle = level.random.nextFloat() * 360f;
        this.lifetime = 40 + this.random.nextInt(20);
        this.gravity = 0;
        this.friction = 0.95f;
        this.setSize(0.2f, 0.2f);
    }

    @Override
    public void tick() {
        TornadoInstance tornado = tornadoRef.get();
        if (tornado == null) {
            remove();
            return;
        }
        float angle = startAngle + (tornado.getLifetimeSeconds() * 20 + this.age) * angularSpeed;
        double rad = Math.toRadians(angle);
        setPos(
                tornado.position.x + Math.cos(rad) * radius,
                tornado.position.y + baseY,
                tornado.position.z + Math.sin(rad) * radius
        );
        this.yd += 0.02;
        super.tick();
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<DebrisParticleData> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(DebrisParticleData data, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            DebrisParticle particle = new DebrisParticle(level, data.tornado(), data.radius(), data.height(), data.angularSpeed());
            particle.pickSprite(sprites);
            return particle;
        }
    }
}

