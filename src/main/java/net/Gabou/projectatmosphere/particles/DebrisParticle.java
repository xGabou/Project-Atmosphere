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
    private final float verticalDrift;
    private final float radialJitter;
    private final int band;
    private final float startAngle;

    protected DebrisParticle(ClientLevel level, TornadoInstance tornado, double radius, double height, float angularSpeed,
                             float verticalDrift, float radialJitter, int band) {
        super(level, tornado.position.x, tornado.position.y + height, tornado.position.z, 0, 0, 0);
        this.tornadoRef = new WeakReference<>(tornado);
        this.radius = radius;
        this.baseY = height;
        this.angularSpeed = angularSpeed;
        this.verticalDrift = verticalDrift;
        this.radialJitter = radialJitter;
        this.band = band;
        this.startAngle = level.random.nextFloat() * 360f;
        this.lifetime = switch (band) {
            case 0 -> 34 + this.random.nextInt(16);
            case 1 -> 42 + this.random.nextInt(18);
            default -> 48 + this.random.nextInt(24);
        };
        this.gravity = 0;
        this.friction = 0.94f;
        float size = switch (band) {
            case 0 -> 0.18f;
            case 1 -> 0.14f;
            default -> 0.24f;
        };
        this.setSize(size, size);
        this.alpha = switch (band) {
            case 0 -> 0.85f;
            case 1 -> 0.72f;
            default -> 0.58f;
        };
    }

    @Override
    public void tick() {
        TornadoInstance tornado = tornadoRef.get();
        if (tornado == null) {
            remove();
            return;
        }
        float lifeProgress = this.lifetime <= 0 ? 1.0F : (float) this.age / (float) this.lifetime;
        float bandSpinBoost = switch (this.band) {
            case 0 -> 1.25F;
            case 1 -> 1.85F;
            default -> 1.05F;
        };
        float angle = startAngle
                + tornado.getTwist() * 72.0F * bandSpinBoost
                + (tornado.getLifetimeSeconds() * 20 + this.age) * angularSpeed;
        double rad = Math.toRadians(angle);
        double rise = this.baseY + this.age * this.verticalDrift + switch (this.band) {
            case 0 -> Math.sin((this.age + this.startAngle) * 0.10F) * 0.24D;
            case 1 -> lifeProgress * 1.8D;
            default -> lifeProgress * 2.6D;
        };
        double pulse = Math.sin((this.age + this.startAngle) * 0.12F) * this.radialJitter
                + Math.cos((this.age + this.startAngle) * 0.05F + this.band) * this.radialJitter * 0.45D;
        double spiralOffset = switch (this.band) {
            case 0 -> -lifeProgress * this.radius * 0.10D;
            case 1 -> -lifeProgress * this.radius * 0.24D;
            default -> lifeProgress * this.radius * 0.10D;
        };
        double orbitRadius = Math.max(0.2D, this.radius + pulse + spiralOffset);
        this.alpha = switch (this.band) {
            case 0 -> 0.85F - lifeProgress * 0.35F;
            case 1 -> 0.74F - lifeProgress * 0.28F;
            default -> 0.60F - lifeProgress * 0.22F;
        };
        setPos(
                tornado.position.x + Math.cos(rad) * orbitRadius,
                tornado.position.y + rise,
                tornado.position.z + Math.sin(rad) * orbitRadius
        );
        this.yd += this.verticalDrift * 0.4;
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
            DebrisParticle particle = new DebrisParticle(
                    level,
                    data.tornado(),
                    data.radius(),
                    data.height(),
                    data.angularSpeed(),
                    data.verticalDrift(),
                    data.radialJitter(),
                    data.band()
            );
            try {
                particle.pickSprite(sprites);
            } catch (Throwable ignored) {
                // SpriteSet may not be ready yet
            }
            return particle;
        }
    }
}

